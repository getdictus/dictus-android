package dev.pivisolutions.dictus.service

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.pivisolutions.dictus.core.audio.WaveformEnergy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Wraps the Android AudioRecord API to capture 16kHz mono Float32 audio.
 *
 * Audio samples accumulate in an in-memory buffer (ArrayList<Float>).
 * Each read chunk also feeds [WaveformEnergy], the iOS waveform pipeline.
 *
 * WHY Float32 (ENCODING_PCM_FLOAT): whisper.cpp expects float input.
 * Capturing directly in float avoids a PCM_16BIT-to-float conversion step.
 * Supported on all devices since API 23 (our minSdk is 29).
 *
 * WHY 16kHz: Whisper models expect 16kHz mono audio. Capturing at this rate
 * avoids resampling.
 */
class AudioCaptureManager(
    private val audioManager: AudioManager? = null,
) {

    companion object {
        private const val SAMPLE_RATE = RecordingDurationPolicy.SAMPLE_RATE_HZ
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT

        /**
         * Samples per read: the iOS tap delivers 4096 frames at 48 kHz, so ~1365 samples
         * once converted to 16 kHz. Reading the same amount keeps the waveform history
         * scrolling at the iOS speed (6 buckets every ~85 ms).
         */
        internal const val READ_CHUNK_SAMPLES = 4096 * SAMPLE_RATE / 48_000
    }

    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null
    private val samples = ArrayList<Float>()
    private val waveformEnergy = WaveformEnergy()

    /** Called after each read chunk, once the waveform levels include it. */
    var onEnergyUpdate: (() -> Unit)? = null

    /**
     * Start capturing audio.
     *
     * Creates an AudioRecord instance and launches a coroutine that continuously
     * reads samples into the in-memory buffer. Energy updates are posted via
     * [onEnergyUpdate].
     *
     * @param scope CoroutineScope tied to the service lifecycle. The read loop
     *              runs on Dispatchers.Default (background thread pool) so it
     *              doesn't block the main thread.
     */
    fun start(scope: CoroutineScope) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Timber.e("AudioRecord.getMinBufferSize returned $minBufferSize -- device may not support ENCODING_PCM_FLOAT")
            return
        }

        // Room for two read chunks, and never less than twice the minimum, so a chunk
        // being processed cannot overrun the recorder's buffer.
        val bufferSize = maxOf(minBufferSize * 2, READ_CHUNK_SAMPLES * Float.SIZE_BYTES * 2)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        ).also { rec ->
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("AudioRecord failed to initialize (state=${rec.state})")
                rec.release()
                recorder = null
                return
            }
            val routeResult = audioManager?.let { manager ->
                preferBuiltInMicrophone(AndroidAudioInputRouteBackend(manager, rec))
            } ?: AudioInputRouteResult.NoBuiltInMic
            rec.startRecording()
            Timber.d(
                "AudioRecord started: ${SAMPLE_RATE}Hz mono Float32, buffer=$bufferSize, " +
                    "inputPreference=$routeResult, routedType=${rec.routedDevice?.type ?: "unknown"}",
            )
        }

        // Read loop on a background thread.
        // Each read produces a chunk of float samples. We accumulate them
        // and feed the waveform pipeline.
        captureJob = scope.launch(Dispatchers.Default) {
            val readBuffer = FloatArray(READ_CHUNK_SAMPLES)
            while (isActive) {
                val read = recorder?.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING) ?: break
                if (read <= 0) break
                synchronized(samples) {
                    for (i in 0 until read) {
                        samples.add(readBuffer[i])
                    }
                }
                waveformEnergy.push(readBuffer, read)
                onEnergyUpdate?.invoke()
            }
        }
    }

    /**
     * Stop capturing and return the collected audio samples.
     *
     * @return FloatArray of all captured samples at 16kHz mono.
     */
    fun stop(): FloatArray {
        val job = captureJob
        val activeRecorder = recorder
        stopCaptureAndAwait(job) { activeRecorder?.stop() }
        captureJob = null
        activeRecorder?.release()
        recorder = null
        Timber.d("AudioRecord stopped, captured ${samples.size} samples")
        val result: FloatArray
        synchronized(samples) {
            result = samples.toFloatArray()
            samples.clear()
        }
        waveformEnergy.reset()
        return result
    }

    /**
     * Cancel capturing and discard all audio data.
     */
    fun cancel() {
        val job = captureJob
        val activeRecorder = recorder
        job?.cancel()
        stopCaptureAndAwait(job) { activeRecorder?.stop() }
        captureJob = null
        activeRecorder?.release()
        recorder = null
        synchronized(samples) {
            samples.clear()
        }
        waveformEnergy.reset()
        Timber.d("AudioRecord cancelled, samples discarded")
    }

    /** The 30 waveform levels for the audio read so far (all zero before the first read). */
    fun getEnergyHistory(): List<Float> = waveformEnergy.snapshot()

    /**
     * Stop the recorder before waiting for the capture loop. AudioRecord.stop()
     * releases a blocking read, allowing its final chunk to be appended before
     * the caller snapshots [samples]. The controller API is synchronous, so the
     * short join is intentionally bridged here rather than exposing a racy result.
     */
    internal fun stopCaptureAndAwait(job: Job?, stopRecorder: () -> Unit) {
        stopRecorder()
        if (job != null) {
            runBlocking { job.join() }
        }
    }
}
