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

    fun request(engineState: SttEngineState): MicGateCommand = when (engineState) {
        is SttEngineState.Ready -> {
            isPending = false
            MicGateCommand.START_RECORDING
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
            // Nothing to wait for: prewarming again cannot conjure a model that was never
            // downloaded. Leaving the request pending would silently swallow the next Ready.
            isPending = false
            MicGateCommand.NONE
        }
    }

    fun engineChanged(engineState: SttEngineState): MicGateCommand {
        if (!isPending) return MicGateCommand.NONE
        return when (engineState) {
            is SttEngineState.Ready -> {
                isPending = false
                MicGateCommand.START_RECORDING
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
