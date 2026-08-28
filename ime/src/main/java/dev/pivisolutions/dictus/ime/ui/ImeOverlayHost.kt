package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hosts an IME surface whose height is defined only by [content].
 *
 * The overlay matches the measured content bounds without participating in the
 * parent's measurement. This is required in an InputMethodService, where a
 * fillMaxSize root expands the IME window to the full display height.
 */
@Composable
internal fun ImeOverlayHost(
    content: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        content()
        Box(modifier = Modifier.matchParentSize()) {
            overlay()
        }
    }
}
