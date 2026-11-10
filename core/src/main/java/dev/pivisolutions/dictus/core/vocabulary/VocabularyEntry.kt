package dev.pivisolutions.dictus.core.vocabulary

import java.util.UUID

/**
 * One term the user taught Dictus: a canonical spelling plus the variants the engine
 * writes in its place. Port of iOS `VocabularyEntry` (#203).
 *
 * Each variant triggers a replacement to [term] on the raw transcript (see
 * [VocabularyReplacer]). That is the whole of what an entry does, so an entry with no
 * variants changes nothing — see [hasEffect]. It is still stored: a user often saves a
 * term before knowing how Dictus will misspell it.
 *
 * WHY a data class with an internal constructor: [create] is the only way in from
 * user input, and it cleans or refuses. The internal constructor exists for the store's
 * decoder, which has to rebuild exactly what is on disk so [isValid] can judge it.
 *
 * No language tag, as on iOS: `Kubernetes` is `Kubernetes` in French and in English.
 */
@ConsistentCopyVisibility
data class VocabularyEntry internal constructor(
    val id: UUID,
    /** The canonical spelling. What replacements emit. */
    val term: String,
    /** What the engine produces instead. May be empty. */
    val variants: List<String>,
    /** The per-entry switch. With the global flag, the only two ways to stop a replacement. */
    val isEnabled: Boolean,
    /** Epoch milliseconds. */
    val dateAdded: Long,
) {

    /**
     * Whether an entry that came off disk still satisfies what [create] enforces.
     *
     * Rebuilt and compared rather than re-checked, as on iOS: a second list of
     * conditions beside [create] is a list that drifts from it.
     */
    val isValid: Boolean
        get() = create(term, variants, isEnabled, id, dateAdded) == this

    /**
     * Whether this entry can change a transcript at all. Separate from [isValid] on
     * purpose: the store filters on [isValid] at load, so folding this rule into it
     * would silently delete every entry saved without a variant.
     */
    val hasEffect: Boolean
        get() = variants.isNotEmpty()

    /** The same entry with its switch flipped. */
    fun withEnabled(newValue: Boolean): VocabularyEntry =
        create(term, variants, newValue, id, dateAdded) ?: this

    companion object {

        /**
         * Longest accepted term, and longest accepted variant, in user-perceived
         * characters. Far beyond any real term; it keeps a paste accident from putting a
         * paragraph into the matcher.
         */
        const val MAX_FIELD_LENGTH = 100

        /**
         * Build an entry from user input, or refuse (null).
         *
         * Refuses only on the term: an empty or over-long canonical spelling has nothing
         * to store. Variants are cleaned rather than refused — an empty, over-long or
         * duplicated one (ignoring case) is dropped, and so is one byte-identical to the
         * term. A variant differing from its term only by case is kept on purpose:
         * `ampli` for `Ampli` is the commonest mangling, and [VocabularyReplacer] treats
         * it as a fixed point.
         */
        fun create(
            term: String,
            variants: List<String> = emptyList(),
            isEnabled: Boolean = true,
            id: UUID = UUID.randomUUID(),
            dateAdded: Long = System.currentTimeMillis(),
        ): VocabularyEntry? {
            val cleanTerm = term.trim()
            if (cleanTerm.isEmpty() || graphemeCount(cleanTerm) > MAX_FIELD_LENGTH) return null

            val seen = HashSet<String>()
            val cleanVariants = ArrayList<String>()
            for (variant in variants) {
                val clean = variant.trim()
                if (clean.isEmpty() || graphemeCount(clean) > MAX_FIELD_LENGTH) continue
                if (clean == cleanTerm) continue
                if (!seen.add(clean.lowercase())) continue
                cleanVariants.add(clean)
            }
            return VocabularyEntry(id, cleanTerm, cleanVariants, isEnabled, dateAdded)
        }

        private fun graphemeCount(text: String): Int = VocabularyReplacer.graphemes(text).size
    }
}
