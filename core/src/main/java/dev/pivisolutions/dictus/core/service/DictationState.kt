package dev.pivisolutions.dictus.core.service

/**
 * Sealed class representing the dictation state machine.
 *
 * Lives in the core module so both the app module (DictationService) and
 * the ime module (DictusImeService) can reference it without a circular
 * dependency. The app module implements the service; the ime module observes
 * the state for UI switching.
 *
 * State transitions:
 *   Idle -> Recording (when user taps mic)
 *   Recording -> Transcribing (when user confirms)
 *   Recording -> Idle (when user cancels)
 *   Transcribing -> Idle (when transcription completes or fails)
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

    /** Transcription is in progress. No user action possible -- wait for result. */
    data object Transcribing : DictationState()
}
