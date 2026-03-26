package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.ui.WaveformBars
import kotlin.math.sin

/**
 * Transcribing overlay shown while whisper.cpp processes audio.
 *
 * Reuses the same 30-bar WaveformBars as RecordingScreen, but feeds it
 * a sine-wave-generated energy list instead of live audio. This creates
 * a smooth traveling wave animation consistent with the design system.
 *
 * Total height: 310.dp (matching RecordingScreen and KeyboardScreen)
 * Layout: empty top row (46.dp) + center with waveform + label (218.dp) + empty bottom row (46.dp)
 */
@Composable
fun TranscribingScreen(
    modifier: Modifier = Modifier,
) {
    // Animate phase offset for sine wave traveling across bars
    val infiniteTransition = rememberInfiniteTransition(label = "transcribingSine")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sinePhase",
    )

    // Generate 30 energy values from sine wave (iOS formula from BrandWaveformDriver)
    val sineEnergy = List(30) { i ->
        val normalizedIndex = i / 30f
        0.2f + 0.25f * (sin(2f * Math.PI.toFloat() * (normalizedIndex + phase / (2f * Math.PI.toFloat()))) + 1f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Top spacer (46.dp) — no buttons during transcription
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(DictusColors.Background),
        )

        // Center (218.dp) — animated waveform bars + label
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(DictusColors.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Animated waveform bars (same component as RecordingScreen)
            WaveformBars(
                energyLevels = sineEnergy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(100.dp),
            )

            // "Transcription..." label
            Text(
                text = "Transcription...",
                color = DictusColors.KeyText.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Bottom spacer (46.dp)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(DictusColors.Background),
        )
    }
}
