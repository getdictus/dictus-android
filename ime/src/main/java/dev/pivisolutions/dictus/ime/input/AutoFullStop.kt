package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import android.view.inputmethod.InputConnection

/**
 * Whether a second Space press becomes ". " (#193).
 *
 * Ported from the iOS keyboard (`DictusCore/AutoFullStop.swift`, dictus-ios develop), rule for
 * rule: two spaces after anything but a space or a full stop end the sentence. iOS has no
 * setting for it, so neither does Android. The capital that follows comes from
 * [AutoCapitalization], which already capitalizes after ". ".
 *
 * iOS also refuses while its text mirror is suspect. Android reads the editor itself through
 * the InputConnection, so it has no mirror; what plays that role here is an editor that cannot
 * answer (null context) or a selection that is not a caret, and both refuse.
 */
object AutoFullStop {
    const val REPLACEMENT = ". "

    /**
     * Whether the field may get a full stop at all, from its input type alone.
     *
     * Text fields only, and never a password: iOS never shows a third-party keyboard in a secure
     * field, so its rule never meets one, while on Android two spaces typed into a password must
     * stay two spaces.
     */
    fun allowedFor(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return inputType and InputType.TYPE_MASK_VARIATION !in PASSWORD_VARIATIONS
    }

    /**
     * Whether the caller must replace the trailing space with ". ".
     *
     * @param context the text before the cursor, read BEFORE the second space is inserted, so it
     *   still ends `[char][space]`. Null when the editor could not answer.
     */
    fun shouldSubstitute(context: CharSequence?): Boolean {
        val text = context ?: return false
        if (text.length < 2 || text.last() != ' ') return false
        // The one before the space is neither a space nor a period, otherwise ". " would double a
        // period or fire on triple spaces.
        val beforeSpace = text[text.length - 2]
        return beforeSpace != ' ' && beforeSpace != '.'
    }

    private val PASSWORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )
}

/**
 * Applies the double-space full stop and its one-shot Backspace undo against an editor.
 *
 * The undo is not on iOS, whose Backspace always deletes one character: it is Android's own
 * pattern, the same as the autocorrect undo, where only an immediately following Backspace
 * reverts the automatic edit. It turns ". " back into the single space the user had typed, and
 * is withdrawn by any other input, by a caret move away from where the full stop left it and by
 * the end of the input session. Editor text is only read, never kept or logged.
 */
class AutoFullStopInput {
    private var pending = false

    // Caret the full stop left behind; null until learned from the editor's selection echo.
    private var expectedCursor: Int? = null

    /**
     * Replaces the trailing space with ". " if the rule allows it, in one batch edit.
     *
     * @return true when ". " was written, so the caller must not insert a space of its own.
     */
    fun onSpace(inputConnection: InputConnection, selectionCollapsed: Boolean): Boolean {
        clear()
        if (!selectionCollapsed) return false
        inputConnection.beginBatchEdit()
        try {
            if (!AutoFullStop.shouldSubstitute(inputConnection.getTextBeforeCursor(CONTEXT_LENGTH, 0))) {
                return false
            }
            if (!inputConnection.deleteSurroundingText(1, 0)) return false
            if (!inputConnection.commitText(AutoFullStop.REPLACEMENT, 1)) {
                // Put the deleted space back rather than leave the word without one.
                inputConnection.commitText(" ", 1)
                return false
            }
        } finally {
            inputConnection.endBatchEdit()
        }
        pending = true
        return true
    }

    /**
     * Reverts the last full stop if this Backspace immediately follows it.
     *
     * @return true when ". " became " ". False means nothing was touched and the caller performs
     *   its ordinary delete.
     */
    fun onBackspace(inputConnection: InputConnection, selectionCollapsed: Boolean): Boolean {
        val wasPending = pending
        clear()
        if (!wasPending || !selectionCollapsed) return false
        inputConnection.beginBatchEdit()
        try {
            val before = inputConnection.getTextBeforeCursor(AutoFullStop.REPLACEMENT.length, 0)
            if (before?.toString() != AutoFullStop.REPLACEMENT) return false
            if (!inputConnection.deleteSurroundingText(AutoFullStop.REPLACEMENT.length, 0)) return false
            if (!inputConnection.commitText(" ", 1)) {
                inputConnection.commitText(AutoFullStop.REPLACEMENT, 1)
                return false
            }
        } finally {
            inputConnection.endBatchEdit()
        }
        return true
    }

    /**
     * Reports an editor selection update. The full stop's own echo keeps the undo; any other
     * caret position or a non-empty selection withdraws it.
     */
    fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (!pending) return
        if (selStart < 0 || selStart != selEnd) {
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

    /** Withdraws the undo: any other input, end of the input session. */
    fun clear() {
        pending = false
        expectedCursor = null
    }

    private companion object {
        // The rule looks at the last two characters only.
        const val CONTEXT_LENGTH = 2
    }
}
