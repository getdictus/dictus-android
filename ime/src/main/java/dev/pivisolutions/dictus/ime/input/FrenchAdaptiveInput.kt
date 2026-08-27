package dev.pivisolutions.dictus.ime.input

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
): Boolean = applyFrenchAdaptiveText(inputConnection, state, state.label)

/** Applies a long-press choice as one editor transaction. */
fun applyFrenchAdaptiveVariant(
    inputConnection: InputConnection,
    state: FrenchAdaptiveKey.State,
    variant: String,
): Boolean = variant in state.variants && applyFrenchAdaptiveText(inputConnection, state, variant)

private fun applyFrenchAdaptiveText(
    inputConnection: InputConnection,
    state: FrenchAdaptiveKey.State,
    text: String,
): Boolean {
    inputConnection.beginBatchEdit()
    try {
        if (state.replacesPrevious && !inputConnection.deleteSurroundingText(1, 0)) return false
        return inputConnection.commitText(text, 1)
    } finally {
        inputConnection.endBatchEdit()
    }
}
