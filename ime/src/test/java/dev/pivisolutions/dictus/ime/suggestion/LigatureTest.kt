package dev.pivisolutions.dictus.ime.suggestion

import dev.pivisolutions.dictus.trie.TrieCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class LigatureTest {

    private val dictionary = object : NativeTrieHandle {
        private val words = setOf("coeur", "oeil", "vitae", "zorglub")
        override fun wordExists(word: String) = word in words
        override fun complete(prefix: String, maxResults: Int) = emptyList<String>()
        override fun correctionCandidates(word: String, maxEditDistance: Float, maxResults: Int) =
            emptyList<TrieCorrection>()
        override fun close() = Unit
    }

    @Test
    fun `each ligature is written as its two letters`() {
        assertEquals("coeur", Ligature.spelledOut("cœur"))
        assertEquals("OEil", Ligature.spelledOut("Œil"))
        assertEquals("ex aequo", Ligature.spelledOut("ex æquo"))
        assertEquals("AE", Ligature.spelledOut("Æ"))
        assertEquals("poète", Ligature.spelledOut("poète"))
    }

    @Test
    fun `Unicode decomposition leaves the ligatures whole`() {
        listOf("œ", "æ").forEach { ligature ->
            assertEquals(ligature, Normalizer.normalize(ligature, Normalizer.Form.NFKD))
        }
    }

    @Test
    fun `a word with a ligature is known in the dictionary's spelling`() {
        assertTrue(dictionary.knowsWord("cœur"))
        assertTrue(dictionary.knowsWord("œil"))
        assertTrue(dictionary.knowsWord("vitæ"))
        assertTrue(dictionary.knowsWord("coeur"))
        assertFalse(dictionary.knowsWord("cœurr"))
        assertFalse(dictionary.knowsWord("cour"))
    }
}
