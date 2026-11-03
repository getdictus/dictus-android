package dev.pivisolutions.dictus.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The debug log file (#295), after iOS `PolishEventStore` and the 7-day retention of `PolishMetricsRing`.
 * Robolectric only for `org.json`, which the plain JVM android.jar stubs out.
 */
@RunWith(RobolectricTestRunner::class)
class TranscriptionDebugStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var nowMs = 1_793_000_000_000L
    private val file by lazy { File(folder.root, TranscriptionDebugStore.FILE_NAME) }
    private fun store() = TranscriptionDebugStore(file, now = { nowMs })

    @Test
    fun `an appended entry reads back whole, one JSON line each`() {
        val store = store()
        val first = entry("A", ageMs = 2_000)
        val second = entry("B", ageMs = 1_000)
        store.append(first)
        store.append(second)

        assertEquals(listOf(first, second), store.readAll())
        assertEquals(2, file.readLines().size)
    }

    @Test
    fun `entries older than 7 days are purged from the file at the first append`() {
        store().apply {
            append(entry("old", ageMs = TranscriptionDebugStore.RETENTION_MS + 1_000))
            append(entry("recent", ageMs = TranscriptionDebugStore.RETENTION_MS - 10_000))
        }

        // A new process: its first append purges what aged out.
        nowMs += 2_000
        store().append(entry("new", ageMs = 0))

        assertFalse(file.readText().contains("\"old\""))
        assertEquals(listOf("recent", "new"), store().readAll().map { it.id })
    }

    @Test
    fun `a read drops what aged out while the process ran`() {
        val store = store()
        store.append(entry("A", ageMs = 0))

        nowMs += TranscriptionDebugStore.RETENTION_MS + 1
        assertTrue(store.readAll().isEmpty())
        assertEquals("", file.readText())
    }

    @Test
    fun `a line that does not decode is skipped`() {
        file.writeText("not json\n")
        val store = store()
        store.append(entry("A", ageMs = 0))

        assertEquals(listOf("A"), store.readAll().map { it.id })
    }

    @Test
    fun `clear empties the log`() {
        val store = store()
        store.append(entry("A", ageMs = 0))
        store.clear()

        assertTrue(store.readAll().isEmpty())
    }

    private fun entry(id: String, ageMs: Long) = TranscriptionDebugEntry(
        id = id,
        // The log keeps whole seconds, as iOS's ISO 8601 encoder does.
        timestampMs = (nowMs - ageMs) / 1_000 * 1_000,
        raw = " Bonjour",
        writer = "<KBD>",
        metrics = TranscriptionDebugMetrics(
            detectedLanguage = null,
            languageResolution = LanguageResolution("followKeyboard", "fr", "fr", sttLanguageIsEffective = true),
            rawCharCount = 8,
            polishedCharCount = 8,
            latencyMs = 120,
            sttEngine = "WK",
            sttModelID = "tiny",
            timings = Timings(preprocessMs = 20, engineMs = 90, postprocessMs = 10),
        ),
    )
}
