package dev.pivisolutions.dictus.ime.ui

import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * Emoji picker screen that replaces the keyboard area.
 *
 * Uses AndroidView to host the official EmojiPickerView (View-based)
 * inside the Compose-in-IME host.
 *
 * Includes a bottom toolbar with ABC (return to keyboard) and Delete buttons,
 * matching iOS emoji picker layout where the user can quickly switch back
 * or delete characters without closing the picker first.
 *
 * Height: 266.dp (picker) + 42.dp (bottom row) = 308.dp total, fitting within keyboard area
 * so the toolbar stays visible above the system navigation bar.
 *
 * @param onEmojiSelected Callback with the emoji string to insert via InputConnection.
 * @param onReturnToKeyboard Callback to dismiss picker and return to keyboard (ABC button).
 * @param onDeleteBackward Callback to delete one character (backspace button).
 * @param isDarkTheme Whether the app is using dark theme (to style the EmojiPickerView).
 * @param modifier Optional modifier.
 */
@Composable
fun EmojiPickerScreen(
    onEmojiSelected: (String) -> Unit,
    onReturnToKeyboard: () -> Unit,
    onDeleteBackward: () -> Unit,
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 308dp total: fits within keyboard area so the ABC/Delete toolbar
    // stays visible above the system navigation bar (globe icon row)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(308.dp),
    ) {
        // Emoji picker grid — fills available space above the bottom toolbar
        AndroidView(
            factory = { context ->
                // Force dark/light background on the EmojiPickerView itself
                // ContextThemeWrapper controls the widget's internal theming
                val themedContext = ContextThemeWrapper(
                    context,
                    if (isDarkTheme) {
                        android.R.style.Theme_DeviceDefault
                    } else {
                        android.R.style.Theme_DeviceDefault_Light
                    },
                )
                EmojiPickerView(themedContext).apply {
                    emojiGridColumns = 8
                    setOnEmojiPickedListener { emojiViewItem ->
                        onEmojiSelected(emojiViewItem.emoji)
                    }
                    // Force the View's background to match our theme surface color
                    setBackgroundColor(surfaceColor.toArgb())
                }
            },
            update = { view ->
                // Update background when theme changes
                view.setBackgroundColor(surfaceColor.toArgb())
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Bottom toolbar: ABC button (left) + Delete button (right)
        // Positioned at bottom, just above the system navigation bar (globe icon).
        // Matches iOS layout: ABC and backspace always accessible during emoji entry.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ABC button — returns to keyboard
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalDictusColors.current.keyBackground)
                    .clickable(onClick = onReturnToKeyboard)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ABC",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Delete/backspace button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalDictusColors.current.keySpecialBackground)
                    .clickable(onClick = onDeleteBackward)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u232B", // ⌫ delete symbol
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
