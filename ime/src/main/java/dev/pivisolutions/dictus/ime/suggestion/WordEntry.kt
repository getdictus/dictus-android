package dev.pivisolutions.dictus.ime.suggestion

/**
 * A single dictionary entry: a word, its AOSP frequency score, and its
 * pre-computed accent-stripped lowercase form for fast prefix matching.
 *
 * Frequency values are in the range 0–255 (AOSP convention).
 * Higher values are more common words (e.g., "bonjour" = 200, "bonhomme" = 45).
 *
 * [strippedLower] is computed once at load time so that getSuggestions() does not
 * need to run NFD normalization on 50k words per keystroke.
 */
data class WordEntry(
    val word: String,
    val frequency: Int,
    val strippedLower: String,
)
