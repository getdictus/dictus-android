package dev.pivisolutions.dictus.recording

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.core.ui.WaveformBars
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Standalone recording screen — triggered from Home via "Nouvelle dictée".
 *
 * Reuses the recording/transcription flow from OnboardingTestRecordingScreen but
 * without any onboarding-specific elements (progress dots, Passer/Continuer buttons).
 *
 * Layout uses a layered Box so the mic/stop button stays at a fixed position
 * (200dp from bottom) regardless of state:
 * 1. Upper content (idle text OR result card) — fills the area above the button
 * 2. Bottom block (waveform + timer + button) — anchored to BottomCenter
 * 3. Back button — top-left, always visible
 *
 * Recording auto-starts on first composition so the user lands directly in recording
 * state — matching iOS Dictus behavior where tapping "Nouvelle dictée" starts immediately.
 *
 * @param dictationController Controller for the DictationService (may be null if not yet bound).
 * @param onBack Called when the user taps the back arrow to return to Home.
 */
@Composable
fun RecordingScreen(
    dictationController: DictationController?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dictationState by dictationController?.state?.collectAsState()
        ?: remember { mutableStateOf(DictationState.Idle as DictationState) }

    var transcriptionResult by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    // Auto-start recording on first composition — iOS parity: tapping "Nouvelle dictée"
    // brings the user directly into the recording state without a manual tap.
    LaunchedEffect(Unit) {
        dictationController?.startRecording()
    }

    // Sine-wave animation for the transcribing state
    val infiniteTransition = rememberInfiniteTransition(label = "transcribingSine")
    val sinePhase by infiniteTransition.animateFloat(
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
        0.2f + 0.25f * (sin(2f * Math.PI.toFloat() * (normalizedIndex + sinePhase / (2f * Math.PI.toFloat()))) + 1f)
    }

    val hasResult = transcriptionResult != null
    val isRecording = dictationState is DictationState.Recording
    val isTranscribing = dictationState is DictationState.Transcribing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DictusColors.Background)
            .padding(horizontal = 32.dp),
    ) {
        // ── Back button (top-left, always visible) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = DictusColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        // ── Layer 1: Upper content (idle text OR result card) ──
        when {
            hasResult -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 160.dp, bottom = 240.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "R\u00e9sultat",
                        color = DictusColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = transcriptionResult ?: "",
                            color = DictusColors.TextPrimary,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(CircleShape)
                                .clickable {
                                    val clipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE,
                                    ) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Dictus", transcriptionResult),
                                    )
                                    copied = true
                                }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copier",
                                tint = if (copied) DictusColors.Success else DictusColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            !isRecording && !isTranscribing -> {
                // Idle: title + subtitle centered above the button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Nouvelle dict\u00e9e",
                        color = DictusColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Appuyez sur le micro et parlez.\nDictus transcrira votre voix.",
                        color = DictusColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = (15 * 1.5).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Layer 2: Bottom block (waveform + timer + button) ──
        // For recording/transcribing: waveform, timer, and button are grouped together
        // in the lower portion of the screen.
        // For idle: just the mic button.
        // For result: blue mic button to re-record.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasResult) {
                // Waveform + timer (only during recording/transcribing)
                when {
                    isRecording -> {
                        val recording = dictationState as DictationState.Recording

                        WaveformBars(
                            energyLevels = recording.energy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val seconds = (recording.elapsedMs / 1000).toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        Text(
                            text = "%d:%02d".format(minutes, secs),
                            color = DictusColors.TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    isTranscribing -> {
                        Text(
                            text = "Transcription en cours\u2026",
                            color = DictusColors.TextSecondary,
                            fontSize = 17.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        WaveformBars(
                            energyLevels = sineEnergy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // Button — changes appearance based on state
            when {
                isRecording -> {
                    // Red stop button during recording
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(DictusColors.Recording)
                            .clickable {
                                scope.launch {
                                    val result = dictationController?.confirmAndTranscribe()
                                    transcriptionResult = result ?: "(Aucun r\u00e9sultat)"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Arr\u00eater",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                isTranscribing -> {
                    // Disabled mic button during transcription
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(DictusColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = DictusColors.TextSecondary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                hasResult -> {
                    // Blue mic button in result state — tapping re-records
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(DictusColors.Accent)
                            .clickable {
                                transcriptionResult = null
                                copied = false
                                dictationController?.startRecording()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Nouvelle dict\u00e9e",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                else -> {
                    // Idle state: blue mic button to start recording
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(DictusColors.Accent)
                            .clickable {
                                dictationController?.startRecording()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Enregistrer",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Reserve space for label below the button
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    isRecording -> "Toucher pour arr\u00eater"
                    hasResult -> "Nouvelle dict\u00e9e"
                    else -> ""
                },
                color = DictusColors.TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
