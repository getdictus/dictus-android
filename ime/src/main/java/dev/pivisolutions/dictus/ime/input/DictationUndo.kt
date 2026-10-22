package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the last dictation insertion so the keyboard can offer to undo it (issue #132).
 *
 * Mirrors iOS `DictationUndo.swift`: the undo deletes text the user cannot see being counted,
 * so it only ever removes what the keyboard inserted, and only while the text right before the
 * caret still ends with it. Any doubt fails closed: nothing is deleted and the offer disappears.
 *
 * The offer is withdrawn as soon as the field stops being in the state the insertion left it
 * in: a keystroke, a caret move, a new dictation or the end of the input session. Callers
 * report those through [clear] and [onSelectionChanged].
 *
 * This class never logs and never exposes the inserted text, so transcription content cannot
 * leak into logs through it (see PrivacySafeLog).
 */
class DictationUndo {

    sealed interface Decision {
        /** Safe: delete exactly [length] UTF-16 units before the caret. */
        data class Delete(val length: Int) : Decision

        /** Unsafe: delete nothing. */
        data class Refused(val reason: RefusalReason) : Decision
    }

    enum class RefusalReason {
        /** No insertion is pending. */
        NOTHING_PENDING,

        /** The editor returned no text before the caret (detached connection, secure field). */
        NO_CONTEXT,

        /** The text before the caret no longer ends with the inserted text. */
        SUFFIX_MISMATCH,
    }

    private var insertedText: String? = null

    // Caret position the insertion left behind; null until learned from the editor's echo.
    private var expectedCursor: Int? = null

    private val _isAvailable = MutableStateFlow(false)

    /** Whether the undo control should be shown. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * Records a dictation that was just committed.
     *
     * @param text the exact string handed to `commitText`, separator included.
     * @param cursorAfter the caret position expected after the insertion, or null when the
     *   selection before the insertion was unknown; the next collapsed selection update is then
     *   taken as the insertion's own echo.
     */
    fun recordInsertion(text: String, cursorAfter: Int?) {
        if (text.isEmpty()) {
            clear()
            return
        }
        insertedText = text
        expectedCursor = cursorAfter
        _isAvailable.value = true
    }

    /**
     * Reports an editor selection update. The insertion's own echo keeps the offer; any other
     * caret position or a non-empty selection withdraws it.
     */
    fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (insertedText == null) return
        val collapsed = selStart >= 0 && selStart == selEnd
        if (!collapsed) {
            clear()
            return
        }
        val expected = expectedCursor
        if (expected == null) {
            expectedCursor = selStart
        } else if (selStart != expected) {
            clear()
        }
    }

    /** Withdraws the offer: keystroke, new dictation, end of input session. */
    fun clear() {
        insertedText = null
        expectedCursor = null
        _isAvailable.value = false
    }

    /**
     * Decides what the undo may delete, then withdraws the offer whatever the outcome.
     *
     * @param readTextBeforeCursor reads the LIVE text before the caret, at most `n` characters
     *   (typically `InputConnection.getTextBeforeCursor(n, 0)`).
     */
    fun consume(readTextBeforeCursor: (Int) -> CharSequence?): Decision {
        val text = insertedText
        clear()
        if (text == null) return Decision.Refused(RefusalReason.NOTHING_PENDING)
        return verify(readTextBeforeCursor(text.length), text)
    }

    companion object {
        /**
         * Pure safety rule: [insertedText] may be deleted only if [textBeforeCursor] ends with it.
         *
         * Unlike iOS, Android lets the keyboard ask for exactly `insertedText.length` characters,
         * so a window shorter than the insertion is treated as a refusal rather than a weaker
         * partial match.
         */
        fun verify(textBeforeCursor: CharSequence?, insertedText: String): Decision {
            if (insertedText.isEmpty()) return Decision.Refused(RefusalReason.NOTHING_PENDING)
            if (textBeforeCursor.isNullOrEmpty()) return Decision.Refused(RefusalReason.NO_CONTEXT)
            if (!textBeforeCursor.endsWith(insertedText)) {
                return Decision.Refused(RefusalReason.SUFFIX_MISMATCH)
            }
            return Decision.Delete(insertedText.length)
        }
    }
}
