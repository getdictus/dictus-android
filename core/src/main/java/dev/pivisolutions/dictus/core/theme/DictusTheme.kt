package dev.pivisolutions.dictus.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
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
 * Custom Dictus color tokens that have no direct Material 3 equivalent.
 *
 * These colors vary between dark and light themes and are provided via
 * [LocalDictusColors] so that all UI composables respond to theme changes.
 *
 * WHY a separate data class (not more Material slots): Material 3 has a fixed
 * set of semantic color roles. Keyboard-specific colors (KeyBackground, KeyText)
 * and design-specific tokens (TextSecondary, BorderSubtle) don't map cleanly to
 * any Material role. A CompositionLocal keeps custom colors theme-aware without
 * misusing Material slots.
 */
@Immutable
data class DictusColorScheme(
    val textSecondary: Color,
    val borderSubtle: Color,
    val keyBackground: Color,
    val keyText: Color,
    val keySpecialBackground: Color,
    val iconBackground: Color,
)

/** Dark variant of custom Dictus colors. */
private val DictusDarkExtraColors = DictusColorScheme(
    textSecondary = DictusColors.TextSecondary,
    borderSubtle = DictusColors.BorderSubtle,
    keyBackground = DictusColors.KeyBackground,
    keyText = DictusColors.KeyText,
    keySpecialBackground = DictusColors.KeySpecialBackground,
    iconBackground = DictusColors.IconBackground,
)

/** Light variant of custom Dictus colors. */
private val DictusLightExtraColors = DictusColorScheme(
    textSecondary = DictusColors.LightTextSecondary,
    borderSubtle = DictusColors.LightBorderSubtle,
    keyBackground = DictusColors.LightKeyBackground,
    keyText = DictusColors.LightOnSurface,
    keySpecialBackground = DictusColors.LightKeySpecialBackground,
    iconBackground = DictusColors.LightBackground,
)

/**
 * CompositionLocal providing the current [DictusColorScheme].
 *
 * Access via `LocalDictusColors.current` inside any composable within [DictusTheme].
 */
val LocalDictusColors = staticCompositionLocalOf { DictusDarkExtraColors }

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
 * based on the requested ThemeMode. Also provides [LocalDictusColors] so
 * custom Dictus color tokens respond to theme changes.
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

    val extraColors = if (useDark) DictusDarkExtraColors else DictusLightExtraColors

    CompositionLocalProvider(LocalDictusColors provides extraColors) {
        MaterialTheme(
            colorScheme = if (useDark) DictusDarkColorScheme else DictusLightColorScheme,
            typography = DictusTypography,
            content = content,
        )
    }
}
