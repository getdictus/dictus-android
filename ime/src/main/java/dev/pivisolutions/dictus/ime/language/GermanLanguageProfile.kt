package dev.pivisolutions.dictus.ime.language

/** German profile matching the approved iOS v1.7 correction data contract. */
val germanLanguageProfile = LanguageProfile(
    code = "de",
    displayName = "Deutsch",
    shortCode = "DE",
    // QWERTY is intentional for parity; QWERTZ remains a separately deferred feature.
    defaultLayout = KeyboardLayout.QWERTY,
    spaceLabel = "Leertaste",
    returnLabel = "Eingabe",
    dictionaryAssetName = "dict_de.txt",
    nativeDictionaryAssetName = "de_spellcheck.dict",
    supportsAutocorrect = true,
    autocorrectEnabledByDefault = true,
    // Kept empty until native-speaker feedback establishes safe forced corrections.
    overrides = emptyMap(),
    accentMap = mapOf(
        'a' to listOf('ä'),
        'o' to listOf('ö'),
        'u' to listOf('ü'),
    ),
    contractionPrefixes = emptyList(),
    collapseRules = listOf(
        CollapseRule("ae", "ä"),
        CollapseRule("oe", "ö"),
        CollapseRule("ue", "ü"),
        CollapseRule("ss", "ß"),
    ),
)
