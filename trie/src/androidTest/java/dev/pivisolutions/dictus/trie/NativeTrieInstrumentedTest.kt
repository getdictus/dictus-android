package dev.pivisolutions.dictus.trie

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeTrieInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun spanishDictionarySupportsAccentsAndRejectsMalformedCopy() {
        NativeTrie.open(context, "es_spellcheck.dict", TrieKeyboardLayout.QWERTY).use { trie ->
            assertTrue(trie.wordExists("español"))
            assertTrue(trie.wordExists("también"))
            assertTrue(trie.correct("espanol", maxResults = 20).contains("español"))
        }

        val valid = context.assets.open("es_spellcheck.dict").use { it.readBytes() }
        val malformed = File(context.cacheDir, "malformed-es.dict").apply {
            writeBytes(valid.copyOf(valid.size - 1))
        }
        try {
            assertThrows(IllegalStateException::class.java) {
                NativeTrie.openPathForTesting(malformed, TrieKeyboardLayout.QWERTY)
            }
        } finally {
            malformed.delete()
        }
    }

    @Test
    fun frenchDictionarySupportsExactFuzzyAndDeterministicPrefixLookup() {
        NativeTrie.open(
            context,
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

            assertEquals(listOf("bonne", "bonjour", "bonsoir"), trie.complete("bon", 3))
            assertEquals(trie.complete("bon", 5), trie.complete("BON", 5))
            assertTrue(trie.wordExists("BONJOUR"))
            assertTrue(trie.wordExists("CAFÉ"))
            assertEquals(trie.complete("café", 5), trie.complete("CAFE\u0301", 5))
            assertTrue(trie.complete("CAFÉ", 5).contains("caféine"))
            assertTrue(trie.correct("CAFE", maxResults = 20).contains("café"))
            assertTrue(trie.correct("CAFÈ", maxResults = 20).contains("café"))
            assertEquals(listOf("bonne"), trie.complete("bon", 1))
            assertEquals(trie.complete("bon", 10), trie.complete("bon", 10))
            assertTrue(trie.complete("a".repeat(33)).isEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                trie.complete("bon", maxResults = 21)
            }
            assertThrows(IllegalArgumentException::class.java) {
                trie.correct("bonjour", maxEditDistance = 3f)
            }
        }
    }

    @Test
    fun englishDictionaryCompletesPatriciaMidNodePrefixAndReopensAfterClose() {
        val trie = NativeTrie.open(
            context,
            "en_spellcheck.dict",
            TrieKeyboardLayout.QWERTY,
        )
        trie.use {
            assertTrue(trie.wordExists("hello"))
            val candidates = trie.correct("helo")
            assertTrue("helo candidates: $candidates", candidates.contains("hello"))
            assertTrue(trie.correct("HELO", maxResults = 20).contains("hello"))
            assertEquals(
                listOf("dictionary", "dictionaries"),
                trie.complete("dictio", maxResults = 5),
            )
        }
        assertThrows(IllegalStateException::class.java) { trie.wordExists("hello") }
        assertThrows(IllegalStateException::class.java) { trie.complete("hel") }

        NativeTrie.open(
            context,
            "en_spellcheck.dict",
            TrieKeyboardLayout.QWERTY,
        ).use { reopened ->
            assertEquals(listOf("help", "held", "helpful"), reopened.complete("hel", 3))
        }
    }

    @Test
    fun damerauTranspositionCrossesPatriciaNodeBoundaryInBothDictionaries() {
        NativeTrie.open(
            context,
            "en_spellcheck.dict",
            TrieKeyboardLayout.QWERTY,
        ).use { trie ->
            val candidates = trie.correct("dicitonary", maxEditDistance = 0.7f, maxResults = 20)
            assertTrue("dicitonary candidates: $candidates", candidates.contains("dictionary"))
        }

        NativeTrie.open(
            context,
            "fr_spellcheck.dict",
            TrieKeyboardLayout.AZERTY,
        ).use { trie ->
            val candidates = trie.correct("bojnour", maxEditDistance = 0.7f, maxResults = 20)
            assertTrue("bojnour candidates: $candidates", candidates.contains("bonjour"))
        }
    }

    @Test
    fun malformedStructuresAreRejectedBeforeTraversal() {
        val valid = context.assets.open("fr_spellcheck.dict").use { it.readBytes() }
        val malformed = mutableListOf<ByteArray>()
        malformed += valid.copyOf(valid.size - 1) // truncated final node
        malformed += valid.copyOf().also { it[20] = 0xff.toByte() } // impossible root list
        malformed += valid.copyOf().also { bytes ->
            // First root's child pointer redirected into its own node.
            val flags = bytes[32].toInt() and 0xff
            val charCount = if (flags and 0x20 != 0) bytes[33].toInt() and 0xff else 1
            val pointerSize = intArrayOf(0, 2, 3, 4)[flags ushr 6]
            check(pointerSize > 0)
            var pointer = 33 + (if (flags and 0x20 != 0) 1 else 0) + charCount * 2
            if (flags and 0x10 != 0) pointer += 2
            pointer++ // child count
            repeat(pointerSize) { index -> bytes[pointer + index] = if (index == 0) 32 else 0 }
        }
        malformed += valid.copyOf().also { bytes ->
            // Multi-character edge claims a word depth beyond the validated native buffer.
            bytes[32] = (bytes[32].toInt() or 0x20).toByte()
            bytes[33] = 0xff.toByte()
        }

        malformed.forEachIndexed { index, bytes ->
            val file = File(context.cacheDir, "malformed-$index.dict").apply { writeBytes(bytes) }
            try {
                assertThrows(IllegalStateException::class.java) {
                    NativeTrie.openPathForTesting(file, TrieKeyboardLayout.AZERTY)
                }
            } finally {
                file.delete()
            }
        }
    }

    /**
     * Reusable issue-69 Pixel smoke/benchmark. This deliberately uses the production asset copy,
     * hash validation, mmap load, canonicalization and JNI paths. The thresholds are generous
     * regression oracles rather than microbenchmark targets, so ordinary emulator noise is safe.
     */
    @Test
    fun pixelRapidQueriesAndLanguageSwitchesReportWarmP50P95() {
        val lookupNanos = mutableListOf<Long>()
        val switchNanos = mutableListOf<Long>()
        val dictionaries = listOf(
            Triple("fr_spellcheck.dict", TrieKeyboardLayout.AZERTY, "bon" to "bonjr"),
            Triple("en_spellcheck.dict", TrieKeyboardLayout.QWERTY, "hel" to "helo"),
        )

        repeat(12) { switch ->
            val (asset, layout, queries) = dictionaries[switch % dictionaries.size]
            val loadStart = SystemClock.elapsedRealtimeNanos()
            NativeTrie.open(context, asset, layout).use { trie ->
                switchNanos += SystemClock.elapsedRealtimeNanos() - loadStart
                assertTrue("$asset prefix smoke", trie.complete(queries.first, 3).isNotEmpty())
                assertTrue("$asset correction smoke", trie.correct(queries.second).isNotEmpty())

                // Warm the mmap pages/JNI path before recording rapid-query latency.
                repeat(10) {
                    trie.complete(queries.first, 3)
                    trie.correct(queries.second)
                }
                repeat(40) {
                    val lookupStart = SystemClock.elapsedRealtimeNanos()
                    trie.complete(queries.first, 3)
                    trie.correct(queries.second)
                    lookupNanos += SystemClock.elapsedRealtimeNanos() - lookupStart
                }
            }
        }

        val lookupP50Ms = lookupNanos.percentileMillis(50)
        val lookupP95Ms = lookupNanos.percentileMillis(95)
        val switchP50Ms = switchNanos.percentileMillis(50)
        val switchP95Ms = switchNanos.percentileMillis(95)
        val report = "issue69-native-smoke device=${Build.MODEL} samples=${lookupNanos.size} " +
            "warmLookupMs[p50=$lookupP50Ms,p95=$lookupP95Ms,oracle<500] " +
            "languageSwitchLoadMs[p50=$switchP50Ms,p95=$switchP95Ms,oracle<5000]"
        Log.i("NativeTrieSmoke", report)
        println(report)

        assertTrue("$report; warm lookup p95 exceeded generous oracle", lookupP95Ms < 500.0)
        assertTrue("$report; language-switch load p95 exceeded generous oracle", switchP95Ms < 5_000.0)
    }

    private fun List<Long>.percentileMillis(percentile: Int): Double {
        require(isNotEmpty())
        val sorted = sorted()
        val rank = ((sorted.size * percentile + 99) / 100).coerceIn(1, sorted.size)
        return sorted[rank - 1] / 1_000_000.0
    }
}
