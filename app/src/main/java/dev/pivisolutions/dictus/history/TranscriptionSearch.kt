package dev.pivisolutions.dictus.history

import java.text.Normalizer
import java.util.Locale

/**
 * Which saved transcriptions a search query finds, ported from iOS
 * `DictusCore/History/TranscriptionSearch.swift`.
 *
 * Case- and accent-insensitive, so `ecole` and `Ecole` both find `école`, and `école` finds a
 * transcript that lost its accents. Only the text is searched, never the language or the engine.
 * A linear filter is enough: the history is capped at [TranscriptionHistoryRepository.MAX_ENTRIES].
 */
object TranscriptionSearch {
    // Accents do not fold curly apostrophes into the straight one, and keyboards type both.
    private val curlyApostrophes = Regex("[\\u2019\\u2018\\u02BC]")
    private val combiningMarks = Regex("\\p{Mn}+")

    /** The entries [query] finds, in the order given. A blank query finds everything. */
    fun filter(entries: List<TranscriptionHistoryEntry>, query: String): List<TranscriptionHistoryEntry> {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return entries
        return entries.filter { fold(it.text).contains(needle) }
    }

    private fun fold(value: String): String {
        val withoutAccents = combiningMarks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
        return curlyApostrophes.replace(withoutAccents, "'").lowercase(Locale.ROOT)
    }
}
