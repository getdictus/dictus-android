package dev.pivisolutions.dictus.asr

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import dev.pivisolutions.dictus.core.stt.SttProvider
import timber.log.Timber
import java.io.File

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
 */
class ParakeetProvider : SttProvider {

    override val providerId: String = "parakeet"
    override val displayName: String = "Parakeet (sherpa-onnx)"

    // Parakeet supports both English-only (CTC) and multilingual (TDT v3).
    // Empty list = all languages supported; actual constraint is per-model.
    override val supportedLanguages: List<String> = emptyList()

    private var recognizer: OfflineRecognizer? = null

    override val isReady: Boolean get() = recognizer != null

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
    override suspend fun initialize(modelPath: String): Boolean {
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
            recognizer = OfflineRecognizer(assetManager = null, config = config)
            Timber.d("ParakeetProvider initialized (%s) with model: %s",
                if (isTransducer) "transducer" else "CTC", modelPath)
            true
        } catch (e: Exception) {
            Timber.e(e, "ParakeetProvider: initialization failed for %s", modelPath)
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
