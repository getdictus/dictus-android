package dev.pivisolutions.dictus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.audio.DictationSoundPlayer
import dev.pivisolutions.dictus.core.logging.DictationTrace
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationStartGuard
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.core.premium.PremiumFlags
import dev.pivisolutions.dictus.history.TranscriptionHistoryMetadata
import dev.pivisolutions.dictus.history.TranscriptionHistoryWriter
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.pivisolutions.dictus.asr.ParakeetProvider
import dev.pivisolutions.dictus.model.AiProvider
import dev.pivisolutions.dictus.model.ModelManager
import timber.log.Timber

/**
 * Foreground service that manages audio recording for voice dictation.
 *
 * WHY a foreground service: Android kills background audio capture aggressively.
 * A foreground service with microphone type keeps the process alive and shows a
 * notification so the user knows recording is active. This is mandatory since
 * Android 14 (API 34) for microphone access from a service.
 *
 * HOW the IME uses this: DictusImeService binds to DictationService via
 * [LocalBinder] (same-process, zero overhead). It observes [state] with
 * collectAsState() to switch between keyboard and recording UI, and calls
 * [startRecording]/[stopRecording]/[cancelRecording] via the binder reference.
 *
 * WHY LocalBinder (not AIDL): The IME and this service run in the same process.
 * Local binding gives direct object access without IPC serialization overhead.
 */
