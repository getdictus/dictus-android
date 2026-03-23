package dev.pivisolutions.dictus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationStateTest {

    @Test
    fun `Idle is singleton`() {
        val a = DictationState.Idle
        val b = DictationState.Idle
        assertSame(a, b)
    }

    @Test
    fun `Recording has default values`() {
        val recording = DictationState.Recording()
        assertEquals(0L, recording.elapsedMs)
        assertTrue(recording.energy.isEmpty())
    }

    @Test
    fun `Recording with elapsed time`() {
        val recording = DictationState.Recording(elapsedMs = 5000L)
        assertEquals(5000L, recording.elapsedMs)
    }

    @Test
    fun `Recording with energy list`() {
        val energy = listOf(0.1f, 0.5f, 0.8f)
        val recording = DictationState.Recording(energy = energy)
        assertEquals(3, recording.energy.size)
        assertEquals(0.5f, recording.energy[1], 0.001f)
    }

    @Test
    fun `Idle is a DictationState`() {
        val state: DictationState = DictationState.Idle
        assertNotNull(state)
    }

    @Test
    fun `Recording is a DictationState`() {
        val state: DictationState = DictationState.Recording()
        assertNotNull(state)
    }
}
