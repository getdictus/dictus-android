package dev.pivisolutions.dictus.whisper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.pivisolutions.dictus.core.stt.SttTranscript
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
    private val abortFlag: AbortFlag,
    // Injectable so a JVM test can stand in for whisper.cpp, which has no library there.
    private val runFull: (ptr: Long, abortFlag: AbortFlag, data: FloatArray, language: String) -> SttTranscript =
        ::runWhisperFull,
) {

    // Supervised: a transcription its caller gave up on must not cancel the next one.
    private val nativeScope = CoroutineScope(SupervisorJob() + dispatcher)

    // One transcription's claim on the native thread. Guarded by abortFlag's monitor.
    private class Decode {
        var abortRequested = false
    }

    // The decode on the native thread, if any. Read and written under abortFlag's monitor,
    // like the flag itself: an abort lands only on the decode it was meant for, never on
    // the next one and never on a freed flag.
    private var running: Decode? = null

    /**
     * Transcribe audio samples to text, with no timeout of its own.
     *
     * @param data FloatArray of 16kHz mono audio samples (from AudioCaptureManager)
     * @param language BCP-47 language code (e.g. "fr", "en").
     * @return Transcribed text (all segments joined).
     */
    suspend fun transcribeData(data: FloatArray, language: String = "fr"): String =
        checkNotNull(transcribeData(data, language, Long.MAX_VALUE)).text

    /**
     * Transcribe within [timeoutMs]. At the timeout the decode is aborted through
     * whisper.cpp's abort_callback (#173) and the segments decoded before it are returned,
     * with complete = false (#179). Null when none was decoded.
     *
     * WHY async + await rather than withContext (#145): withContext waits for its block even
     * once cancelled. Here a caller that gives up (cancellation) returns at once; its decode
     * is aborted and its result dropped. The aborted decode returns once the encoder pass or
     * decoder step under way ends, so a timeout waits [ABORT_GRACE_MS] at most for it. A
     * transcription still queued when its caller gives up never runs. The context stays
     * usable, and release() frees it after an aborted decode, never under it.
     */
    suspend fun transcribeData(data: FloatArray, language: String, timeoutMs: Long): SttTranscript? {
        val decode = Decode()
        val decoding = nativeScope.async {
            require(ptr != 0L) { "WhisperContext has been released" }
            synchronized(abortFlag) {
                if (decode.abortRequested) return@async null
                running = decode
                abortFlag.set(false)
            }
            val transcript = try {
                runFull(ptr, abortFlag, data, language)
            } finally {
                synchronized(abortFlag) { running = null }
            }
            // Not aborted, so whisper_full failed on its own: no text from a broken decode.
            check(transcript.complete || decode.abortRequested) { "whisper_full failed" }
            transcript
        }
        return try {
            withTimeoutOrNull(timeoutMs) { decoding.await() } ?: run {
                abort(decode)
                withTimeoutOrNull(ABORT_GRACE_MS) { decoding.await() }
                    ?.takeIf { it.complete || (it.segmentCount ?: 0) > 0 }
            }
        } catch (cancellation: CancellationException) {
            abort(decode)
            decoding.cancel()
            throw cancellation
        }
    }

    private fun abort(decode: Decode) = synchronized(abortFlag) {
        decode.abortRequested = true
        if (running === decode) {
            abortFlag.set(true)
            Timber.i("Whisper decode abort requested")
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
                    synchronized(abortFlag) { abortFlag.free() }
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

            val abortFlag = NativeAbortFlag.create()
            if (abortFlag == null) {
                Timber.e("Failed to allocate the whisper abort flag")
                withContext(NonCancellable + dispatcher) { WhisperLib.freeContext(ptr) }
                dispatcher.close()
                return null
            }

            Timber.d("WhisperContext created in %d ms (ptr=%d)", System.currentTimeMillis() - startMs, ptr)
            return WhisperContext(ptr, dispatcher, abortFlag)
        }

        // An aborted decode returns once its encoder pass ends: up to about 40 s with full
        // Small on the Pixel 4. Past this, the timeout gives up on its segments (#179).
        private const val ABORT_GRACE_MS = 60_000L

        /**
         * One whisper_full run and its joined segments, complete = false when it returned
         * non-zero: after an abort, the segments of the windows decoded before it, which
         * whisper.cpp keeps. Called on the context's native thread.
         */
        private fun runWhisperFull(ptr: Long, abortFlag: AbortFlag, data: FloatArray, language: String): SttTranscript {
            val numThreads = WhisperCpuConfig.preferredThreadCount
            Timber.d("Transcribing %d samples with %d threads, language=%s", data.size, numThreads, language)
            val startMs = System.currentTimeMillis()

            val code = WhisperLib.fullTranscribe(ptr, abortFlag.nativePtr, numThreads, data, language)
            Timber.i("whisper_full returned %d after %d ms", code, System.currentTimeMillis() - startMs)

            val textCount = WhisperLib.getTextSegmentCount(ptr)
            val result = buildString {
                for (i in 0 until textCount) {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }

            val durationMs = System.currentTimeMillis() - startMs
            Timber.d(PrivacySafeLog.transcriptionComplete(textCount, durationMs, result))
            return SttTranscript(result, textCount, complete = code == 0)
        }

        /**
         * Return whisper.cpp system info string (CPU features, SIMD support).
         */
        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}
