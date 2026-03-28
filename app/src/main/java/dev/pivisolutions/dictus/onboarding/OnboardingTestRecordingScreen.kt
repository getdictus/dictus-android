package dev.pivisolutions.dictus.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.core.ui.WaveformBars
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCTAButton
import dev.pivisolutions.dictus.ui.onboarding.OnboardingProgressDots
import dev.pivisolutions.dictus.ui.onboarding.accentGradient
import kotlinx.coroutines.launch

/**
 * Onboarding Step 6 — Test recording screen.
 *
 * Uses a layered Box layout so the mic/stop button stays at a fixed position
 * (200dp from bottom) regardless of state. Three layers:
 * 1. Upper content (text, waveform, timer) — centered above the button
 * 2. Button layer — fixed 200dp from bottom, stable across all states
 * 3. Bottom bar (Passer/Continuer + progress dots) — pinned to bottom
 *
 * @param dictationController Controller for the DictationService (may be null if not yet bound).
 * @param onNext Called when the user taps "Continuer" after seeing the result, or "Passer" to skip.
 */
@Composable
fun OnboardingTestRecordingScreen(
    dictationController: DictationController?,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dictationState by dictationController?.state?.collectAsState()
        ?: remember { mutableStateOf(DictationState.Idle as DictationState) }

    var transcriptionResult by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    // Processing animation driver for the transcribing state.
    // Uses WaveformDriver.processingEnergy() — same formula as iOS BrandWaveformDriver.
    val processingDriver = remember {
        WaveformDriver().apply { isProcessing = true }
    }
    val processingPhase by processingDriver.processingPhase.collectAsState()

    // Run the processing animation loop when transcribing.
    if (dictationState is DictationState.Transcribing) {
        LaunchedEffect(Unit) {
            processingDriver.runLoop()
        }
    }

    val hasResult = transcriptionResult != null
    val isRecording = dictationState is DictationState.Recording
    val isTranscribing = dictationState is DictationState.Transcribing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
    ) {
        // ── Layer 1: Upper content (idle text OR result card) ──
        // Only used for idle and result states. Recording/transcribing put everything
        // in the bottom block to match the iOS layout.
        when {
            hasResult -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 180.dp, bottom = 240.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "R\u00e9sultat",
                        color = MaterialTheme.colorScheme.onBackground,
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
                            color = MaterialTheme.colorScheme.onBackground,
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
                                tint = if (copied) DictusColors.Success else LocalDictusColors.current.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            !isRecording && !isTranscribing -> {
                // Idle: title + subtitle centered
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Testez la dict\u00e9e",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Appuyez sur le micro et parlez.\nDictus transcrira votre voix.",
                        color = LocalDictusColors.current.textSecondary,
                        fontSize = 15.sp,
                        lineHeight = (15 * 1.5).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Layer 2: Bottom block (waveform + timer + button) ──
        // For recording/transcribing: waveform, timer, and button are grouped together
        // in the lower portion of the screen (matching iOS layout).
        // For idle: just the mic button.
        if (!hasResult) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    isTranscribing -> {
                        Text(
                            text = "Transcription en cours\u2026",
                            color = LocalDictusColors.current.textSecondary,
                            fontSize = 17.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        WaveformBars(
                            energyLevels = emptyList(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            isProcessing = true,
                            processingPhase = processingPhase,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Button (same position in all non-result states)
                when {
                    isRecording -> {
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
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = LocalDictusColors.current.textSecondary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    else -> {
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

                // Reserve space for label
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isRecording) "Toucher pour arr\u00eater" else "",
                    color = LocalDictusColors.current.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // ── Layer 3: Bottom bar (Passer/Continuer + dots) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasResult) {
                OnboardingCTAButton(
                    text = "Continuer",
                    onClick = onNext,
                    gradient = accentGradient,
                )
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }

            Spacer(modifier = Modifier.padding(top = 24.dp))

            OnboardingProgressDots(currentStep = 6)
        }
    }
}
