package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold
import kotlin.random.Random

/**
 * Onboarding Step 1 — Welcome screen.
 *
 * Displays the Dictus wordmark with a decorative 15-bar static waveform above it
 * and a tagline below. The waveform uses a fixed Random seed to produce consistent
 * bar heights across recompositions and device configurations.
 *
 * WHY static waveform (not animated): Step 1 is purely introductory — there is no
 * audio input yet. An animated waveform would imply recording is happening, which
 * it is not. The static version is decorative-only, matching the iOS Dictus design.
 *
 * @param onNext Called when the user taps "Commencer".
 */
@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
) {
    // Generate consistent bar heights using a fixed seed (42)
    // Random is remembered so heights do not change on recomposition
    val barHeights = remember {
        val rng = Random(42)
        // 15 bars with heights between 20% and 100% of container height
        List(15) { rng.nextFloat() * 0.8f + 0.2f }
    }

    OnboardingStepScaffold(
        currentStep = 1,
        ctaText = "Commencer",
        onCtaClick = onNext,
    ) {
        // Decorative waveform: 200dp x 80dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            WaveformDecoration(
                barHeights = barHeights,
                modifier = Modifier
                    .fillMaxWidth(0.65f) // ~200dp equivalent proportion
                    .height(80.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // "Dictus" wordmark
        Text(
            text = "Dictus",
            color = DictusColors.TextPrimary,
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-0.5).sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tagline
        Text(
            text = "Dictation vocale, 100\u00a0% offline",
            color = DictusColors.TextSecondary,
            fontSize = 17.sp,
        )
    }
}

/**
 * Canvas-drawn 15-bar decorative waveform for the welcome screen.
 *
 * Bar coloring:
 * - Center 5 bars (indices 5-9): gradient from AccentHighlight (#6BA3FF) to Accent (#3D7EFF)
 * - Edge bars: white with opacity fading from 73% (near center) to 45% (at edges)
 *
 * Each bar is 4dp wide with 3dp gaps and 2dp corner radii.
 */
@Composable
private fun WaveformDecoration(
    barHeights: List<Float>,
    modifier: Modifier = Modifier,
) {
    val barCount = 15
    val centerStart = 5
    val centerEnd = 9

    Canvas(modifier = modifier) {
        val gapPx = 3.dp.toPx()
        val barWidthPx = 4.dp.toPx()
        val totalWidth = barCount * barWidthPx + (barCount - 1) * gapPx
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        barHeights.forEachIndexed { index, heightFraction ->
            val barHeight = heightFraction * size.height
            val x = startX + index * (barWidthPx + gapPx)

            val color: Color = if (index in centerStart..centerEnd) {
                // Gradient interpolation from AccentHighlight to Accent
                val fraction = (index - centerStart).toFloat() / (centerEnd - centerStart)
                lerp(DictusColors.AccentHighlight, DictusColors.Accent, fraction)
            } else {
                // Edge bars: white with fading opacity
                val distFromCenter = if (index < centerStart) {
                    (centerStart - index).toFloat() / centerStart
                } else {
                    (index - centerEnd).toFloat() / (barCount - 1 - centerEnd)
                }
                val alpha = 0.73f - (0.73f - 0.45f) * distFromCenter
                Color.White.copy(alpha = alpha)
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/**
 * Linear interpolation between two colors.
 */
private fun lerp(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)
