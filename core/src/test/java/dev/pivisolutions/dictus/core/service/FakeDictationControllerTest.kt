package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for FakeDictationController to verify the test double
 * behaves correctly for use in app and ime test suites.
 */
class FakeDictationControllerTest {

    private lateinit var controller: FakeDictationController

    @Before
    fun setUp() {
        controller = FakeDictationController()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(controller.state.value is DictationState.Idle)
    }

    @Test
    fun `startRecording transitions to Recording`() {
        controller.startRecording()

        assertTrue(controller.state.value is DictationState.Recording)
        assertEquals(1, controller.startRecordingCallCount)
    }

    @Test
    fun `stopRecording transitions to Idle and returns result`() {
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f)
        controller.stopRecordingResult = expected
        controller.startRecording()

        val result = controller.stopRecording()

        assertTrue(controller.state.value is DictationState.Idle)
        assertArrayEquals(expected, result, 0.001f)
        assertEquals(1, controller.stopRecordingCallCount)
    }

    @Test
    fun `cancelRecording transitions to Idle`() {
        controller.startRecording()
        controller.cancelRecording()

        assertTrue(controller.state.value is DictationState.Idle)
        assertEquals(1, controller.cancelRecordingCallCount)
    }

    @Test
    fun `emitState sets arbitrary state`() {
        val recording = DictationState.Recording(5000L, listOf(0.5f))
        controller.emitState(recording)

        val state = controller.state.value
        assertTrue(state is DictationState.Recording)
        val recordingState = state as DictationState.Recording
        assertEquals(5000L, recordingState.elapsedMs)
        assertEquals(listOf(0.5f), recordingState.energy)
    }
}
