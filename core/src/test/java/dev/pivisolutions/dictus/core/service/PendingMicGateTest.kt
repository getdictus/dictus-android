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
}