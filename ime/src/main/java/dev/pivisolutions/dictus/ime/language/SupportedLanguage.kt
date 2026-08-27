package dev.pivisolutions.dictus.ime.language

/** Canonical set of keyboard languages. ASR language selection remains independent. */
enum class SupportedLanguage(
    val code: String,
    val profile: LanguageProfile,
) {
    FRENCH("fr", frenchLanguageProfile),
    ENGLISH("en", englishLanguageProfile),
    ;

    companion object {
        fun fromCodeOrDefault(code: String?): SupportedLanguage =
            entries.firstOrNull { it.code == code } ?: FRENCH
    }
}
