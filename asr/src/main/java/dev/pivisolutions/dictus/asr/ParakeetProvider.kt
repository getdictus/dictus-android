package dev.pivisolutions.dictus.asr

import android.os.Build
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import dev.pivisolutions.dictus.core.stt.SttProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * SttProvider implementation backed by sherpa-onnx OfflineRecognizer for
 * NeMo Parakeet models (both CTC and transducer architectures).
 *
 * WHY sherpa-onnx: It ships ONNX Runtime as a pre-built Android JNI library,
 * so we don't need to build ONNX Runtime ourselves. The OfflineRecognizer API
 * wraps the JNI bridge, and NeMo models are supported out of the box.
 *
 * Supports two architectures:
 * - CTC (110M): single model file, English-only
 * - Transducer/TDT (0.6B v3): encoder/decoder/joiner, 25 languages with auto-detection
 *
 * WHY null AssetManager: Models live in the app's files directory, not in assets.
 * Passing null to OfflineRecognizer routes to the newFromFile() JNI path.
 *
 * WHY a dedicated native thread: the JNI calls block for seconds (model load, decode
 * of a long dictation). Callers run on the main thread, so running them inline froze
 * every frame, including the transcribing animation (#155). All native calls run one
 * at a time on this provider's own [nativeExecutor], like WhisperContext's single-thread
 * executor. Serializing them also means release() waits for an in-flight decode instead
 * of freeing the recognizer under it.
 *
 * WHY a thread per provider, and a bounded release (#159): a decode that never returns
 * keeps its thread busy forever. With one shared thread, the next provider's model load
 * queued behind it and every later dictation hung on « Transcription en cours ». Now a
 * call its caller abandoned while it ran makes [isReady] false, so SttProviderSlot
 * replaces this provider; [release] waits [releaseGraceMs] at most and then gives up.
 * The free stays queued behind the stuck call on the same thread, so the recognizer is
 * never freed under a running decode: it is freed if the decode ever returns, and
 * leaked with its parked thread otherwise. The replacement loads on a fresh thread.
 */
class ParakeetProvider(
    // Injectable so a unit test can use a fake instead of the real OfflineRecognizer,
    // whose class init loads libsherpa-onnx-jni.
    private val createRecognizer: (OfflineRecognizerConfig) -> ParakeetRecognizer =
        { config -> SherpaParakeetRecognizer(OfflineRecognizer(assetManager = null, config = config)) },
    private val nativeExecutor: ExecutorService = newNativeExecutor(),
    private val releaseGraceMs: Long = RELEASE_GRACE_MS,
) : SttProvider {

    private val nativeScope = CoroutineScope(SupervisorJob() + nativeExecutor.asCoroutineDispatcher())

    // Native calls whose caller gave up while they ran and that have not returned yet.
    private val abandonedCalls = AtomicInteger(0)

    @Volatile
    private var released = false

    override val providerId: String = "parakeet"
    override val displayName: String = "Parakeet (sherpa-onnx)"

    // Parakeet supports both English-only (CTC) and multilingual (TDT v3).
    // Empty list = all languages supported; actual constraint is per-model.
    override val supportedLanguages: List<String> = emptyList()

    // Written only on the native thread; read by isReady from any thread.
    @Volatile
    private var recognizer: ParakeetRecognizer? = null

    // Not ready while an abandoned call still holds the native thread: a reuse would
    // only queue behind it until the caller's timeout.
    override val isReady: Boolean get() = recognizer != null && !released && abandonedCalls.get() == 0

    /**
     * Initialize the engine with a model file path.
     *
     * Detects architecture from directory contents:
     * - If encoder.*.onnx exists → transducer config (TDT v3)
     * - Otherwise → CTC config (110M)
     *
     * @param modelPath Absolute path to the main ONNX model file.
     *                  tokens.txt is expected in the same directory.
     * @return true if initialization succeeded.
     */
    override suspend fun initialize(modelPath: String): Boolean = runNative { initializeBlocking(modelPath) }

    private fun initializeBlocking(modelPath: String): Boolean {
        val modelDir = modelPath.substringBeforeLast("/")
        val tokensPath = "$modelDir/tokens.txt"

        // Detect transducer by checking for encoder file
        val dir = File(modelDir)
        val encoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("encoder") && it.name.endsWith(".onnx") }
        val isTransducer = encoderFile != null

        val modelConfig = if (isTransducer) {
            val decoderFile = dir.listFiles()?.firstOrNull { it.name.startsWith("decoder") && it.name.endsWith(".onnx") }
            val joinerFile = dir.listFiles()?.firstOrNull { it.name.startsWith("joiner") && it.name.endsWith(".onnx") }
            if (decoderFile == null || joinerFile == null) {
                Timber.e("Transducer model missing decoder or joiner in %s", modelDir)
                return false
            }
            OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = encoderFile.absolutePath,
                    decoder = decoderFile.absolutePath,
                    joiner = joinerFile.absolutePath,
                ),
                tokens = tokensPath,
                numThreads = 2,
                provider = "cpu",
                modelType = "nemo_transducer",
            )
        } else {
            OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = 2,
                provider = "cpu",
            )
        }

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )

        return try {
            recognizer = createRecognizer(config)
            Timber.d("ParakeetProvider initialized (%s) with model: %s",
                if (isTransducer) "transducer" else "CTC", modelPath)
            true
        } catch (e: Exception) {
            Timber.e(e, "ParakeetProvider: initialization failed for %s", modelPath)
            false
        } catch (e: LinkageError) {
            // sherpa-onnx ships arm64-v8a and armeabi-v7a only. On an x86_64 device or
            // emulator the first load throws UnsatisfiedLinkError, and every later
            // attempt NoClassDefFoundError (the class init already failed). Any other
            // LinkageError (NoSuchMethodError, ExceptionInInitializerError...) means the
            // library loaded but its JNI does not match, e.g. after a sherpa-onnx upgrade.
            // Either way fail cleanly instead of crashing, but log what really happened.
            // Other Errors (OutOfMemoryError...) still propagate.
            when (e) {
                is UnsatisfiedLinkError, is NoClassDefFoundError ->
                    Timber.e(e, "ParakeetProvider: sherpa-onnx native library unavailable on %s, Parakeet disabled",
                        Build.SUPPORTED_ABIS?.firstOrNull())
                else ->
                    Timber.e(e, "Parakeet native init failed: %s: %s", e.javaClass.simpleName, e.message)
            }
            false
        }
    }

    /**
     * Transcribe 16kHz mono audio samples to text.
     *
     * For CTC models the language param is ignored (English-only).
     * For transducer v3 models, language is auto-detected from audio.
     *
     * @param samples FloatArray of 16kHz mono audio.
     * @param language BCP-47 language code (ignored — Parakeet auto-detects).
     * @return Raw transcribed text.
     */
    override suspend fun transcribe(samples: FloatArray, language: String): String = runNative {
        val r = recognizer ?: throw IllegalStateException("ParakeetProvider not initialized — call initialize() first")
        r.transcribe(samples)
    }

    /**
     * Release native resources.
     *
     * Explicitly frees the native ONNX Runtime recognizer before clearing the reference.
     * Relying on finalization would retain large native allocations across model switches.
     *
     * Returns within [releaseGraceMs] even when a stuck call holds the native thread; the
     * free then stays queued behind that call (see the class comment).
     */
    override suspend fun release() {
        if (released) return
        released = true
        // Not runNative: this block must stay queued even if we stop waiting for it, or a
        // decode that eventually returns would leave the native model allocated.
        val freeing = nativeScope.async {
            val current = recognizer
            recognizer = null
            current?.release()
        }
        // Lets the native thread exit once its queue drains.
        nativeExecutor.shutdown()
        // NonCancellable: the slot holds its mutex here and must not leave mid-release.
        val freed = withContext(NonCancellable) {
            withTimeoutOrNull(releaseGraceMs) { freeing.await() }
        } != null
        if (freed) {
            Timber.d("ParakeetProvider released")
        } else {
            Timber.w("ParakeetProvider: native thread still busy after %d ms, release left queued behind it", releaseGraceMs)
        }
    }

    /**
     * Run [block] on the native thread and await its result.
     *
     * WHY async + await rather than withContext: a JNI call cannot be interrupted, and
     * withContext waits for its block even once cancelled, so withTimeoutOrNull would
     * still hang on a stuck decode. await() returns on cancellation; the block then
     * finishes on its own and its late result is dropped with its Deferred, so it can
     * never reach the next dictation. A block still queued is skipped.
     */
    private suspend fun <T> runNative(block: () -> T): T {
        val call = AtomicInteger(QUEUED)
        val work = nativeScope.async {
            call.set(RUNNING)
            try {
                block()
            } finally {
                if (call.getAndSet(DONE) == ABANDONED) abandonedCalls.decrementAndGet()
            }
        }
        return try {
            work.await()
        } catch (cancellation: CancellationException) {
            work.cancel()
            if (call.compareAndSet(RUNNING, ABANDONED)) abandonedCalls.incrementAndGet()
            throw cancellation
        }
    }

    internal companion object {
        // Time a release waits for the native thread. A decode abandoned at the 120 s
        // dictation timeout gets this long to return before its model is left behind.
        const val RELEASE_GRACE_MS = 10_000L

        private const val QUEUED = 0
        private const val RUNNING = 1
        private const val DONE = 2
        private const val ABANDONED = 3

        fun newNativeExecutor(): ExecutorService =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "parakeet-native").apply { isDaemon = true } }
    }
}
