package dev.pivisolutions.dictus.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Rule from iOS DictusCore/History/TranscriptionSearch.swift: `ecole` must find `école`.
class TranscriptionSearchTest {
    private val ecole = entry(1, "Demain je vais à l'école")
    private val plain = entry(2, "Rendez-vous au cafe")
    private val curly = entry(3, "C’est l’heure")
    private val entries = listOf(ecole, plain, curly)

    @Test
    fun `accentless query finds accented text and the reverse`() {
        assertEquals(listOf(ecole), TranscriptionSearch.filter(entries, "ecole"))
        assertEquals(listOf(plain), TranscriptionSearch.filter(entries, "café"))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf(ecole), TranscriptionSearch.filter(entries, "ÉCOLE"))
        assertEquals(listOf(ecole), TranscriptionSearch.filter(entries, "Demain"))
    }

    @Test
    fun `straight and curly apostrophes find each other`() {
        assertEquals(listOf(ecole), TranscriptionSearch.filter(entries, "l’ecole"))
        assertEquals(listOf(curly), TranscriptionSearch.filter(entries, "c'est"))
    }

    @Test
    fun `unmatched query finds nothing`() {
        assertTrue(TranscriptionSearch.filter(entries, "piscine").isEmpty())
    }

    @Test
    fun `blank query keeps every entry in order and surrounding spaces are ignored`() {
        assertEquals(entries, TranscriptionSearch.filter(entries, "   "))
        assertEquals(listOf(plain), TranscriptionSearch.filter(entries, " cafe "))
    }

    private fun entry(id: Long, text: String) = TranscriptionHistoryEntry(
        id = id,
        text = text,
        requestedLanguage = "fr",
        durationMillis = 1_000,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = id,
    )
}
