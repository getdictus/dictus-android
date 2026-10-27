package dev.pivisolutions.dictus.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureManagerTest {

    private val manager = AudioCaptureManager()

    @Test
    fun `energy history starts with 30 zero entries`() {
        // Before the first read the waveform renders all 30 bars flat, no slide-in.
        val history = manager.getEnergyHistory()
        assertEquals(30, history.size)
        history.forEach { assertEquals(0f, it, 0.0001f) }
    }

    @Test
    fun `read chunk matches the iOS tap converted to 16 kHz`() {
        // 4096 frames at 48 kHz = 1365 samples at 16 kHz -> 6 buckets per read, as on iOS.
        assertEquals(1365, AudioCaptureManager.READ_CHUNK_SAMPLES)
    }

    @Test
    fun `stop waits for final read reaching exact minimum sample count`() = runBlocking {
        assertFinalReadIsIncluded(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT)
    }

    @Test
    fun `stop waits for final read above minimum sample count`() = runBlocking {
        assertFinalReadIsIncluded(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT + 1)
    }

    private suspend fun assertFinalReadIsIncluded(finalSampleCount: Int) {
        val stopSignal = CompletableDeferred<Unit>()
        val collectedSamples = ArrayList<Float>()
        val captureJob = CoroutineScope(Dispatchers.Default).launch {
            stopSignal.await()
            // Model a blocking AudioRecord read that completes only after stop().
            delay(20)
            repeat(finalSampleCount) { collectedSamples.add(0.1f) }
        }

        manager.stopCaptureAndAwait(captureJob) { stopSignal.complete(Unit) }

        assertEquals(finalSampleCount, collectedSamples.size)
        assertTrue(RecordingDurationPolicy.canTranscribe(collectedSamples.size))
    }
}
