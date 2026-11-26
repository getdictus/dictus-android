package dev.pivisolutions.dictus.ime.input

import android.os.Build
import android.view.inputmethod.InputConnection

/** The delete slide's edits on a real editor (#408), reading at most [readLimit] units. */
class InputConnectionDeleteSwipeEditor(
    private val inputConnection: InputConnection,
    override val readLimit: Int,
) : DeleteSwipeEditor {
    override fun textBeforeCursor(): String? =
        inputConnection.getTextBeforeCursor(readLimit, 0)?.toString()

    /** Read without any text around it, so it costs the same in a long document (API 31+). */
    override fun cursor(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val surrounding = inputConnection.getSurroundingText(0, 0, 0) ?: return null
        if (surrounding.offset < 0 || surrounding.selectionStart != surrounding.selectionEnd) return null
        return surrounding.offset + surrounding.selectionStart
    }

    override fun deleteBefore(length: Int) {
        inputConnection.deleteSurroundingText(length, 0)
    }

    override fun insert(text: String) {
        inputConnection.commitText(text, 1)
    }
}
