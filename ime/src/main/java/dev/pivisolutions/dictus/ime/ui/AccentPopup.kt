package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors

/**
 * Accent strip content shown above a long-pressed key.
 *
 * This composable only renders the strip. Positioning is handled by the caller so
 * the popup can stay anchored to the pressed key.
 */
@Composable
fun AccentPopup(
    accents: List<String>,
    highlightedIndex: Int?,
    onAccentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accents.isEmpty()) return

    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(DictusColors.KeyBackground),
    ) {
        accents.forEachIndexed { index, accent ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (highlightedIndex == index) {
                            DictusColors.AccentHighlight.copy(alpha = 0.35f)
                        } else {
                            DictusColors.KeyBackground
                        }
                    )
                    .clickable { onAccentSelected(accent) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = accent,
                    color = DictusColors.KeyText,
                    fontSize = 22.sp,
                )
            }
        }
    }
}
