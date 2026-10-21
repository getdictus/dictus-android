package dev.pivisolutions.dictus.recording

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.MicGateCommand
import dev.pivisolutions.dictus.core.service.PendingMicGate
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.core.ui.ModelLoadingOverlay
import dev.pivisolutions.dictus.core.ui.WaveformBars
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.core.ui.rememberSyntheticMotionEnabled
import kotlinx.coroutines.launch

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
    dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dictationState by dictationController?.state?.collectAsState()
        ?: remember { mutableStateOf(DictationState.Idle as DictationState) }
    val engineState by dictationController?.engineState?.collectAsState()
        ?: remember { mutableStateOf(SttEngineState.Cold as SttEngineState) }
    val micGate = remember(dictationController) { PendingMicGate() }

    // True while a recording this screen started has not been stopped yet. The service is
    // shared with the keyboard, so leaving must never cancel a dictation the IME owns.
    // Saveable, like autoStarted below, so both survive activity recreation (rotation): the
    // recording keeps running in the service and the recreated screen picks it up.
    var ownsRecording by rememberSaveable { mutableStateOf(false) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    fun runGateCommand(command: MicGateCommand) {
        when (command) {
            MicGateCommand.PREWARM -> dictationController?.prewarmEngine()
            MicGateCommand.START_RECORDING -> {
                ownsRecording = true
                dictationController?.startRecording()
            }
            MicGateCommand.NONE -> Unit
        }
    }

    // Leaving the screen releases the microphone (#142): the back arrow, the system back
    // gesture, any other navigation and the app going to the background all end here. It is
    // the discard path, so nothing is transcribed. A stop already tapped is untouched: its
    // result only lives on this screen. A configuration change is not leaving: the activity
    // is recreated around the same recording, so it is skipped.
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    val currentController by rememberUpdatedState(dictationController)
    val currentMicGate by rememberUpdatedState(micGate)
    DisposableEffect(lifecycleOwner) {
        fun releaseMicrophone() {
            if (activity?.isChangingConfigurations == true) return
            // A tap still waiting for the engine must not start recording in the background.
            currentMicGate.cancel()
            // Not gated on the Recording state: a start still queued in the service is
            // still Idle here, and cancelling it is what keeps it from opening the mic.
            if (ownsRecording) currentController?.cancelRecording()
            ownsRecording = false
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) releaseMicrophone()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseMicrophone()
        }
    }

    var transcriptionResult by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    // Auto-start recording on first composition — iOS parity: tapping "Nouvelle dictée"
    // brings the user directly into the recording state without a manual tap.
    // Key the effect to the controller so a screen composed before service binding
    // starts recording as soon as the controller becomes available. Only once per screen
    // entry: a screen recreated by a rotation must not start a second recording.
    LaunchedEffect(dictationController) {
        if (dictationController != null && !autoStarted) {
            autoStarted = true
            runGateCommand(micGate.request(engineState))
        }
    }
    LaunchedEffect(engineState) {
        runGateCommand(micGate.engineChanged(engineState))
    }

    // Processing animation driver for the transcribing state.
    // Uses WaveformDriver.processingEnergy() — same formula as iOS BrandWaveformDriver.
    val processingDriver = remember {
        WaveformDriver().apply { isProcessing = true }
    }
    val processingPhase by processingDriver.processingPhase.collectAsState()
    val syntheticMotionEnabled by rememberSyntheticMotionEnabled()

    // Keep transcription feedback static when Android asks to reduce motion or save power.
    LaunchedEffect(dictationState is DictationState.Transcribing, syntheticMotionEnabled) {
        if (dictationState is DictationState.Transcribing && syntheticMotionEnabled) {
            processingDriver.runLoop()
        } else {
            processingDriver.reset()
        }
    }

    val hasResult = transcriptionResult != null
    val isRecording = dictationState is DictationState.Recording
    val isTranscribing = dictationState is DictationState.Transcribing
    val isEngineOverlayVisible = engineState is SttEngineState.Loading ||
        engineState is SttEngineState.Failed

    val noResultLabel = stringResource(R.string.recording_no_result)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .then(
                    if (isEngineOverlayVisible) Modifier.clearAndSetSemantics { } else Modifier,
                ),
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
                contentDescription = stringResource(R.string.recording_back_cd),
                tint = MaterialTheme.colorScheme.onBackground,
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
                        text = stringResource(R.string.recording_result_title),
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
                                contentDescription = stringResource(R.string.recording_copy_cd),
                                tint = if (copied) DictusColors.Success else LocalDictusColors.current.textSecondary,
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
                        text = stringResource(R.string.recording_idle_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.recording_idle_body),
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
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    isTranscribing -> {
                        Text(
                            text = stringResource(R.string.recording_transcribing),
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
                                ownsRecording = false
                                scope.launch {
                                    val result = dictationController?.confirmAndTranscribe(
                                        TranscriptionRetention.PERSIST_HISTORY,
                                    )
                                    transcriptionResult = result ?: noResultLabel
                                    // Keep the Home preview for a short, privacy-bounded period.
                                    if (result != null) {
                                        dataStore?.let { LastTranscriptionStore.save(it, result) }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.recording_stop_cd),
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
                                runGateCommand(micGate.request(engineState))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.recording_new_dictation_cd),
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
                                runGateCommand(micGate.request(engineState))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.recording_record_cd),
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
                    isRecording -> stringResource(R.string.recording_tap_to_stop)
                    hasResult -> stringResource(R.string.recording_idle_title)
                    else -> ""
                },
                color = LocalDictusColors.current.textSecondary,
                fontSize = 13.sp,
            )
        }

        }

        ModelLoadingOverlay(
            engineState = engineState,
            onRetry = { runGateCommand(micGate.retry()) },
            onCancel = {
                micGate.cancel()
                onBack()
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
