package dev.pivisolutions.dictus.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WaveformDriver.
 *
 * Tests the pure math functions (tickLevels, interpolateToBarTargets) that
 * drive the smooth animation — no Android framework needed.
 */
class WaveformDriverTest {

    // --- tickLevels: rise (target > current) ---

    @Test
    fun `tickLevels rise from 0 to 1 applies smoothingFactor`() {
        val result = WaveformDriver.tickLevels(
            current = listOf(0.0f),
            targets = listOf(1.0f),
            smoothingFactor = 0.3f,
            decayFactor = 0.85f,
        )
        // current + (target - current) * smoothingFactor = 0 + 1 * 0.3 = 0.3
        assertEquals(0.3f, result[0], 0.0001f)
    }

    // --- tickLevels: decay (target < current) ---

    @Test
    fun `tickLevels decay from 1 to 0 applies decayFactor`() {
        val result = WaveformDriver.tickLevels(
            current = listOf(1.0f),
            targets = listOf(0.0f),
            smoothingFactor = 0.3f,
            decayFactor = 0.85f,
        )
        // target + (current - target) * decayFactor = 0 + 1 * 0.85 = 0.85
        assertEquals(0.85f, result[0], 0.0001f)
    }

    // --- tickLevels: snap to zero ---

    @Test
    fun `tickLevels snaps value below 0_005 to exactly 0`() {
        // Decay from a very small value will produce a result below 0.005
        // current=0.004, target=0 -> decay: 0 + 0.004 * 0.85 = 0.0034 < 0.005 -> snap to 0
        val result = WaveformDriver.tickLevels(
            current = listOf(0.004f),
            targets = listOf(0.0f),
            smoothingFactor = 0.3f,
            decayFactor = 0.85f,
        )
        assertEquals(0.0f, result[0], 0.0001f)
    }

    // --- interpolateToBarTargets: empty input ---

    @Test
    fun `interpolateToBarTargets empty input returns 30 zeros`() {
        val result = WaveformDriver.interpolateToBarTargets(emptyList())
        assertEquals(30, result.size)
        assertTrue(result.all { it == 0f })
    }

    // --- interpolateToBarTargets: single value ---

    @Test
    fun `interpolateToBarTargets single value produces 30 bars all equal`() {
        val result = WaveformDriver.interpolateToBarTargets(listOf(0.5f))
        assertEquals(30, result.size)
        assertTrue("All bars should equal 0.5", result.all { it == 0.5f })
    }

    // --- interpolateToBarTargets: two values gradient ---

    @Test
    fun `interpolateToBarTargets two values produces linear gradient across 30 bars`() {
        val result = WaveformDriver.interpolateToBarTargets(listOf(0.0f, 1.0f))
        assertEquals(30, result.size)
        // First bar should be near 0.0, last bar should be near 1.0
        assertEquals(0.0f, result.first(), 0.05f)
        assertEquals(1.0f, result.last(), 0.05f)
        // Check that values are monotonically increasing
        for (i in 1 until result.size) {
            assertTrue("Bar $i should be >= bar ${i - 1}", result[i] >= result[i - 1] - 0.001f)
        }
    }

    // --- frameFactors: iOS 60 Hz smoothing at any refresh rate ---

    @Test
    fun `frameFactors at 60 Hz are the iOS factors`() {
        val (rise, decay) = WaveformDriver.frameFactors(1.0 / 60)
        assertEquals(0.3f, rise, 0.0001f)
        assertEquals(0.85f, decay, 0.0001f)
    }

    @Test
    fun `three 90 Hz frames match two 60 Hz frames on rise and decay`() {
        fun run(fps: Double, frames: Int, start: Float, target: Float): Float {
            val (rise, decay) = WaveformDriver.frameFactors(1.0 / fps)
            var levels = listOf(start)
            repeat(frames) {
                levels = WaveformDriver.tickLevels(levels, listOf(target), rise, decay)
            }
            return levels[0]
        }
        // Rise 0 -> 1 over 1/30 s: 1 - 0.7^2 = 0.51
        assertEquals(0.51f, run(60.0, 2, 0f, 1f), 0.0001f)
        assertEquals(0.51f, run(90.0, 3, 0f, 1f), 0.0001f)
        // Decay 1 -> 0 over 1/30 s: 0.85^2 = 0.7225
        assertEquals(0.7225f, run(60.0, 2, 1f, 0f), 0.0001f)
        assertEquals(0.7225f, run(90.0, 3, 1f, 0f), 0.0001f)
    }

    @Test
    fun `frameFactors clamp a long gap to 100 ms and a negative one to zero`() {
        assertEquals(WaveformDriver.frameFactors(0.1), WaveformDriver.frameFactors(5.0))
        val (rise, decay) = WaveformDriver.frameFactors(-1.0)
        assertEquals(0f, rise, 0f)
        assertEquals(1f, decay, 0f)
    }

    @Test
    fun `step response reaches the iOS curve and snaps to zero on silence`() {
        val (rise, decay) = WaveformDriver.frameFactors(1.0 / 60)
        var levels = listOf(0f)
        repeat(10) { levels = WaveformDriver.tickLevels(levels, listOf(1f), rise, decay) }
        // 1 - 0.7^10
        assertEquals(0.97175f, levels[0], 0.0001f)
        // 0.97 * 0.85^n < 0.005 after 33 frames, then snaps to exactly 0.
        repeat(33) { levels = WaveformDriver.tickLevels(levels, listOf(0f), rise, decay) }
        assertEquals(0f, levels[0], 0f)
    }

    // --- phaseAdvance: iOS ProcessingWaveform.phaseAdvance(for: .sweep) ---

    @Test
    fun `phaseAdvance runs at half a cycle per second`() {
        assertEquals(1.0 / 120, WaveformDriver.phaseAdvance(1.0 / 60), 1e-9)
    }

    @Test
    fun `phaseAdvance clamps one frame to 100 ms`() {
        assertEquals(0.05, WaveformDriver.phaseAdvance(3.0), 1e-9)
        assertEquals(0.0, WaveformDriver.phaseAdvance(-0.5), 1e-9)
    }
}
