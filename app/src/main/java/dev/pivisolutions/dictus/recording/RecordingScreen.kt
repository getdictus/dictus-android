package dev.pivisolutions.dictus.recording

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
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
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationStartGuard
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.MicGateCommand
import dev.pivisolutions.dictus.core.service.ModelPreparationGate
import dev.pivisolutions.dictus.core.service.PendingMicGate
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.core.ui.GlassCard
import dev.pivisolutions.dictus.core.ui.LiveWaveform
import dev.pivisolutions.dictus.core.ui.WaveformBars
import dev.pivisolutions.dictus.core.ui.WaveformDriver
import dev.pivisolutions.dictus.core.ui.rememberSyntheticMotionEnabled
import dev.pivisolutions.dictus.ui.ModelLoadingOverlay
import kotlinx.coroutines.launch
import timber.log.Timber
import dev.pivisolutions.dictus.core.R as CoreR

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
 * 3. Close button (✕) — top-left, always visible
 *
 * Recording auto-starts on first composition so the user lands directly in recording
 * state — matching iOS Dictus behavior where tapping "Nouvelle dictée" starts immediately.
 * A dictation already running, from the keyboard for instance, is shown instead of starting
 * a second one (#237): it offers only Stop and ✕.
 *
 * @param dictationController Controller for the DictationService (may be null if not yet bound).
 * @param onBack Called once ✕ or system back has ended the dictation, to return to Home.
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
    // A Ready engine on the previous model is not ready for this tap (#175).
    LaunchedEffect(dataStore, micGate) {
        dataStore?.data?.collect { micGate.selectedModelKey = it[PreferenceKeys.ACTIVE_MODEL] }
    }

    // True between a start this screen asked for and the recording it opens: startForegroundService()
    // is asynchronous, so the service is still Idle meanwhile. Saveable, like autoStarted below, so
    // both survive activity recreation (rotation): the recreated screen picks the dictation up.
    var startPending by rememberSaveable { mutableStateOf(false) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    fun runGateCommand(command: MicGateCommand) {
        when (command) {
            MicGateCommand.PREWARM -> dictationController?.prewarmEngine()
            // The service refuses a start that arrives while another dictation runs (#237).
            // A refused start leaves a start already pending as it is.
            MicGateCommand.START_RECORDING ->
                if (dictationController?.startRecording(DictationSource.APP) == true) startPending = true
            MicGateCommand.NONE -> Unit
        }
    }

    // ✕ and system back (#243, iOS RecordingView close + resetStatus): end the running dictation,
    // whoever started it, then leave. After a result there is nothing left to end.
    fun close() {
        micGate.cancel()
        // startPending also covers a start still queued in the service, where the state is Idle.
        if (startPending || dictationController?.state?.value is DictationState.Recording) {
            dictationController?.cancelRecording()
        }
        startPending = false
        onBack()
    }
    BackHandler(onBack = ::close)

    // The service has opened the microphone: the start is no longer pending.
    LaunchedEffect(dictationState is DictationState.Recording) {
        if (dictationState is DictationState.Recording) startPending = false
    }

    // Leaving the app or locking the screen keeps an app dictation recording (#248, iOS parity):
    // the microphone foreground service started while the app was visible may go on in the
    // background, and coming back reopens this screen (ActiveDictationRedirect). It used to be
    // cancelled here (#142). Only a start still pending is cancelled: the foreground service
    // cannot start from the background, and a tap still waiting for the engine must not start
    // recording there. A configuration change is not leaving, so it is skipped.
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    val currentController by rememberUpdatedState(dictationController)
    val currentMicGate by rememberUpdatedState(micGate)
    DisposableEffect(lifecycleOwner) {
        fun leave(event: String) {
            if (activity?.isChangingConfigurations == true) return
            currentMicGate.cancel()
            // Read the live state: the service may have opened the mic before the effect above ran.
            if (currentController?.state?.value is DictationState.Recording) {
                Timber.i("%s ignored, dictation continues", event)
            } else if (startPending) {
                Timber.i("%s: pending app start cancelled", event)
                currentController?.cancelRecording()
            }
            startPending = false
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) leave("ON_STOP")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            leave("Screen disposed")
        }
    }

    var transcriptionResult by remember { mutableStateOf<String?>(null) }
    // The last dictation produced no text (#153): the idle layout says so until the next one.
    var noWordsDetected by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    // Asked before the gate, not only when it fires: a tap queued behind the engine would
    // otherwise start a recording once the running dictation ends (#237).
    fun requestStart() {
        val state = dictationController?.state?.value ?: return
        if (!DictationStartGuard.canStartNewDictation(state)) {
            Timber.i("App start skipped: a dictation is running, state=%s", state::class.simpleName)
            return
        }
        runGateCommand(micGate.request(engineState))
    }

    // Auto-start recording on first composition — iOS parity: tapping "Nouvelle dictée"
    // brings the user directly into the recording state without a manual tap.
    // Key the effect to the controller so a screen composed before service binding
    // starts recording as soon as the controller becomes available. Only once per screen
    // entry: a screen recreated by a rotation must not start a second recording.
    LaunchedEffect(dictationController) {
        if (dictationController != null && !autoStarted) {
            autoStarted = true
            requestStart()
        }
    }
    LaunchedEffect(engineState) {
        runGateCommand(micGate.engineChanged(engineState))
    }

    fun requestNewDictation() {
        transcriptionResult = null
        noWordsDetected = false
        copied = false
        requestStart()
    }

    // Processing animation driver for the transcribing state.
    // Uses WaveformDriver.processingEnergy() — same formula as iOS BrandWaveformDriver.
    val processingDriver = remember {
        WaveformDriver(logLabel = "app").apply { isProcessing = true }
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
    // A load that starts under a dictation never covers it (#175).
    val overlayEngineState = ModelPreparationGate.overlayState(engineState, dictationState)
    val isEngineOverlayVisible = overlayEngineState is SttEngineState.Loading ||
        overlayEngineState is SttEngineState.Failed

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
        // ── Close (top-left, always visible): the screen's only way out ──
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp)
                .clip(CircleShape)
                .clickable(onClick = ::close)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.recording_close_cd),
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
                        text = stringResource(
                            if (noWordsDetected) CoreR.string.dictation_no_words_detected else R.string.recording_idle_title,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center,
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

                        // iOS RecordingView: BrandWaveform 120 pt at opacity 0.5 while recording.
                        LiveWaveform(
                            energyLevels = recording.energy,
                            logLabel = "app",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .alpha(0.5f),
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

                        // iOS RecordingView: opacity 0.3 once recording has stopped.
                        WaveformBars(
                            energyLevels = emptyList(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .alpha(0.3f),
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
                                startPending = false
                                // A keyboard dictation may come from a field that must not be kept
                                // (password, incognito): the app cannot tell, so its history keeps nothing.
                                val fromApp = (dictationState as? DictationState.Recording)?.source == DictationSource.APP
                                scope.launch {
                                    val outcome = dictationController?.confirmAndTranscribe(
                                        if (fromApp) TranscriptionRetention.PERSIST_HISTORY else TranscriptionRetention.EPHEMERAL,
                                    )
                                    when (outcome) {
                                        is TranscriptionOutcome.Transcribed -> transcriptionResult = outcome.text
                                        TranscriptionOutcome.NoWordsDetected -> {
                                            noWordsDetected = true
                                            Timber.i("No words detected notice shown: surface=app")
                                        }
                                        TranscriptionOutcome.Failed, null -> transcriptionResult = noResultLabel
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
                            .clickable { requestNewDictation() },
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
                            .clickable { requestNewDictation() },
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
            engineState = overlayEngineState,
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
