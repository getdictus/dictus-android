package dev.pivisolutions.dictus.core.stt

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Contract for all speech-to-text engines in Dictus.
 *
 * WHY an interface in core/: The core module cannot depend on any specific STT engine.
 * DictationService in the app module wires the concrete implementation (WhisperProvider today,
 * ParakeetProvider in Phase 9). This interface is the only coupling point -- adding a new engine
 * requires only a new implementation class, no changes to DictationService's contract.
 *
 * WHY metadata fields: Phase 9 will need providerId to distinguish engines in settings,
 * displayName for UI, and supportedLanguages to warn users about language limitations
 * (Parakeet is English-only).
 */
interface SttProvider {
    /** Stable machine-readable identifier (e.g., "whisper", "parakeet"). */
    val providerId: String

    /** Human-readable name shown in settings (e.g., "Whisper (whisper.cpp)"). */
    val displayName: String

    /**
     * BCP-47 language codes this provider supports.
     * Empty list means all languages are supported (Whisper).
     * Non-empty list restricts to those languages (Parakeet = listOf("en")).
     */
    val supportedLanguages: List<String>

    /** Whether the engine is ready (model loaded). */
    val isReady: Boolean

    /**
     * Initialize the engine with a model file.
     * @param modelPath Absolute path to the model file.
     * @return true if initialization succeeded.
     */
    suspend fun initialize(modelPath: String): Boolean

    /**
     * Transcribe audio samples to raw text.
     * @param samples FloatArray of 16kHz mono audio.
     * @param language BCP-47 language code ("fr", "en").
     * @return Raw transcribed text (before post-processing).
     */
    suspend fun transcribe(samples: FloatArray, language: String): String

    /**
     * Transcribe within [timeoutMs].
     *
     * By default the whole decode is lost at the timeout (null). An engine that decodes in
     * segments overrides this to return the segments decoded before the timeout, with
     * [SttTranscript.complete] false (Whisper, #179); null then means none was decoded.
     */
    suspend fun transcribe(samples: FloatArray, language: String, timeoutMs: Long): SttTranscript? =
        withTimeoutOrNull(timeoutMs) { transcribe(samples, language) }
            ?.let { text -> SttTranscript(text, segmentCount = null, complete = true) }

    /** Release native resources. */
    suspend fun release()
}
