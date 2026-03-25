package dev.pivisolutions.dictus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
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
class DictationService : Service(), DictationController {

    companion object {
        const val CHANNEL_ID = "dictus_recording"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "dev.pivisolutions.dictus.action.START"
        const val ACTION_STOP = "dev.pivisolutions.dictus.action.STOP"
        private const val TRANSCRIPTION_TIMEOUT_MS = 30_000L
        private const val LANGUAGE = "fr" // Hard-coded for Phase 3; Phase 4 adds settings
    }

    private val transcriptionEngine = WhisperTranscriptionEngine()
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

    private var audioCaptureManager: AudioCaptureManager? = null
    private var timerJob: Job? = null
    private var elapsedMs: Long = 0L

    // State machine exposed to the IME via the binder.
    // MutableStateFlow is thread-safe; updates from any coroutine are fine.
    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)

    /** Observable state for the IME to collect. */
    override val state: StateFlow<DictationState> = _state.asStateFlow()

    override fun onBind(intent: Intent): IBinder = binder

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
                startAudioCapture()
                Timber.d("DictationService started foreground with ACTION_START")
            }
            ACTION_STOP -> {
                stopRecordingInternal(discard = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Timber.d("DictationService stopped via ACTION_STOP")
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
     * startForeground() (required for microphone access) then startAudioCapture().
     */
    override fun startRecording() {
        val intent = Intent(this, DictationService::class.java).apply {
            action = ACTION_START
        }
        startForegroundService(intent)
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
        val samples = audioCaptureManager?.stop() ?: FloatArray(0)
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        _state.value = DictationState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Recording stopped, ${samples.size} samples captured")
        return samples
    }

    /**
     * Cancel recording and discard all audio data.
     *
     * Used when the user taps the X button during recording.
     * Returns to idle state without producing any audio output.
     */
    override fun cancelRecording() {
        stopRecordingInternal(discard = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Recording cancelled, samples discarded")
    }

    /**
     * Stop recording, transcribe audio, and return processed text.
     *
     * Full pipeline: stop recording -> ensure model downloaded -> init engine ->
     * transcribe with 30s timeout -> post-process -> return text.
     *
     * WHY 30s timeout: On Pixel 4 with tiny model, transcription takes 2-5s for
     * typical dictation (5-30s audio). 30s covers worst-case long recordings.
     * A stuck JNI call should not block the UI indefinitely.
     */
    override suspend fun confirmAndTranscribe(): String? {
        // 1. Stop recording and get audio samples
        val samples = audioCaptureManager?.stop() ?: FloatArray(0)
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        audioCaptureManager = null

        if (samples.isEmpty()) {
            Timber.w("confirmAndTranscribe: no audio samples captured")
            _state.value = DictationState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return null
        }

        Timber.d("confirmAndTranscribe: %d samples captured, transitioning to Transcribing", samples.size)
        _state.value = DictationState.Transcribing

        return try {
            // 2. Ensure model is available (download on first use)
            val modelPath = modelDownloader.ensureModelAvailable(ModelManager.DEFAULT_MODEL_KEY)
            if (modelPath == null) {
                Timber.e("Model not available, cannot transcribe")
                _state.value = DictationState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return null
            }

            // 3. Initialize engine if needed
            if (!transcriptionEngine.isReady) {
                val initialized = transcriptionEngine.initialize(modelPath)
                if (!initialized) {
                    Timber.e("Failed to initialize transcription engine")
                    _state.value = DictationState.Idle
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return null
                }
            }

            // 4. Transcribe with timeout
            val rawText = withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
                transcriptionEngine.transcribe(samples, LANGUAGE)
            }

            if (rawText == null) {
                Timber.e("Transcription timed out after %d ms", TRANSCRIPTION_TIMEOUT_MS)
                _state.value = DictationState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return null
            }

            // 5. Post-process (trim + punctuation)
            val processedText = TextPostProcessor.process(rawText)
            Timber.d("Transcription result: raw='%s', processed='%s'", rawText, processedText)

            _state.value = DictationState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            if (processedText.isEmpty()) null else processedText
        } catch (e: Exception) {
            Timber.e(e, "Transcription failed")
            _state.value = DictationState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            null
        }
    }

    /**
     * Internal helper to stop recording, optionally discarding samples.
     */
    private fun stopRecordingInternal(discard: Boolean) {
        if (discard) {
            audioCaptureManager?.cancel()
        } else {
            audioCaptureManager?.stop()
        }
        audioCaptureManager = null
        timerJob?.cancel()
        timerJob = null
        elapsedMs = 0L
        _state.value = DictationState.Idle
    }

    /**
     * Initialize AudioCaptureManager and start the read loop + timer.
     */
    private fun startAudioCapture() {
        val manager = AudioCaptureManager()
        audioCaptureManager = manager

        // Energy updates come from the capture read loop (Dispatchers.Default).
        // We update the state flow with the latest energy history each time.
        manager.onEnergyUpdate = { _ ->
            _state.value = DictationState.Recording(
                elapsedMs = elapsedMs,
                energy = manager.getEnergyHistory(),
            )
        }

        manager.start(serviceScope)

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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            transcriptionEngine.release()
        }
        serviceScope.cancel()
        Timber.d("DictationService destroyed")
    }
}
