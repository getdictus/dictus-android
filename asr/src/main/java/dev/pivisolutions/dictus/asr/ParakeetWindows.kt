package dev.pivisolutions.dictus.asr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Splits a long take into windows Parakeet decodes one by one, and stitches their text.
 *
 * WHY (#330): decoded in one block, a 60 s French take drifts into pseudo-English or comes
 * back empty in most runs on the Pixel 4, while takes of a few seconds are fine. iOS never
 * decodes 60 s in one block: FluidAudio cuts about 15 s windows aligned on silences, with
 * 2 s of overlap and a fresh decoder state per window. This is the same scheme.
 *
 * A take of up to [MAX_SEGMENT_S] is one window, decoded as before. A longer one is cut at
 * the quietest 100 ms (RMS energy) near evenly spaced targets, so no segment between two
 * cuts exceeds [MAX_SEGMENT_S]. Each window extends [MARGIN_S] past its cuts on both sides
 * (2 s of overlap), so a word cut at a window edge is still heard whole by its neighbour.
 *
 * Stitching works on whole words and times, not on matching text: a window keeps the
 * words that start between its two cuts, and the margins only give the decoder context.
 * A word decoded on both sides of a cut (it starts right on it) is kept once.
 *
 * Pure functions, no native call: unit tested without sherpa-onnx.
 */
internal object ParakeetWindows {

    const val SAMPLE_RATE = 16_000

    // Longest stretch between two cuts; a take up to this long stays one window.
    const val MAX_SEGMENT_S = 15f

    // Context each window takes past its cuts; twice this is the overlap.
    const val MARGIN_S = 1f

    // How far a cut may move from its even-spacing target to land on a silence.
    private const val SEARCH_BEFORE_S = 2.5f
    private const val SEARCH_AFTER_S = 1.5f

    // Energy is summed over QUIET_FRAMES frames of FRAME samples (5 x 20 ms).
    private const val FRAME = SAMPLE_RATE / 50
    private const val QUIET_FRAMES = 5

    // Two equal words this close at a cut are the same word decoded by both windows.
    private const val SAME_WORD_S = 0.5f

    /**
     * A window of samples [start, end) to decode on its own. Its words starting in
     * [keepFrom, keepUntil) (sample indices of the take) go into the stitched text.
     */
    data class Window(val start: Int, val end: Int, val keepFrom: Int, val keepUntil: Int)

    /** The windows of a take of [samples]: one for a short take. */
    fun plan(samples: FloatArray): List<Window> {
        val cuts = cuts(samples)
        val margin = (MARGIN_S * SAMPLE_RATE).toInt()
        val bounds = listOf(0) + cuts + listOf(samples.size)
        return bounds.zipWithNext().map { (from, until) ->
            Window(
                start = if (from == 0) 0 else max(0, from - margin),
                end = if (until == samples.size) until else min(samples.size, until + margin),
                keepFrom = from,
                keepUntil = until,
            )
        }
    }

    /** Sample indices where the take is cut, in order; empty for a short take. */
    fun cuts(samples: FloatArray): List<Int> {
        val maxSegment = (MAX_SEGMENT_S * SAMPLE_RATE).toInt()
        val cuts = mutableListOf<Int>()
        var previous = 0
        while (samples.size - previous > maxSegment) {
            val remaining = samples.size - previous
            // Even spacing keeps the last window from being a few seconds of tail.
            val parts = ceil(remaining.toDouble() / maxSegment).toInt()
            val target = previous + remaining / parts
            val from = target - (SEARCH_BEFORE_S * SAMPLE_RATE).toInt()
            val until = min(target + (SEARCH_AFTER_S * SAMPLE_RATE).toInt(), previous + maxSegment)
            val cut = quietestPoint(samples, from, until, target)
            cuts += cut
            previous = cut
        }
        return cuts
    }

    // Centre of the quietest QUIET_FRAMES-frame span whose centre lies in [from, until];
    // the one closest to target among equally quiet spans.
    private fun quietestPoint(samples: FloatArray, from: Int, until: Int, target: Int): Int {
        val span = FRAME * QUIET_FRAMES
        var best = target
        var bestEnergy = Double.MAX_VALUE
        var centre = from
        while (centre <= until) {
            val start = max(0, centre - span / 2)
            val end = min(samples.size, start + span)
            var energy = 0.0
            for (i in start until end) energy += samples[i] * samples[i]
            if (energy < bestEnergy || (energy == bestEnergy && abs(centre - target) < abs(best - target))) {
                bestEnergy = energy
                best = centre
            }
            centre += FRAME
        }
        return best
    }

    /** The text of a take from the decode of each of its [windows], in order. */
    fun stitch(windows: List<Window>, decodes: List<ParakeetDecode>): String {
        require(windows.size == decodes.size) { "${windows.size} windows, ${decodes.size} decodes" }
        if (windows.size == 1) return decodes.single().text
        val kept = mutableListOf<Word>()
        windows.zip(decodes).forEach { (window, decode) ->
            val offset = window.start.toFloat() / SAMPLE_RATE
            val from = window.keepFrom.toFloat() / SAMPLE_RATE
            val until = window.keepUntil.toFloat() / SAMPLE_RATE
            // Without a time per token the window cannot be trimmed to its cuts: keep its
            // whole text, which may repeat a word or two of the overlap rather than lose any.
            val words = if (decode.timestamps.size != decode.tokens.size) {
                listOf(Word(decode.text.trim(), from)).filter { it.text.isNotEmpty() }
            } else {
                words(decode).map { it.copy(time = it.time + offset) }.filter { it.time >= from && it.time < until }
            }
            val previous = kept.lastOrNull()
            val first = words.firstOrNull()
            val duplicate = previous != null && first != null &&
                first.time - previous.time < SAME_WORD_S && normalized(first.text) == normalized(previous.text)
            kept += if (duplicate) words.drop(1) else words
        }
        return kept.joinToString(" ") { it.text }
    }

    data class Word(val text: String, val time: Float)

    /**
     * The words of a decode with the start time of their first token. A token starting with
     * « ▁ » (SentencePiece) or a space opens a word; the others, punctuation included,
     * continue the current one.
     */
    fun words(decode: ParakeetDecode): List<Word> {
        val words = mutableListOf<Word>()
        var text = StringBuilder()
        var time = 0f
        decode.tokens.zip(decode.timestamps).forEach { (token, start) ->
            val piece = token.replace('▁', ' ')
            if (piece.startsWith(" ") && text.isNotBlank()) {
                words += Word(text.toString().trim(), time)
                text = StringBuilder()
            }
            if (text.isBlank()) time = start
            text.append(piece)
        }
        if (text.isNotBlank()) words += Word(text.toString().trim(), time)
        return words
    }

    private fun normalized(word: String) = word.lowercase().filter { it.isLetterOrDigit() }
}
