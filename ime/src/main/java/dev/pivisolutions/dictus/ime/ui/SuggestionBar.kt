package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors
import androidx.compose.material3.MaterialTheme

/**
 * Gboard-style 3-slot suggestion bar above the keyboard.
 *
 * Center slot (index 1) is bold as the primary suggestion.
 * Vertical separators divide the slots. Height is 36.dp.
 *
 * @param suggestions List of up to 3 suggestion strings.
 * @param onSuggestionSelected Callback with selected suggestion text.
 * @param modifier Optional modifier.
 */
@Composable
fun SuggestionBar(
    suggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pad to exactly 3 slots (empty string = empty slot, not tappable)
    val slots = (suggestions + listOf("", "", "")).take(3)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, text ->
            // Slot
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(
                        if (text.isNotEmpty()) {
                            Modifier.clickable { onSuggestionSelected(text) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }

            // Vertical separator between slots (not after the last slot)
            if (index < slots.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(LocalDictusColors.current.borderSubtle),
                )
            }
        }
    }
}
