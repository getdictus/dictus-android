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

/**
 * Canvas-based 30-bar waveform visualization for the recording overlay.
 *
 * Each bar is a rounded rectangle (pill shape) drawn on a single Canvas.
 * Center bars (indices 11-18) use an accent gradient from AccentHighlight
 * to Accent. Edge bars use white with alpha fading toward the edges.
 *
 * Energy values are expected in 0.0..1.0 range. Lists shorter than 30
 * are padded with 0f; lists longer than 30 take the last 30 items.
 *
 * This composable lives in core so both the app module and the ime module
 * can reuse it without duplication.
 */
@Composable
fun WaveformBars(
    energyLevels: List<Float>,
    modifier: Modifier = Modifier,
) {
    val paddedLevels = padEnergy(energyLevels)

    Canvas(modifier = modifier) {
        val barCount = 30
        val gapPx = 2.dp.toPx()
        val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
        val minBarHeight = 2.dp.toPx()
        val centerY = size.height / 2f

        paddedLevels.forEachIndexed { index, energy ->
            val barHeight = maxOf(minBarHeight, energy * size.height)
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
 * Center bars (indices 11-18) use a gradient from AccentHighlight to Accent.
 * Edge bars use white with alpha fading from 0.6 near center to 0.15 at edges.
 */
internal fun barColor(index: Int, barCount: Int): Color {
    // Center bars: indices 11-18 (8 bars)
    if (index in 11..18) {
        // Interpolate from AccentHighlight (index 11) to Accent (index 18)
        val fraction = (index - 11) / 7f
        return lerp(DictusColors.AccentHighlight, DictusColors.Accent, fraction)
    }

    // Edge bars: white with alpha fading from 0.6 (near center) to 0.15 (at edges)
    val distanceFromCenter = if (index < 11) {
        // Left side: index 10 is closest to center, index 0 is farthest
        (10 - index) / 10f
    } else {
        // Right side: index 19 is closest to center, index 29 is farthest
        (index - 19) / 10f
    }
    val alpha = 0.6f - (0.6f - 0.15f) * distanceFromCenter
    return Color.White.copy(alpha = alpha)
}

/**
 * Linear interpolation between two colors.
 */
private fun lerp(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction,
    )
}
