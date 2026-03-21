package dev.pivisolutions.dictus.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dictus dark color scheme using Material 3.
 *
 * Maps Dictus brand colors to Material 3 semantic roles:
 * - primary/onPrimary: accent blue for interactive elements
 * - background/surface: dark navy tones from the iOS design
 * - error: red used for recording state indicator
 * - tertiary: purple for smart/AI mode indicator
 */
private val DictusDarkColorScheme = darkColorScheme(
    primary = DictusColors.Accent,
    onPrimary = Color.White,
    primaryContainer = DictusColors.AccentHighlight,
    background = DictusColors.Background,
    surface = DictusColors.Surface,
    onBackground = DictusColors.OnBackground,
    onSurface = DictusColors.OnSurface,
    error = DictusColors.Recording,
    tertiary = DictusColors.SmartMode,
)

/**
 * Dictus application theme.
 *
 * Wraps content in Material 3 theming with the Dictus dark color scheme
 * and custom typography. This is always-dark -- Dictus does not support
 * light mode, matching the iOS app behavior.
 */
@Composable
fun DictusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DictusDarkColorScheme,
        typography = DictusTypography,
        content = content,
    )
}
