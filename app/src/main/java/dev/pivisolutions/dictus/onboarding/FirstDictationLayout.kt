package dev.pivisolutions.dictus.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The first dictation page between the top row and the keyboard: the title block, the
 * practice field, then the hint and « Passer » (#344).
 *
 * WHY NOT A COLUMN WITH THE FIELD IN weight(1f): a column serves the title block first, so
 * on a short screen or with a large font (360x640 dp, font scale 1.3) the title and the
 * subtitle filled the space, the field shrank to nothing and « Passer » went under the
 * keyboard. Here the bottom row and the field's minimum come first; the title block gets
 * what is left and scrolls inside it, and the field takes the rest.
 *
 * @param header the title block. Measured no taller than what it is left, so it has to
 *   scroll its own content.
 * @param field the practice field, given an exact height.
 * @param bottom the hint and the Skip row, at the bottom.
 * @param fieldMinHeight the field's minimum, kept as long as the bottom row leaves room for it.
 */
@Composable
fun FirstDictationLayout(
    header: @Composable () -> Unit,
    field: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    fieldMinHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(header, field, bottom), modifier = modifier) { (headers, fields, bottoms), constraints ->
        val height = constraints.maxHeight
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bottomPlaceable = bottoms.single().measure(loose)
        val headerMax = (height - bottomPlaceable.height - fieldMinHeight.roundToPx()).coerceAtLeast(0)
        val headerPlaceable = headers.single().measure(loose.copy(maxHeight = headerMax))
        // Below the minimum only when the bottom row alone fills the page (landscape, #127).
        val fieldHeight = (height - headerPlaceable.height - bottomPlaceable.height).coerceAtLeast(0)
        val fieldPlaceable = fields.single().measure(Constraints.fixed(constraints.maxWidth, fieldHeight))
        layout(constraints.maxWidth, height) {
            headerPlaceable.place(0, 0)
            fieldPlaceable.place(0, headerPlaceable.height)
            bottomPlaceable.place(0, height - bottomPlaceable.height)
        }
    }
}
