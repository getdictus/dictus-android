package dev.pivisolutions.dictus.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMicGateTest {
    @Test fun `cold intent prewarms and starts exactly once after ready`() {
        val gate = PendingMicGate()
        assertEquals(MicGateCommand.PREWARM, gate.request(SttEngineState.Cold))
        assertTrue(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Loading("tiny")))
        assertEquals(MicGateCommand.START_RECORDING, gate.engineChanged(SttEngineState.Ready("tiny")))
        assertFalse(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    @Test fun `loading intent stays pending without prewarming or starting`() {
        val gate = PendingMicGate()
        assertEquals(MicGateCommand.NONE, gate.request(SttEngineState.Loading("tiny")))
        assertTrue(gate.isPending)
    }

    @Test fun `ready intent starts immediately and never prewarms`() {
        val gate = PendingMicGate()
        assertEquals(MicGateCommand.START_RECORDING, gate.request(SttEngineState.Ready("tiny")))
        assertFalse(gate.isPending)
    }

    @Test fun `tapping with no model rechecks the disk without queueing the intent`() {
        val gate = PendingMicGate()
        // Without a Retry button this tap is the only refresh path, so it must prewarm.
        assertEquals(MicGateCommand.PREWARM, gate.request(SttEngineState.ModelMissing("tiny")))
        assertFalse(gate.isPending)
        // A later Ready must not start a recording the user never asked for.
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    @Test fun `prewarm that discovers no model clears a pending intent`() {
        val gate = PendingMicGate()
        assertEquals(MicGateCommand.PREWARM, gate.request(SttEngineState.Cold))
        assertTrue(gate.isPending)

        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.ModelMissing("tiny")))
        assertFalse(gate.isPending)

        // The user downloads a model later; recording must wait for a fresh tap.
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    @Test fun `failed retry prewarms and cancel clears pending intent`() {
        val gate = PendingMicGate()
        assertEquals(MicGateCommand.NONE, gate.request(SttEngineState.Failed("tiny")))
        assertEquals(MicGateCommand.PREWARM, gate.retry())
        gate.cancel()
        assertFalse(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    @Test fun `retry after background failure does not create microphone intent`() {
        val gate = PendingMicGate()

        assertEquals(MicGateCommand.PREWARM, gate.retry())
        assertFalse(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    // #175: after a model switch the engine still holds the previous model.
    @Test fun `ready on the previous model after a switch is not ready at tap`() {
        val gate = PendingMicGate().apply { selectedModelKey = "tiny" }

        assertEquals(MicGateCommand.PREWARM, gate.request(SttEngineState.Ready("small-q5_1")))
        assertTrue(gate.isPending)
        // The previous model republished as Ready must not start the recording either.
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("small-q5_1")))
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Cold))
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Loading("tiny")))
        assertEquals(MicGateCommand.START_RECORDING, gate.engineChanged(SttEngineState.Ready("tiny")))
        assertFalse(gate.isPending)
    }

    @Test fun `load of the selected model in progress at tap waits for it`() {
        val gate = PendingMicGate().apply { selectedModelKey = "tiny" }

        assertEquals(MicGateCommand.NONE, gate.request(SttEngineState.Loading("tiny")))
        assertTrue(gate.isPending)
        assertEquals(MicGateCommand.START_RECORDING, gate.engineChanged(SttEngineState.Ready("tiny")))
    }

    @Test fun `ready on the selected model starts at once`() {
        val gate = PendingMicGate().apply { selectedModelKey = "tiny" }

        assertEquals(MicGateCommand.START_RECORDING, gate.request(SttEngineState.Ready("tiny")))
        assertFalse(gate.isPending)
    }

    @Test fun `unknown selection keeps accepting any ready engine`() {
        val gate = PendingMicGate()

        assertEquals(MicGateCommand.START_RECORDING, gate.request(SttEngineState.Ready("small-q5_1")))
    }

    @Test fun `switch to a model not on disk ends on the missing state, not queued`() {
        val gate = PendingMicGate().apply { selectedModelKey = "base" }

        assertEquals(MicGateCommand.PREWARM, gate.request(SttEngineState.Ready("tiny")))
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.ModelMissing("base")))
        assertFalse(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Ready("base")))
    }

    @Test fun `failed load of the selected model waits for an explicit choice`() {
        val gate = PendingMicGate().apply { selectedModelKey = "tiny" }

        assertEquals(MicGateCommand.NONE, gate.request(SttEngineState.Failed("tiny")))
        assertTrue(gate.isPending)
        assertEquals(MicGateCommand.NONE, gate.engineChanged(SttEngineState.Failed("tiny")))
    }
}
