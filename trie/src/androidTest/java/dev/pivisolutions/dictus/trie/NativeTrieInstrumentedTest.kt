package dev.pivisolutions.dictus.trie

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTrieInstrumentedTest {
    @Test
    fun frenchDictionarySupportsExactAndFuzzyLookup() {
        NativeTrie.open(
            ApplicationProvider.getApplicationContext(),
            "fr_spellcheck.dict",
            TrieKeyboardLayout.AZERTY,
        ).use { trie ->
            assertTrue(trie.wordExists("bonjour"))
            assertFalse(trie.wordExists("bonjr"))
            val bonjrCandidates = trie.correct("bonjr")
            assertTrue("bonjr candidates: $bonjrCandidates", bonjrCandidates.contains("bonjour"))
            assertEquals(bonjrCandidates.distinct(), bonjrCandidates)
            val cafeCandidates = trie.correct("cafe")
            assertTrue("cafe candidates: $cafeCandidates", cafeCandidates.contains("café"))
            assertTrue(trie.correct("a".repeat(33)).isEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                trie.correct("bonjour", maxEditDistance = 3f)
            }
        }
    }

    @Test
    fun englishDictionaryLoadsIndependently() {
        val trie = NativeTrie.open(
            ApplicationProvider.getApplicationContext(),
            "en_spellcheck.dict",
            TrieKeyboardLayout.QWERTY,
        )
        trie.use {
            assertTrue(trie.wordExists("hello"))
            val candidates = trie.correct("helo")
            assertTrue("helo candidates: $candidates", candidates.contains("hello"))
        }
        assertThrows(IllegalStateException::class.java) { trie.wordExists("hello") }
    }
}