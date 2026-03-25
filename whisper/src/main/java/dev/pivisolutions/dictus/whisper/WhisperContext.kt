package dev.pivisolutions.dictus.whisper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Thread-safe Kotlin wrapper for a whisper.cpp inference context.
 *
 * WHY single-thread dispatcher: whisper.cpp is NOT thread-safe for concurrent
 * access to the same context. All JNI calls (init, transcribe, free) MUST
 * execute on the same thread. Internally whisper_full() uses multiple threads
 * for inference (controlled by n_threads), but the JNI entry point must be
 * serial.
 *
 * WHY create once, reuse: Model initialization loads the entire GGML model
 * into memory (takes seconds for large models). Creating a new context per
 * transcription would add unacceptable latency.
 */
class WhisperContext private constructor(private var ptr: Long) {

    // Single-thread executor ensures all whisper.cpp calls are serialized.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    /**
     * Transcribe audio samples to text.
     *
     * @param data FloatArray of 16kHz mono audio samples (from AudioCaptureManager)
     * @param language BCP-47 language code (e.g. "fr", "en"). Phase 3 always passes "fr".
     * @return Transcribed text (all segments joined). Empty string if transcription fails.
     */
    suspend fun transcribeData(data: FloatArray, language: String = "fr"): String =
        withContext(scope.coroutineContext) {
            require(ptr != 0L) { "WhisperContext has been released" }
            val numThreads = WhisperCpuConfig.preferredThreadCount
            Timber.d("Transcribing %d samples with %d threads, language=%s", data.size, numThreads, language)
            val startMs = System.currentTimeMillis()

            WhisperLib.fullTranscribe(ptr, numThreads, data, language)

            val textCount = WhisperLib.getTextSegmentCount(ptr)
            val result = buildString {
                for (i in 0 until textCount) {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            Timber.d("Transcription complete: %d segments, %d ms, result='%s'", textCount, durationMs, result)
            return@withContext result
        }

    /**
     * Release native resources. Context cannot be used after this call.
     */
    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            Timber.d("WhisperContext released")
            ptr = 0L
        }
    }

    /**
     * Check if this context is still valid (not released).
     */
    fun isValid(): Boolean = ptr != 0L

    companion object {
        /**
         * Create a WhisperContext by loading a GGML model file.
         *
         * @param modelPath Absolute path to a .bin GGML model file on internal storage.
         * @return WhisperContext ready for transcription, or null if loading failed.
         */
        suspend fun createFromFile(modelPath: String): WhisperContext? {
            Timber.d("Loading whisper model from %s", modelPath)
            val startMs = System.currentTimeMillis()
            val ptr = WhisperLib.initContext(modelPath)
            val durationMs = System.currentTimeMillis() - startMs

            if (ptr == 0L) {
                Timber.e("Failed to create WhisperContext from %s", modelPath)
                return null
            }

            Timber.d("WhisperContext created in %d ms (ptr=%d)", durationMs, ptr)
            return WhisperContext(ptr)
        }

        /**
         * Return whisper.cpp system info string (CPU features, SIMD support).
         */
        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}
