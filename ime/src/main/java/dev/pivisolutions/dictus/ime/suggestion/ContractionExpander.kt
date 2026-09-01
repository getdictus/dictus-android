package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.ime.language.LanguageProfile
import java.util.Locale

/**
 * Restores the apostrophe French speakers routinely omit: `cest` → `c'est`, `jai` → `j'ai`.
 *
 * WHY this is not left to the fuzzy corrector: an apostrophe is an insertion, so `cest` sits at
 * edit distance 1 from several real words and the trie has no reason to prefer `c'est`. The
 * expansion is instead an exact, high-confidence transformation — a prefix the language declares,
 * plus a suffix the dictionary already contains — which is why it outranks fuzzy candidates.
 *
 * Ported from the iOS `ContractionExpander`, prefix list included, so both keyboards accept the
 * same inputs. The accent fallback is what stops `Cetait` from being corrected to `Était` with
 * the `c` silently dropped.
 */
object ContractionExpander {

    /** Looks a word up in the active dictionary. Kept as a seam so the rules stay unit-testable. */
    fun interface WordLookup {
        fun exists(word: String): Boolean
    }

    /**
     * The contraction [word] was most likely meant to be, or null.
     *
     * Capitalization of the input is carried over to the result — `Cest` at the start of a
     * sentence becomes `C'est`, not `c'est` — while the rest of the word keeps the dictionary's
     * own spelling.
     */
    fun expand(
        word: String,
        profile: LanguageProfile,
        accentedForm: (String) -> String? = { null },
        lookup: WordLookup,
    ): String? {
        if (profile.contractionPrefixes.isEmpty()) return null
        val lowered = word.lowercase(Locale.ROOT)
        if (lowered.length < 2 || lowered.any { it == '\'' || it == '’' }) return null

        // Shortest prefixes first, so `c'` is tried before `qu'` and the one-letter elisions that
        // dominate French are reached without a longer prefix accidentally shadowing them.
        profile.contractionPrefixes.sortedBy { it.length }.forEach { prefix ->
            val letters = prefix.length - 1
            if (letters <= 0 || lowered.length <= letters) return@forEach
            if (lowered.substring(0, letters) + "'" != prefix) return@forEach
            val suffix = lowered.substring(letters)
            if (suffix.isEmpty()) return@forEach

            if (lookup.exists(suffix)) return preserveCase(word, prefix + suffix)
            // The suffix may be a real word missing its accents: `Cetait` → `etait` → `était`.
            // Only dictionary words come back from the accent pass, so this cannot invent one.
            accentedForm(suffix)?.let { return preserveCase(word, prefix + it) }
        }
        return null
    }

    /** Uppercases only the first character: `c'est` must never become `C'Est`. */
    private fun preserveCase(original: String, expansion: String): String {
        val firstIsUpper = original.firstOrNull()?.isUpperCase() == true
        if (!firstIsUpper || expansion.isEmpty()) return expansion
        return expansion.replaceFirstChar { it.uppercaseChar() }
    }
}
