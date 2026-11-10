package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState

/**
 * Tells the keyboard's own dictations from the app's (#248), fed every state of the shared service.
 *
 * WHY: the keyboard rendered the shared state, so an app dictation showed the keyboard's recording
 * UI, whose ✓ inserted the app's text into the current field. Like the iOS keyboard, which follows
 * only the session it started (`KeyboardState.activeSessionID`), the keyboard now shows a
 * dictation only when it started it; during the app's it keeps typing and disables its mic.
 *
 * Transcribing carries no source: it belongs to whoever owned the recording before it. A
 * Transcribing seen first, by a keyboard bound mid-way, was not started by this keyboard.
 */
class KeyboardDictationOwnership {

    private var keyboardOwns = false

    /** Returns true while the state belongs to a dictation the keyboard did not start. */
    fun isForeign(state: DictationState): Boolean {
        keyboardOwns = when (state) {
            DictationState.Idle -> false
            is DictationState.Recording -> state.source == DictationSource.KEYBOARD
            DictationState.Transcribing -> keyboardOwns
        }
        return state != DictationState.Idle && !keyboardOwns
    }
}
