package dev.pivisolutions.dictus.ime.spatial

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize

/** Where one key is laid out, kept by the key and read when a tap or the grid needs it. */
open class KeyPlacement {
    var coordinates: LayoutCoordinates? = null

    /** The key's bounds in window pixels; null once it is detached. */
    internal open fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.let { Rect(it.positionInWindow(), it.size.toSize()) }

    /** [local], a point in the key's own coordinates, in window pixels. */
    internal fun inWindow(local: Offset): KeyPoint? = bounds()?.let { KeyPoint((it.left + local.x).toDouble(), (it.top + local.y).toDouble()) }

    internal fun centre(): KeyPoint? = bounds()?.center?.let { KeyPoint(it.x.toDouble(), it.y.toDouble()) }
}

/**
 * The letter keys on screen, and where the last one was touched (#385), in window pixels. Each
 * letter [KeyButton][dev.pivisolutions.dictus.ime.ui.KeyButton] registers its [KeyPlacement]
 * while it is composed and reports its touch point right before it types; the service takes the
 * point back when it writes the letter. Main thread only.
 *
 * WHY through a composition local: the touch point is only known inside the key's gesture
 * handler, and the key callbacks between it and the service carry no position. Passing it
 * alongside them keeps those signatures as they are.
 */
class KeyTouches {
    private val keys = HashMap<Char, KeyPlacement>()
    private var pending: Pair<String, KeyPoint>? = null

    fun register(output: String, placement: KeyPlacement) {
        letter(output)?.let { keys[it] = placement }
    }

    fun unregister(output: String, placement: KeyPlacement) {
        letter(output)?.let { if (keys[it] === placement) keys.remove(it) }
    }

    /** The key typing [output] was touched at [local], in the key's coordinates. */
    fun touched(output: String, placement: KeyPlacement, local: Offset) {
        pending = placement.inWindow(local)?.let { output to it }
    }

    /** The touch point of the key that typed [text], once; null when no key touch typed it. */
    internal fun take(text: String): KeyPoint? {
        val touch = pending
        pending = null
        return touch?.takeIf { it.first.equals(text, ignoreCase = true) }?.second
    }

    /**
     * The letter grid the taps are scored on, or null when the letters are not on screen. The key
     * pitch is measured between neighbours that every layout has: `e` and `r` side by side on the
     * top row, `d` on the row below.
     */
    internal fun grid(): KeyGrid? {
        val centres = HashMap<Char, KeyPoint>()
        for ((letter, placement) in keys) placement.centre()?.let { centres[letter] = it }
        val e = centres['e'] ?: return null
        val r = centres['r'] ?: return null
        val d = centres['d'] ?: return null
        val pitchX = r.x - e.x
        val pitchY = d.y - e.y
        if (pitchX <= 0 || pitchY <= 0) return null
        return KeyGrid(centres, pitchX, pitchY)
    }

    private fun letter(output: String) = output.singleOrNull()?.lowercaseChar()?.takeIf(Char::isLetter)
}

/** Null outside the keyboard (previews, tests): the keys then report nothing. */
val LocalKeyTouches = staticCompositionLocalOf<KeyTouches?> { null }
