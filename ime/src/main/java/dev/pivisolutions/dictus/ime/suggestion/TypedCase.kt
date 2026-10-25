package dev.pivisolutions.dictus.ime.suggestion

import java.util.Locale

/**
 * Carries the case the user typed over to a dictionary candidate.
 *
 * The tries store lowercase words, so "Bonjoru" was corrected to "bonjour" and the capital the
 * user (or autocapitalization) typed was lost. iOS restores it at the same place, on the
 * candidates themselves (dictus-ios `AOSPTrieEngine.spellCheck`, "case restoration").
 */
object TypedCase {
    /**
     * [typed] in capitals (two letters or more) gives the candidate in capitals; a capital first
     * letter gives the candidate with a capital first letter; anything else keeps the candidate
     * as the dictionary has it.
     */
    fun applyTo(candidate: String, typed: String, locale: Locale): String {
        val first = typed.firstOrNull() ?: return candidate
        val allCaps = typed.length > 1 && typed.any(Char::isLetter) && typed.none(Char::isLowerCase)
        return when {
            allCaps -> candidate.uppercase(locale)
            first.isUpperCase() -> candidate.replaceFirstChar { it.titlecase(locale) }
            else -> candidate
        }
    }
}
