package dev.pivisolutions.dictus.ime.language

/** Spanish profile matching the approved iOS correction data contract. */
val spanishLanguageProfile = LanguageProfile(
    code = "es",
    displayName = "Español",
    shortCode = "ES",
    defaultLayout = KeyboardLayout.QWERTY,
    spaceLabel = "espacio",
    returnLabel = "intro",
    dictionaryAssetName = "dict_es.txt",
    nativeDictionaryAssetName = "es_spellcheck.dict",
    supportsAutocorrect = true,
    autocorrectEnabledByDefault = true,
    overrides = emptyMap(),
    accentMap = mapOf(
        'a' to listOf('á'),
        'e' to listOf('é'),
        'i' to listOf('í'),
        'o' to listOf('ó'),
        'u' to listOf('ú', 'ü'),
        'n' to listOf('ñ'),
    ),
    contractionPrefixes = emptyList(),
)
