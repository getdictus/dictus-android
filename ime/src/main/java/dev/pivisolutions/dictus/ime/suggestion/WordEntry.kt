package dev.pivisolutions.dictus.ime.suggestion

/**
 * A single dictionary entry: a word and its AOSP frequency score.
 *
 * Frequency values are in the range 0–255 (AOSP convention).
 * Higher values are more common words (e.g., "bonjour" = 200, "bonhomme" = 45).
 * Used internally by DictionaryEngine for ranked prefix matching.
 */
data class WordEntry(val word: String, val frequency: Int)
