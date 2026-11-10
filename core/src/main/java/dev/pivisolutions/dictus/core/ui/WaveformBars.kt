package dev.pivisolutions.dictus.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
 *   - Center 40% of bars: brand blue (DictusColors.AccentHighlight, iOS dictusGradientStart)
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
    WaveformCanvas(
        levels = { paddedLevels },
        modifier = modifier,
        isProcessing = isProcessing,
        processingPhase = processingPhase,
    )
}

/**
 * Live microphone waveform: smooths [energyLevels] with its own [WaveformDriver]
 * and draws the result. Every recording surface uses this one composable, so the
 * keyboard, the recording screen and the onboarding test move identically.
 *
 * WHY the smoothed levels are read only inside the Canvas draw lambda: the driver
 * publishes new levels on every display frame. Reading them in the draw phase
 * redraws the bars without recomposing this composable or its parents (the IME
 * root used to recompose at display rate).
 *
 * @param logLabel Surface name in the waveform heartbeat and stall logs.
 */
@Composable
fun LiveWaveform(
    energyLevels: List<Float>,
    logLabel: String,
    modifier: Modifier = Modifier,
) {
    val driver = remember(logLabel) { WaveformDriver(logLabel = logLabel) }
    SideEffect { driver.update(energyLevels) }
    LaunchedEffect(driver) { driver.runLoop() }
    val levels = driver.displayLevels.collectAsState()
    WaveformCanvas(levels = { levels.value }, modifier = modifier)
}

@Composable
private fun WaveformCanvas(
    levels: () -> List<Float>,
    modifier: Modifier,
    isProcessing: Boolean = false,
    processingPhase: Double = 0.0,
) {
    // Outer bar base color: white in dark theme, gray in light theme (matches iOS)
    val isDark = MaterialTheme.colorScheme.background == DictusColors.Background
    val outerBarBase = if (isDark) Color.White else Color(0xFF8E8E93)

    Canvas(modifier = modifier) {
        val barCount = WaveformDriver.BAR_COUNT
        val gapPx = 2.dp.toPx()
        val barWidth = (size.width - gapPx * (barCount - 1)) / barCount
        // Min bar height: 2dp idle (thin visible baseline), 4dp processing
        // Matches iOS: let minHeight: CGFloat = driver.isProcessing ? 4 : 2
        val minBarHeight = if (isProcessing) 4.dp.toPx() else 2.dp.toPx()
        val maxBarHeight = size.height
        val centerY = size.height / 2f
        val currentLevels = if (isProcessing) emptyList() else levels()

        for (index in 0 until barCount) {
            val energy: Float = if (isProcessing) {
                WaveformDriver.processingEnergy(index, processingPhase, barCount)
            } else {
                currentLevels.getOrElse(index) { 0f }
            }

            // iOS formula: max(minHeight + energy * (maxHeight - minHeight), minHeight)
            // This maps energy 0..1 to minBarHeight..maxBarHeight linearly.
            val barHeight = maxOf(minBarHeight + energy * (maxBarHeight - minBarHeight), minBarHeight)
            val x = index * (barWidth + gapPx)
            val color = barColor(index, barCount, outerBarBase)

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
/**
 * @param outerBase Base color for outer bars: White in dark theme, gray in light theme.
 *   Matches iOS where outer bars are gray (#8E8E93) on light backgrounds.
 */
internal fun barColor(index: Int, barCount: Int, outerBase: Color = Color.White): Color {
    val center = (barCount - 1) / 2f
    val distanceFromCenter = abs(index - center) / center

    // Inner 40%: solid brand blue (iOS dictusGradientStart, #6BA3FF)
    if (distanceFromCenter < 0.4f) {
        return DictusColors.AccentHighlight
    }

    // Outer 60%: outerBase with opacity decreasing toward edges
    // Matches iOS: Double(1.0 - distanceFromCenter) * 0.9 + 0.15
    val opacity = (1.0f - distanceFromCenter) * 0.9f + 0.15f
    return outerBase.copy(alpha = opacity)
}
