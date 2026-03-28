package dev.pivisolutions.dictus.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme modes supported by Dictus.
 *
 * - DARK: Always use the dark color scheme (default, matches iOS original behavior).
 * - LIGHT: Always use the light color scheme.
 * - AUTO: Follow the system dark/light preference (isSystemInDarkTheme).
 */
enum class ThemeMode { DARK, LIGHT, AUTO }

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
 * Dictus light color scheme using Material 3.
 *
 * Maps Dictus light palette tokens to Material 3 semantic roles.
 * Accent blue remains the same to preserve brand identity.
 */
private val DictusLightColorScheme = lightColorScheme(
    primary = DictusColors.Accent,
    onPrimary = Color.White,
    primaryContainer = DictusColors.AccentHighlight,
    background = DictusColors.LightBackground,
    surface = DictusColors.LightSurface,
    onBackground = DictusColors.LightOnBackground,
    onSurface = DictusColors.LightOnSurface,
    error = DictusColors.Recording,
    tertiary = DictusColors.SmartMode,
)

/**
 * Dictus application theme.
 *
 * Wraps content in Material 3 theming with the appropriate color scheme
 * based on the requested ThemeMode. Supports DARK (default), LIGHT, and AUTO
 * (follows system dark/light preference) modes.
 *
 * @param themeMode The desired theme mode. Defaults to DARK for backward compatibility.
 * @param content The composable content to wrap with the theme.
 */
@Composable
fun DictusTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) DictusDarkColorScheme else DictusLightColorScheme,
        typography = DictusTypography,
        content = content,
    )
}
