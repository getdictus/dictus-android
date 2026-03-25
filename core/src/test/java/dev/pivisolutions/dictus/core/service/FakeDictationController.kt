package dev.pivisolutions.dictus.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for DictationController that allows tests to control state
 * transitions and verify method call counts.
 *
 * Lives in core/src/test so both app and ime test suites can use it
 * (via testImplementation(testFixtures(project(":core"))) or direct copy).
 */
class FakeDictationController : DictationController {

    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    override val state: StateFlow<DictationState> = _state.asStateFlow()

    var startRecordingCallCount = 0; private set
    var stopRecordingCallCount = 0; private set
    var cancelRecordingCallCount = 0; private set
    var stopRecordingResult = FloatArray(0)

    override fun startRecording() {
        startRecordingCallCount++
        _state.value = DictationState.Recording(elapsedMs = 0L, energy = emptyList())
    }

    override fun stopRecording(): FloatArray {
        stopRecordingCallCount++
        _state.value = DictationState.Idle
        return stopRecordingResult
    }

    override fun cancelRecording() {
        cancelRecordingCallCount++
        _state.value = DictationState.Idle
    }

    var confirmAndTranscribeCallCount = 0; private set
    var transcriptionResult: String? = "Fake transcription result. "

    override suspend fun confirmAndTranscribe(): String? {
        confirmAndTranscribeCallCount++
        _state.value = DictationState.Transcribing
        val result = transcriptionResult
        _state.value = DictationState.Idle
        return result
    }

    /** Emit an arbitrary state for test scenarios. */
    fun emitState(state: DictationState) {
        _state.value = state
    }
}
