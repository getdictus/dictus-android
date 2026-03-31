package dev.pivisolutions.dictus.asr

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import dev.pivisolutions.dictus.core.stt.SttProvider
import timber.log.Timber

/**
 * SttProvider implementation backed by sherpa-onnx OfflineRecognizer for
 * NeMo CTC Parakeet models.
 *
 * WHY sherpa-onnx: It ships ONNX Runtime as a pre-built Android JNI library,
 * so we don't need to build ONNX Runtime ourselves. The OfflineRecognizer API
 * wraps the JNI bridge, and NeMo CTC (Parakeet) is supported out of the box.
 *
 * WHY English-only: Parakeet is a NeMo CTC model trained exclusively on English
 * data. The language param in transcribe() is ignored.
 *
 * WHY null AssetManager: Models live in the app's files directory, not in assets.
 * Passing null to OfflineRecognizer routes to the newFromFile() JNI path.
 */
class ParakeetProvider : SttProvider {

    override val providerId: String = "parakeet"
    override val displayName: String = "Parakeet (sherpa-onnx)"

    // English-only: non-empty list restricts the engine to "en" in provider selection logic.
    override val supportedLanguages: List<String> = listOf("en")

    private var recognizer: OfflineRecognizer? = null

    override val isReady: Boolean get() = recognizer != null

    /**
     * Initialize the engine with a model file path.
     *
     * @param modelPath Absolute path to the ONNX model file (e.g., models/parakeet-ctc-110m-int8/model.int8.onnx).
     *                  tokens.txt is expected in the same directory.
     * @return true if initialization succeeded.
     */
    override suspend fun initialize(modelPath: String): Boolean {
        // tokens.txt lives alongside the model file (same directory)
        val modelDir = modelPath.substringBeforeLast("/")
        val tokensPath = "$modelDir/tokens.txt"

        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = 2,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )

        return try {
            recognizer = OfflineRecognizer(assetManager = null, config = config)
            Timber.d("ParakeetProvider initialized with model: %s", modelPath)
            true
        } catch (e: Exception) {
            Timber.e(e, "ParakeetProvider: initialization failed for %s", modelPath)
            false
        }
    }

    /**
     * Transcribe 16kHz mono audio samples to text.
     *
     * The language param is intentionally ignored — Parakeet CTC is English-only
     * and does not accept a language hint.
     *
     * @param samples FloatArray of 16kHz mono audio.
     * @param language BCP-47 language code (ignored for Parakeet).
     * @return Raw transcribed text.
     */
    override suspend fun transcribe(samples: FloatArray, language: String): String {
        val r = recognizer ?: throw IllegalStateException("ParakeetProvider not initialized — call initialize() first")
        val stream = r.createStream()
        stream.acceptWaveform(samples, sampleRate = 16000)
        r.decode(stream)
        return r.getResult(stream).text
    }

    /**
     * Release native resources.
     *
     * Sets recognizer to null so isReady returns false. The OfflineRecognizer's
     * finalize() method handles native ONNX Runtime object cleanup via JNI.
     */
    override suspend fun release() {
        recognizer = null
        Timber.d("ParakeetProvider released")
    }
}
