package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardMicTapTest {
    private val everyEngineState = listOf(
        SttEngineState.Cold,
        SttEngineState.Loading("tiny"),
        SttEngineState.Ready("tiny"),
        SttEngineState.Failed("tiny"),
        SttEngineState.ModelMissing("tiny"),
    )

    @Test fun `a model on disk records at once whatever the engine is doing`() {
        everyEngineState.filterNot { it is SttEngineState.ModelMissing }.forEach { state ->
            assertEquals(state.toString(), KeyboardMicTap.RECORD, KeyboardMicTap.decide(state, micGranted = true))
        }
    }

    @Test fun `no model on disk opens the Models screen`() {
        assertEquals(
            KeyboardMicTap.OPEN_MODELS,
            KeyboardMicTap.decide(SttEngineState.ModelMissing("tiny"), micGranted = true),
        )
    }

    @Test fun `a refused microphone never records nor opens Models, whatever the engine state`() {
        everyEngineState.forEach { state ->
            assertEquals(
                state.toString(),
                KeyboardMicTap.MICROPHONE_REFUSED,
                KeyboardMicTap.decide(state, micGranted = false),
            )
        }
    }
}
