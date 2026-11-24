package dev.pivisolutions.dictus.ime.language

import java.util.Locale

/**
 * A typed word with two correct spellings, where the words before it decide: French `a` (the
 * verb *avoir*) and `à` (the preposition). Port of iOS `ContextualHomograph` (dictus-ios #1017,
 * which takes over #971 and adds #979's sentence start).
 *
 * WHY not the spell checker: neither spelling is a typo, so there is no edit distance to weigh,
 * and `à` is only 2.7× as frequent as `a`, below the accent pass's 5× bar.
 *
 * WHY a table of words and not the n-grams: the French bigrams prefer `à` after most nouns
 * (`le chien a`), because in books a noun is followed by `à` more often than by `a`. On iOS,
 * choosing by bigram score accented 11% of the verb `a` in Tatoeba's French sentences; this table
 * accents 0.07%.
 *
 * One instance is one pair. Each set matches a word whole, by the elision it starts with
 * (`m'aider` matches `m'`) or by what follows the elision (`n'y` matches `y`). The rules run in
 * a fixed order and leave the typed word as it is when none fires, so a context the table does
 * not know costs a missed accent, not a broken verb.
 */
data class ContextualHomograph(
    /** The spelling the user types, e.g. `a`. */
    val typed: String,
    /** The spelling the context can call for, e.g. `à`. */
    val accented: String,
    /** The word right before proves the typed spelling (`il a`, `y a`). */
    val typedAfter: Set<String>,
    /** The word right before, or the two words before joined by a space, prove the accented one. */
    val accentedAfter: Set<String>,
    /** The word two back proves the word right before is a verb (`je pense à`, `est arrivé à`). */
    val accentedTwoAfter: Set<String>,
    /** Opens a relative clause: within three words of one, nothing is accented. */
    val clauseOpeners: Set<String>,
    /** The first word of a sentence takes the accented spelling: `à demain`. */
    val accentedAtSentenceStart: Boolean = false,
) {
    /**
     * The accented spelling when [context], the text before the cursor ending with [word], calls
     * for it, else null.
     */
    fun accentedForm(word: String, context: String): String? {
        if (word.lowercase(Locale.ROOT) != typed || !context.endsWith(word)) return null
        val words = clauseWords(context.dropLast(word.length))
        val previous = words.lastOrNull()
        if (previous == null) {
            // Nothing before it in the clause: only the start of a sentence decides, not a comma
            // (`ce qu'il dit, a choqué`). The keyboard capitalises the first word, so `A` is
            // read as `a` here and keeps its capital.
            if (!accentedAtSentenceStart || !isAtSentenceStart(context, word)) return null
            return if (word == typed) accented else accented.uppercase(Locale.ROOT)
        }
        // Inside a sentence, a capital `A` is left as typed.
        if (word != typed) return null

        // A capital inside a clause is a name, and a name can be the subject (`Marie a`).
        if (words.size > 1 && previous.first().isUpperCase()) return null

        val lowered = words.map { it.lowercase(Locale.ROOT) }
        val previousLowered = lowered.last()
        if (matches(previousLowered, typedAfter)) return null
        if (lowered.dropLast(1).takeLast(3).any { matches(it, clauseOpeners) }) return null

        if (matches(previousLowered, accentedAfter)) return accented
        val twoBack = lowered.getOrNull(lowered.size - 2) ?: return null
        if ("$twoBack $previousLowered" in accentedAfter) return accented
        if (matches(twoBack, accentedTwoAfter)) return accented
        return null
    }

    private fun matches(word: String, set: Set<String>): Boolean {
        if (word in set) return true
        val apostrophe = word.indexOf('\'')
        if (apostrophe < 0) return false
        return word.substring(0, apostrophe + 1) in set || word.substring(apostrophe + 1) in set
    }

    companion object {
        private const val CLAUSE_BREAKS = ".,;:!?…()\"«»\n"
        private const val SENTENCE_ENDS = ".!?…"

        /**
         * The words of the clause [text] ends in: everything after its last punctuation mark,
         * split on whitespace so that `elle-même` stays one word.
         */
        fun clauseWords(text: CharSequence): List<String> {
            val clauseStart = text.indexOfLast { it in CLAUSE_BREAKS } + 1
            return text.substring(clauseStart)
                .replace('’', '\'')
                .replace("​", "")
                .split(Regex("\\s+"))
                .filter(String::isNotEmpty)
        }

        /**
         * Whether [word] starts a sentence in [context]: start of the text, after a newline, or
         * after `. ! ? …`. Port of iOS `ProperNounGuard.isAtSentenceStart`, which answers true
         * when the context does not end with the word.
         */
        fun isAtSentenceStart(context: String, word: String): Boolean {
            if (word.isEmpty() || !context.endsWith(word)) return true
            val preceding = context.dropLast(word.length)
            if (preceding.isEmpty() || preceding.last() == '\n') return true
            val trimmed = preceding.trim()
            return trimmed.isEmpty() || trimmed.last() in SENTENCE_ENDS
        }
    }
}

/**
 * The forced correction of [word] after [previousWord] from [LanguageProfile.contextOverrides],
 * with the typed capital kept, or null (dictus-ios #970).
 *
 * The previous word is matched on its part after the last apostrophe, so `qu'il ma` and `s'il ma`
 * read as `il ma`.
 *
 * WHY a table and not the n-grams: the bigram store keeps each word's most frequent followers,
 * so a zero score says "rare", not "impossible". After `il`, `ma` and `la` both score zero, and
 * only one of them is wrong.
 */
fun LanguageProfile.contextOverride(previousWord: String, word: String): String? {
    val previous = previousWord.lowercase(Locale.ROOT).replace('’', '\'')
    val key = previous.substringAfterLast('\'')
    val override = contextOverrides[key]?.get(word.lowercase(Locale.ROOT)) ?: return null
    return if (word.firstOrNull()?.isUpperCase() == true) {
        override.replaceFirstChar { it.uppercase(Locale.ROOT) }
    } else {
        override
    }
}

/**
 * The spelling the words before call for in place of [word], or null: the homograph pairs
 * first, then the previous-word overrides, as iOS `SupplementaryLexicon.earlyAnswer` orders them.
 * [context] is the text before the cursor and must end with [word].
 */
fun LanguageProfile.contextualSpelling(word: String, context: String): String? {
    if (word.isEmpty() || !context.endsWith(word)) return null
    contextualHomographs.firstNotNullOfOrNull { it.accentedForm(word, context) }?.let { return it }
    if (contextOverrides.isEmpty()) return null
    val previous = ContextualHomograph.clauseWords(context.dropLast(word.length)).lastOrNull()
        ?: return null
    return contextOverride(previous, word)
}
