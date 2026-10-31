package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPreparationGateTest {
    private val engineStates = listOf(
        SttEngineState.Cold,
        SttEngineState.Loading("tiny"),
        SttEngineState.Ready("tiny"),
        SttEngineState.Failed("tiny"),
        SttEngineState.ModelMissing("tiny"),
    )

    @Test fun `idle dictation shows the engine state as is`() {
        engineStates.forEach { state ->
            assertEquals(state, ModelPreparationGate.overlayState(state, DictationState.Idle))
        }
    }

    @Test fun `recording hides the overlay whatever the engine does`() {
        engineStates.forEach { state ->
            assertEquals(
                SttEngineState.Cold,
                ModelPreparationGate.overlayState(state, DictationState.Recording(DictationSource.APP)),
            )
        }
    }

    // #175: the transcription loads the newly selected model; its overlay must not cover it.
    @Test fun `transcribing hides the overlay whatever the engine does`() {
        engineStates.forEach { state ->
            assertEquals(
                SttEngineState.Cold,
                ModelPreparationGate.overlayState(state, DictationState.Transcribing),
            )
        }
    }
}
