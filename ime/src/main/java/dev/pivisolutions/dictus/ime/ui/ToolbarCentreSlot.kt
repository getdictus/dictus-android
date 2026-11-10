package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import dev.pivisolutions.dictus.ime.input.DictationNotice

/**
 * The one occupant of the keyboard toolbar's centre slot (#139).
 *
 * Port of iOS `ToolbarCentreSlot` (DictusCore, develop 4fe6549), reduced to the occupants the
 * Android keyboard has: a priority table is easier to check as a value than as nested branches
 * in a composable.
 *
 * 1. **Error**: the dictation failed. Nothing below it is worth saying, and undo in particular is
 *    meaningless because nothing was inserted.
 * 1b. **App dictation in progress** (#248, Android-only): explains the greyed mic for as long as
 *    the app records. It takes the rung of iOS `recoveredTranscription` (decided with the iOS dev):
 *    below the error, which is the newer and more urgent fact, above the undo and the suggestions.
 * 2. **Undo**: expires in seconds and on the first keystroke, and it is the only alternative to
 *    holding backspace on a long dictation. Right after an insertion the user has typed nothing,
 *    so the suggestions were predicted from dictated text and are worth less.
 * 3. **Suggestions**: the keyboard's core job, the autocorrect undo chip included (#276).
 */
sealed interface ToolbarCentreSlot {

    /** A dictation that inserted nothing, in grey (#153, #182). */
    data class Error(val notice: DictationNotice.Message) : ToolbarCentreSlot

    /** "Dictation in progress in Dictus" while the app records (#248). */
    data object AppDictationInProgress : ToolbarCentreSlot

    /** The undo-insertion pill (#132). */
    data object DictationUndo : ToolbarCentreSlot

    /** The suggestion slots. */
    data object Suggestions : ToolbarCentreSlot

    /** Nothing to say: ☰ sits alone opposite the mic. */
    data object Empty : ToolbarCentreSlot

    /**
     * Whether this occupant needs the full width, taking ☰'s place with it. iOS: the error, the
     * recovered transcription (here the app dictation notice), the undo and the suggestions do,
     * because they arrive mid-task and are read at a glance.
     */
    val evictsHamburger: Boolean
        get() = this != Empty

    companion object {
        fun resolve(
            notice: DictationNotice.Message?,
            appDictationInProgress: Boolean,
            offersDictationUndo: Boolean,
            hasSuggestions: Boolean,
        ): ToolbarCentreSlot = when {
            notice != null -> Error(notice)
            appDictationInProgress -> AppDictationInProgress
            offersDictationUndo -> DictationUndo
            hasSuggestions -> Suggestions
            else -> Empty
        }

        /**
         * Whether the suggestion row has anything to offer. The word being typed does not count
         * on its own: iOS `SuggestionState` empties the row when there is no completion, and ☰
         * comes back. The autocorrect undo chip counts, as iOS puts the typed word in the row; it
         * shows even with the suggestions turned off, as the only way left to revert a correction.
         */
        fun hasSuggestions(suggestions: List<String>, autocorrectUndo: AutocorrectUndoOffer? = null): Boolean =
            suggestions.isNotEmpty() || autocorrectUndo != null
    }
}
