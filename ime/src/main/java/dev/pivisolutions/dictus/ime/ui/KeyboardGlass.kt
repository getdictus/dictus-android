package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * iOS `.dictusGlass(in:)` for the keyboard, without the blur.
 *
 * An IME window cannot sample the app drawn behind it, so the glass is a translucent fill and a
 * hairline edge. Values read off the iOS keyboard captures (develop fe220179): near-opaque white
 * in light mode, a faint lift with a light edge in dark mode.
 */
@Immutable
internal class KeyboardGlass(
    val fill: Color,
    val edge: Color,
    /** iOS `dictusPillIconSecondary`: the glyph labels the glass, it does not compete with it. */
    val iconSecondary: Color,
)

private val LightGlass = KeyboardGlass(
    fill = Color.White.copy(alpha = 0.85f),
    edge = Color.Black.copy(alpha = 0.08f),
    iconSecondary = Color(0xFF262626).copy(alpha = 0.5f),
)

private val DarkGlass = KeyboardGlass(
    fill = Color.White.copy(alpha = 0.06f),
    edge = Color.White.copy(alpha = 0.15f),
    iconSecondary = Color.White.copy(alpha = 0.5f),
)

@Composable
@ReadOnlyComposable
internal fun keyboardGlass(): KeyboardGlass =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkGlass else LightGlass

@Composable
internal fun Modifier.keyboardGlass(shape: Shape): Modifier {
    val glass = keyboardGlass()
    return background(glass.fill, shape).border(1.dp, glass.edge, shape)
}
