package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.InputConnection

/** The delete slide's edits on a real editor (#408), reading at most [contextLength] units. */
class InputConnectionDeleteSwipeEditor(
    private val inputConnection: InputConnection,
    private val contextLength: Int,
) : DeleteSwipeEditor {
    override fun textBeforeCursor(): String? =
        inputConnection.getTextBeforeCursor(contextLength, 0)?.toString()

    override fun deleteBefore(length: Int) {
        inputConnection.deleteSurroundingText(length, 0)
    }

    override fun insert(text: String) {
        inputConnection.commitText(text, 1)
    }
}
