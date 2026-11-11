package dev.pivisolutions.dictus.asr

import com.k2fsa.sherpa.onnx.OfflineRecognizer

/**
 * The native calls ParakeetProvider makes on a loaded model.
 *
 * WHY an interface: OfflineRecognizer loads libsherpa-onnx-jni when its class loads, so
 * a JVM unit test cannot create one. Tests pass a fake that blocks or returns on demand
 * to exercise the threading of ParakeetProvider (#159).
 */
interface ParakeetRecognizer {
    /** Decode 16 kHz mono [samples] on a fresh stream. Blocks for the whole decode. */
    fun transcribe(samples: FloatArray): ParakeetDecode

    /** Free the native model. Never called while [transcribe] runs. */
    fun release()
}

/** [ParakeetRecognizer] backed by sherpa-onnx. */
internal class SherpaParakeetRecognizer(private val recognizer: OfflineRecognizer) : ParakeetRecognizer {

    override fun transcribe(samples: FloatArray): ParakeetDecode {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate = 16000)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            return ParakeetDecode(result.text, result.tokens.toList(), result.timestamps.toList())
        } finally {
            stream.release()
        }
    }

    override fun release() = recognizer.release()
}

/**
 * One decode: the text, and its tokens with their start times in seconds from the start
 * of the decoded samples. The tokens are what [ParakeetWindows] stitches windows with.
 */
data class ParakeetDecode(
    val text: String,
    val tokens: List<String> = emptyList(),
    val timestamps: List<Float> = emptyList(),
)
