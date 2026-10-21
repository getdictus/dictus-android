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
    /** Decode 16 kHz mono [samples]. Blocks for the whole decode. */
    fun transcribe(samples: FloatArray): String

    /** Free the native model. Never called while [transcribe] runs. */
    fun release()
}

/** [ParakeetRecognizer] backed by sherpa-onnx. */
internal class SherpaParakeetRecognizer(private val recognizer: OfflineRecognizer) : ParakeetRecognizer {

    override fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, sampleRate = 16000)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    override fun release() = recognizer.release()
}
