package dev.pivisolutions.dictus.ime.language

/**
 * Resolves rollback-safe persisted keyboard preferences without mutating DataStore.
 * The layout has its own resolver since #134: [KeyboardLayoutPreference].
 */
object KeyboardPreferenceResolver {
    fun language(code: String?): SupportedLanguage = SupportedLanguage.fromCodeOrDefault(code)

    fun usesFrenchAdaptiveKey(language: SupportedLanguage, layout: KeyboardLayout): Boolean =
        language == SupportedLanguage.FRENCH && layout == KeyboardLayout.AZERTY
}