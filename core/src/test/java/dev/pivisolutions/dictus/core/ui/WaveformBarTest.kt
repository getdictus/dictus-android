package dev.pivisolutions.dictus.core.ui

import androidx.compose.ui.graphics.Color
import dev.pivisolutions.dictus.core.theme.DictusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WaveformBars helper functions.
 *
 * Tests energy padding logic (padEnergy) and color assignment (barColor)
 * to ensure the waveform visualization behaves correctly at boundaries.
 *
 * Moved from ime module to core after WaveformBars extraction, since
 * padEnergy and barColor are internal to the core.ui package.
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
        // Remaining entries should be 0f
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
        // Should contain items 5..34 (last 30)
        assertEquals(5f, result[0], 0.001f)
        assertEquals(34f, result[29], 0.001f)
    }

    // --- barColor tests ---

    @Test
    fun `barColor returns accent range for center index 14`() {
        val color = barColor(14, 30)
        // Index 14 is in center range (11-18), should be between AccentHighlight and Accent
        // fraction = (14 - 11) / 7 = 3/7 ~ 0.43
        // Color should be blended - verify it has blue channel dominant (both accent colors are blue)
        assertTrue("Center bar should have high blue", color.blue > 0.5f)
    }

    @Test
    fun `barColor returns accent range for index 11`() {
        val color = barColor(11, 30)
        // Index 11 is the start of center range, fraction = 0, should be AccentHighlight
        assertEquals(DictusColors.AccentHighlight.red, color.red, 0.01f)
        assertEquals(DictusColors.AccentHighlight.green, color.green, 0.01f)
        assertEquals(DictusColors.AccentHighlight.blue, color.blue, 0.01f)
    }

    @Test
    fun `barColor returns accent range for index 18`() {
        val color = barColor(18, 30)
        // Index 18 is the end of center range, fraction = 1, should be Accent
        assertEquals(DictusColors.Accent.red, color.red, 0.01f)
        assertEquals(DictusColors.Accent.green, color.green, 0.01f)
        assertEquals(DictusColors.Accent.blue, color.blue, 0.01f)
    }

    @Test
    fun `barColor returns white with low alpha for edge index 0`() {
        val color = barColor(0, 30)
        // Index 0 is the farthest edge, should have alpha ~0.15
        assertEquals(1f, color.red, 0.01f) // White
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
        assertEquals(0.15f, color.alpha, 0.05f)
    }

    @Test
    fun `barColor returns white with higher alpha for index 10`() {
        val color = barColor(10, 30)
        // Index 10 is adjacent to center, distance = 0, should have alpha ~0.6
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.6f, color.alpha, 0.05f)
    }

    @Test
    fun `barColor returns white with low alpha for edge index 29`() {
        val color = barColor(29, 30)
        // Index 29 is the farthest right edge, should have alpha ~0.15
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.15f, color.alpha, 0.05f)
    }
}
