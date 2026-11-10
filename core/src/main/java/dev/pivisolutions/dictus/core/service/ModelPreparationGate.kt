package dev.pivisolutions.dictus.core.service

/**
 * When the model preparation overlay may cover the screen (#175, parity with iOS #458).
 *
 * WHY: a dictation that records or transcribes owns the display. A model load that starts
 * under it (the transcription loading a newly selected model) runs on, but its overlay would
 * say "preparing" over a live microphone or over the transcription animation. iOS refuses the
 * preparation screen whenever its dictation is not idle (`ModelPreparationGate.swift`).
 */
object ModelPreparationGate {

    /** The engine state the overlay should render: [SttEngineState.Cold] hides it. */
    fun overlayState(engineState: SttEngineState, dictationState: DictationState): SttEngineState =
        if (dictationState is DictationState.Idle) engineState else SttEngineState.Cold
}
