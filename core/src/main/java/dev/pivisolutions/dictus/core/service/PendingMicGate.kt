package dev.pivisolutions.dictus.core.service

/** Commands emitted by [PendingMicGate]. */
enum class MicGateCommand {
    NONE,
    PREWARM,
    START_RECORDING,
}

/**
 * Deterministic state machine for a microphone intent that may arrive before the STT engine is ready.
 * It deliberately contains no coroutine or Compose state so every UI surface uses the same race-free rules.
 */
class PendingMicGate {
    var isPending: Boolean = false
        private set

    /**
     * The model the user selected, or null when the caller does not know it.
     *
     * WHY (#175): the engine keeps the previous model after a model switch until something
     * loads the new one. Treating that Ready as ready started the recording on the old engine,
     * and the transcription then loaded the new model under the preparation overlay.
     */
    var selectedModelKey: String? = null

    /** Ready for this tap: the engine holds the selected model, or the selection is unknown. */
    private fun SttEngineState.Ready.matchesSelection(): Boolean =
        selectedModelKey == null || modelKey == selectedModelKey

    fun request(engineState: SttEngineState): MicGateCommand = when (engineState) {
        is SttEngineState.Ready -> if (engineState.matchesSelection()) {
            isPending = false
            MicGateCommand.START_RECORDING
        } else {
            // Load the selected model first, under the overlay, as from Cold.
            isPending = true
            MicGateCommand.PREWARM
        }
        SttEngineState.Cold -> {
            isPending = true
            MicGateCommand.PREWARM
        }
        is SttEngineState.Loading -> {
            isPending = true
            MicGateCommand.NONE
        }
        is SttEngineState.Failed -> {
            isPending = true
            // Failure requires an explicit user choice from the overlay.
            MicGateCommand.NONE
        }
        is SttEngineState.ModelMissing -> {
            // Re-check the disk: the user may have downloaded a model since this state was
            // published, and without a Retry button this tap is the only refresh path.
            // Never stay pending — a queued intent would start recording on a later Ready
            // the user never asked for.
            isPending = false
            MicGateCommand.PREWARM
        }
    }

    fun engineChanged(engineState: SttEngineState): MicGateCommand {
        if (!isPending) return MicGateCommand.NONE
        return when (engineState) {
            is SttEngineState.Ready -> if (engineState.matchesSelection()) {
                isPending = false
                MicGateCommand.START_RECORDING
            } else {
                // The previous model is still loaded; the selected one has not started loading.
                MicGateCommand.NONE
            }
            is SttEngineState.ModelMissing -> {
                // A prewarm that discovers no model must not leave the tap queued: a later
                // Ready would then start a recording the user never asked for.
                isPending = false
                MicGateCommand.NONE
            }
            else -> MicGateCommand.NONE
        }
    }

    fun retry(): MicGateCommand {
        // Retrying a background prewarm must not invent microphone consent.
        // An existing pending mic request remains pending until Ready.
        return MicGateCommand.PREWARM
    }

    fun cancel() {
        isPending = false
    }
}
