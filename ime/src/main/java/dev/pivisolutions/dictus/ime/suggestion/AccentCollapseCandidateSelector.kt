package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.language.LanguageProfile
import java.util.Locale
import kotlin.math.ln

/** Pure, bounded expansion and dictionary-backed selection for profile accent/collapse rules. */
object AccentCollapseCandidateSelector {
    const val MAX_CANDIDATES = 32
    private const val ENCODED_SCORE_MAX = 65_535.0
    private const val KNOWN_INPUT_DOMINANCE_RATIO = 5.0

    data class WordFrequency(val encodedScore: Int)

    data class Selection(
        val word: String,
        val knownInputDominance: Boolean,
    )

    /** Generates single/double accent substitutions and one collapse at a time, up to a hard cap. */
    fun generate(input: String, profile: LanguageProfile): List<String> {
        if (input.isEmpty()) return emptyList()
        val seed = input.lowercase(Locale.ROOT)
        val result = linkedSetOf<String>()
        fun add(candidate: String) {
            if (candidate != seed && result.size < MAX_CANDIDATES) result += candidate
        }

        val accentSubstitutions = buildList {
            seed.forEachIndexed { index, character ->
                profile.accentMap[character].orEmpty().forEach { replacement ->
                    if (replacement != character) add(index to replacement)
                }
            }
        }
        accentSubstitutions.forEach { (index, replacement) ->
            add(seed.replaceRange(index, index + 1, replacement.toString()))
        }
        accentSubstitutions.forEachIndexed { firstIndex, (firstPosition, firstReplacement) ->
            accentSubstitutions.drop(firstIndex + 1).forEach { (secondPosition, secondReplacement) ->
                if (firstPosition != secondPosition) {
                    val characters = seed.toCharArray()
                    characters[firstPosition] = firstReplacement
                    characters[secondPosition] = secondReplacement
                    add(characters.concatToString())
                }
            }
        }

        profile.collapseRules.forEach { rule ->
            if (rule.from.isEmpty() || rule.from == rule.to) return@forEach
            var start = 0
            while (start <= seed.length - rule.from.length && result.size < MAX_CANDIDATES) {
                val occurrence = seed.indexOf(rule.from, start)
                if (occurrence < 0) break
                add(seed.replaceRange(occurrence, occurrence + rule.from.length, rule.to))
                start = occurrence + 1
            }
        }
        return result.toList()
    }

    /** Selects the highest encoded-frequency candidate that satisfies known-input safety. */
    fun select(
        input: String,
        profile: LanguageProfile,
        inputKnown: Boolean,
        maxRawFrequency: Long,
        lookup: (String) -> WordFrequency?,
    ): Selection? {
        val inputScore = if (inputKnown) lookup(input)?.encodedScore ?: return null else 0
        val delta = if (inputKnown) dominanceScoreDelta(maxRawFrequency) else 0
        val best = generate(input, profile)
            .mapNotNull { word -> lookup(word)?.let { word to it.encodedScore } }
            .filter {
                !inputKnown || it.second.toLong() > inputScore.toLong() + delta.toLong()
            }
            .maxByOrNull { it.second }
            ?: return null
        return Selection(best.first, knownInputDominance = inputKnown)
    }

    /** Integer boundary equivalent to score > inputScore + 65535*ln(5)/ln(maxRaw+1). */
    fun dominanceScoreDelta(maxRawFrequency: Long): Int {
        if (maxRawFrequency <= 1L) return Int.MAX_VALUE
        return (ENCODED_SCORE_MAX * ln(KNOWN_INPUT_DOMINANCE_RATIO) /
            ln(maxRawFrequency.toDouble() + 1.0)).toInt()
    }
}
