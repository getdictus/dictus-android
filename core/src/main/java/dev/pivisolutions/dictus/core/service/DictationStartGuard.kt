package dev.pivisolutions.dictus.core.service

/**
 * Whether a new dictation may start (#237, iOS `ColdStartResolutionPolicy.canStartNewDictation`).
 *
 * WHY: the keyboard and the app share one dictation service and one microphone. A start while
 * a dictation records or transcribes used to open a second capture and orphan the first one.
 * The keyboard and the app ask this before calling start, and the service asks it again as the
 * last backstop. [startPending] is only known to the service: a start it accepted but has not
 * delivered yet, while the state still reads Idle.
 */
object DictationStartGuard {

    fun canStartNewDictation(state: DictationState, startPending: Boolean = false): Boolean =
        when (state) {
            DictationState.Idle -> !startPending
            is DictationState.Recording, DictationState.Transcribing -> false
        }
}
