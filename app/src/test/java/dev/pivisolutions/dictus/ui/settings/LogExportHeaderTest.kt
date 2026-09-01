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
    fun `header names the build, the device and the retained window`() {
        val header = LogExporter.buildHeader(
            androidRelease = "13",
            apiLevel = 33,
            device = "Google Pixel 4",
            appVersion = "1.0.0",
            versionCode = 1,
            revision = "832f4125c4d9@develop",
            microphoneGranted = true,
            selectedIme = "dev.pivisolutions.dictus/.ime.DictusImeService",
            window = "2026-09-01 14:29:55 -> 2026-09-01 14:31:55 | 212 lines | 19464 bytes",
        )

        assertEquals(
            """
            Dictus Debug Log
            Android 13 (API 33) | App 1.0.0 (1) | rev 832f4125c4d9@develop | Google Pixel 4
            Mic granted: true | IME: dev.pivisolutions.dictus/.ime.DictusImeService
            Window: 2026-09-01 14:29:55 -> 2026-09-01 14:31:55 | 212 lines | 19464 bytes
            ---

            """.trimIndent(),
            header,
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

        val zip = LogExporter.createZip(context, logFile)!!
        val content = ZipFile(zip).use { archive ->
            archive.getInputStream(archive.getEntry("dictus.log")).bufferedReader().readText()
        }

        assertTrue(content, content.startsWith("Dictus Debug Log\n"))
        assertTrue(content, content.contains("rev "))
        assertTrue(content, content.contains("Dictus application started"))
        assertTrue(content, content.indexOf("---") < content.indexOf("Dictus application started"))
    }
}
