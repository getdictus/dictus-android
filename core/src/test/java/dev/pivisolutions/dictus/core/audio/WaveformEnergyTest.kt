package dev.pivisolutions.dictus.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/** Checks the port of the iOS waveform pipeline (UnifiedAudioEngine.swift) formula by formula. */
class WaveformEnergyTest {

    private fun constant(amplitude: Float, count: Int) = FloatArray(count) { amplitude }

    // --- bucketCount: max(3, min(6, count / 160)) ---

    @Test
    fun `bucketCount follows the iOS bounds`() {
        assertEquals(3, WaveformEnergy.bucketCount(100))
        assertEquals(3, WaveformEnergy.bucketCount(480))
        assertEquals(4, WaveformEnergy.bucketCount(640))
        assertEquals(6, WaveformEnergy.bucketCount(1365))
        assertEquals(6, WaveformEnergy.bucketCount(16_000))
    }

    // --- makeBuckets: clamp(peak * 0.65 + rms * 6.5, 0, 1) ---

    @Test
    fun `silence gives zero buckets`() {
        val buckets = WaveformEnergy.makeBuckets(FloatArray(960))
        assertEquals(6, buckets.size)
        buckets.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `known RMS gives peak times 0_65 plus rms times 6_5`() {
        // Constant 0.01: peak = rms = 0.01 -> 0.0065 + 0.065 = 0.0715
        val buckets = WaveformEnergy.makeBuckets(constant(0.01f, 480))
        assertEquals(3, buckets.size)
        buckets.forEach { assertEquals(0.0715f, it, 0.0001f) }
    }

    @Test
    fun `negative samples count by magnitude`() {
        val buckets = WaveformEnergy.makeBuckets(constant(-0.01f, 480))
        buckets.forEach { assertEquals(0.0715f, it, 0.0001f) }
    }

    @Test
    fun `full-scale sine clamps to 1`() {
        // peak 1, rms 0.707 -> 0.65 + 4.6 -> clamped to 1
        val sine = FloatArray(1600) { sin(2 * PI * 440 * it / 16_000).toFloat() }
        WaveformEnergy.makeBuckets(sine).forEach { assertEquals(1f, it, 0f) }
    }

    @Test
    fun `a 1365-sample read gives six buckets plus the iOS remainder bucket`() {
        // bucketSize = 1365 / 6 = 227 -> 6 x 227 = 1362, then a last bucket of 3 samples.
        assertEquals(7, WaveformEnergy.makeBuckets(FloatArray(1365)).size)
    }

    @Test
    fun `only count samples are read`() {
        val samples = constant(0.01f, 480) + constant(1f, 480)
        WaveformEnergy.makeBuckets(samples, 480).forEach { assertEquals(0.0715f, it, 0.0001f) }
    }

    // --- resample ---

    @Test
    fun `resample of empty source is all zeros`() {
        assertEquals(List(30) { 0f }, WaveformEnergy.resample(emptyList(), 30))
    }

    @Test
    fun `resample interpolates linearly`() {
        val result = WaveformEnergy.resample(listOf(0f, 1f), 30)
        assertEquals(0f, result.first(), 0.0001f)
        assertEquals(1f, result.last(), 0.0001f)
        assertEquals(15f / 29f, result[15], 0.0001f)
    }

    // --- enhanceContrast ---

    @Test
    fun `contrast leaves a quiet row untouched`() {
        val quiet = List(30) { 0.05f }
        assertEquals(quiet, WaveformEnergy.enhanceContrast(quiet))
    }

    @Test
    fun `contrast leaves a row with enough spread untouched`() {
        val varied = List(30) { if (it % 2 == 0) 0.2f else 0.4f }
        assertEquals(varied, WaveformEnergy.enhanceContrast(varied))
    }

    @Test
    fun `contrast stretches a flat audible row with the floor and centre bias`() {
        // Flat 0.3: spread 0 -> normalized 0.5; floor = min(0.3 * 0.28, 0.16) = 0.084;
        // stretched = 0.084 + 0.5 * 0.916 = 0.542; edge bias 1 - 0.5 * 0.18 = 0.91.
        val result = WaveformEnergy.enhanceContrast(List(30) { 0.3f })
        assertEquals(0.542f * 0.91f, result.first(), 0.0001f)
        assertEquals(0.542f * 0.91f, result.last(), 0.0001f)
        assertEquals(0.542f * (1f - (0.5f - 14f / 29f) * 0.18f), result[14], 0.0001f)
    }

    @Test
    fun `contrast floor is capped at 0_16`() {
        // max 0.9 -> 0.9 * 0.28 = 0.252, capped to 0.16. Min value maps onto the floor.
        val row = List(30) { if (it == 0) 0.8f else 0.9f }
        val result = WaveformEnergy.enhanceContrast(row)
        assertEquals(0.16f * 0.91f, result.first(), 0.0001f)
    }

    // --- rolling history ---

    @Test
    fun `snapshot is 30 zeros before any audio`() {
        assertEquals(List(30) { 0f }, WaveformEnergy().snapshot())
    }

    @Test
    fun `history keeps the last 30 buckets`() {
        val energy = WaveformEnergy()
        // 480-sample reads give 3 buckets each: 11 reads = 33 buckets, the 3 oldest drop.
        val amplitudes = List(11) { (it + 1) * 0.01f }
        amplitudes.forEach { energy.push(constant(it, 480)) }

        val snapshot = energy.snapshot()
        assertEquals(30, snapshot.size)
        // First read (0.01 -> 0.0715) is gone; the oldest kept read is 0.02.
        assertEquals(0.02f * 7.15f, snapshot.first(), 0.0001f)
        assertEquals(0.11f * 7.15f, snapshot.last(), 0.0001f)
    }

    @Test
    fun `a partial history is stretched across 30 bars`() {
        val energy = WaveformEnergy()
        energy.push(constant(0.02f, 480))
        energy.push(constant(0.1f, 480))

        val snapshot = energy.snapshot()
        assertEquals(30, snapshot.size)
        assertEquals(0.143f, snapshot.first(), 0.0001f)
        assertEquals(0.715f, snapshot.last(), 0.0001f)
    }

    @Test
    fun `reset returns to a flat row`() {
        val energy = WaveformEnergy()
        energy.push(constant(0.1f, 480))
        energy.reset()
        assertEquals(List(30) { 0f }, energy.snapshot())
    }

    @Test
    fun `snapshots stay valid during concurrent pushes`() {
        val energy = WaveformEnergy()
        val start = CountDownLatch(1)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val writers = List(2) {
            thread {
                start.await()
                repeat(20_000) { index ->
                    runCatching { energy.push(constant((index % 10) / 10f, 480)) }
                        .exceptionOrNull()?.let(failures::add)
                }
            }
        }
        val readers = List(2) {
            thread {
                start.await()
                repeat(20_000) {
                    runCatching {
                        val snapshot = energy.snapshot()
                        assertEquals(30, snapshot.size)
                        snapshot.forEach { level -> assertTrue(level in 0f..1f) }
                    }.exceptionOrNull()?.let(failures::add)
                }
            }
        }

        start.countDown()
        (writers + readers).forEach { worker ->
            worker.join(10_000)
            assertTrue("Concurrent worker did not finish", !worker.isAlive)
        }
        assertTrue("Concurrent access failed: ${failures.firstOrNull()}", failures.isEmpty())
    }
}
