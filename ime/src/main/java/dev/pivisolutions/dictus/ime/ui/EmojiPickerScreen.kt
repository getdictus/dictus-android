package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView

/**
 * Emoji picker screen that replaces the keyboard area.
 *
 * Uses AndroidView to host the official EmojiPickerView (View-based)
 * inside the Compose-in-IME host. Height is fixed at 310.dp to match
 * the keyboard total height, preventing IME window resize.
 *
 * @param onEmojiSelected Callback with the emoji string to insert via InputConnection.
 * @param modifier Optional modifier.
 */
@Composable
fun EmojiPickerScreen(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            EmojiPickerView(context).apply {
                emojiGridColumns = 8
                setOnEmojiPickedListener { emojiViewItem ->
                    onEmojiSelected(emojiViewItem.emoji)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp),
    )
}
