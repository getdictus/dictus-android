package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardMicTapTest {
    @Test fun `a model on disk records at once whatever the engine is doing`() {
        listOf(
            SttEngineState.Cold,
            SttEngineState.Loading("tiny"),
            SttEngineState.Ready("tiny"),
            SttEngineState.Failed("tiny"),
        ).forEach { state ->
            assertEquals(state.toString(), KeyboardMicTap.RECORD, KeyboardMicTap.decide(state))
        }
    }

    @Test fun `no model on disk opens the Models screen`() {
        assertEquals(KeyboardMicTap.OPEN_MODELS, KeyboardMicTap.decide(SttEngineState.ModelMissing("tiny")))
    }
}
