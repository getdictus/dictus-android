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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class NativeNgramInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun fixtureSupportsBigramTrigramBackoffUtf8ScoreAndReload() {
        withTrie { trie ->
            val fixture = fixtureFile("valid-😊.ngrm", buildFixture())
            assertTrue(trie.loadNgramForTesting(fixture))
            assertTrue(trie.isNgramLoadedForTesting())

            assertEquals(
                listOf(
                    NgramPrediction("suis", 60_000),
                    NgramPrediction("vais", 50_000),
                    NgramPrediction("pense", 40_000),
                ),
                trie.predictAfterWord("JE", 3),
            )
            assertEquals(
                listOf(
                    NgramPrediction("un", 65_000),
                    NgramPrediction("vraiment", 55_000),
                    NgramPrediction("en", 40_000),
                    NgramPrediction("sûr", 12_000),
                ),
                trie.predictAfterWords("je", "suis", 4),
            )
            assertEquals(60_000, trie.bigramScore("Je", "SUIS"))
            assertEquals(30_000, trie.bigramScore("suis", "sûr"))
            assertEquals(0, trie.bigramScore("inconnu", "mot"))
            assertTrue(trie.predictAfterWord("a".repeat(256)).isEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                trie.predictAfterWord("je", 17)
            }

            trie.unloadNgramForTesting()
            assertFalse(trie.isNgramLoadedForTesting())
            assertTrue(trie.predictAfterWord("je").isEmpty())
            assertTrue(trie.loadNgramForTesting(fixture))
            assertEquals("suis", trie.predictAfterWord("je", 1).single().word)
        }
    }

    @Test
    fun malformedHeadersSectionsResultsAndStringsFailClosed() {
        val valid = buildFixture()
        val bigramOffset = valid.readLeInt(16)
        val stringOffset = valid.readLeInt(24)
        val malformed = listOf(
            valid.copyOf().also { it[0] = 'X'.code.toByte() },
            valid.copyOf(31),
            valid.copyOf().also { it.writeLeInt(24, it.size + 1) },
            valid.copyOf().also { it[bigramOffset + 4] = 17 },
            valid.copyOf().also { it.writeLeInt(bigramOffset + 5, Int.MAX_VALUE) },
            valid.copyOf().also { it[it.lastIndex] = 'x'.code.toByte() },
            valid.copyOf().also { it[stringOffset] = 0xc0.toByte() },
            valid.copyOf().also { it.writeLeInt(8, Int.MAX_VALUE) },
            valid.copyOf().also { it.writeLeInt(20, bigramOffset - 1) },
            valid.copyOf().also { it.writeLeInt(28, it.size) },
        )

        withTrie { trie ->
            assertTrue(trie.loadNgramForTesting(fixtureFile("baseline.ngrm", valid)))
            malformed.forEachIndexed { index, bytes ->
                assertFalse(
                    "malformed fixture $index was accepted",
                    trie.loadNgramForTesting(fixtureFile("malformed-$index.ngrm", bytes)),
                )
                assertFalse("failed load must clear prior mmap", trie.isNgramLoadedForTesting())
                assertTrue(trie.predictAfterWord("je").isEmpty())
            }
            val oversized = File(context.cacheDir, "oversized.ngrm")
            RandomAccessFile(oversized, "rw").use { it.setLength(64L * 1024L * 1024L + 1L) }
            assertFalse(trie.loadNgramForTesting(oversized))
            assertTrue(stringOffset in valid.indices)
        }
    }

    @Test
    fun pixelRepeatedLookupP95RemainsBelowTenMilliseconds() {
        withTrie { trie ->
            assertTrue(trie.loadNgramForTesting(fixtureFile("latency.ngrm", buildFixture())))
            repeat(100) { trie.predictAfterWords("je", "suis", 4) }
            val timings = MutableList(1_000) {
                val started = SystemClock.elapsedRealtimeNanos()
                val predictions = trie.predictAfterWords("je", "suis", 4)
                assertEquals("un", predictions.first().word)
                SystemClock.elapsedRealtimeNanos() - started
            }.sorted()
            val p50Ms = timings[499] / 1_000_000.0
            val p95Ms = timings[949] / 1_000_000.0
            val report = "issue73-ngram-smoke device=${Build.MODEL} samples=${timings.size} " +
                "lookupMs[p50=$p50Ms,p95=$p95Ms,oracle<10]"
            Log.i("NativeNgramSmoke", report)
            println(report)
            assertTrue(report, p95Ms < 10.0)
        }
    }

    @Test
    fun productionLanguagePairsLoadExactAssetsAndStayBelowLookupBudget() {
        val cases = listOf(
            ProductionCase(
                asset = "fr_spellcheck.dict",
                layout = TrieKeyboardLayout.AZERTY,
                firstWord = "a",
                secondWord = "besoin",
                expectedBigram = "ne",
                expectedTrigram = "de",
            ),
            ProductionCase(
                asset = "en_spellcheck.dict",
                layout = TrieKeyboardLayout.QWERTY,
                firstWord = "a",
                secondWord = "bit",
                expectedBigram = "can",
                expectedTrigram = "of",
                bigramContext = "you",
            ),
            ProductionCase(
                asset = "es_spellcheck.dict",
                layout = TrieKeyboardLayout.QWERTY,
                firstWord = "a",
                secondWord = "través",
                expectedBigram = "se",
                expectedTrigram = "de",
                bigramContext = "también",
            ),
        )

        cases.forEach { case ->
            NativeTrie.open(context, case.asset, case.layout).use { trie ->
                assertTrue("${case.asset} must activate its matching n-gram", trie.hasNgram)
                assertEquals(
                    case.expectedBigram,
                    trie.predictAfterWord(case.bigramContext, 3).first().word,
                )
                assertEquals(
                    case.expectedTrigram,
                    trie.predictAfterWords(case.firstWord, case.secondWord, 3).first().word,
                )
                repeat(100) { trie.predictAfterWords(case.firstWord, case.secondWord, 3) }
                val timings = MutableList(1_000) {
                    val started = SystemClock.elapsedRealtimeNanos()
                    trie.predictAfterWords(case.firstWord, case.secondWord, 3)
                    SystemClock.elapsedRealtimeNanos() - started
                }.sorted()
                val p50Ms = timings[499] / 1_000_000.0
                val p95Ms = timings[949] / 1_000_000.0
                val report = "issue75-production-ngram asset=${case.asset} device=${Build.MODEL} " +
                    "samples=${timings.size} lookupMs[p50=$p50Ms,p95=$p95Ms,oracle<10]"
                Log.i("NativeNgramSmoke", report)
                println(report)
                assertTrue(report, p95Ms < 10.0)
            }
        }
    }

    @Test
    fun corruptOptionalNgramKeepsSpellTrieUsable() {
        val spell = File(context.cacheDir, "es-spell-fixture.dict").also { output ->
            context.assets.open("es_spellcheck.dict").use { input ->
                output.outputStream().use { destination -> input.copyTo(destination) }
            }
        }
        val corruptNgram = fixtureFile("corrupt-production.ngrm", "not-an-ngrm".toByteArray())

        NativeTrie.openPathsForTesting(spell, TrieKeyboardLayout.QWERTY, corruptNgram).use { trie ->
            assertFalse(trie.hasNgram)
            assertTrue(trie.wordExists("español"))
            assertTrue(trie.predictAfterWord("también").isEmpty())
        }
    }

    private fun withTrie(block: (NativeTrie) -> Unit) {
        NativeTrie.open(context, "fr_spellcheck.dict", TrieKeyboardLayout.AZERTY).use(block)
    }

    private fun fixtureFile(name: String, bytes: ByteArray): File =
        File(context.cacheDir, name).apply { writeBytes(bytes) }

    private data class FixtureResult(val word: String, val score: Int)
    private data class FixtureEntry(val key: ByteArray, val results: List<FixtureResult>)
    private data class ProductionCase(
        val asset: String,
        val layout: TrieKeyboardLayout,
        val firstWord: String,
        val secondWord: String,
        val expectedBigram: String,
        val expectedTrigram: String,
        val bigramContext: String = "je",
    )

    private fun buildFixture(): ByteArray {
        val bigrams = listOf(
            FixtureEntry(
                "je".toByteArray(),
                listOf(
                    FixtureResult("suis", 60_000),
                    FixtureResult("vais", 50_000),
                    FixtureResult("pense", 40_000),
                ),
            ),
            FixtureEntry(
                "suis".toByteArray(),
                listOf(
                    FixtureResult("en", 50_000),
                    FixtureResult("un", 45_000),
                    FixtureResult("sûr", 30_000),
                ),
            ),
        )
        val trigrams = listOf(
            FixtureEntry(
                "je\u0000suis".toByteArray(),
                listOf(
                    FixtureResult("un", 65_000),
                    FixtureResult("vraiment", 55_000),
                    FixtureResult("en", 40_000),
                ),
            ),
        )
        val words = (bigrams + trigrams).flatMap { entry -> entry.results.map { it.word } }.distinct()
        val strings = ByteArrayOutputStream()
        val offsets = words.associateWith { word ->
            strings.size().also {
                strings.write(word.toByteArray(StandardCharsets.UTF_8))
                strings.write(0)
            }
        }
        val bigramSection = section(bigrams, offsets)
        val trigramSection = section(trigrams, offsets)
        val bigramOffset = 32
        val trigramOffset = bigramOffset + bigramSection.size
        val stringOffset = trigramOffset + trigramSection.size
        return ByteArrayOutputStream().apply {
            write("NGRM".toByteArray())
            writeLeShort(1)
            writeLeShort(0)
            writeLeInt(bigrams.size)
            writeLeInt(trigrams.size)
            writeLeInt(bigramOffset)
            writeLeInt(trigramOffset)
            writeLeInt(stringOffset)
            writeLeInt(strings.size())
            write(bigramSection)
            write(trigramSection)
            write(strings.toByteArray())
        }.toByteArray()
    }

    private fun section(
        entries: List<FixtureEntry>,
        offsets: Map<String, Int>,
    ): ByteArray = ByteArrayOutputStream().apply {
        entries.sortedWith { left, right ->
            left.key.fnv1a().toUInt().compareTo(right.key.fnv1a().toUInt())
        }.forEach { entry ->
            writeLeInt(entry.key.fnv1a())
            write(entry.results.size)
            entry.results.forEach { result ->
                writeLeInt(offsets.getValue(result.word))
                writeLeShort(result.score)
            }
        }
    }.toByteArray()

    private fun ByteArray.fnv1a(): Int {
        var hash = 0x811c9dc5U
        forEach { byte ->
            hash = (hash xor byte.toUByte().toUInt()) * 0x01000193U
        }
        return hash.toInt()
    }

    private fun ByteArray.readLeInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.writeLeInt(offset: Int, value: Int) {
        repeat(4) { index -> this[offset + index] = (value ushr (8 * index)).toByte() }
    }

    private fun ByteArrayOutputStream.writeLeShort(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeLeInt(value: Int) {
        repeat(4) { index -> write((value ushr (8 * index)) and 0xff) }
    }
}
