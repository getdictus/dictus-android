package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** Between two drawn keys of a row ([KeyRow]'s spacing). */
internal val KEY_GAP = 4.dp

/** Between the screen edge and a row's first and last drawn key ([KeyRow]'s padding). */
internal val ROW_EDGE_PADDING = 2.dp

/** Above and below a drawn key, inside its row ([KeyButton]): two rows' keys are twice this apart. */
internal val KEY_VERTICAL_PADDING = 2.dp

/**
 * How far, in px, a key's touch area reaches past its drawn key on each side (#400).
 *
 * The touch areas of a page tile the key grid, as iOS's touch targets do
 * (`KeyPlaneGeometry.layout`, getdictus/dictus-ios develop): the gap between two keys and between
 * two rows is split at its middle, and a row's first and last key reach the screen edge. A tap
 * anywhere on the grid then types the key nearest to it instead of nothing. The top and bottom
 * rows stop at their drawn keys: the strips under the toolbar and above the navigation bar stay
 * dead.
 */
data class KeyTouchArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        /** Only the drawn key: the emoji picker's keys and the tests that build a key alone. */
        val NONE = KeyTouchArea(0, 0, 0, 0)
    }
}

/**
 * The touch area of the key at [keyIndex] of [keyCount] in the row at [rowIndex] of [rowCount].
 *
 * Computed on [KEY_GAP] rounded to px, the way the row's spacing is, so two neighbours' halves
 * add up to the gap exactly: one px less leaves a dead line, one more lets two keys share it.
 */
internal fun keyTouchArea(
    density: Density,
    keyIndex: Int,
    keyCount: Int,
    rowIndex: Int,
    rowCount: Int,
): KeyTouchArea =
    with(density) {
        val gap = KEY_GAP.roundToPx()
        val edge = ROW_EDGE_PADDING.roundToPx()
        val vertical = KEY_VERTICAL_PADDING.roundToPx()
        KeyTouchArea(
            left = if (keyIndex == 0) edge else gap / 2,
            top = if (rowIndex == 0) 0 else vertical,
            right = if (keyIndex == keyCount - 1) edge else gap - gap / 2,
            bottom = if (rowIndex == rowCount - 1) 0 else vertical,
        )
    }

/**
 * Runs [gesture] on the drawn key grown by [area], and lays out and draws everything after this
 * modifier exactly where it was: the parent's layout and the key's drawing do not move.
 */
internal fun Modifier.touchArea(area: KeyTouchArea, gesture: Modifier): Modifier {
    val horizontal = area.left + area.right
    val vertical = area.top + area.bottom
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.offset(horizontal, vertical))
        layout(placeable.width - horizontal, placeable.height - vertical) {
            placeable.place(-area.left, -area.top)
        }
    }
        .then(gesture)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.offset(-horizontal, -vertical))
            layout(placeable.width + horizontal, placeable.height + vertical) {
                placeable.place(area.left, area.top)
            }
        }
}
