package dev.pivisolutions.dictus.core.service

/**
 * What a keyboard mic tap does when no dictation is running (#247, iOS `RecordTapRouting`).
 *
 * WHY no wait state: the keyboard never covers itself to wait for the engine. With a model on
 * disk the recording starts at once and the load runs behind it; the transcription then waits
 * for the load in `SttProviderSlot`, and its timeout starts only once the provider is ready.
 * The audio is in memory the whole time, so a load that has not finished loses nothing.
 */
enum class KeyboardMicTap {
    /** Start recording and load the selected model in the background if it is not loaded. */
    RECORD,

    /** No model on disk: open Dictus on the Models screen, with no keyboard panel. */
    OPEN_MODELS;

    companion object {
        fun decide(engineState: SttEngineState): KeyboardMicTap =
            if (engineState is SttEngineState.ModelMissing) OPEN_MODELS else RECORD
    }
}
