package dev.pivisolutions.dictus.ime.model

import java.util.Locale

/** Pure, French-only policy for the adaptive key on the AZERTY letters layer. */
object FrenchAdaptiveKey {
    data class State(
        val label: String,
        val replacesPrevious: Boolean,
        val vowel: String?,
        val variants: List<String>,
    )

    val DEFAULT = State("'", replacesPrevious = false, vowel = null, variants = emptyList())

    private val defaults = mapOf(
        "e" to "é",
        "a" to "à",
        "u" to "ù",
        "i" to "î",
        "o" to "ô",
    )

    // Order intentionally matches Dictus iOS AccentedCharacters exactly.
    private val variants = mapOf(
        "e" to listOf("é", "è", "ê", "ë"),
        "a" to listOf("à", "â", "ä", "á"),
        "u" to listOf("ù", "û", "ü", "ú"),
        "i" to listOf("î", "ï", "í"),
        "o" to listOf("ô", "ö", "ó"),
    )

    /** Derives state from no more than the final two Unicode code points. */
    fun fromContext(context: CharSequence?): State {
        val points = context?.toString()?.codePoints()?.toArray()?.takeLast(2).orEmpty()
        if (points.isEmpty()) return DEFAULT

        val last = String(Character.toChars(points.last()))
        val lowered = last.lowercase(Locale.ROOT)
        // Only unaccented ASCII vowels are replacement targets.
        if (lowered.length != 1 || lowered[0] !in "aeiou") return DEFAULT

        val preceding = points.getOrNull(points.lastIndex - 1)
            ?.let(Character::toChars)
            ?.let(::String)
            ?.lowercase(Locale.ROOT)
        if (preceding == "q" && lowered == "u") return DEFAULT

        val uppercase = last == last.uppercase(Locale.ROOT) && last != lowered
        fun preserveCase(value: String) = if (uppercase) value.uppercase(Locale.ROOT) else value
        return State(
            label = preserveCase(defaults.getValue(lowered)),
            replacesPrevious = true,
            vowel = lowered,
            variants = variants.getValue(lowered).map(::preserveCase),
        )
    }
}
