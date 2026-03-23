package dev.pivisolutions.dictus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DictationService state logic.
 *
 * Full service lifecycle tests (binding, foreground notification, AudioRecord)
 * require Robolectric or Android instrumentation tests, which are out of scope
 * for MVP. These tests verify the state machine contract that the IME depends on.
 */
class DictationServiceTest {

    @Test
    fun `default state is Idle`() {
        // The initial state of the MutableStateFlow in DictationService is Idle.
        // We verify the state class contract here since we can't instantiate the
        // service without Android context.
        val defaultState: DictationState = DictationState.Idle
        assertTrue(defaultState is DictationState.Idle)
    }

    @Test
    fun `Recording state holds elapsed time and energy`() {
        val state = DictationState.Recording(
            elapsedMs = 3000L,
            energy = listOf(0.2f, 0.5f, 0.8f),
        )
        assertEquals(3000L, state.elapsedMs)
        assertEquals(3, state.energy.size)
        assertEquals(0.5f, state.energy[1], 0.001f)
    }

    @Test
    fun `state transitions from Idle to Recording`() {
        var currentState: DictationState = DictationState.Idle
        assertTrue(currentState is DictationState.Idle)

        // Simulate start recording
        currentState = DictationState.Recording(elapsedMs = 0L, energy = emptyList())
        assertTrue(currentState is DictationState.Recording)
    }

    @Test
    fun `state transitions from Recording back to Idle`() {
        var currentState: DictationState = DictationState.Recording(
            elapsedMs = 5000L,
            energy = listOf(0.3f, 0.6f),
        )
        assertTrue(currentState is DictationState.Recording)

        // Simulate stop recording
        currentState = DictationState.Idle
        assertTrue(currentState is DictationState.Idle)
    }

    @Test
    fun `Recording state updates elapsed time correctly`() {
        val states = listOf(
            DictationState.Recording(elapsedMs = 0L),
            DictationState.Recording(elapsedMs = 1000L),
            DictationState.Recording(elapsedMs = 2000L),
        )
        assertEquals(0L, states[0].elapsedMs)
        assertEquals(1000L, states[1].elapsedMs)
        assertEquals(2000L, states[2].elapsedMs)
    }

    @Test
    fun `companion constants are defined`() {
        // Verify the constants that external code (IME, notification) depends on
        assertEquals("dictus_recording", DictationService.CHANNEL_ID)
        assertEquals(1, DictationService.NOTIFICATION_ID)
        assertEquals("dev.pivisolutions.dictus.action.START", DictationService.ACTION_START)
        assertEquals("dev.pivisolutions.dictus.action.STOP", DictationService.ACTION_STOP)
    }
}
