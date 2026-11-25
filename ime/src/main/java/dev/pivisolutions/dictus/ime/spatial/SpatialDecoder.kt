// Ported from Hex (github.com/anomalyco/hex, commit be2f394,
// android/app/src/main/java/ly/anoma/hex/typing/SpatialDecoder.kt), MIT,
// Copyright (c) 2026 Christopher Langton (#385).
//
// Kept as Hex has it: the tap costs, the beam search with one edit, the two-word readings,
// the margins and Hex's tuned parameters. Changed, as the iOS port (getdictus/dictus-ios#1013)
// does: the literal is the word as the document has it, apostrophes included, and Dictus's trie
// says whether it is a word; an elided clitic ("l'", "j'") ends a two-word reading with no
// space after it and never ends one; `cost` scores a given word on the same taps, for the face-off
// with the trie. Left out: the never-suggest list.
package dev.pivisolutions.dictus.ime.spatial

import java.text.Normalizer

/** Positions are all in the same layout coordinates. The IME supplies measured key centres;
 * the replay supplies the original test layout, not a guessed phone screen size. */
internal data class KeyPoint(val x: Double, val y: Double)
internal class KeyGrid(val centres: Map<Char, KeyPoint>, val pitchX: Double, val pitchY: Double) {
    val bottomY = centres.values.maxOf { it.y }
    // R1's reference pitch is 39.5 × 56. Keep its vertical spread and its 6-nat split
    // prior on that layout. On shorter rows the travel to space is shorter: scale its
    // Gaussian (squared-distance) cost, down to the Android 2%-omission prior.
    private val referenceY = pitchX * (56.0 / 39.5)
    val verticalSpread = .26 * maxOf(pitchY, referenceY)
    private val rowScale = minOf(1.0, pitchY / referenceY)
    val missedSpaceCost = maxOf(3.9, 6.0 * rowScale * rowScale)
}
internal class SpatialDecoder(val model: TypingModel) {
    class Tap(val cost: DoubleArray, val choices: IntArray, val belowLetters: Boolean, val missedSpaceCost: Double)
    data class Reading(val text: String, val cost: Double, val frequency: Int)
    data class Decision(val replace: String?, val readings: List<Reading>)
    // Google Books English has richer word pairs; these are the shared iOS R1 weights.
    private val lmWeight = if (model.language == "en") 1.5 else 1.25
    fun score(grid: KeyGrid, x: Double, y: Double): Tap {
        val costs = DoubleArray(model.alphabet + 1) { Double.POSITIVE_INFINITY }
        val sy = grid.verticalSpread
        for ((c, p) in grid.centres) {
            val code = model.code(c)
            if (code == 0) continue
            val dx = (x - p.x) / (grid.pitchX * .29)
            val dy = (y - p.y) / sy
            costs[code] = .5 * (dx * dx + dy * dy)
        }
        val min = costs.min()
        for (i in 1 until costs.size) if (!costs[i].isFinite() && model.bases[i] != i) costs[i] = costs[model.bases[i]] + 1
        for (i in 1 until costs.size) costs[i] -= min
        val choices = (1 until costs.size).filter { costs[it] <= 9.0 }.sortedBy { costs[it] }.toIntArray()
        return Tap(costs, choices, y > grid.bottomY, grid.missedSpaceCost)
    }

    private class State(val lo: Int, val hi: Int, val tap: Int, val edits: Int, val cost: Double)
    private data class Found(val word: Int, val cost: Double)
    // Keep at most 48 promising prefixes at each spelling depth. One extra, missing or
    // transposed letter is possible; an untyped apostrophe costs nothing.
    private fun search(list: WordList, taps: List<Tap>, from: Int, end: Int, edits: Int): List<Found> {
        val depthLimit = end - from + edits + 2
        val layers = Array(depthLimit + 1) { ArrayList<State>() }
        layers[0].add(State(0, list.words, from, 0, 0.0))
        val found = HashMap<Int, Double>()
        for (d in 0..depthLimit) {
            val layer = layers[d]
            if (layer.size > 48) { layer.sortBy { it.cost }; layer.subList(48, layer.size).clear() }
            var i = 0
            while (i < layer.size) {
                val s = layer[i++]
                if (s.tap == end && list.length(s.lo) == d && s.cost < (found[s.lo] ?: Double.POSITIVE_INFINITY))
                    found[s.lo] = s.cost
                if (d == depthLimit) continue
                fun push(c: Int, next: Int, e: Int, cost: Double) {
                    val child = list.child(s.lo, s.hi, d, c)
                    if (child != 0L) layers[d + 1].add(State((child ushr 32).toInt(), child.toInt(), next, e, cost))
                }
                val t = s.tap
                if (t < end) for (c in taps[t].choices) push(c, t + 1, s.edits, s.cost + taps[t].cost[c])
                if (d > 0 && model.apostrophe != 0) push(model.apostrophe, t, s.edits, s.cost)
                if (s.edits >= edits) continue
                for (c in 1..model.alphabet) if (c != model.apostrophe) push(c, t, s.edits + 1, s.cost + 7)
                if (t < end) layer.add(State(s.lo, s.hi, t + 1, s.edits + 1, s.cost + 7))
                if (t + 1 >= end || d + 2 > depthLimit) continue
                val first = taps[t + 1]; val second = taps[t]
                for (ai in 0 until minOf(3, first.choices.size)) {
                    val a = first.choices[ai]; val r = list.child(s.lo, s.hi, d, a)
                    if (r == 0L) continue
                    for (bi in 0 until minOf(3, second.choices.size)) {
                        val b = second.choices[bi]; if (b == a) continue
                        val child = list.child((r ushr 32).toInt(), r.toInt(), d + 1, b)
                        if (child != 0L) layers[d+2].add(State((child ushr 32).toInt(), child.toInt(), t+2,
                            s.edits+1, s.cost + first.cost[a] + second.cost[b] + 5))
                    }
                }
            }
        }
        return found.map { Found(it.key, it.value) }
    }

