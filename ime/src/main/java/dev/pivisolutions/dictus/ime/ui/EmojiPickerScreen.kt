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
 * Height: 264.dp (picker) + 46.dp (bottom row) = 310.dp total, matching keyboard height.
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
    Column(modifier = modifier.fillMaxWidth()) {
        // Emoji picker grid (264.dp — leaves 46.dp for bottom toolbar)
        AndroidView(
            factory = { context ->
                // Wrap context with appropriate theme to match app theme, not system theme.
                // Uses platform Material themes which are always available on Android 8+.
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
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(264.dp),
        )

        // Bottom toolbar: ABC button (left) + Delete button (right)
        // Matches iOS emoji picker layout for quick keyboard return and deletion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ABC",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Delete/backspace button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalDictusColors.current.keySpecialBackground)
                    .clickable(onClick = onDeleteBackward)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u232B", // ⌫ delete symbol
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                )
            }
        }
    }
}
