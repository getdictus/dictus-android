package dev.pivisolutions.dictus.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
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
}
