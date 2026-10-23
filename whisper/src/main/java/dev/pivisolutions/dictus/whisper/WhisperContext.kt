package dev.pivisolutions.dictus.whisper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

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
class WhisperContext internal constructor(
    private var ptr: Long,
    // Single-thread executor ensures all whisper.cpp calls are serialized.
    private val dispatcher: ExecutorCoroutineDispatcher,
    // Injectable so a JVM test can stand in for whisper.cpp, which has no library there.
    private val runFull: (ptr: Long, data: FloatArray, language: String) -> String = ::runWhisperFull,
) {

    // Supervised: a transcription its caller gave up on must not cancel the next one.
    private val nativeScope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * Transcribe audio samples to text.
     *
     * WHY async + await rather than withContext (#145): whisper_full cannot be interrupted,
     * and withContext waits for its block even once cancelled. The 120 s dictation timeout
     * then never fired and the screen stayed on « Transcription en cours » for as long as
     * the decode ran. await() returns on cancellation; the decode finishes on its own and
     * its late result is dropped with its Deferred. A transcription still queued behind it
     * is skipped. The context stays usable: the next transcription queues behind the late
     * decode on the same thread, within its own timeout, and release() frees the context
     * after it, never under it.
     *
     * @param data FloatArray of 16kHz mono audio samples (from AudioCaptureManager)
     * @param language BCP-47 language code (e.g. "fr", "en"). Phase 3 always passes "fr".
     * @return Transcribed text (all segments joined). Empty string if transcription fails.
     */
    suspend fun transcribeData(data: FloatArray, language: String = "fr"): String {
        val run = nativeScope.async {
            require(ptr != 0L) { "WhisperContext has been released" }
            runFull(ptr, data, language)
        }
        return try {
            run.await()
        } catch (cancellation: CancellationException) {
            run.cancel()
            throw cancellation
        }
    }

    /**
     * Release native resources. Context cannot be used after this call.
     */
    suspend fun release() {
        try {
            withContext(dispatcher) {
                if (ptr != 0L) {
                    WhisperLib.freeContext(ptr)
                    Timber.d("WhisperContext released")
                    ptr = 0L
                }
            }
        } finally {
            dispatcher.close()
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
         * WHY on the context's executor: the load blocks for 0.2 to 0.7 s and callers run
         * on the main thread, which froze the UI like the Parakeet decode did (#155, #159).
         *
         * @param modelPath Absolute path to a .bin GGML model file on internal storage.
         * @return WhisperContext ready for transcription, or null if loading failed.
         */
        suspend fun createFromFile(modelPath: String): WhisperContext? {
            val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "whisper-native") }
                .asCoroutineDispatcher()
            // Set on the native thread; read here once withContext returns or throws.
            val loaded = AtomicLong(0L)
            Timber.d("Loading whisper model from %s", modelPath)
            val startMs = System.currentTimeMillis()
            try {
                withContext(dispatcher) { loaded.set(WhisperLib.initContext(modelPath)) }
            } catch (cancellation: CancellationException) {
                // A load that finished after its caller gave up would otherwise leak the model.
                // finally: returning to the cancelled caller throws again once the free is done.
                try {
                    val ptr = loaded.get()
                    if (ptr != 0L) withContext(NonCancellable + dispatcher) { WhisperLib.freeContext(ptr) }
                } finally {
                    dispatcher.close()
                }
                throw cancellation
            } catch (failure: Throwable) {
                // e.g. UnsatisfiedLinkError: close the thread, which is not a daemon, so a
                // failed attempt does not leave one behind.
                dispatcher.close()
                throw failure
            }

            val ptr = loaded.get()
            if (ptr == 0L) {
                Timber.e("Failed to create WhisperContext from %s", modelPath)
                dispatcher.close()
                return null
            }

            Timber.d("WhisperContext created in %d ms (ptr=%d)", System.currentTimeMillis() - startMs, ptr)
            return WhisperContext(ptr, dispatcher)
        }

        /** One whisper_full run and its joined segments. Called on the context's native thread. */
        private fun runWhisperFull(ptr: Long, data: FloatArray, language: String): String {
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
            Timber.d(PrivacySafeLog.transcriptionComplete(textCount, durationMs, result))
            return result
        }

        /**
         * Return whisper.cpp system info string (CPU features, SIMD support).
         */
        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}
