package dev.pivisolutions.dictus.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogExportHeaderTest {

    private val context: android.app.Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `header names the build, the device, the dictation setup and the retained window`() {
        val header = LogExporter.buildHeader(
            LogExporter.ExportContext(
                exportedAt = "2026-10-23T00:15:02.123+02:00",
                timeZone = "Europe/Paris",
                appVersion = "1.2.0-17-g44a3cd6",
                versionCode = 1020017,
                buildType = "debug",
                revision = "832f4125c4d9@develop",
                device = "Google Pixel 4",
                androidRelease = "13",
                apiLevel = 33,
                buildId = "TP1A.221005.002",
                abi = "arm64-v8a",
                totalRamBytes = 5_800L * 1024 * 1024,
                availableRamBytes = 2_100L * 1024 * 1024,
                activeModel = "tiny",
                engine = "WHISPER",
                language = "fr",
                keyboardEnabled = true,
                keyboardDefault = true,
                defaultIme = "dev.pivisolutions.dictus/.ime.DictusImeService",
                recordAudio = "granted",
                postNotifications = "denied",
                window = "2026-09-01 14:29:55 -> 2026-09-01 14:31:55 | 212 lines | 19464 bytes",
            ),
        )

        assertEquals(
            """
            Dictus Debug Log
            Exported: 2026-10-23T00:15:02.123+02:00 (Europe/Paris)
            App: 1.2.0-17-g44a3cd6 (1020017) debug | rev 832f4125c4d9@develop
            Device: Google Pixel 4 | Android 13 (API 33) | build TP1A.221005.002 | arm64-v8a
            RAM: total 5800 MB, available 2100 MB
            STT: model tiny | engine WHISPER | language fr
            Keyboard: enabled true | default true | IME dev.pivisolutions.dictus/.ime.DictusImeService
            Permissions: RECORD_AUDIO granted | POST_NOTIFICATIONS denied
            Window: 2026-09-01 14:29:55 -> 2026-09-01 14:31:55 | 212 lines | 19464 bytes
            ---

            """.trimIndent(),
            header,
        )
    }

    @Test
    fun `export context reads the active model engine and the device from the system`() {
        val logFile = File(context.filesDir, "context.log").apply { writeText("") }

        val exportContext = LogExporter.exportContext(
            context,
            logFile,
            LogExporter.DictationSettings(activeModel = "tiny", language = "fr"),
        )

        assertEquals("WHISPER", exportContext.engine)
        assertEquals("fr", exportContext.language)
        assertEquals(33, exportContext.apiLevel)
        assertEquals("denied", exportContext.recordAudio)
        val zoned = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}([+-]\d{2}:\d{2}|Z)""")
        assertTrue(exportContext.exportedAt, exportContext.exportedAt.matches(zoned))
    }

    @Test
    fun `window reads the zoned timestamps written since 112`() {
        val logFile = File(context.filesDir, "zoned.log").apply {
            writeText(
                "2026-10-23T00:12:03.456+02:00 INFO    [IME] DictusImeService: first\n" +
                    "2026-10-23T00:12:09.001+02:00 INFO    [INSERT] DictusImeService: last\n",
            )
        }

        assertTrue(
            LogExporter.window(logFile),
            LogExporter.window(logFile).startsWith("2026-10-23T00:12:03 -> 2026-10-23T00:12:09 | 2 lines |"),
        )
    }

    @Test
    fun `window reports the first and last timestamps actually retained`() {
        val logFile = File(context.filesDir, "window.log").apply {
            writeText(
                "2026-09-01 14:29:55.413 [D/null] first\n" +
                    "2026-09-01 14:30:00.000 [D/null] middle\n" +
                    "2026-09-01 14:31:55.218 [D/null] last\n",
            )
        }

        val window = LogExporter.window(logFile)

        assertTrue(window, window.startsWith("2026-09-01 14:29:55 -> 2026-09-01 14:31:55 | 3 lines |"))
    }

    @Test
    fun `window says so rather than inventing one when nothing was retained`() {
        val missing = File(context.filesDir, "absent.log")
        val blank = File(context.filesDir, "blank.log").apply { writeText("\n\n") }

        assertEquals("empty", LogExporter.window(missing))
        assertEquals("empty", LogExporter.window(blank))
    }

    @Test
    fun `revision carries the injected commit and flags an unreproducible tree`() {
        val revision = LogExporter.revision()

        assertTrue(revision, revision.contains("@"))
        assertTrue(revision, revision.isNotBlank())
    }

    @Test
    fun `exported archive leads with the header before the first log line`() {
        val logFile = File(context.filesDir, "export.log").apply {
            writeText("2026-09-01 14:29:55.413 [D/null] Dictus application started\n")
        }

        val zip = LogExporter.createZip(
            context,
            logFile,
            LogExporter.DictationSettings(activeModel = "tiny", language = "fr"),
        )!!
        val content = ZipFile(zip).use { archive ->
            archive.getInputStream(archive.getEntry("dictus.log")).bufferedReader().readText()
        }

        assertTrue(content, content.startsWith("Dictus Debug Log\n"))
        assertTrue(content, content.contains("rev "))
        assertTrue(content, content.contains("STT: model tiny | engine WHISPER | language fr"))
        assertTrue(content, content.contains("Dictus application started"))
        assertTrue(content, content.indexOf("---") < content.indexOf("Dictus application started"))
    }

    @Test
    fun `a rotated log exports the previous file first and the window spans both files`() {
        val dir = File(context.filesDir, "rotated").apply { mkdirs() }
        File(dir, "dictus.previous.log").writeText("2026-10-20T08:00:00.000+02:00 INFO    [IME] DictusImeService: oldest\n")
        val logFile = File(dir, "dictus.log").apply {
            writeText("2026-10-23T02:18:42.450+02:00 INFO    [INSERT] DictusImeService: newest\n")
        }

        val zip = LogExporter.createZip(
            context,
            logFile,
            LogExporter.DictationSettings(activeModel = "tiny", language = "fr"),
        )!!
        val entries = ZipFile(zip).use { archive -> archive.entries().toList().map { it.name } }
        val current = ZipFile(zip).use { archive ->
            archive.getInputStream(archive.getEntry("dictus.log")).bufferedReader().readText()
        }

        assertEquals(listOf("dictus.previous.log", "dictus.log"), entries)
        assertTrue(current, current.contains("Window: 2026-10-20T08:00:00 -> 2026-10-23T02:18:42 | 2 lines |"))
    }

    @Test
    fun `an export right after a rotation still works with only the previous file`() {
        val dir = File(context.filesDir, "just-rotated").apply { mkdirs() }
        File(dir, "dictus.previous.log").writeText("2026-10-20T08:00:00.000+02:00 INFO    [IME] DictusImeService: only\n")

        val zip = LogExporter.createZip(
            context,
            File(dir, "dictus.log"),
            LogExporter.DictationSettings(activeModel = "tiny", language = "fr"),
        )

        val entries = ZipFile(zip!!).use { archive -> archive.entries().toList().map { it.name } }
        assertEquals(listOf("dictus.previous.log", "dictus.log"), entries)
    }
}
