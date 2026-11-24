package dev.pivisolutions.dictus.ime.suggestion

import java.text.Normalizer
import kotlin.math.abs

/** A dictionary word the trie offers for an unknown typed word. */
data class CorrectionCandidate(
    /** The dictionary word, lowercased. */
    val word: String,
    /**
     * Weighted edit cost from the typed word, as the trie scores it: a neighbouring key, a
     * missing accent, two swapped letters, a letter too many or too few.
     */
    val cost: Double,
    /** The trie's frequency, log-encoded to 0..65535. */
    val frequency: Int,
    /** The bigram score of this word after the previous word, 0 without such a pair. */
    val context: Int = 0,
)

/** What the corrector proposes for the word being typed (iOS `CorrectionProposal`, #997). */
sealed interface CorrectionProposal {
    /** Written in place of the typed word on Space. */
    data class Autocorrect(val correction: String, val alternatives: List<String>) : CorrectionProposal

    /**
     * Offered in the bar for a tap, never written on Space: no candidate is sure enough, so the
     * typed word is kept, as Apple's keyboard does.
     */
    data class Suggest(val words: List<String>) : CorrectionProposal
}

/**
 * Ranks the trie's candidates for an unknown word and says whether the best one is sure enough
 * to replace what was typed. Port of iOS `CorrectionRanking` (getdictus/dictus-ios#997, #993).
 *
 * Before, the first correction the trie listed won whatever it was, so two equally close
 * readings (`sats`: `sais` or `sans`; `irning`: `irving`, `inning` or `ironing`) gave whichever
 * came first. The score is the candidate's frequency minus what it costs to reach:
 *
 *     score = frequency / 65535 - COST_WEIGHT * cost
 *
 * - **Context only between near neighbours.** A bigram after the previous word adds to a
 *   candidate's score only when it costs at most [CONTEXT_WINDOW] more than the winner without
 *   context: `an boue` gives `hour`, `vers lidi` keeps `midi` over the more frequent `lui`.
 * - **Close readings are settled.** When the runner-up costs about as much to reach (within
 *   [AMBIGUOUS_COST]) and scores within [AMBIGUOUS_SCORE] of the best, every candidate that does
 *   is a close reading, and the one seen after the previous word wins, then the most frequent:
 *   `sats` gives `sais`. The typed word is kept, and the readings only offered, when there are
 *   more than [MAX_CLOSE_READINGS] of them once accents are dropped (`irning`), or when the pick
 *   scores under [PLAUSIBLE_SCORE] (`reùion`: `réunion` at 0.10), the score of a typed word.
 * - **Total order.** Ties fall to the cheaper candidate, then to the word.
 *
 * The constants are iOS's, fitted on the #954 bench runs (the Android bench of #382 replays the
 * same taps).
 */
object CorrectionRanking {
    const val COST_WEIGHT = 0.35
    const val CONTEXT_WEIGHT = 0.5
    const val CONTEXT_WINDOW = 0.5
    const val AMBIGUOUS_COST = 0.25
    const val AMBIGUOUS_SCORE = 0.12
    const val PLAUSIBLE_SCORE = 0.15
    const val MAX_CLOSE_READINGS = 2
    private const val SCALE = 65_535.0

    data class Decision(
        /** The word to write instead of the typed one, or null to keep what was typed. */
        val replacement: String?,
        /** Every candidate, best first. */
        val ranked: List<String>,
    ) {
        /**
         * The replacement, or, when the typed word is kept, the best two candidates as
         * suggestions only. Null when there is no candidate at all.
         */
        val proposal: CorrectionProposal?
            get() = when {
                replacement != null ->
                    CorrectionProposal.Autocorrect(replacement, ranked.filter { it != replacement }.take(2))
                ranked.isEmpty() -> null
                else -> CorrectionProposal.Suggest(ranked.take(2))
            }
    }

    private data class Scored(val candidate: CorrectionCandidate, val score: Double)

    fun decide(candidates: List<CorrectionCandidate>): Decision {
        val scored = scoredWithContext(candidates)
        if (scored.isEmpty()) return Decision(null, emptyList())
        val ranked = scored.map { it.candidate.word }
        val close = closeReadings(scored)
        if (close.size <= 1) return Decision(ranked.first(), ranked)

        val readings = close.mapTo(mutableSetOf()) { folded(it.candidate.word) }
        val pick = close.minWithOrNull(PICK_ORDER)
        return if (readings.size <= MAX_CLOSE_READINGS && pick != null && pick.score >= PLAUSIBLE_SCORE) {
            Decision(pick.candidate.word, ranked)
        } else {
            Decision(null, ranked)
        }
    }

    /**
     * The accented spellings to offer when the accent pass should leave [typed] alone, because
     * two of them are close readings: `reserve` is `réserve` or `réservé`. Neither is picked by
     * frequency: the accent meant is a matter of grammar the n-gram data does not hold. Null when
     * one spelling is clearly the more likely.
     */
    fun ambiguousAccentSpellings(typed: String, candidates: List<CorrectionCandidate>): List<String>? {
        val letters = folded(typed)
        val spellings = candidates.filter { it.word != typed && folded(it.word) == letters }
        val close = closeReadings(scoredWithContext(spellings))
        return if (close.size > 1) close.take(2).map { it.candidate.word } else null
    }

    /**
     * The best candidate and every other one as easy to reach and about as likely, when the
     * runner-up is one of them; empty otherwise. A clear winner stays one however many rarer
     * neighbours sit near the runner-up (`rins` gives `runs`).
     */
    private fun closeReadings(scored: List<Scored>): List<Scored> {
        if (scored.size < 2 || !isClose(scored[1], scored[0])) return emptyList()
        return scored.filter { isClose(it, scored[0]) }
    }

    private fun isClose(candidate: Scored, best: Scored): Boolean =
        abs(candidate.candidate.cost - best.candidate.cost) <= AMBIGUOUS_COST &&
            best.score - candidate.score < AMBIGUOUS_SCORE

    /** Between close readings: seen after the previous word, more frequent, cheaper, the word. */
    private val PICK_ORDER = compareByDescending<Scored> { it.candidate.context }
        .thenByDescending { it.candidate.frequency }
        .thenBy { it.candidate.cost }
        .thenBy { it.candidate.word }

    private val RANK_ORDER = compareByDescending<Scored> { it.score }
        .thenBy { it.candidate.cost }
        .thenBy { it.candidate.word }

    private fun scoredWithContext(candidates: List<CorrectionCandidate>): List<Scored> {
        val withoutContext = candidates.map { Scored(it, priorScore(it)) }.sortedWith(RANK_ORDER)
        val anchor = withoutContext.firstOrNull()?.candidate ?: return emptyList()
        return withoutContext.map { scored ->
            val candidate = scored.candidate
            if (candidate.context > 0 && candidate.cost <= anchor.cost + CONTEXT_WINDOW) {
                scored.copy(score = scored.score + CONTEXT_WEIGHT * candidate.context / SCALE)
            } else {
                scored
            }
        }.sortedWith(RANK_ORDER)
    }

    private fun priorScore(candidate: CorrectionCandidate): Double =
        candidate.frequency / SCALE - COST_WEIGHT * candidate.cost

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun folded(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
}
