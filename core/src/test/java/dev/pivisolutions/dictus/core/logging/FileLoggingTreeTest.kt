package dev.pivisolutions.dictus.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import java.util.TimeZone

/** The exported line format (#112): zoned time, level, category, calling class, dictation id. */
class FileLoggingTreeTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private lateinit var logFile: File

    // 2026-10-22T22:12:03.456Z, i.e. 00:12:03.456 in Paris (CEST, +02:00).
    private val fixedMillis = 1_792_707_123_456L

    @Before
    fun setUp() {
        Timber.uprootAll()
        DictationTrace.reset()
        logFile = tmpDir.newFile("dictus.log")
        Timber.plant(FileLoggingTree(logFile, now = { fixedMillis }, timeZone = TimeZone.getTimeZone("Europe/Paris")))
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        DictationTrace.reset()
    }

    @Test
    fun `a line carries the zoned timestamp, the level, the category and the calling class`() {
        Timber.i("hello")

        assertEquals(
            "2026-10-23T00:12:03.456+02:00 INFO    [LIFECYCLE] FileLoggingTreeTest: hello\n",
            logFile.readText(),
        )
    }

    @Test
    fun `the class is found through a lambda, not reported as null`() {
        val fromLambda = { Timber.d("from a lambda") }
        fromLambda()

        assertTrue(logFile.readText(), logFile.readText().contains("DEBUG   [LIFECYCLE] FileLoggingTreeTest: from a lambda"))
    }

    @Test
    fun `an explicit category tag sets the category and keeps the class`() {
        Timber.tag(LogCategory.INSERT.name).i("inserted")

        assertTrue(logFile.readText(), logFile.readText().contains("[INSERT] FileLoggingTreeTest: inserted"))
    }

    @Test
    fun `lines written during a dictation carry its id, and stop once it ends`() {
        DictationTrace.random = kotlin.random.Random(7)
        val id = DictationTrace.begin(source = "keyboard")
        Timber.i("during")
        DictationTrace.end()
        Timber.i("after")

        val lines = logFile.readLines()
        assertTrue(lines[0], lines[0].endsWith("FileLoggingTreeTest d=$id: during"))
        assertTrue(lines[1], lines[1].endsWith("FileLoggingTreeTest: after"))
    }

    @Test
    fun `the formatter pads the level so columns line up`() {
        val line = FileLoggingTree.formatLine(
            timestamp = "2026-10-23T00:12:03.456+02:00",
            priority = 5,
            category = LogCategory.AUDIO,
            source = "AudioCaptureManager",
            dictationId = "7f3a01",
            message = "x",
        )

        assertEquals("2026-10-23T00:12:03.456+02:00 WARN    [AUDIO] AudioCaptureManager d=7f3a01: x\n", line)
    }

    @Test
    fun `simple name drops the package, nested and lambda classes, and the file facade`() {
        assertEquals(
            "DictusImeService",
            FileLoggingTree.simpleName("dev.pivisolutions.dictus.ime.DictusImeService\$KeyboardContent\$gatedMicTap\$1"),
        )
        assertEquals("RecordingScreen", FileLoggingTree.simpleName("dev.pivisolutions.dictus.recording.RecordingScreenKt"))
    }

    @Test
    fun `writing past the cap moves the file to previous and starts an empty current file`() {
        val dir = tmpDir.newFolder("rotate-once")
        val current = File(dir, "dictus.log")
        Timber.uprootAll()
        Timber.plant(FileLoggingTree(current, now = { fixedMillis }, maxBytes = 300))

        repeat(3) { Timber.i("line $it padded to make the file grow quickly past the cap") }
        val previous = File(dir, "dictus.previous.log")

        assertTrue(previous.exists())
        assertTrue(previous.readText(), previous.readText().contains("line 0"))
        assertTrue(!current.exists() || current.length() < 300)
    }

    @Test
    fun `many rotations keep two files and bound the total size`() {
        val dir = tmpDir.newFolder("rotate-many")
        val current = File(dir, "dictus.log")
        val cap = 500L
        Timber.uprootAll()
        Timber.plant(FileLoggingTree(current, now = { fixedMillis }, maxBytes = cap))

        repeat(500) { Timber.i("line $it of a long session") }
        Timber.i("tail")

        val files = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("dictus.log", "dictus.previous.log"), files)
        val previous = File(dir, "dictus.previous.log")
        val longestLine = previous.readLines().maxOf { it.length + 1 }
        assertTrue(current.length() <= cap)
        assertTrue(previous.length() <= cap + longestLine)
        assertTrue(current.length() + previous.length() <= 2 * cap + longestLine)
        // No line lost at the seam: the current file continues where the previous one stops.
        val lineNumber = Regex("line (\\d+) of")
        val lastPrevious = lineNumber.find(previous.readLines().last())!!.groupValues[1].toInt()
        val firstCurrent = lineNumber.find(current.readLines().first())?.groupValues?.get(1)?.toInt() ?: 500
        assertEquals(lastPrevious + 1, firstCurrent)
    }

    @Test
    fun `a rotation that cannot rename truncates instead of growing`() {
        val dir = tmpDir.newFolder("rotate-blocked")
        val current = File(dir, "dictus.log")
        // A non-empty directory where the previous file should go: delete and rename both fail.
        File(dir, "dictus.previous.log").apply { mkdir() }.resolve("blocker").writeText("x")
        Timber.uprootAll()
        Timber.plant(FileLoggingTree(current, now = { fixedMillis }, maxBytes = 300))

        repeat(50) { Timber.i("line $it padded to make the file grow quickly past the cap") }

        assertTrue(current.length() <= 300)
    }

    @Test
    fun `the previous file sits next to the log`() {
        assertEquals(File("/data/files/dictus.previous.log"), FileLoggingTree.previousFileFor(File("/data/files/dictus.log")))
    }
}
