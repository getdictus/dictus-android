package dev.pivisolutions.dictus.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.ui.WaveformBars
import dev.pivisolutions.dictus.ui.onboarding.OnboardingStepScaffold
import kotlin.math.sin

/**
 * Onboarding Step 1 — Welcome screen.
 *
 * Displays the Dictus wordmark with an animated 30-bar sine-wave waveform above it
 * and a tagline below.
 *
 * WHY sine-wave (not audio-driven): Step 1 has no audio input. The sine wave is the
 * "transcription waveform" pattern (matching iOS) — a smooth traveling wave that indicates
 * Dictus's speech capability without implying active recording.
 *
 * WHY WaveformBars (not a custom Canvas): WaveformBars is the shared component from core/ui
 * used throughout the app (TranscribingScreen, recording feedback). Using the same component
 * ensures visual consistency and avoids duplicating bar rendering logic.
 *
 * @param onNext Called when the user taps "Commencer".
 */
@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
) {
    // Traveling sine-wave animation — same pattern as TranscribingScreen.kt in the IME.
    // The phase value animates 0 → 2π over 2 seconds, creating a smooth leftward sweep.
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeSine")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sinePhase",
    )
    val sineEnergy = List(30) { i ->
        val normalizedIndex = i / 30f
        0.2f + 0.25f * (sin(2f * Math.PI.toFloat() * (normalizedIndex + phase / (2f * Math.PI.toFloat()))) + 1f)
    }

    OnboardingStepScaffold(
        currentStep = 1,
        ctaText = "Commencer",
        onCtaClick = onNext,
    ) {
        // Animated sine-wave waveform using the shared WaveformBars component from core/ui
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            WaveformBars(
                energyLevels = sineEnergy,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
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
