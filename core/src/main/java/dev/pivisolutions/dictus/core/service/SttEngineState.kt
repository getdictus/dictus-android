package dev.pivisolutions.dictus.core.service

/**
 * Lifecycle state of the native speech-to-text engine.
 *
 * This is separate from [DictationState]: recording can start while an engine is
 * cold, while loading is useful to gate transcription UI independently.
 */
sealed interface SttEngineState {
    /** No native provider is retained in memory. */
    data object Cold : SttEngineState

    /** A provider is currently initializing for the selected model. */
    data class Loading(val modelKey: String) : SttEngineState

    /** The selected model is initialized and ready for transcription. */
    data class Ready(val modelKey: String) : SttEngineState

    /** Initialization failed; a later request may retry. */
    data class Failed(val modelKey: String) : SttEngineState
}
