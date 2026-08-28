package dev.pivisolutions.dictus.ime.language

/** Canonical set of keyboard languages. ASR language selection remains independent. */
enum class SupportedLanguage(
    val code: String,
    val profile: LanguageProfile,
) {
    FRENCH("fr", frenchLanguageProfile),
    ENGLISH("en", englishLanguageProfile),
    SPANISH("es", spanishLanguageProfile),
    ;

    /** Toolbar order is the canonical registry order and wraps at the end. */
    fun next(): SupportedLanguage = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCodeOrDefault(code: String?): SupportedLanguage =
            entries.firstOrNull { it.code == code } ?: FRENCH
    }
}
