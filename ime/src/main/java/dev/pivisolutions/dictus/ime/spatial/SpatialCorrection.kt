package dev.pivisolutions.dictus.ime.spatial

import dev.pivisolutions.dictus.ime.input.AutocorrectToken
import java.util.Locale

/**
 * The space bar's question to the spatial decoder (#385), for one word as the editor has it:
 * its case, its elided clitic ("J'aa"), and the taps that typed it. The rules are the iOS port's
 * (getdictus/dictus-ios#1013, `SpatialCorrection` and `TextPredictionEngine`); the case rules
 * are Hex's `TypingWord`.
 */
internal object SpatialCorrection {
    /** What the rest of the engine knows about the word. */
    data class Facts(
        /** The trie knows the word: the decoder then needs a higher margin to replace it. */
        val isKnown: Boolean,
        /** The user taught the keyboard this word: the decoder is not asked (ADR 0004 on iOS). */
        val isLearned: Boolean,
        /** The trie's correction from the letters, or null when the trie keeps the word. */
        val trieCorrection: String?,
    )

    /**
     * The decoder's replacement for [word], in the word's case, or null to leave the word to the
     * trie. [taps] holds one point per character of [word], null where no tap typed it.
     *
     * When the trie corrects the word too, to another word, the decoder scores the trie's
     * correction on the same taps and keeps whichever costs less. A correction it cannot score
     * (more than one edit away, two words, a word its model lacks) stays the trie's: on Pierre's
     * iOS sessions the trie was right in most such cases, and the decoder's strength is where the
     * trie keeps the word.
     */
    fun correct(
        word: String,
        previousWord: String?,
        taps: List<KeyPoint?>,
        grid: KeyGrid,
        decoder: SpatialDecoder,
        facts: Facts,
    ): String? {
        if (facts.isLearned || taps.size != word.length) return null
        // A capital inside the word (an acronym, "iPhone") is not the decoder's to read.
        if (word.drop(1).any(Char::isUpperCase)) return null
        val lowered = word.lowercase(Locale.ROOT).replace('’', '\'')
        if (lowered.length != word.length) return null
        val model = decoder.model
        // Google Books keeps most elisions whole ("j'ai", "qu'il", "c'est") and splits off "l'"
        // and "d'" ("l'" + "homme"). So a word after a clitic the model has on its own is read
        // alone, with the clitic as the word before; any other word is read whole, its
        // apostrophes left out of the taps for the search to put back for free.
        val apostrophe = lowered.lastIndexOf('\'')
        val cliticEnd = if (apostrophe >= 0 && apostrophe + 1 < lowered.length &&
            model.index(lowered.substring(0, apostrophe + 1)) >= 0
        ) apostrophe + 1 else 0
        val kept = (cliticEnd until lowered.length).filter { lowered[it] != '\'' }
        if (kept.size < 2) return null
        val points = kept.map { taps[it] ?: return null }
        val literal = lowered.substring(cliticEnd)
        val clitic = lowered.substring(0, cliticEnd)
        val previous = model.index(clitic.ifEmpty { previousWord?.lowercase(Locale.ROOT)?.replace('’', '\'') ?: "" })
        val scored = points.map { decoder.score(grid, it.x, it.y) }
        val decision = decoder.decide(kept.map(lowered::get).joinToString(""), literal, scored, previous, facts.isKnown)
        val replacement = decision.replace ?: return null
        // A change of accents alone is the accent rules' to make, which know French grammar the
        // word-pair model does not; and an accent typed with the accent key was meant ("sôt" is
        // "sont", never "sot").
        if (SpatialDecoder.foldAccents(replacement) == SpatialDecoder.foldAccents(literal)) return null

        val trie = facts.trieCorrection?.lowercase(Locale.ROOT)?.replace('’', '\'')
        if (trie != null) {
            // The same word up to its accents: the accents are the trie's, whose French rules know
            // "suis arrivé" where the word-pair model may not.
            if (SpatialDecoder.foldAccents(trie) == SpatialDecoder.foldAccents(clitic + replacement)) return null
            // The trie's word in the decoder's terms: what follows the same clitic.
            if (!trie.startsWith(clitic)) return null
            val best = decision.readings.first().cost
            val rival = decoder.cost(trie.substring(clitic.length), scored, previous) ?: return null
            if (best >= rival) return null
        }

        val whole = word.substring(0, cliticEnd) + replacement
        if (!whole.split(' ').all(AutocorrectToken::isLetterToken)) return null
        return if (word.first().isUpperCase()) whole.replaceFirstChar { it.uppercaseChar() } else whole
    }
}
