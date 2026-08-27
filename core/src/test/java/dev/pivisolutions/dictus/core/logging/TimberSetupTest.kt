package dev.pivisolutions.dictus.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber

class TimberSetupTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `init with debug true plants DebugTree and FileLoggingTree`() {
        Timber.uprootAll()
        TimberSetup.init(isDebug = true, filesDir = tmpDir.root)
        // DebugTree + FileLoggingTree = 2 trees
        assertEquals(2, Timber.treeCount)
    }

    @Test
    fun `init with debug false plants only FileLoggingTree`() {
        Timber.uprootAll()
        TimberSetup.init(isDebug = false, filesDir = tmpDir.root)
        // Only FileLoggingTree planted
        assertEquals(1, Timber.treeCount)
    }

    @Test
    fun `getLogFile returns dictus dot log file`() {
        Timber.uprootAll()
        TimberSetup.init(isDebug = false, filesDir = tmpDir.root)
        val logFile = TimberSetup.getLogFile()
        assertEquals("dictus.log", logFile?.name)
    }

    @Test
    fun `first privacy migration removes legacy sensitive log content`() {
        val filesDir = tmpDir.newFolder("legacy-migration")
        val canary = "PRIVATE_TRANSCRIPTION_CANARY"
        val legacyLog = filesDir.resolve("dictus.log").apply { writeText(canary) }

        TimberSetup.init(isDebug = false, filesDir = filesDir)

        assertFalse(legacyLog.takeIf { it.exists() }?.readText().orEmpty().contains(canary))
    }

    @Test
    fun `failed legacy purge disables file logging and does not mark migration complete`() {
        val filesDir = tmpDir.newFolder("failed-migration")
        val undeletableLog = filesDir.resolve("dictus.log").apply {
            mkdir()
            resolve("legacy-entry").writeText("PRIVATE_TRANSCRIPTION_CANARY")
        }

        TimberSetup.init(isDebug = false, filesDir = filesDir)

        assertNull(TimberSetup.getLogFile())
        assertFalse(filesDir.resolve(".privacy-log-v1").exists())
        assertTrue(undeletableLog.exists())
    }
}
