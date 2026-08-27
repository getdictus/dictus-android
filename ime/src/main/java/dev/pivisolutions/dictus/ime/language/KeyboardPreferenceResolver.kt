package dev.pivisolutions.dictus.ime.language

/** Resolves rollback-safe persisted keyboard preferences without mutating DataStore. */
object KeyboardPreferenceResolver {
    fun language(code: String?): SupportedLanguage = SupportedLanguage.fromCodeOrDefault(code)

    fun layout(persistedValue: String?, language: SupportedLanguage): KeyboardLayout =
        KeyboardLayout.entries.firstOrNull { it.persistedValue == persistedValue }
            ?: language.profile.defaultLayout

    fun usesFrenchAdaptiveKey(language: SupportedLanguage, layout: KeyboardLayout): Boolean =
        language == SupportedLanguage.FRENCH && layout == KeyboardLayout.AZERTY
}