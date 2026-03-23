package dev.pivisolutions.dictus.service

/**
 * Sealed class representing the dictation state machine.
 *
 * The state transitions are:
 *   Idle -> Recording (when user taps mic)
 *   Recording -> Idle (when user confirms or cancels)
 *
 * This is exposed via StateFlow from DictationService and observed
 * by the IME to switch between keyboard and recording UI.
 */
sealed class DictationState {

    /** No recording in progress. Default state. */
    data object Idle : DictationState()

    /**
     * Actively recording audio.
     *
     * @param elapsedMs Milliseconds since recording started (for timer display).
     * @param energy Rolling window of normalized energy values (0.0-1.0) for
     *               waveform visualization. Maximum 30 entries.
     */
    data class Recording(
        val elapsedMs: Long = 0L,
        val energy: List<Float> = emptyList(),
    ) : DictationState()
}
