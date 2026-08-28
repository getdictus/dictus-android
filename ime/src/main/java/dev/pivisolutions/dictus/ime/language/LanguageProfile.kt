package dev.pivisolutions.dictus.ime.language

/** Pure per-language data consumed by keyboard correction and prediction features. */
data class LanguageProfile(
    val code: String,
    val displayName: String,
    val shortCode: String,
    val defaultLayout: KeyboardLayout,
    val spaceLabel: String,
    val returnLabel: String,
    val dictionaryAssetName: String,
    /** Binary mmap-ready dictionary packaged by the trie module. */
    val nativeDictionaryAssetName: String,
    /** Whether this language's dictionary is safe to use for automatic replacement. */
    val supportsAutocorrect: Boolean,
    /** Intent used until the user persists an explicit autocorrect preference. */
    val autocorrectEnabledByDefault: Boolean,
    val overrides: Map<String, String>,
    val accentMap: Map<Char, List<Char>>,
    val contractionPrefixes: List<String>,
    val collapseRules: List<CollapseRule> = emptyList(),
    val seedBigrams: List<BigramSeed> = emptyList(),
)

enum class KeyboardLayout(val persistedValue: String) {
    AZERTY("azerty"),
    QWERTY("qwerty"),
}

data class CollapseRule(
    val from: String,
    val to: String,
)

data class BigramSeed(
    val firstWord: String,
    val secondWord: String,
    val frequency: Int,
)
