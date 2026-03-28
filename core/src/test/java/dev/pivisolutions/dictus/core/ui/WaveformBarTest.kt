package dev.pivisolutions.dictus.core.ui

import dev.pivisolutions.dictus.core.theme.DictusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WaveformBars helper functions.
 *
 * Tests energy padding logic (padEnergy), color assignment (barColor),
 * and processing energy generation to ensure the waveform visualization
 * behaves correctly and matches the iOS BrandWaveform.
 */
class WaveformBarTest {

    // --- padEnergy tests ---

    @Test
    fun `padEnergy pads short list to 30 with zeros`() {
        val input = listOf(0.5f, 0.8f)
        val result = padEnergy(input)

        assertEquals(30, result.size)
        assertEquals(0.5f, result[0], 0.001f)
        assertEquals(0.8f, result[1], 0.001f)
        for (i in 2 until 30) {
            assertEquals("Index $i should be 0f", 0f, result[i], 0.001f)
        }
    }

    @Test
    fun `padEnergy returns empty list padded to 30 zeros`() {
        val result = padEnergy(emptyList())
        assertEquals(30, result.size)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun `padEnergy returns exactly 30 items unchanged`() {
        val input = List(30) { it / 30f }
        val result = padEnergy(input)

        assertEquals(30, result.size)
        assertEquals(input, result)
    }

    @Test
    fun `padEnergy takes last 30 items when list exceeds 30`() {
        val input = List(35) { it.toFloat() }
        val result = padEnergy(input)

        assertEquals(30, result.size)
        assertEquals(5f, result[0], 0.001f)
        assertEquals(34f, result[29], 0.001f)
    }

    // --- barColor tests (distance-from-center, matching iOS BrandWaveform) ---

    @Test
    fun `barColor returns accent for center bar index 14`() {
        val color = barColor(14, 30)
        // Index 14 is near center (14.5), distance < 0.4 → brand accent
        assertEquals(DictusColors.Accent.red, color.red, 0.01f)
        assertEquals(DictusColors.Accent.green, color.green, 0.01f)
        assertEquals(DictusColors.Accent.blue, color.blue, 0.01f)
    }

    @Test
    fun `barColor returns accent for index 15`() {
        val color = barColor(15, 30)
        // Index 15: distance = |15 - 14.5| / 14.5 = 0.034 < 0.4 → accent
        assertEquals(DictusColors.Accent.red, color.red, 0.01f)
        assertEquals(DictusColors.Accent.blue, color.blue, 0.01f)
    }

    @Test
    fun `barColor returns accent for index 9`() {
        val color = barColor(9, 30)
        // Index 9: distance = |9 - 14.5| / 14.5 = 0.379 < 0.4 → accent
        assertEquals(DictusColors.Accent.red, color.red, 0.01f)
        assertEquals(DictusColors.Accent.blue, color.blue, 0.01f)
    }

    @Test
    fun `barColor returns white with decreasing alpha for edge index 0`() {
        val color = barColor(0, 30)
        // Index 0: distance = 14.5 / 14.5 = 1.0 (farthest edge)
        // opacity = (1.0 - 1.0) * 0.9 + 0.15 = 0.15
        assertEquals(1f, color.red, 0.01f) // White
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
        assertEquals(0.15f, color.alpha, 0.05f)
    }

    @Test
    fun `barColor returns white with higher alpha near center boundary`() {
        // Index 8: distance = |8 - 14.5| / 14.5 = 0.448 > 0.4 → white
        // opacity = (1.0 - 0.448) * 0.9 + 0.15 = 0.647
        val color = barColor(8, 30)
        assertEquals(1f, color.red, 0.01f)
        assertTrue("Alpha near boundary should be > 0.5", color.alpha > 0.5f)
    }

    @Test
    fun `barColor returns white with low alpha for edge index 29`() {
        val color = barColor(29, 30)
        // Index 29: distance = |29 - 14.5| / 14.5 = 1.0
        // opacity = 0.15
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.15f, color.alpha, 0.05f)
    }

    // --- processingEnergy tests ---

    @Test
    fun `processingEnergy output is within expected range`() {
        // iOS formula: 0.2 + 0.25 * (sin(...) + 1.0)
        // sin range is -1..1, so output range is 0.2 + 0.25*(0..2) = 0.2..0.7
        for (i in 0 until 30) {
            val energy = WaveformDriver.processingEnergy(i, 0.0)
            assertTrue("Energy at index $i should be >= 0.2, was $energy", energy >= 0.19f)
            assertTrue("Energy at index $i should be <= 0.7, was $energy", energy <= 0.71f)
        }
    }

    @Test
    fun `processingEnergy varies across bars`() {
        // At phase=0, different bar indices should produce different energies
        val energies = (0 until 30).map { WaveformDriver.processingEnergy(it, 0.0) }
        val uniqueCount = energies.toSet().size
        assertTrue("Processing energy should vary across bars", uniqueCount > 5)
    }

    // --- interpolateToBarTargets tests ---

    @Test
    fun `interpolateToBarTargets returns zeros for empty input`() {
        val result = WaveformDriver.interpolateToBarTargets(emptyList())
        assertEquals(30, result.size)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun `interpolateToBarTargets applies threshold`() {
        // Values below 0.05 should snap to 0 (matching iOS)
        val input = listOf(0.03f, 0.04f, 0.06f, 0.5f)
        val result = WaveformDriver.interpolateToBarTargets(input)
        // First bars should be 0 (below threshold), last bars should be > 0
        assertEquals(0f, result[0], 0.001f)
        assertTrue("Above-threshold values should pass through", result.last() > 0f)
    }
}
