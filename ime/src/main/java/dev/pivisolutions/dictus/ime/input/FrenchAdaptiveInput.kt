package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey

private const val MAX_CONTEXT_UTF16_UNITS = 4 // At most two supplementary Unicode code points.

/** Reads transient editor context only; callers must not log or persist the returned state. */
fun readFrenchAdaptiveKeyState(
    inputConnection: InputConnection?,
    selectionCollapsed: Boolean = true,
): FrenchAdaptiveKey.State {
    if (!selectionCollapsed) return FrenchAdaptiveKey.DEFAULT
    val context = inputConnection
        ?.getTextBeforeCursor(MAX_CONTEXT_UTF16_UNITS, 0)
    return FrenchAdaptiveKey.fromContext(context)
}

/** Applies a tap as one editor transaction. */
fun applyFrenchAdaptiveKey(
    inputConnection: InputConnection,
    state: FrenchAdaptiveKey.State,
    selectionCollapsed: Boolean,
): Boolean = selectionCollapsed && applyFrenchAdaptiveText(inputConnection, state, state.label)

/** Applies a long-press choice as one editor transaction. */
fun applyFrenchAdaptiveVariant(
    inputConnection: InputConnection,
    state: FrenchAdaptiveKey.State,
    variant: String,
    selectionCollapsed: Boolean,
): Boolean = selectionCollapsed &&
    variant in state.variants &&
    applyFrenchAdaptiveText(inputConnection, state, variant)

private fun applyFrenchAdaptiveText(
    inputConnection: InputConnection,
    state: FrenchAdaptiveKey.State,
    text: String,
): Boolean {
    val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0) ?: return false
    if (extracted.selectionStart < 0 || extracted.selectionStart != extracted.selectionEnd) return false

    inputConnection.beginBatchEdit()
    try {
        if (!state.replacesPrevious) return inputConnection.commitText(text, 1)

        val sourceVowel = state.vowel ?: return false
        val expectedSource = if (state.label.firstOrNull()?.isUpperCase() == true) {
            sourceVowel.uppercase()
        } else {
            sourceVowel
        }
        val snapshot = extracted.text?.toString() ?: return false
        val localCursor = extracted.selectionStart
        if (localCursor !in 1..snapshot.length ||
            snapshot.substring(localCursor - 1, localCursor) != expectedSource
        ) return false

        val cursor = extracted.startOffset + extracted.selectionStart
        if (cursor <= extracted.startOffset) return false
        if (!inputConnection.setComposingRegion(cursor - 1, cursor)) return false
        return inputConnection.commitText(text, 1).also { committed ->
            if (!committed) {
                // Marking a composing region leaves source text intact; clear the mark on failure.
                inputConnection.finishComposingText()
            }
        }
    } finally {
        inputConnection.endBatchEdit()
    }
}
