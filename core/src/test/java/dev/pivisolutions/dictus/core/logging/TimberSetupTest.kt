package dev.pivisolutions.dictus.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test
import timber.log.Timber

class TimberSetupTest {

    @Test
    fun `init with debug true plants DebugTree`() {
        Timber.uprootAll()
        TimberSetup.init(isDebug = true)
        assertEquals(1, Timber.treeCount)
    }

    @Test
    fun `init with debug false plants no tree`() {
        Timber.uprootAll()
        TimberSetup.init(isDebug = false)
        assertEquals(0, Timber.treeCount)
    }
}
