package dev.pivisolutions.dictus.ime.suggestion

/**
 * The ligatures œ and æ, which a user types as one letter and the dictionaries store as two
 * (#406, port of dictus-ios `Ligature`).
 *
 * No native dictionary Dictus ships holds a ligature: the French one writes "coeur", "soeur",
 * "oeuvre". Since #406 the long-press popups offer œ and æ, so "cœur" typed by hand reached a
 * trie that only knows "coeur", counted as unknown, and Space corrected it to its nearest
 * neighbour, "cour". "coeur" stays a valid word: what was missing is the lookup in the
 * dictionary's own spelling, which [knowsWord] adds.
 *
 * WHY not Unicode normalization: neither NFD nor NFKD decomposes œ (U+0153) or æ (U+00E6).
 * They are letters in their own right, so the accent folding that turns "é" into "e" leaves
 * them as they are.
 */
internal object Ligature {

    private val spellings = mapOf(
        'œ' to "oe", 'Œ' to "OE", // œ Œ
        'æ' to "ae", 'Æ' to "AE", // æ Æ
    )

    fun contains(word: String): Boolean = word.any { it in spellings }

    /** [word] with each ligature written as its two letters: "cœur" → "coeur", "Æ" → "AE". */
    fun spelledOut(word: String): String =
        if (!contains(word)) word else buildString { word.forEach { append(spellings[it] ?: it) } }
}

/**
 * Whether the dictionary has [word], asked in its own spelling: a word typed with a ligature is
 * also looked up with it spelled out, so "cœur" is the dictionary's "coeur".
 *
 * WHY a fallback and not a rewrite of the word: a dictionary that does store the ligature is
 * found by the first lookup, and the typed word is never changed, only recognised.
 */
internal fun NativeTrieHandle.knowsWord(word: String): Boolean =
    wordExists(word) || (Ligature.contains(word) && wordExists(Ligature.spelledOut(word)))
