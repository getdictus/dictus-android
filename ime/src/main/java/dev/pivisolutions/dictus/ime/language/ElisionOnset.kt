package dev.pivisolutions.dictus.ime.language

import java.util.Locale

/**
 * What may follow an elision prefix: French elides before a vowel or a mute h only, so
 * `d'accord`, `l'homme`, `j'y` are words and `d'votre` never is.
 *
 * Every place that glues a prefix onto a word asks [allows] before proposing the result. Without
 * it, `dvotre` came back as `d'votre`. Port of iOS `ElisionOnset` (dictus-ios #1035).
 *
 * The rule only rejects: a word starting with a letter outside [initials], after one of
 * [prefixes]. A prefix that is not listed (English `don'`, `it'`), a word with no apostrophe and a
 * language without this value are left exactly as they were.
 */
data class ElisionOnset(
    /** The elision prefixes, lowercase, each ending with its apostrophe: `d'`, `qu'`. */
    val prefixes: Set<String>,
    /** The lowercase letters an elided word may start with: the vowels, accented or not, and h. */
    val initials: Set<Char>,
) {
    /** Whether [word] may follow [prefix]. True when [prefix] is null or not an elision prefix. */
    fun allows(word: String, prefix: String?): Boolean {
        if (prefix == null || normalized(prefix) !in prefixes) return true
        val first = word.lowercase(Locale.ROOT).firstOrNull() ?: return true
        if (!first.isLetter()) return true
        return first in initials
    }

    /**
     * Whether a whole word, prefix included, is allowed: `l'homme` is, `d'votre` is not. The
     * prefix is everything up to the last apostrophe.
     */
    fun allows(word: String): Boolean {
        val normalized = normalized(word)
        val apostrophe = normalized.lastIndexOf('\'')
        if (apostrophe < 0) return true
        return allows(normalized.substring(apostrophe + 1), normalized.substring(0, apostrophe + 1))
    }

    /** Lowercase, with the typographic apostrophe read as the straight one. */
    private fun normalized(text: String): String =
        text.lowercase(Locale.ROOT).replace('’', '\'')
}

/** Whether [word] may follow [prefix] in this language. Always true without [LanguageProfile.elisionOnset]. */
fun LanguageProfile.allowsElided(word: String, prefix: String?): Boolean =
    elisionOnset?.allows(word, prefix) ?: true

/** Whether a whole word, prefix included, is allowed in this language: `d'votre` is not in French. */
fun LanguageProfile.allowsElision(word: String): Boolean =
    elisionOnset?.allows(word) ?: true
