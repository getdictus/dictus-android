package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.pivisolutions.dictus.ui.onboarding.LocalOnboardingTopBarFold
import dev.pivisolutions.dictus.ui.onboarding.OnboardingMetrics

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
 * WHY THE PAGE SCROLLS WHEN EVEN THAT DOES NOT FIT (#355): on a phone in landscape the
 * keyboard leaves 50 to 120 dp, less than the bottom row and the field's minimum. The page
 * then folds the shell's top row, gives the field the whole height left and scrolls: the
 * title block above the field, the hint and « Passer » below it. It opens on the field.
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
    val topBarFold = LocalOnboardingTopBarFold.current
    val topBarHeight = with(LocalDensity.current) {
        (OnboardingMetrics.topBarTopPadding + OnboardingMetrics.topBarHeight).roundToPx()
    }
    val scrollState = rememberScrollState()
    var isScrolling by remember { mutableStateOf(false) }
    var fieldTop by remember { mutableIntStateOf(0) }

    LaunchedEffect(isScrolling, fieldTop) {
        if (isScrolling) scrollState.scrollTo(fieldTop)
    }
    DisposableEffect(topBarFold) {
        onDispose { topBarFold?.isFolded = false }
    }

    var pageHeight by remember { mutableIntStateOf(0) }
    Layout(
        contents = listOf(header, field, bottom),
        modifier = modifier
            // The scroll below hands the content an unbounded height: read the page's first.
            .layout { measurable, constraints ->
                pageHeight = constraints.maxHeight
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            // Content that fits is exactly the page's height, so the scroll has nothing to move.
            .verticalScroll(scrollState),
    ) { (headers, fields, bottoms), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = pageHeight)
        // Its natural height: clamped to a short page, its hint's size animation never settled.
        val bottomPlaceable = bottoms.single().measure(loose.copy(maxHeight = Constraints.Infinity))
        // Judged on the page under the top row, folded or not, so folding it keeps the decision.
        val underTopBar = pageHeight - if (topBarFold?.isFolded == true) topBarHeight else 0
        val scrolls = underTopBar < bottomPlaceable.height + fieldMinHeight.roundToPx()
        if (topBarFold != null && topBarFold.isFolded != scrolls) topBarFold.isFolded = scrolls
        isScrolling = scrolls

        if (scrolls) {
            // Whole: the page's scroll reaches the title, the header's own has nothing to move.
            val headerHeight = headers.single().maxIntrinsicHeight(constraints.maxWidth)
            val headerPlaceable = headers.single().measure(loose.copy(maxHeight = headerHeight))
            val fieldPlaceable = fields.single().measure(Constraints.fixed(constraints.maxWidth, pageHeight))
            fieldTop = headerPlaceable.height
            layout(constraints.maxWidth, headerPlaceable.height + pageHeight + bottomPlaceable.height) {
                headerPlaceable.place(0, 0)
                fieldPlaceable.place(0, headerPlaceable.height)
                bottomPlaceable.place(0, headerPlaceable.height + pageHeight)
            }
        } else {
            val headerMax = (pageHeight - bottomPlaceable.height - fieldMinHeight.roundToPx()).coerceAtLeast(0)
            val headerPlaceable = headers.single().measure(loose.copy(maxHeight = headerMax))
            val fieldHeight = pageHeight - headerPlaceable.height - bottomPlaceable.height
            val fieldPlaceable = fields.single().measure(Constraints.fixed(constraints.maxWidth, fieldHeight))
            layout(constraints.maxWidth, pageHeight) {
                headerPlaceable.place(0, 0)
                fieldPlaceable.place(0, headerPlaceable.height)
                bottomPlaceable.place(0, pageHeight - bottomPlaceable.height)
            }
        }
    }
}
