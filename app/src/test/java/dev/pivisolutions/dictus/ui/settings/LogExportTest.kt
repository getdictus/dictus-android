package dev.pivisolutions.dictus.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit tests for LogExporter.
 *
 * Uses Robolectric's ApplicationProvider to get a real context with a
 * working filesDir and cacheDir. FileProvider URI generation is NOT tested
 * here because it requires a registered authority in a merged manifest,
 * which Robolectric's unit-test profile does not support.
 *
 * The createZip() helper is tested directly; exportLogs() delegates to it
 * and only adds the FileProvider.getUriForFile() call on top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogExportTest {

    private lateinit var context: android.app.Application
    private lateinit var logFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logFile = File(context.filesDir, "dictus.log")
        // Clean up from previous runs
        logFile.delete()
        File(context.cacheDir, "dictus-logs.zip").delete()
    }

    @Test
    fun `exportLogs returns null when logFile does not exist`() {
        // createZip is called internally; it returns null if logFile missing
        val result = LogExporter.createZip(context, logFile)
        assertNull(result)
    }

    @Test
    fun `createZip creates ZIP file in cacheDir`() {
        logFile.writeText("2026-03-26 10:00:00.000 [D/Test] Hello\n")

        val zipFile = LogExporter.createZip(context, logFile)

        assertNotNull("createZip should return a File", zipFile)
        assertTrue("ZIP file should exist in cacheDir", zipFile!!.exists())
        assertTrue(
            "ZIP must be inside cacheDir",
            zipFile.canonicalPath.startsWith(context.cacheDir.canonicalPath),
        )
    }

    @Test
    fun `createZip ZIP contains entry named dictus dot log`() {
        logFile.writeText("2026-03-26 10:00:00.000 [D/Test] Hello\n")

        val zipFile = LogExporter.createZip(context, logFile)
        assertNotNull("ZIP file must exist", zipFile)

        val entries = ZipFile(zipFile!!).use { zip ->
            zip.entries().toList().map { it.name }
        }
        assertTrue(
            "ZIP must contain 'dictus.log' entry but has: $entries",
            entries.contains("dictus.log"),
        )
    }
}
