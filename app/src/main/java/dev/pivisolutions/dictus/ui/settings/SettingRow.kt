package dev.pivisolutions.dictus.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * A Settings row: [label] on the left, an optional [trailing] value or control on the right, both
 * centred vertically (#350).
 *
 * A Row with a weighted label measures the trailing value first, at its full width: with a large
 * font « Suivre la langue du clavier » left « Langue » a few dp and cut it into « La / n ».
 * Here the trailing value keeps its width only while the label still fits beside it, and is
 * never pushed below half the row or its longest word, and the label always keeps room for its
 * own longest word; whatever does not fit wraps on more lines, and the row grows from its 56 dp
 * to hold them instead of clipping them.
 *
 * When even the two longest words cannot sit side by side (font scale 2.0 and above), the label
 * goes above the value: the label on the full width, the value under it on the right (#359).
 */
@Composable
internal fun SettingRow(
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Layout(
        contents = listOf(label, trailing ?: {}),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { (labelMeasurables, trailingMeasurables), constraints ->
        val width = constraints.maxWidth
        val labelWidth = labelMeasurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val trailingNatural = trailingMeasurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        // The longest words, so neither side breaks inside one ("Automatiqu / e").
        val labelWord = labelMeasurables.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
        val trailingWord = trailingMeasurables.maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0
        if (stacksLabel(width, labelWord, trailingWord)) {
            val labelPlaceables = labelMeasurables.map { it.measure(Constraints(maxWidth = width)) }
            // Its natural width, wrapping only past the row's.
            val stackedMax = minOf(trailingNatural, width)
            val trailingPlaceables = trailingMeasurables.map { it.measure(Constraints(maxWidth = stackedMax)) }
            val labelHeight = labelPlaceables.maxOfOrNull { it.height } ?: 0
            val trailingTop = labelHeight + StackGap.roundToPx()
            val contentHeight = trailingTop + (trailingPlaceables.maxOfOrNull { it.height } ?: 0)
            val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            return@Layout layout(width, height) {
                labelPlaceables.forEach { it.place(0, 0) }
                trailingPlaceables.forEach { it.place(width - it.width, trailingTop) }
            }
        }
        val trailingMax = trailingWidth(width, labelWidth, labelWord, trailingNatural, trailingWord)

        val trailingPlaceables = trailingMeasurables.map { it.measure(Constraints(maxWidth = trailingMax)) }
        val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
        val labelMax = (width - trailingWidth).coerceAtLeast(0)
        val labelPlaceables = labelMeasurables.map { it.measure(Constraints(maxWidth = labelMax)) }

        val contentHeight = (labelPlaceables + trailingPlaceables).maxOfOrNull { it.height } ?: 0
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            labelPlaceables.forEach { it.place(0, (height - it.height) / 2) }
            trailingPlaceables.forEach { it.place(width - it.width, (height - it.height) / 2) }
        }
    }
}

/** Space between a label and the value stacked under it (#359). */
private val StackGap = 4.dp

/**
 * Whether a Settings row puts its label above its trailing value: when the label's longest word
 * ([labelWord]) and the value's ([trailingWord]) do not fit side by side in [available], one of
 * them would break inside a word, « Automati / que » at font scale 2.0 (#359). A row without a
 * value never stacks.
 */
internal fun stacksLabel(available: Int, labelWord: Int, trailingWord: Int): Boolean =
    trailingWord > 0 && labelWord + trailingWord > available

/**
 * Width of a Settings row's trailing value: its natural [trailing] width while the label's
 * natural [label] width still fits in [available] beside it, otherwise half of [available], and
 * no narrower than its longest word ([trailingWord]) (#350).
 *
 * The label's longest word ([labelWord]) is always left to the label: when the two longest words
 * cannot sit side by side, the value is the one that breaks inside a word, never the label.
 */
internal fun trailingWidth(available: Int, label: Int, labelWord: Int, trailing: Int, trailingWord: Int): Int =
    minOf(trailing, available - labelWord, maxOf(available - label, available / 2, trailingWord))
        .coerceAtLeast(0)
