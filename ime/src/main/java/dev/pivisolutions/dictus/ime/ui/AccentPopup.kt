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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.ime.model.AccentMap

/**
 * Popup overlay showing accented character variants for a given base character.
 *
 * Appears above the long-pressed key as a horizontal row of selectable accent
 * options. Dismisses when an accent is selected or when tapping outside.
 *
 * Uses AccentMap to look up available accents for the character.
 */
@Composable
fun AccentPopup(
    char: Char,
    onAccentSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val accents = AccentMap.accentsFor(char) ?: return

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -60),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .shadow(elevation = 4.dp, shape = shape)
                .clip(shape)
                .background(DictusColors.Surface),
        ) {
            accents.forEach { accent ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            onAccentSelected(accent)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = accent,
                        color = DictusColors.KeyText,
                        fontSize = 20.sp,
                    )
                }
            }
        }
    }
}