/**
 * Hilt entry point for DictationService.
 *
 * WHY EntryPointAccessors (not @AndroidEntryPoint): DictationService uses a
 * LocalBinder pattern — the IME holds a direct reference to the service instance.
 * @AndroidEntryPoint wraps the service class via code generation and can interfere
 * with the LocalBinder pattern. EntryPointAccessors gives us Hilt-managed
 * singletons without changing the service's class hierarchy.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DictationServiceEntryPoint {
    fun dataStore(): DataStore<Preferences>
    fun transcriptionHistoryWriter(): TranscriptionHistoryWriter
}

class DictationService : Service(), DictationController {

    companion object {
        const val CHANNEL_ID = "dictus_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "dev.pivisolutions.dictus.action.START"
        const val ACTION_STOP = "dev.pivisolutions.dictus.action.STOP"
        private const val ENGINE_IDLE_TIMEOUT_MS = 10 * 60 * 1_000L
    }

    /**
     * DataStore accessed via EntryPoint so that DictationService can read
     * preferences without being an @AndroidEntryPoint component itself.
     */
    private val dataStore: DataStore<Preferences> by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DictationServiceEntryPoint::class.java,
        ).dataStore()
    }

    private val transcriptionHistoryWriter: TranscriptionHistoryWriter by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DictationServiceEntryPoint::class.java,
        ).transcriptionHistoryWriter()
    }

    private val providerSlot by lazy {
        SttProviderSlot(serviceScope, ENGINE_IDLE_TIMEOUT_MS)
    }
    private val modelManager by lazy { ModelManager(applicationContext) }
    private val modelDownloader by lazy { ModelDownloader(modelManager) }
    private val transcriber by lazy {
        DictationTranscriber(
            ensureModel = { modelKey -> modelDownloader.ensureModelAvailable(modelKey) },
            withProvider = { modelKey, modelPath, use -> withProviderForModel(modelKey, modelPath, use) },
            wakeLock = transcriptionWakeLock,
            lastTranscription = LastTranscriptionStore.instance,
        )
    }
    private val transcriptionWakeLock by lazy { PowerManagerTranscriptionWakeLock(applicationContext) }

    /**
     * Local binder for same-process binding.
     *
     * The IME gets a direct reference to DictationService through this binder,
     * allowing it to call methods and observe StateFlow without any IPC overhead.
     */
    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    private val binder = LocalBinder()

    // Coroutine scope tied to service lifecycle.
    // SupervisorJob ensures one child failure doesn't cancel others.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var prewarmJob: Job? = null

    // The transcription of a take stopped without confirm (#248). It runs on cleanupScope so a
    // service destroyed meanwhile does not cancel it; onDestroy waits for it before the release.
    private var unconfirmedJob: Job? = null

    // True between startRecording() and the ACTION_START it queues. startForegroundService()
    // is asynchronous, so a cancel can land first; the start must then not open the mic.
    // Only touched on the main thread (binder calls from UI and onStartCommand).
    private var pendingStart = false
    private var pendingSource = DictationSource.APP

    // Sound feedback for recording lifecycle events.
    // Initialized in onCreate(); conditionally played based on SOUND_ENABLED preference.
    // Sound names and volume are reactively observed from DataStore so changes in
    // SoundSettingsScreen take effect without a service restart.
    private lateinit var soundPlayer: DictationSoundPlayer
    private var soundEnabled: Boolean = false
    private var soundVolume: Float = 0.5f

    // State machine exposed to the IME via the binder.
    // MutableStateFlow is thread-safe; updates from any coroutine are fine.
    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)

    /** Observable state for the IME to collect. */
    override val state: StateFlow<DictationState> = _state.asStateFlow()

    private val recorder = DictationRecorder(serviceScope, _state) {
        AudioCaptureManager(getSystemService(AudioManager::class.java))
    }

    private val unconfirmedStop by lazy {
        UnconfirmedStop(recorder, _state) { samples ->
            transcriber.rejectBeforeEngine(samples) ?: transcribeTake(samples, TranscriptionRetention.PERSIST_HISTORY)
        }
    }

    /** Native engine readiness for IME/UI loading gates. */
    override val engineState: StateFlow<SttEngineState>
        get() = providerSlot.state

    override fun onBind(intent: Intent): IBinder {
        prewarmEngine()
        return binder
    }

    override fun prewarmEngine() {
        if (prewarmJob?.isActive == true) return
        prewarmJob = serviceScope.launch(Dispatchers.Default) { prewarmActiveModel() }
    }

    override fun onCreate() {
        super.onCreate()
        soundPlayer = DictationSoundPlayer(applicationContext)

        // Load a newly selected model right away, from the Models screen, Settings or onboarding,
        // so it is ready before the next mic tap (#175, iOS ModelManager.selectModel). Without it
        // the first dictation recorded on the previous model and loaded the new one while
        // transcribing. drop(1): the current selection is the one onBind already prewarms.
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.ACTIVE_MODEL] }
                .distinctUntilChanged()
                .drop(1)
                .collect { modelKey ->
                    // A prewarm still running loads the previous selection and would swallow this one.
                    prewarmJob?.join()
                    Timber.i("Active model changed to %s, prewarming", modelKey)
                    prewarmEngine()
                }
        }

        // Load sounds with user-selected names from DataStore on first launch.
        // Subsequent preference changes are handled by the reactive observers below.
        serviceScope.launch {
            val prefs = dataStore.data.first()
            val startSound = prefs[PreferenceKeys.RECORD_START_SOUND] ?: "electronic_01f"
            val stopSound = prefs[PreferenceKeys.RECORD_STOP_SOUND] ?: "electronic_02b"
            val cancelSound = prefs[PreferenceKeys.RECORD_CANCEL_SOUND] ?: "electronic_03c"
            soundPlayer.loadSounds(startSound, stopSound, cancelSound)

            soundPlayer.volume = prefs[PreferenceKeys.SOUND_VOLUME] ?: 0.5f
        }

        // Reactively observe the SOUND_ENABLED preference so changes take effect
        // without a service restart. Runs on the service scope (Main dispatcher).
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.SOUND_ENABLED] ?: false }
                .collect { soundEnabled = it }
        }

        // Reactively observe volume changes.
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.SOUND_VOLUME] ?: 0.5f }
                .collect { volume ->
                    soundVolume = volume
                    soundPlayer.volume = volume
                }
        }

        // Reactively observe sound name changes and reload the affected slot.
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.RECORD_START_SOUND] ?: "electronic_01f" }
                .collect { soundPlayer.reloadSound("start", it) }
        }
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.RECORD_STOP_SOUND] ?: "electronic_02b" }
                .collect { soundPlayer.reloadSound("stop", it) }
        }
        serviceScope.launch {
            dataStore.data
                .map { it[PreferenceKeys.RECORD_CANCEL_SOUND] ?: "electronic_03c" }
                .collect { soundPlayer.reloadSound("cancel", it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // CRITICAL: call startForeground() FIRST, before AudioRecord setup.
                // Android gives us ~10 seconds after startForegroundService() to call
                // startForeground(). AudioRecord init can be slow on some devices.
                createNotificationChannel()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
                if (recorder.isCapturing || !DictationStartGuard.canStartNewDictation(_state.value)) {
                    // Backstop (#237): a live dictation owns the microphone and the foreground.
                    Timber.w("ACTION_START refused: state=%s", _state.value::class.simpleName)
                    return START_NOT_STICKY
                }
                if (!pendingStart) {
                    // Cancelled before this start was delivered. startForeground() above is
                    // still required after startForegroundService(), then nothing records.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    Timber.d("DictationService dropped ACTION_START cancelled before delivery")
                    DictationTrace.end()
                    return START_NOT_STICKY
                }
                pendingStart = false
                recorder.start(pendingSource)
                if (soundEnabled) soundPlayer.playStart()
                Timber.i(
                    "DictationService started foreground with ACTION_START: source=%s, t+=%dms",
                    pendingSource,
                    DictationTrace.elapsedMs(),
                )
            }
            ACTION_STOP -> {
                pendingStart = false
                if (_state.value is DictationState.Transcribing) {
                    // Stopping now would tear down the transcription that holds the take.
                    Timber.i("ACTION_STOP ignored: transcription in progress")
                    return START_NOT_STICKY
                }
                unconfirmedStop.stop(UnconfirmedStop.Reason.NOTIFICATION_STOP, cleanupScope, ::finishTranscription)?.let {
                    unconfirmedJob = it
                    if (soundEnabled) soundPlayer.playStop()
                    return START_NOT_STICKY
                }
                discardRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Timber.d("DictationService stopped via ACTION_STOP")
                DictationTrace.end()
            }
        }
        // START_NOT_STICKY: don't restart automatically if the system kills us.
        // Recording state is transient; the user will re-tap mic if needed.
        return START_NOT_STICKY
    }

    /**
     * Start recording via foreground service promotion.
     *
     * Sends ACTION_START to self, which triggers onStartCommand to call
     * startForeground() (required for microphone access) then starts the capture.
     * Refused while a dictation records, transcribes or is still being started (#237).
     */
    override fun startRecording(source: DictationSource): Boolean {
        if (!DictationStartGuard.canStartNewDictation(_state.value, pendingStart)) {
            Timber.w(
                "Recording start refused: source=%s, state=%s, pendingStart=%b",
                source,
                _state.value::class.simpleName,
                pendingStart,
            )
            return false
        }
        // Like iOS startDictation: the previous text must not pass for this dictation's (#245).
        LastTranscriptionStore.instance.clear()
        val intent = Intent(this, DictationService::class.java).apply {
            action = ACTION_START
        }
        pendingStart = true
        pendingSource = source
        val trace = DictationTrace.onRecordingRequested(fallbackSource = source.name.lowercase())
        Timber.i("Recording requested: source=%s, t+=%dms", trace.source, DictationTrace.elapsedMs())
        startForegroundService(intent)
        return true
    }

    /**
     * Stop recording and return the captured audio buffer.
     *
     * The audio data is returned as a FloatArray of 16kHz mono samples,
     * ready to be passed to whisper.cpp for transcription (Phase 3).
     *
     * @return FloatArray of captured audio samples, or empty array if not recording.
     */
    override fun stopRecording(): FloatArray {
        pendingStart = false
        val samples = recorder.stop()
        if (soundEnabled) soundPlayer.playStop()
        _state.value = DictationState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Recording stopped, ${samples.size} samples captured")
        DictationTrace.end()
        return samples
    }

    /**
     * Cancel recording and discard all audio data.
     *
     * Used when the user taps the X button during recording.
     * Returns to idle state without producing any audio output.
     */
    override fun cancelRecording() {
        pendingStart = false
        if (soundEnabled) soundPlayer.playCancel()
        discardRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Recording cancelled, samples discarded")
        DictationTrace.end()
    }

    /**
     * Stop recording, transcribe audio, and return processed text.
     *
     * Full pipeline: stop recording -> ensure model downloaded -> init engine ->
     * transcribe within [TranscriptionTimeout] -> post-process -> return text.
     *
     * WHY a timeout: a stuck or very slow JNI call must not block the UI indefinitely. Both
     * providers return at the timeout while their native call is still running (#158, #145);
     * Whisper aborts its decode and keeps the segments decoded so far (#173, #179).
     */
    override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome {
        // 1. Stop recording and get audio samples
        pendingStart = false
        val samples = recorder.stop()

        transcriber.rejectBeforeEngine(samples)?.let { outcome ->
            finishTranscription()
            return outcome
        }

        // Play stop cue when audio capture ends and transcription begins.
        if (soundEnabled) soundPlayer.playStop()

        Timber.i(
            "Recording stopped: samples=%d, audioMs=%d, t+=%dms, transitioning to Transcribing",
            samples.size,
            samples.size * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ,
            DictationTrace.elapsedMs(),
        )
        _state.value = DictationState.Transcribing

        return try {
            transcribeTake(samples, retention)
        } finally {
            finishTranscription()
        }
    }

    /** Transcribes a take that passed rejectBeforeEngine; the caller owns the state and the foreground. */
    private suspend fun transcribeTake(samples: FloatArray, retention: TranscriptionRetention): TranscriptionOutcome {
        return try {
            // 2. Read user preferences at transcription time so changes take effect
            //    without needing a service restart.
            val prefs = dataStore.data.first()
            val activeModelKey = prefs[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY
            val languagePref = TranscriptionLanguage.resolve(prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE])
            // "auto" is passed through: whisper.cpp detects the language itself.
            val engineLanguage = TranscriptionLanguage.engineLanguage(languagePref)

            Timber.i(
                "confirmAndTranscribe: model=%s, engine=%s, language=%s",
                activeModelKey,
                ModelCatalog.findByKey(activeModelKey)?.provider ?: "UNKNOWN",
                engineLanguage,
            )

            // 3. Download the model if needed, transcribe, post-process.
            val outcome = transcriber.transcribe(samples, activeModelKey, engineLanguage)

            // 4. Only a dictation with words reaches history.
            if (outcome is TranscriptionOutcome.Transcribed) {
                val model = ModelCatalog.findByKey(activeModelKey)
                transcriptionHistoryWriter.persist(
                    // Pro gate: callers still express the retention their context deserves,
                    // the flag only ever downgrades it (see PremiumFlags.HISTORY_VISIBLE).
                    retention = PremiumFlags.effectiveRetention(retention),
                    text = outcome.text,
                    metadata = TranscriptionHistoryMetadata(
                        requestedLanguage = languagePref,
                        durationMillis = samples.size * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ,
                        modelKey = activeModelKey,
                        provider = model?.provider?.name ?: "UNKNOWN",
                    ),
                )
            }
            outcome
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            TranscriptionOutcome.Failed
        }
    }

    /** Every confirmAndTranscribe exit: back to Idle and out of the foreground. */
    private fun finishTranscription() {
        _state.value = DictationState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("Transcription pipeline done, t+=%dms", DictationTrace.elapsedMs())
        DictationTrace.end()
    }

    /**
     * Initialize the downloaded active model when the first client binds.
     *
     * This deliberately uses [ModelManager.getModelPath] instead of the downloader:
     * showing the keyboard must never trigger an unexpected network transfer. The
     * same provider slot used by transcription provides the single-flight guarantee.
     */
    private suspend fun prewarmActiveModel() {
        val activeModelKey = dataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
            ?: ModelCatalog.DEFAULT_KEY
        val modelPath = modelManager.getModelPath(activeModelKey)
        if (modelPath == null) {
            providerSlot.markModelMissing(activeModelKey)
            Timber.i("Engine prewarm skipped: model=%s is not downloaded yet", activeModelKey)
            return
        }

        try {
            val ready = withProviderForModel(activeModelKey, modelPath) { true } == true
            if (ready) {
                Timber.d("Engine prewarm complete for model=%s", activeModelKey)
            } else {
                Timber.w("Engine prewarm failed for model=%s", activeModelKey)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Timber.e(failure, "Engine prewarm failed for model=%s", activeModelKey)
        }
    }

    /**
     * Run [block] on the initialized provider for the model's engine.
     *
     * WHY dynamic dispatch: Phase 9 adds Parakeet as a second engine. The provider is
     * determined by the active model's AiProvider enum, not a separate setting.
     * Strict single-engine policy: if the provider type changes, release() the current
     * one before initialize() on the new one — prevents OOM from dual engine loading.
     */
    private suspend fun <T> withProviderForModel(
        modelKey: String,
        modelPath: String,
        block: suspend (SttProvider) -> T,
    ): T? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        val neededProviderClass = when (info.provider) {
            AiProvider.WHISPER -> WhisperProvider::class
            AiProvider.PARAKEET -> ParakeetProvider::class
        }

        return providerSlot.withProvider(
            requestedModelKey = modelKey,
            modelPath = modelPath,
            requiredProviderClass = neededProviderClass,
            createProvider = {
                when (info.provider) {
                    AiProvider.WHISPER -> WhisperProvider()
                    AiProvider.PARAKEET -> ParakeetProvider()
                }
            },
            useProvider = block,
        )
    }

    /** Discard the capture, if any, and reach Idle even when nothing was recording. */
    private fun discardRecording() {
        recorder.cancel()
        _state.value = DictationState.Idle
    }

    /**
     * Create the notification channel for recording notifications.
     *
     * IMPORTANCE_LOW: no sound or vibration, just a persistent icon in the
     * status bar. This is appropriate for an ongoing recording indicator.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Build the foreground notification shown during recording.
     *
     * Shows "Dictus – Recording" with a Stop action button.
     * Body tap is a no-op (per user decision -- the IME is already visible).
     */
    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, DictationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.recording_notification_stop), stopPendingIntent)
            .build()
    }

    // Swiping Dictus away from Recents ends its dictation like the notification's Stop (#248).
    // A keyboard dictation does not belong to the app's task and keeps running.
    override fun onTaskRemoved(rootIntent: Intent?) {
        unconfirmedStop.stop(UnconfirmedStop.Reason.TASK_REMOVED, cleanupScope, ::finishTranscription)
            ?.let { unconfirmedJob = it }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundPlayer.isInitialized) soundPlayer.release()
        // The cancelled transcription releases it in its finally; this covers a lock left held.
        transcriptionWakeLock.release()
        // Torn down while an app dictation records (#248): its take is transcribed on cleanupScope,
        // which outlives the service, before the engine is released. A take already transcribing
        // after a stop without confirm is waited for the same way. A process kill still loses it.
        val keptTake = unconfirmedStop.stop(UnconfirmedStop.Reason.SERVICE_DESTROYED, cleanupScope) {
            _state.value = DictationState.Idle
            DictationTrace.end()
        } ?: unconfirmedJob
        // Cancelling the scope alone would leave a live AudioRecord unreleased.
        recorder.cancel()
        serviceScope.cancel()
        cleanupScope.launch {
            try {
                keptTake?.join()
                providerSlot.release()
            } finally {
                cleanupScope.cancel()
            }
        }
        Timber.d("DictationService destroyed")
    }
}
