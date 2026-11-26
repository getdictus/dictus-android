package dev.pivisolutions.dictus.ime.suggestion

/**
 * The shortcuts of the phone's personal dictionary (#424, port of the shortcut half of iOS
 * `SupplementaryLexicon`, dictus-ios #873 and #1022): `qqc` + Space gives « quelque chose ».
 *
 * WHY pure Kotlin: the rules run under JVM tests. [UserDictionaryShortcuts] reads the pairs from
 * `UserDictionary.Words`; contact names are out of scope (they would need `READ_CONTACTS`).
 *
 * A shortcut expands before anything else decides the word, as on iOS: with autocorrect off, for a
 * shortcut holding a digit (`adr1`), for a shortcut that is a dictionary word, and where the user
 * refused it before.
 */
class TextReplacements(entries: List<Entry>) {

    /** One row of the personal dictionary: [shortcut] is typed, [phrase] is written. */
    data class Entry(val shortcut: String, val phrase: String)

    /** Lowercased shortcut -> phrase. On duplicate shortcuts the first entry wins. */
    private val phrases: Map<String, String> = buildMap {
        for ((shortcut, phrase) in entries) {
            // A shortcut equal to its phrase expands to nothing new.
            if (shortcut.isEmpty() || phrase.isBlank() || shortcut.lowercase() == phrase.lowercase()) continue
            putIfAbsent(shortcut.lowercase(), phrase)
        }
    }

    val isEmpty: Boolean get() = phrases.isEmpty()
    val size: Int get() = phrases.size

    /**
     * The phrase that replaces [ofShortcut] when it is one of the user's shortcuts, or null. A
     * leading capital (sentence autocap) capitalizes the phrase: `Qqc` and `QQC` give « Quelque chose ».
     */
    fun expansion(ofShortcut: String): String? {
        val phrase = phrases[ofShortcut.lowercase()] ?: return null
        return if (ofShortcut.first().isUpperCase()) phrase.replaceFirstChar { it.uppercase() } else phrase
    }

    companion object {
        val EMPTY = TextReplacements(emptyList())
    }
}