    /** [typed] is the letters, one per tap, apostrophes left out; [literal] is the word as the
     * document has it ("aujourd'hui"). [literalIsValid] is the dictionary's word on it. */
    fun decide(typed: String, literal: String, taps: List<Tap>, previous: Int, literalIsValid: Boolean): Decision {
        val none = Decision(null, emptyList())
        if (typed.length !in 2..32 || taps.size != typed.length || typed.any { model.code(it) == 0 || it == '\'' }) return none
        val literalWord = model.index(literal)
        val knownLiteral = literalWord >= 0 || literalIsValid
        var cost = lmWeight * when {
            literalWord >= 0 -> model.languageCost(previous, literalWord)
            knownLiteral -> 15.0
            else -> 22.0
        }
        for (i in taps.indices) cost += taps[i].cost[model.code(typed[i])]
        if (!cost.isFinite()) return none
        val readings = ArrayList<Reading>()
        // iOS applies the language score only to the five best spatial readings.
        for ((w, spatial) in search(model, taps, 0, taps.size, 1).sortedWith(spatialOrder).take(5)) if (w != literalWord)
            readings.add(Reading(model.text(w), spatial + lmWeight * model.languageCost(previous, w), model.frequencyCost(w)))
        if (taps.size >= 3) {
            fun alone(f: Found) = model.length(f.word) > 1 || model.frequencyCost(f.word) < 56
            fun half(a: Int, b: Int) = search(model, taps, a, b, 0).sortedWith(spatialOrder)
                .take(3).filter(::alone)
            for (k in 1 until taps.size) {
                val left = half(0, k)
                if (left.isEmpty()) continue
                val stops = if (k + 1 < taps.size && taps[k].belowLetters) k..k+1 else k..k
                for (start in stops) {
                    // A clitic ("l'") ends a split only before a word, never at its end.
                    val right = half(start, taps.size).filter { !model.text(it.word).endsWith('\'') }
                    for (l in left) for (r in right) {
                        val lm = model.languageCost(previous, l.word) + model.languageCost(l.word, r.word)
                        readings.add(Reading(join(model.text(l.word), model.text(r.word)), l.cost + r.cost + taps[k].missedSpaceCost + lmWeight * lm,
                            model.frequencyCost(l.word) + model.frequencyCost(r.word)))
                    }
                }
            }
        }
        readings.removeAll { it.text == literal }
        readings.sortWith(compareBy<Reading> { it.cost }.thenBy { it.text })
        val best = readings.firstOrNull() ?: return none
        val margin = if (!knownLiteral || foldAccents(best.text) == foldAccents(literal)) 2.0 else 7.0
        return Decision(if (cost - best.cost >= margin) best.text else null, readings.asSequence().filter { it.cost < cost }.distinctBy { it.text }.take(3).toList())
    }

    /** What [decide] would score [reading] at for these taps, in the same nats as a reading:
     * its best alignment within one edit, plus the word-pair cost. Null when the model lacks
     * the word or no alignment fits. The question Hex's iOS `replaces(with:)` asks. */
    fun cost(reading: String, taps: List<Tap>, previous: Int): Double? {
        val word = model.index(reading)
        if (word < 0 || taps.isEmpty()) return null
        val spatial = search(OneWord(IntArray(reading.length) { model.code(reading[it]) }), taps, 0, taps.size, 1)
            .minOfOrNull { it.cost } ?: return null
        return spatial + lmWeight * model.languageCost(previous, word)
    }

    /** One word, searched as a word list. */
    private class OneWord(val codes: IntArray) : WordList {
        override val words = 1
        override fun length(word: Int) = codes.size
        override fun child(lo: Int, hi: Int, depth: Int, c: Int): Long =
            if (depth < codes.size && codes[depth] == c) (lo.toLong() shl 32) or hi.toLong() else 0
    }

    companion object {
        private val spatialOrder = compareBy<Found> { it.cost }.thenBy { it.word }
        /** Two words of a split. An elided clitic takes no space after it ("j'" + "ai"). */
        fun join(left: String, right: String) = if (left.endsWith('\'')) left + right else "$left $right"
        fun foldAccents(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).filter { it !in '̀'..'̈' || it == '̃' }
    }
}
