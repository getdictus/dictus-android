package dev.pivisolutions.dictus.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.sqrt

/**
 * Wraps the Android AudioRecord API to capture 16kHz mono Float32 audio.
 *
 * Audio samples accumulate in an in-memory buffer (ArrayList<Float>).
 * RMS energy is calculated per read chunk and stored in a rolling 30-entry
 * history for waveform visualization.
 *
 * WHY Float32 (ENCODING_PCM_FLOAT): whisper.cpp expects float input.
 * Capturing directly in float avoids a PCM_16BIT-to-float conversion step.
 * Supported on all devices since API 23 (our minSdk is 29).
 *
 * WHY 16kHz: Whisper models expect 16kHz mono audio. Capturing at this rate
 * avoids resampling.
 */
class AudioCaptureManager {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val MAX_ENERGY_HISTORY = 30
    }

    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null
    private val samples = ArrayList<Float>()
    private val energyHistory = ArrayDeque<Float>(MAX_ENERGY_HISTORY)

    /** Callback invoked on each energy update with a normalized 0.0-1.0 value. */
    var onEnergyUpdate: ((Float) -> Unit)? = null

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

        // Double the minimum buffer to reduce the risk of buffer underruns
        val bufferSize = minBufferSize * 2

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
            rec.startRecording()
            Timber.d("AudioRecord started: ${SAMPLE_RATE}Hz mono Float32, buffer=$bufferSize")
        }

        // Read loop on a background thread.
        // Each read produces a chunk of float samples. We accumulate them
        // and compute RMS energy for the waveform display.
        captureJob = scope.launch(Dispatchers.Default) {
            val readBuffer = FloatArray(minBufferSize / 4) // Float = 4 bytes
            while (isActive) {
                val read = recorder?.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING) ?: break
                if (read > 0) {
                    synchronized(samples) {
                        for (i in 0 until read) {
                            samples.add(readBuffer[i])
                        }
                    }
                    val rms = calculateRmsEnergy(readBuffer, read)
                    val normalized = normalizeEnergy(rms)
                    addEnergyToHistory(normalized)
                    onEnergyUpdate?.invoke(normalized)
                }
            }
        }
    }

    /**
     * Stop capturing and return the collected audio samples.
     *
     * @return FloatArray of all captured samples at 16kHz mono.
     */
    fun stop(): FloatArray {
        captureJob?.cancel()
        captureJob = null
        recorder?.stop()
        recorder?.release()
        recorder = null
        Timber.d("AudioRecord stopped, captured ${samples.size} samples")
        val result: FloatArray
        synchronized(samples) {
            result = samples.toFloatArray()
            samples.clear()
        }
        energyHistory.clear()
        return result
    }

    /**
     * Cancel capturing and discard all audio data.
     */
    fun cancel() {
        captureJob?.cancel()
        captureJob = null
        recorder?.stop()
        recorder?.release()
        recorder = null
        synchronized(samples) {
            samples.clear()
        }
        energyHistory.clear()
        Timber.d("AudioRecord cancelled, samples discarded")
    }

    /**
     * Calculate the root-mean-square energy of a buffer of audio samples.
     *
     * RMS = sqrt( sum(sample^2) / count )
     *
     * This gives a single energy value representing the "loudness" of the chunk.
     * Public for testability.
     *
     * @param buffer Array of float samples (typically -1.0 to 1.0).
     * @param count Number of valid samples in the buffer (may be less than buffer.size).
     * @return RMS energy value. Returns 0f if count is 0.
     */
    fun calculateRmsEnergy(buffer: FloatArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0f
        for (i in 0 until count) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / count)
    }

    /**
     * Normalize a raw RMS energy value to the 0.0-1.0 range for display.
     *
     * Typical speech RMS ranges from ~0.01 to ~0.3. Multiplying by 5 maps this
     * to roughly 0.05-1.0 which gives a good visual range for the waveform bars.
     * Values above 1.0 are clamped.
     *
     * @param rms Raw RMS energy value.
     * @return Normalized value in [0.0, 1.0].
     */
    fun normalizeEnergy(rms: Float): Float {
        return (rms * 5f).coerceIn(0f, 1f)
    }

    /**
     * Add a normalized energy value to the rolling history.
     *
     * The history maintains at most [MAX_ENERGY_HISTORY] (30) entries.
     * When full, the oldest entry is dropped. This provides the data
     * for the 30-bar waveform visualization.
     */
    fun addEnergyToHistory(energy: Float) {
        energyHistory.addLast(energy)
        if (energyHistory.size > MAX_ENERGY_HISTORY) {
            energyHistory.removeFirst()
        }
    }

    /**
     * Get a snapshot of the current energy history for waveform display.
     *
     * @return Immutable list of up to 30 normalized energy values.
     */
    fun getEnergyHistory(): List<Float> = energyHistory.toList()
}
