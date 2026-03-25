package dev.pivisolutions.dictus.ime.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import kotlin.math.sin

/**
 * Transcribing overlay shown while whisper.cpp processes audio.
 *
 * Per CONTEXT.md decisions:
 * - Sinusoidal waveform animation (not bar waveform used during recording)
 * - "Transcription..." label
 * - No action buttons (no cancel -- transcription is fast enough)
 * - No timer (processing time is indeterminate)
 *
 * Total height: 310.dp (matching RecordingScreen and KeyboardScreen)
 * Layout: empty top row (46.dp) + center with sine wave + label (218.dp) + empty bottom row (46.dp)
 */
@Composable
fun TranscribingScreen(
    modifier: Modifier = Modifier,
) {
    // Animate phase offset for sine wave movement
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

        // Center (218.dp) — sinusoidal waveform + label
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .background(DictusColors.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Sinusoidal waveform
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(60.dp),
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f
                val amplitude = height * 0.35f
                val wavelength = width / 2f // Two full cycles across the canvas

                val path = Path()
                val steps = 200
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * width
                    val y = centerY + amplitude * sin(
                        (x / wavelength) * 2f * Math.PI.toFloat() + phase
                    ).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = DictusColors.Accent,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }

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
