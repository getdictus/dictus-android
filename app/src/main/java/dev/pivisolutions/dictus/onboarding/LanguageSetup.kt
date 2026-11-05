package dev.pivisolutions.dictus.onboarding

import androidx.datastore.preferences.core.MutablePreferences
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.KeyboardLayoutPreference
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import java.util.Locale

/**
 * What the onboarding's language step chooses: the keyboard language and its layout (#198).
 *
 * Ported from iOS `LanguageSetup` (DictusCore), without its spoken language: dictation follows
 * the keyboard language (#292), so the keyboard language is the only language to choose, and
 * Android's four keyboard languages are the only ones offered.
 *
 * @property language the keyboard language.
 * @property layout the layout it types on.
 */
data class LanguageSetup(
    val language: SupportedLanguage,
    val layout: KeyboardLayout,
) {
    /**
     * Switches to [language] on the layout [layoutFor] gives it, as the keyboard's language
     * key does: the layout follows the language instead of staying on the previous one.
     */
    fun withLanguage(language: SupportedLanguage, layoutFor: (SupportedLanguage) -> KeyboardLayout): LanguageSetup =
        LanguageSetup(language, layoutFor(language))

    /**
     * Writes the choice inside a DataStore edit: [language] becomes the active keyboard
     * language, and [layout] is recorded only if it differs from the one the language
     * already has, so a confirmed default keeps following the language's default (#134).
     */
    fun applyTo(preferences: MutablePreferences) {
        KeyboardLayoutPreference.activate(preferences, language)
        if (KeyboardLayoutPreference.layout(preferences, language) != layout) {
            KeyboardLayoutPreference.setLayout(preferences, layout, language)
        }
    }

    companion object {
        /** The keyboard language when none of the system's languages has a Dictus keyboard, as on iOS. */
        val FALLBACK_LANGUAGE = SupportedLanguage.ENGLISH

        /**
         * The setup the step opens on: the first of the system's [preferredLocales] that has a
         * Dictus keyboard, else [FALLBACK_LANGUAGE], on the layout [layoutFor] gives it.
         *
         * WHY [layoutFor] and not the language's default: a user who runs the onboarding again
         * keeps the layout they already chose for that language.
         */
        fun prefilled(
            preferredLocales: List<Locale>,
            layoutFor: (SupportedLanguage) -> KeyboardLayout,
        ): LanguageSetup {
            val language = preferredLocales.firstNotNullOfOrNull { locale ->
                SupportedLanguage.entries.firstOrNull { it.code == locale.language }
            } ?: FALLBACK_LANGUAGE
            return LanguageSetup(language, layoutFor(language))
        }
    }
}
