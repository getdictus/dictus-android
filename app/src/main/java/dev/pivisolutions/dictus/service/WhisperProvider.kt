package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.whisper.WhisperContext
import timber.log.Timber

/**
 * SttProvider implementation that wraps WhisperContext (whisper.cpp).
 *
 * WHY a separate class (not inline in DictationService): Clean separation of
 * concerns. DictationService orchestrates the flow; this class manages the
 * whisper.cpp lifecycle. Also enables future engine swapping (e.g., Parakeet in Phase 9).
 */
class WhisperProvider : SttProvider {

    override val providerId: String = "whisper"
    override val displayName: String = "Whisper (whisper.cpp)"
    override val supportedLanguages: List<String> = emptyList() // empty = all languages

    private var whisperContext: WhisperContext? = null

    override val isReady: Boolean
        get() = whisperContext?.isValid() == true

    override suspend fun initialize(modelPath: String): Boolean {
        // Release existing context if re-initializing with a different model
        whisperContext?.release()
        Timber.d("Initializing WhisperProvider with model: %s", modelPath)
        val ctx = WhisperContext.createFromFile(modelPath)
        whisperContext = ctx
        if (ctx == null) {
            Timber.e("Failed to initialize WhisperContext from %s", modelPath)
            return false
        }
        Timber.d("WhisperProvider ready. System info: %s", WhisperContext.getSystemInfo())
        return true
    }

    override suspend fun transcribe(samples: FloatArray, language: String): String {
        val ctx = whisperContext
            ?: throw IllegalStateException("WhisperProvider not initialized")
        return ctx.transcribeData(samples, language)
    }

    override suspend fun release() {
        whisperContext?.release()
        whisperContext = null
        Timber.d("WhisperProvider released")
    }
}
