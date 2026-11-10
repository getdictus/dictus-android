package dev.pivisolutions.dictus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.companion.CompanionActionReceiver
import dev.pivisolutions.dictus.audio.DictationSoundPlayer
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.history.TranscriptionHistoryMetadata
import dev.pivisolutions.dictus.history.TranscriptionHistoryWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import dev.pivisolutions.dictus.asr.ParakeetProvider
import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
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
        private const val TRANSCRIPTION_TIMEOUT_MS = 120_000L
        private const val ENGINE_IDLE_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val EXTRA_START_TOKEN = "dictation_start_token"
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

    private var audioCaptureManager: AudioCaptureManager? = null
    private var timerJob: Job? = null
    private var prewarmJob: Job? = null
    private var elapsedMs: Long = 0L
    private val ownership = DictationSessionOwnership()
    private var transcriptionJob: Deferred<String?>? = null
    private val _companionFailure = MutableStateFlow<String?>(null)
    val companionFailure: StateFlow<String?> = _companionFailure.asStateFlow()
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && ownership.companionOwned) {
                _companionFailure.value = "Dictation cancelled because the screen was locked"
                releaseCompanion()
            }
        }
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Dictation commands require Main" }
    }

    /** Idempotent lease acquisition; refused while any session is pending or active. */
    fun acquireCompanion(): Boolean {
        requireMainThread()
        return ownership.acquireCompanion()
    }

    fun releaseCompanion() {
        requireMainThread()
        if (!ownership.companionOwned) return
        ownership.releaseCompanion()
        cancelSession()
    }

    /** Call from the resumed activity: microphone promotion happens before returning. */
    fun startCompanionRecording(): Boolean {
        requireMainThread()
        val token = ownership.requestStart(DictationSessionOwnership.Owner.COMPANION) ?: return false
        _companionFailure.value = null
        return promoteAndCapture(token, companion = true)
    }

    /** Audio is consumed once; a null result requires a new recording. Never persists history. */
    suspend fun confirmCompanionAndTranscribe(): String? = withContext(Dispatchers.Main.immediate) {
        confirmSession(DictationSessionOwnership.Owner.COMPANION, TranscriptionRetention.EPHEMERAL)
    }

    fun cancelCompanionRecording() {
        requireMainThread()
        if (ownership.permits(DictationSessionOwnership.Owner.COMPANION)) cancelSession()
    }

    private fun promoteAndCapture(token: Long, companion: Boolean): Boolean {
        if (!ownership.acceptStart(token)) return false
        return try {
            createNotificationChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            check(startAudioCapture()) { "Microphone capture could not start" }
            if (companion) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            true
        } catch (failure: Exception) {
            Timber.e(failure, "Unable to start dictation")
            if (companion) _companionFailure.value = "Unable to start microphone recording"
            finishSession(token)
            false
        }
    }

    private fun finishSession(token: Long) {
        if (ownership.token != token) return
        stopRecordingInternal(discard = true)
        ownership.finish(token)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelSession() {
        val token = ownership.token ?: return
        val job = transcriptionJob
        if (ownership.phase == DictationSessionOwnership.Phase.TRANSCRIBING && job != null) {
            job.cancel()
        } else {
            finishSession(token)
        }
    }

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

    /** Native engine readiness for IME/UI loading gates. */
    override val engineState: StateFlow<SttEngineState>
        get() = providerSlot.state

    override fun onBind(intent: Intent): IBinder {
        prewarmEngine()
        return binder
    }

    override fun prewarmEngine() {
        if (ownership.companionOwned) return
        if (prewarmJob?.isActive == true) return
        prewarmJob = serviceScope.launch(Dispatchers.Default) { prewarmActiveModel() }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        soundPlayer = DictationSoundPlayer(applicationContext)

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
                val request = if (intent.hasExtra(EXTRA_START_TOKEN)) {
                    intent.getLongExtra(EXTRA_START_TOKEN, -1L)
                } else {
                    ownership.requestStart(DictationSessionOwnership.Owner.LEGACY)
                }
                if (request != null && ownership.permits(DictationSessionOwnership.Owner.LEGACY)) {
                    if (ownership.token == request && ownership.phase == DictationSessionOwnership.Phase.PENDING) {
                        promoteAndCapture(request, companion = false)
                    } else {
                        acknowledgeStaleStart(startId)
                    }
                } else {
                    acknowledgeStaleStart(startId)
                }
                Timber.d("DictationService started foreground with ACTION_START")
            }
            ACTION_STOP -> {
                cancelRecording()
                Timber.d("DictationService stopped via ACTION_STOP")
            }
        }
        // START_NOT_STICKY: don't restart automatically if the system kills us.
        // Recording state is transient; the user will re-tap mic if needed.
        return START_NOT_STICKY
    }

    // A cancelled queued FGS request still needs promotion before it can be stopped.
    private fun acknowledgeStaleStart(startId: Int) {
        try {
            createNotificationChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (failure: Exception) {
            Timber.w(failure, "Unable to acknowledge obsolete foreground start")
        }
        if (ownership.token == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    /**
     * Start recording via foreground service promotion.
     *
     * Sends ACTION_START to self, which triggers onStartCommand to call
     * startForeground() (required for microphone access) then startAudioCapture().
     */
    override fun startRecording() {
        requireMainThread()
        val token = ownership.requestStart(DictationSessionOwnership.Owner.LEGACY) ?: return
        val intent = Intent(this, DictationService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_START_TOKEN, token)
        }
        try {
            startForegroundService(intent)
        } catch (failure: Exception) {
            Timber.e(failure, "Unable to request foreground dictation")
            finishSession(token)
        }
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
        requireMainThread()
        if (!ownership.permits(DictationSessionOwnership.Owner.LEGACY) ||
            ownership.phase == DictationSessionOwnership.Phase.TRANSCRIBING) return FloatArray(0)
        val token = ownership.token ?: return FloatArray(0)
        return try {
            val samples = audioCaptureManager?.stop() ?: FloatArray(0)
            audioCaptureManager = null
            if (soundEnabled) soundPlayer.playStop()
            samples
        } catch (failure: Exception) {
            Timber.e(failure, "Unable to stop dictation")
            FloatArray(0)
        } finally {
            finishSession(token)
        }
    }

    /**
     * Cancel recording and discard all audio data.
     *
     * Used when the user taps the X button during recording.
     * Returns to idle state without producing any audio output.
     */
    override fun cancelRecording() {
        requireMainThread()
        if (!ownership.permits(DictationSessionOwnership.Owner.LEGACY)) return
        if (soundEnabled) soundPlayer.playCancel()
        cancelSession()
        Timber.d("Recording cancelled, samples discarded")
    }

    /**
     * Stop recording, transcribe audio, and return processed text.
     *
     * Full pipeline: stop recording -> ensure model downloaded -> init engine ->
     * transcribe with a bounded provider timeout -> post-process -> return text.
     * Companion uses the same pipeline with downloaded models and ephemeral retention.
     */
    override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): String? =
        withContext(Dispatchers.Main.immediate) {
            confirmSession(DictationSessionOwnership.Owner.LEGACY, retention)
        }

    private suspend fun confirmSession(
        owner: DictationSessionOwnership.Owner,
        retention: TranscriptionRetention,
    ): String? {
        val token = ownership.beginTranscription(owner) ?: return null
        _state.value = DictationState.Transcribing
        val job = serviceScope.async(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (ownership.companionOwned) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                }
                transcribeCapturedAudio(retention, owner == DictationSessionOwnership.Owner.COMPANION)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.e(failure, "Unable to transcribe captured audio")
                null
            } finally {
                finishSession(token)
            }
        }
        transcriptionJob = job
        return try {
            job.await()
        } catch (cancelled: CancellationException) {
            job.cancel()
            throw cancelled
        } finally {
            if (transcriptionJob === job) transcriptionJob = null
        }
    }

    private suspend fun transcribeCapturedAudio(
        retention: TranscriptionRetention,
        downloadedOnly: Boolean,
    ): String? {
        // 1. Stop recording and get audio samples
        val manager = audioCaptureManager
        val samples = withContext(Dispatchers.IO) { manager?.stop() ?: FloatArray(0) }
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        audioCaptureManager = null

        if (samples.isEmpty()) {
            Timber.w("confirmAndTranscribe: no audio samples captured")
            return null
        }

        if (!RecordingDurationPolicy.canTranscribe(samples.size)) {
            Timber.w(
                "confirmAndTranscribe: clip too short (%d samples, minimum=%d)",
                samples.size,
                RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT,
            )
            return null
        }

        // Play stop cue when audio capture ends and transcription begins.
        if (soundEnabled) soundPlayer.playStop()

        Timber.d("confirmAndTranscribe: %d samples captured, transitioning to Transcribing", samples.size)
        _state.value = DictationState.Transcribing

        return try {
            // 2. Read user preferences at transcription time so changes take effect
            //    without needing a service restart.
            val prefs = dataStore.data.first()
            val activeModelKey = prefs[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY
            val languagePref = prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "auto"
            // whisper.cpp explicitly supports "auto"; do not turn auto-detection into French.
            val whisperLanguage = languagePref

            Timber.d(
                "confirmAndTranscribe: model=%s, language=%s",
                activeModelKey,
                whisperLanguage,
            )

            // Legacy callers retain first-use downloads; Companion never starts a download.
            val modelPath = if (downloadedOnly) modelManager.getModelPath(activeModelKey)
                else modelDownloader.ensureModelAvailable(activeModelKey)
            if (modelPath == null) {
                Timber.e("Model not available, cannot transcribe")
                return null
            }

            // 4. Initialize and use the selected provider/model under one ownership lock.
            val rawText = transcribeWithProvider(
                modelKey = activeModelKey,
                modelPath = modelPath,
                samples = samples,
                language = whisperLanguage,
            )

            if (rawText == null) {
                Timber.e("Provider initialization failed or transcription timed out")
                return null
            }

            // 6. Post-process (trim + punctuation)
            val processedText = TextPostProcessor.process(rawText)
            Timber.d(PrivacySafeLog.transcriptionProcessed(rawText, processedText))

            if (processedText.isNotEmpty()) {
                val model = ModelCatalog.findByKey(activeModelKey)
                transcriptionHistoryWriter.persist(
                    retention = retention,
                    text = processedText,
                    metadata = TranscriptionHistoryMetadata(
                        requestedLanguage = languagePref,
                        durationMillis = samples.size * 1_000L / RecordingDurationPolicy.SAMPLE_RATE_HZ,
                        modelKey = activeModelKey,
                        provider = model?.provider?.name ?: "UNKNOWN",
                    ),
                )
            }

            if (processedText.isEmpty()) null else processedText
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            null
        }
    }

    /**
     * Get or initialize the correct STT provider based on the active model's provider type.
     *
     * WHY dynamic dispatch: Phase 9 adds Parakeet as a second engine. The provider is
     * determined by the active model's AiProvider enum, not a separate setting.
     * Strict single-engine policy: if the provider type changes, release() the current
     * one before initialize() on the new one — prevents OOM from dual engine loading.
     */
    private suspend fun transcribeWithProvider(
        modelKey: String,
        modelPath: String,
        samples: FloatArray,
        language: String,
    ): String? {
        return withProviderForModel(modelKey, modelPath) { provider ->
            withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
                provider.transcribe(samples, language)
            }
        }
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

        return withContext(Dispatchers.Default) {
            providerSlot.withProvider(
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
    }

    /**
     * Internal helper to stop recording, optionally discarding samples.
     */
    private fun stopRecordingInternal(discard: Boolean) {
        val manager = audioCaptureManager
        audioCaptureManager = null
        try {
            if (discard) manager?.cancel() else manager?.stop()
        } catch (failure: Exception) {
            Timber.e(failure, "Unable to stop microphone capture")
        }
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        _state.value = DictationState.Idle
    }

    /**
     * Initialize AudioCaptureManager and start the read loop + timer.
     */
    private fun startAudioCapture(): Boolean {
        val manager = AudioCaptureManager(getSystemService(AudioManager::class.java))
        audioCaptureManager = manager

        manager.onCaptureFailure = {
            serviceScope.launch {
                if (audioCaptureManager === manager &&
                    ownership.phase == DictationSessionOwnership.Phase.RECORDING) {
                    if (ownership.companionOwned) _companionFailure.value = "Microphone recording failed"
                    cancelSession()
                }
            }
        }

        // Energy updates come from the capture read loop (Dispatchers.Default).
        // We update the state flow with the latest energy history each time.
        manager.onEnergyUpdate = { _ ->
            serviceScope.launch {
                if (audioCaptureManager !== manager ||
                    ownership.phase != DictationSessionOwnership.Phase.RECORDING) return@launch
                _state.value = DictationState.Recording(
                    elapsedMs = elapsedMs,
                    energy = manager.getEnergyHistory(),
                )
            }
        }

        if (!manager.start(serviceScope)) return false

        // Timer coroutine: increments elapsed time every second.
        // Runs on Main dispatcher since it only updates the state flow.
        elapsedMs = 0L
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                elapsedMs += 1000L
                _state.value = DictationState.Recording(
                    elapsedMs = elapsedMs,
                    energy = manager.getEnergyHistory(),
                )
            }
        }

        _state.value = DictationState.Recording(elapsedMs = 0L, energy = emptyList())

        // Play start cue after transitioning to Recording state.
        if (soundEnabled) soundPlayer.playStart()
        return true
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
            "Dictus Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active recording notification"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Build the foreground notification shown during recording.
     *
     * Shows "Dictus - Recording" with a Stop action button.
     * Body tap is a no-op (per user decision -- the IME is already visible).
     */
    private fun buildNotification(): Notification {
        if (ownership.companionOwned) {
            val recording = state.value is DictationState.Recording
            fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
                this, requestCode,
                Intent(this, CompanionActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(if (recording) R.string.companion_recording else R.string.companion_processing))
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
            if (recording) {
                notification.addAction(R.drawable.ic_stop, getString(R.string.companion_stop),
                    actionIntent(CompanionActionReceiver.ACTION_STOP, 1))
            }
            return notification.addAction(0, getString(R.string.companion_cancel),
                actionIntent(CompanionActionReceiver.ACTION_CANCEL, 2)).build()
        }
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
            .setContentTitle("Dictus - Recording")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && ownership.token == null) {
            // An idle floating control is not a reason to keep a large native model under pressure.
            prewarmJob?.cancel()
            serviceScope.launch(Dispatchers.Default) { providerSlot.release() }
        }
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        ownership.destroy()
        transcriptionJob?.cancel()
        stopRecordingInternal(discard = true)
        if (::soundPlayer.isInitialized) soundPlayer.release()
        serviceScope.cancel()
        cleanupScope.launch {
            try {
                providerSlot.release()
            } finally {
                cleanupScope.cancel()
            }
        }
        Timber.d("DictationService destroyed")
        super.onDestroy()
    }
}
