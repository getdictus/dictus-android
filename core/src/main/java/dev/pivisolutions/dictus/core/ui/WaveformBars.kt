package dev.pivisolutions.dictus.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusColors
import kotlin.math.abs

/**
 * Canvas-based 30-bar waveform visualization for recording overlays.
 *
 * Ported from iOS BrandWaveform. Each bar is a rounded rectangle (pill shape)
 * drawn on a single Canvas for optimal performance (single GPU draw call).
 *
 * Color scheme (matching iOS BrandWaveform.resolvedBarColor):
 *   - Center 40% of bars: brand blue (DictusColors.Accent)
 *   - Outer 60%: white with opacity decreasing toward edges
 *
 * Bar height formula (matching iOS):
 *   height = max(minHeight + energy * (maxHeight - minHeight), minHeight)
 *   - minHeight = 2dp (idle) or 4dp (processing): always-visible baseline
 *   - This ensures bars scale energy within the available height range
 *     rather than raw multiplication which under-utilizes the space
 *
 * Processing mode: when isProcessing=true, bars show a traveling sine wave
 * using WaveformDriver.processingEnergy() instead of live energy levels.
 *
 * This composable lives in core so both the app module and the ime module
 * can reuse it without duplication.
 */
@Composable
fun WaveformBars(
    energyLevels: List<Float>,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    processingPhase: Double = 0.0,
) {
    val paddedLevels = padEnergy(energyLevels)

    Canvas(modifier = modifier) {
        val barCount = WaveformDriver.BAR_COUNT
        val gapPx = 2.dp.toPx()
        val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
        // Min bar height: 2dp idle (thin visible baseline), 4dp processing
        // Matches iOS: let minHeight: CGFloat = driver.isProcessing ? 4 : 2
        val minBarHeight = if (isProcessing) 4.dp.toPx() else 2.dp.toPx()
        val maxBarHeight = size.height
        val centerY = size.height / 2f

        for (index in 0 until barCount) {
            val energy: Float = if (isProcessing) {
                WaveformDriver.processingEnergy(index, processingPhase, barCount)
            } else {
                paddedLevels.getOrElse(index) { 0f }
            }

            // iOS formula: max(minHeight + energy * (maxHeight - minHeight), minHeight)
            // This maps energy 0..1 to minBarHeight..maxBarHeight linearly.
            val barHeight = maxOf(minBarHeight + energy * (maxBarHeight - minBarHeight), minBarHeight)
            val x = index * (barWidth + gapPx)
            val color = barColor(index, barCount)

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Pads or trims the energy list to exactly 30 entries.
 *
 * - Fewer than 30 items: right-padded with 0f
 * - More than 30 items: last 30 items are taken
 * - Exactly 30 items: returned as-is
 */
internal fun padEnergy(levels: List<Float>): List<Float> {
    return when {
        levels.size >= 30 -> levels.takeLast(30)
        else -> levels + List(30 - levels.size) { 0f }
    }
}

/**
 * Determines the color for a waveform bar at the given index.
 *
 * Matches iOS BrandWaveform.resolvedBarColor:
 *   - Distance from center < 0.4 (inner 40%): brand blue (Accent)
 *   - Distance from center >= 0.4 (outer 60%): white with opacity
 *     = (1.0 - distanceFromCenter) * 0.9 + 0.15
 *
 * WHY distance-based instead of fixed indices: The iOS version uses
 * a continuous distance metric which scales correctly if bar count changes.
 * This also produces smoother opacity gradients at the transition boundary.
 */
internal fun barColor(index: Int, barCount: Int): Color {
    val center = (barCount - 1) / 2f
    val distanceFromCenter = abs(index - center) / center

    // Inner 40%: solid brand blue
    if (distanceFromCenter < 0.4f) {
        return DictusColors.Accent
    }

    // Outer 60%: white with opacity decreasing toward edges
    // Matches iOS: Double(1.0 - distanceFromCenter) * 0.9 + 0.15
    val opacity = (1.0f - distanceFromCenter) * 0.9f + 0.15f
    return Color.White.copy(alpha = opacity)
}
