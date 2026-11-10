package dev.pivisolutions.dictus.core.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DictationTraceTest {

    private var nowNanos = 0L

    @Before
    fun setUp() {
        DictationTrace.reset()
        DictationTrace.clock = { nowNanos }
    }

    @After
    fun tearDown() = DictationTrace.reset()

    private fun advanceMs(ms: Long) {
        nowNanos += ms * 1_000_000
    }

    @Test
    fun `the service adopts the trace a keyboard tap just opened`() {
        val id = DictationTrace.begin(source = "keyboard")
        advanceMs(40)

        val trace = DictationTrace.onRecordingRequested(fallbackSource = "app")

        assertEquals(id, trace.id)
        assertEquals("keyboard", trace.source)
        assertEquals(40L, DictationTrace.elapsedMs())
    }

    @Test
    fun `a recording started from the app opens its own trace`() {
        val trace = DictationTrace.onRecordingRequested(fallbackSource = "app")

        assertEquals("app", trace.source)
        assertEquals(trace.id, DictationTrace.currentId())
    }

    @Test
    fun `a stale tap that never recorded is not adopted`() {
        val staleId = DictationTrace.begin(source = "keyboard")
        advanceMs(61_000)

        val trace = DictationTrace.onRecordingRequested(fallbackSource = "app")

        assertNotEquals(staleId, trace.id)
        assertEquals("app", trace.source)
    }

    @Test
    fun `a trace that already recorded is never adopted by the next recording`() {
        val first = DictationTrace.onRecordingRequested(fallbackSource = "app")

        val second = DictationTrace.onRecordingRequested(fallbackSource = "app")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `after the service ends the trace the insertion still reads its id and total time`() {
        val id = DictationTrace.begin(source = "keyboard")
        DictationTrace.onRecordingRequested(fallbackSource = "app")
        advanceMs(3_000)
        DictationTrace.end()
        advanceMs(15)

        assertNull(DictationTrace.currentId())
        assertEquals(id, DictationTrace.lastId())
        assertEquals(3_015L, DictationTrace.elapsedMs())
    }
}
