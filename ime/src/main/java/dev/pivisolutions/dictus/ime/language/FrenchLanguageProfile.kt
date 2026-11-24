package dev.pivisolutions.dictus.ime.language

/**
 * `a` (avoir) or `à` (dictus-ios #971, #979). The sets were tuned on Tatoeba on iOS. Declared
 * above the profile because top-level values are initialised in file order.
 */
private val frenchAGrave = ContextualHomograph(
    typed = "a",
    accented = "à",
    // Subjects and object pronouns: `il a`, `qui a`, `il y a`, `il nous a`.
    typedAfter = setOf(
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "le", "la", "les", "lui", "leur", "y", "en", "me", "te", "se",
        "qui", "ça", "cela", "ceci", "ce", "tout", "chacun", "chacune", "personne",
        "quoi", "lequel", "laquelle", "celui", "celle", "aucun", "aucune", "nul",
        "et", "ou", "où",
        // `elle aussi a`, `nous deux a`: after a subject pronoun these are part of the subject.
        "aussi", "deux", "trois", "tous", "toutes",
    ),
    accentedAfter = setOf(
        // être and aller: `est à`, `vais à`.
        "est", "suis", "es", "sommes", "êtes", "sont", "était", "étais", "étaient", "sera",
        "va", "vais", "vas", "allons", "allez", "vont", "aller",
        // avoir itself: `il a à faire`.
        "a", "as", "ai", "ont", "avons", "avez", "avait", "avais",
        "pas", "plus", "jamais", "rien", "jusqu", "grâce", "quant", "prête", "prêtes",
        "attention", "face", "confiance", "du mal", "de mal",
        // An object pronoun elided onto a verb: `m'aider à`, `s'attendre à`.
        "m'", "t'", "s'",
    ),
    // A subject, an auxiliary or a pronoun before it makes the word before a verb.
    accentedTwoAfter = setOf(
        "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "a", "ai", "as", "avons", "avez", "ont", "avait", "avais", "avaient", "aura", "aurait",
        "est", "suis", "es", "sommes", "êtes", "sont", "était", "étais", "étaient", "sera", "serait",
        "se", "me", "te", "ne", "n'", "s'", "m'", "t'",
    ),
    clauseOpeners = setOf(
        "que", "qui", "dont", "où", "quoi", "qu'",
        "lequel", "laquelle", "lesquels", "lesquelles", "duquel", "desquels", "desquelles", "auquel", "auxquels",
    ),
    accentedAtSentenceStart = true,
)

/** French profile, data-equivalent to the approved iOS correction reference. */
val frenchLanguageProfile = LanguageProfile(
    code = "fr",
    displayName = "Français",
    shortCode = "FR",
    defaultLayout = KeyboardLayout.AZERTY,
    spaceLabel = "espace",
    returnLabel = "retour",
    dictionaryAssetName = "dict_fr.txt",
    nativeDictionaryAssetName = "fr_spellcheck.dict",
    supportsAutocorrect = true,
    autocorrectEnabledByDefault = true,
    overrides = mapOf(
        "ca" to "ça",
        "tres" to "très",
        "apres" to "après",
        "deja" to "déjà",
        "ete" to "été",
        "etre" to "être",
        "voila" to "voilà",
        "bientot" to "bientôt",
        "plutot" to "plutôt",
        "probleme" to "problème",
        "systeme" to "système",
        "etait" to "était",
        "etaient" to "étaient",
        "evenement" to "événement",
    ),
    // A possessive never follows a subject pronoun, so after one, `ma` and `ta` are a missed
    // apostrophe (dictus-ios #970). `la` is left alone: `il la voit` is right.
    contextOverrides = mapOf(
        "il" to mapOf("ma" to "m'a", "ta" to "t'a"),
        "elle" to mapOf("ma" to "m'a", "ta" to "t'a"),
        "on" to mapOf("ma" to "m'a", "ta" to "t'a"),
        "ça" to mapOf("ma" to "m'a", "ta" to "t'a"),
        "tu" to mapOf("ma" to "m'as"),
    ),
    accentMap = mapOf(
        'e' to listOf('é', 'è', 'ê', 'ë'),
        'a' to listOf('à', 'â'),
        'i' to listOf('î', 'ï'),
        'o' to listOf('ô'),
        'u' to listOf('ù', 'û', 'ü'),
        'c' to listOf('ç'),
    ),
    contractionPrefixes = listOf("l'", "d'", "c'", "j'", "n'", "s'", "m'", "t'", "qu'"),
    // French elides before a vowel or a mute h, never before a consonant: `dvotre` must not
    // become `d'votre` (dictus-ios #1035).
    elisionOnset = ElisionOnset(
        prefixes = setOf(
            "d'", "l'", "j'", "m'", "n'", "t'", "s'", "c'",
            "qu'", "jusqu'", "lorsqu'", "puisqu'",
        ),
        initials = setOf(
            'a', 'e', 'i', 'o', 'u', 'y', 'h',
            'à', 'â', 'ä', 'é', 'è', 'ê', 'ë', 'î', 'ï', 'ô', 'ö', 'ù', 'û', 'ü', 'ÿ', 'œ', 'æ',
        ),
    ),
    contextualHomographs = listOf(frenchAGrave),
)
