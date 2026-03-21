package dev.pivisolutions.dictus.ime.model

/**
 * Maps characters to their accented variants for long-press accent selection.
 *
 * Covers French accented characters (primary use case) plus common
 * accents for other Latin languages. Both lowercase and uppercase
 * variants are included.
 */
object AccentMap {

    private val map: Map<Char, List<String>> = mapOf(
        // Lowercase
        'e' to listOf("\u00E8", "\u00E9", "\u00EA", "\u00EB", "\u0113"),  // e, e, e, e, e
        'a' to listOf("\u00E0", "\u00E2", "\u00E4", "\u00E6"),            // a, a, a, ae
        'u' to listOf("\u00F9", "\u00FB", "\u00FC"),                       // u, u, u
        'i' to listOf("\u00EE", "\u00EF"),                                 // i, i
        'o' to listOf("\u00F4", "\u00F6", "\u0153"),                       // o, o, oe
        'c' to listOf("\u00E7"),                                           // c
        'y' to listOf("\u00FF"),                                           // y
        'n' to listOf("\u00F1"),                                           // n
        // Uppercase
        'E' to listOf("\u00C8", "\u00C9", "\u00CA", "\u00CB"),            // E, E, E, E
        'A' to listOf("\u00C0", "\u00C2", "\u00C4", "\u00C6"),            // A, A, A, AE
        'U' to listOf("\u00D9", "\u00DB", "\u00DC"),                       // U, U, U
        'I' to listOf("\u00CE", "\u00CF"),                                 // I, I
        'O' to listOf("\u00D4", "\u00D6", "\u0152"),                       // O, O, OE
        'C' to listOf("\u00C7"),                                           // C
        'Y' to listOf("\u0178"),                                           // Y
        'N' to listOf("\u00D1"),                                           // N
    )

    /**
     * Returns the list of accented variants for the given character,
     * or null if no accents are available.
     */
    fun accentsFor(char: Char): List<String>? = map[char]

    /**
     * Returns true if the given character has accented variants available.
     */
    fun hasAccents(char: Char): Boolean = map.containsKey(char)
}
