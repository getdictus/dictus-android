package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.whisper.TranscriptionEngine
import dev.pivisolutions.dictus.whisper.WhisperContext
import timber.log.Timber

/**
 * TranscriptionEngine implementation that wraps WhisperContext.
 *
 * WHY a separate class (not inline in DictationService): Clean separation of
 * concerns. DictationService orchestrates the flow; this class manages the
 * whisper.cpp lifecycle. Also enables future engine swapping (e.g., Parakeet in v2).
 */
class WhisperTranscriptionEngine : TranscriptionEngine {

    private var whisperContext: WhisperContext? = null

    override val isReady: Boolean
        get() = whisperContext?.isValid() == true

    override suspend fun initialize(modelPath: String): Boolean {
        // Release existing context if re-initializing with a different model
        whisperContext?.release()
        Timber.d("Initializing WhisperTranscriptionEngine with model: %s", modelPath)
        val ctx = WhisperContext.createFromFile(modelPath)
        whisperContext = ctx
        if (ctx == null) {
            Timber.e("Failed to initialize WhisperContext from %s", modelPath)
            return false
        }
        Timber.d("WhisperTranscriptionEngine ready. System info: %s", WhisperContext.getSystemInfo())
        return true
    }

    override suspend fun transcribe(samples: FloatArray, language: String): String {
        val ctx = whisperContext
            ?: throw IllegalStateException("WhisperTranscriptionEngine not initialized")
        return ctx.transcribeData(samples, language)
    }

    override suspend fun release() {
        whisperContext?.release()
        whisperContext = null
        Timber.d("WhisperTranscriptionEngine released")
    }
}
