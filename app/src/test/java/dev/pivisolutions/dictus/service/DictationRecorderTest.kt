package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One live capture at a time, and a stale one can never write the state again (#237). */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationRecorderTest {

    private class FakeCapture(private val samples: FloatArray = floatArrayOf(0.1f, 0.2f)) : AudioCapture {
        override var onEnergyUpdate: (() -> Unit)? = null
        var started = false
        var stopped = false
        var cancelled = false
        val isLive get() = started && !stopped && !cancelled

        override fun start(scope: CoroutineScope) {
            started = true
        }

        override fun stop(): FloatArray {
            stopped = true
            return samples
        }

        override fun cancel() {
            cancelled = true
        }

        override fun getEnergyHistory(): List<Float> = listOf(0.5f)

        /** A chunk read by this capture's loop, as AudioCaptureManager reports it. */
        fun emitChunk() = onEnergyUpdate?.invoke()
    }

    private val state = MutableStateFlow<DictationState>(DictationState.Idle)
    private val captures = mutableListOf<FakeCapture>()

    private fun recorder(scope: CoroutineScope) =
        DictationRecorder(scope, state) { FakeCapture().also { captures += it } }

    @Test
    fun `a second start releases the first capture instead of orphaning it`() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(backgroundScope)

        recorder.start(DictationSource.KEYBOARD)
        recorder.start(DictationSource.APP)

        assertEquals(2, captures.size)
        assertTrue(captures[0].cancelled)
        assertFalse(captures[0].isLive)
        assertTrue(captures[1].isLive)
        assertEquals(DictationSource.APP, (state.value as DictationState.Recording).source)
    }

    @Test
    fun `stale capture callbacks and timer cannot write Recording`() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(backgroundScope)
        recorder.start(DictationSource.KEYBOARD)
        val first = captures[0]
        recorder.start(DictationSource.APP)
        val second = captures[1]

        // The issue's repro: the app stops its dictation, the state goes back to Idle.
        recorder.stop()
        state.value = DictationState.Idle

        first.emitChunk()
        second.emitChunk()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(DictationState.Idle, state.value)
        assertFalse(recorder.isCapturing)
    }

    @Test
    fun `live capture publishes its source, levels and elapsed time`() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(backgroundScope)
        recorder.start(DictationSource.KEYBOARD)
        assertEquals(DictationState.Recording(DictationSource.KEYBOARD), state.value)

        captures[0].emitChunk()
        assertEquals(DictationState.Recording(DictationSource.KEYBOARD, 0L, listOf(0.5f)), state.value)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(2_000L, (state.value as DictationState.Recording).elapsedMs)

        recorder.cancel()
    }

    @Test
    fun `stop returns the live capture's samples and leaves nothing running`() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(backgroundScope)
        recorder.start(DictationSource.KEYBOARD)

        assertArrayEquals(floatArrayOf(0.1f, 0.2f), recorder.stop(), 0f)
        assertFalse(recorder.isCapturing)
        assertFalse(captures[0].isLive)

        // A second stop, the keyboard's ✓ after the app's stop in the issue, finds nothing.
        assertEquals(0, recorder.stop().size)
    }

    @Test
    fun `cancel and stop with no capture are safe`() = runTest(StandardTestDispatcher()) {
        val recorder = recorder(backgroundScope)

        recorder.cancel()
        assertEquals(0, recorder.stop().size)

        assertFalse(recorder.isCapturing)
        assertTrue(captures.isEmpty())
    }
}
