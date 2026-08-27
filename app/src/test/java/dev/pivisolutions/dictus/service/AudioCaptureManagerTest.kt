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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class AudioCaptureManagerTest {

    private val manager = AudioCaptureManager()

    // --- calculateRmsEnergy tests ---

    @Test
    fun `calculateRmsEnergy returns 0 for empty count`() {
        val result = manager.calculateRmsEnergy(floatArrayOf(0.5f, 0.5f), 0)
        assertEquals(0f, result, 0.0001f)
    }

    @Test
    fun `calculateRmsEnergy with known values`() {
        // [0.5, 0.5] -> sqrt((0.25 + 0.25) / 2) = sqrt(0.25) = 0.5
        val result = manager.calculateRmsEnergy(floatArrayOf(0.5f, 0.5f), 2)
        assertEquals(0.5f, result, 0.0001f)
    }

    @Test
    fun `calculateRmsEnergy with single value`() {
        // [0.3] -> sqrt(0.09 / 1) = 0.3
        val result = manager.calculateRmsEnergy(floatArrayOf(0.3f), 1)
        assertEquals(0.3f, result, 0.0001f)
    }

    @Test
    fun `calculateRmsEnergy with zeros`() {
        val result = manager.calculateRmsEnergy(floatArrayOf(0f, 0f, 0f), 3)
        assertEquals(0f, result, 0.0001f)
    }

    @Test
    fun `calculateRmsEnergy only uses count elements`() {
        // Only first 2 of 3 elements: [0.5, 0.5, 999.0] -> sqrt(0.25) = 0.5
        val result = manager.calculateRmsEnergy(floatArrayOf(0.5f, 0.5f, 999f), 2)
        assertEquals(0.5f, result, 0.0001f)
    }

    // --- normalizeEnergy tests ---

    @Test
    fun `normalizeEnergy zero input returns zero`() {
        assertEquals(0f, manager.normalizeEnergy(0f), 0.0001f)
    }

    @Test
    fun `normalizeEnergy typical speech value`() {
        // 0.1 * 20 = 2.0 -> coerced to 1.0 -> sqrt(1.0) = 1.0
        assertEquals(1.0f, manager.normalizeEnergy(0.1f), 0.0001f)
    }

    @Test
    fun `normalizeEnergy high value clamped to 1`() {
        // 0.3 * 5 = 1.5 -> coerced to 1.0
        assertEquals(1.0f, manager.normalizeEnergy(0.3f), 0.0001f)
    }

    @Test
    fun `normalizeEnergy exact boundary 0_2 returns 1`() {
        // 0.2 * 5 = 1.0
        assertEquals(1.0f, manager.normalizeEnergy(0.2f), 0.0001f)
    }

    // --- Energy history tests ---

    @Test
    fun `energy history starts with 30 zero entries`() {
        // AudioCaptureManager pre-fills energyHistory with 30 zeros so the
        // waveform renders all 30 bars immediately without a slide-in effect.
        val history = manager.getEnergyHistory()
        assertEquals(30, history.size)
        history.forEach { assertEquals(0f, it, 0.0001f) }
    }

    @Test
    fun `energy history caps at 30 entries`() {
        // Add 35 entries, should keep last 30
        repeat(35) { i ->
            manager.addEnergyToHistory(i.toFloat() / 100f)
        }
        val history = manager.getEnergyHistory()
        assertEquals(30, history.size)
    }

    @Test
    fun `energy history drops oldest when full`() {
        // Add values 0..34, history should contain 5..34
        repeat(35) { i ->
            manager.addEnergyToHistory(i.toFloat())
        }
        val history = manager.getEnergyHistory()
        assertEquals(5f, history.first(), 0.0001f)
        assertEquals(34f, history.last(), 0.0001f)
    }

    @Test
    fun `energy history snapshots stay valid during concurrent updates`() {
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val writers = List(2) { writerIndex ->
            thread(start = true) {
                start.await()
                repeat(100_000) { index ->
                    runCatching {
                        manager.addEnergyToHistory((writerIndex + index).toFloat())
                    }.exceptionOrNull()?.let(failures::add)
                }
            }
        }
        val readers = List(2) {
            thread(start = true) {
                start.await()
                repeat(100_000) {
                    runCatching {
                        val snapshot = manager.getEnergyHistory()
                        assertEquals(30, snapshot.size)
                        snapshot.forEach { energy -> assertTrue(energy.isFinite()) }
                    }.exceptionOrNull()?.let(failures::add)
                }
            }
        }

        start.countDown()
        (writers + readers).forEach { worker ->
            worker.join(10_000)
            assertTrue("Concurrent history worker did not finish", !worker.isAlive)
        }

        assertTrue("Concurrent history access failed: ${failures.firstOrNull()}", failures.isEmpty())
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
