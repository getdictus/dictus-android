package dev.pivisolutions.dictus.ime.language

/** German profile matching the approved iOS v1.7 correction data contract. */
val germanLanguageProfile = LanguageProfile(
    code = "de",
    displayName = "Deutsch",
    shortCode = "DE",
    // QWERTZ like iOS (#151 there, #134 here). Only a default: see KeyboardLayoutPreference.
    defaultLayout = KeyboardLayout.QWERTZ,
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
        's' to listOf('ß'),
    ),
    contractionPrefixes = emptyList(),
    collapseRules = listOf(
        CollapseRule("ae", "ä"),
        CollapseRule("oe", "ö"),
        CollapseRule("ue", "ü"),
        CollapseRule("ss", "ß"),
    ),
)
