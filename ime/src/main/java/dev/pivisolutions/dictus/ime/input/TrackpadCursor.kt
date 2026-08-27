package dev.pivisolutions.dictus.ime.input

import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.Locale
import kotlin.math.abs

/** Converts cumulative horizontal pointer travel into discrete cursor steps. */
class TrackpadMotionAccumulator(
    private val characterDistancePx: Float,
) {
    init {
        require(characterDistancePx > 0f) { "characterDistancePx must be positive" }
    }

    private var consumedPositionPx = 0f

    fun start(positionPx: Float) {
        consumedPositionPx = positionPx
    }

    fun moveTo(positionPx: Float): Int {
        val steps = ((positionPx - consumedPositionPx) / characterDistancePx).toInt()
        if (steps != 0) consumedPositionPx += steps * characterDistancePx
        return steps
    }
}

/** Returns a UTF-16 offset moved by whole user-perceived characters, or null at a window edge. */
internal fun cursorOffsetByGraphemes(text: CharSequence, start: Int, delta: Int): Int? {
    if (start !in 0..text.length) return null
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply {
        setText(text.toString())
    }
    var offset = start
    repeat(abs(delta)) {
        offset = if (delta > 0) iterator.following(offset) else iterator.preceding(offset)
        if (offset == BreakIterator.DONE) return null
    }
    return offset
}

/**
 * Moves the editor cursor by [delta] characters.
 *
 * Editors with extracted-text support use an absolute selection update. Editors that decline
 * extraction or selection updates receive directional key events as a compatibility fallback.
 */
fun moveCursorBy(inputConnection: InputConnection, delta: Int): Boolean {
    if (delta == 0) return false

    val movedWithSelection = runCatching {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return@runCatching false
        if (extracted.selectionEnd < 0) return@runCatching false

        val text = extracted.text ?: return@runCatching false
        val lowerBound = extracted.startOffset.coerceAtLeast(0).toLong()
        val upperBound = lowerBound + text.length.toLong()
        val relativeTarget = cursorOffsetByGraphemes(text, extracted.selectionEnd, delta)
            ?: return@runCatching false
        // A substring edge may cut through a grapheme in the complete document. DPAD events
        // let the editor resolve that boundary safely (and are harmless at true document ends).
        if (relativeTarget == 0 || relativeTarget == text.length) return@runCatching false
        val current = lowerBound + extracted.selectionEnd.toLong()
        val requested = lowerBound + relativeTarget.toLong()
        // An extracted window may represent only part of the document. Directional key events
        // are safer than mistaking a window edge for the real document boundary.
        if (current !in lowerBound..upperBound || requested !in lowerBound..upperBound) {
            return@runCatching false
        }
        val target = requested.toInt()
        inputConnection.setSelection(target, target)
    }.getOrDefault(false)
    if (movedWithSelection) return true

    val keyCode = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
    var handled = true
    repeat(abs(delta)) {
        handled = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) && handled
        handled = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)) && handled
    }
    return handled
}
