package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A stop without ✓ transcribes an app dictation; only ✕ discards (#248). */
@OptIn(ExperimentalCoroutinesApi::class)
class UnconfirmedStopTest {

    private val take = floatArrayOf(0.1f, 0.2f, 0.3f)

    private class FakeCapture(private val samples: FloatArray) : AudioCapture {
        override var onEnergyUpdate: (() -> Unit)? = null
        var stopped = false
        var cancelled = false
        override fun start(scope: CoroutineScope) = Unit
        override fun stop(): FloatArray = samples.also { stopped = true }
        override fun cancel() {
            cancelled = true
        }
        override fun getEnergyHistory(): List<Float> = emptyList()
    }

    private val state = MutableStateFlow<DictationState>(DictationState.Idle)
    private val capture = FakeCapture(take)
    private var transcribed: FloatArray? = null
    private var stateWhileTranscribing: DictationState? = null
    private var doneCount = 0

    private fun TestScope.unconfirmedStop(outcome: TranscriptionOutcome): Pair<DictationRecorder, UnconfirmedStop> {
        val recorder = DictationRecorder(backgroundScope, state) { capture }
        return recorder to UnconfirmedStop(recorder, state) { samples ->
            transcribed = samples
            stateWhileTranscribing = state.value
            outcome
        }
    }

    @Test
    fun `notification stop transcribes an app dictation`() = runTest {
        val (recorder, stop) = unconfirmedStop(TranscriptionOutcome.Text("kept text"))
        recorder.start(DictationSource.APP)

        val job = stop.stop(UnconfirmedStop.Reason.NOTIFICATION_STOP, this) { doneCount++ }
        job!!.join()

        assertTrue(capture.stopped)
        assertFalse(capture.cancelled)
        assertArrayEquals(take, transcribed, 0f)
        assertEquals(DictationState.Transcribing, stateWhileTranscribing)
        assertEquals(1, doneCount)
    }

    @Test
    fun `service teardown transcribes too`() = runTest {
        val (recorder, stop) = unconfirmedStop(TranscriptionOutcome.Text("kept text"))
        recorder.start(DictationSource.APP)

        stop.stop(UnconfirmedStop.Reason.SERVICE_DESTROYED, this) { doneCount++ }!!.join()

        assertArrayEquals(take, transcribed, 0f)
        assertEquals(1, doneCount)
    }

    @Test
    fun `a keyboard dictation is left to the caller`() = runTest {
        val (recorder, stop) = unconfirmedStop(TranscriptionOutcome.Text("keyboard text"))
        recorder.start(DictationSource.KEYBOARD)

        val job = stop.stop(UnconfirmedStop.Reason.NOTIFICATION_STOP, this) { doneCount++ }

        assertNull(job)
        assertFalse(capture.stopped)
        assertFalse(capture.cancelled)
        assertNull(transcribed)
        assertEquals(DictationState.Recording(DictationSource.KEYBOARD), state.value)
        assertEquals(0, doneCount)
    }

    @Test
    fun `nothing happens without a live capture`() = runTest {
        val (_, stop) = unconfirmedStop(TranscriptionOutcome.Text("text"))

        assertNull(stop.stop(UnconfirmedStop.Reason.SERVICE_DESTROYED, this) { doneCount++ })
        assertNull(transcribed)
        assertEquals(0, doneCount)
    }
}
