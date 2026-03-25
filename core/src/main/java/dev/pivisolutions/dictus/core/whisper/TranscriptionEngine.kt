package dev.pivisolutions.dictus.core.whisper

/**
 * Abstraction for speech-to-text engine.
 *
 * WHY an interface: The core module cannot depend on the whisper module.
 * DictationService in the app module wires the concrete WhisperContext
 * implementation. This also enables FakeTranscriptionEngine for testing.
 */
interface TranscriptionEngine {
    /** Whether the engine is ready (model loaded). */
    val isReady: Boolean

    /**
     * Initialize the engine with a model file.
     * @param modelPath Absolute path to GGML .bin model file.
     * @return true if initialization succeeded.
     */
    suspend fun initialize(modelPath: String): Boolean

    /**
     * Transcribe audio samples to raw text.
     * @param samples FloatArray of 16kHz mono audio (from AudioCaptureManager).
     * @param language BCP-47 language code ("fr", "en").
     * @return Raw transcribed text (before post-processing).
     */
    suspend fun transcribe(samples: FloatArray, language: String): String

    /** Release native resources. */
    suspend fun release()
}
