package dev.pivisolutions.dictus.ime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The keyboard's height, from the screen it is on (#127).
 *
 * Everything is in dp, so the density is already applied: a 1080 px wide screen at 440 dpi and a
 * 720 px one at 294 dpi are both 393 dp wide and get the same keyboard.
 *
 * Rule, in order:
 * 1. A row is 13.5 % of the screen's narrow side: wider keys can be taller. A 393 dp phone gets
 *    53 dp rows, near iOS's 216 pt grid for four rows (KeyboardHeightProvider.swift,
 *    getdictus/dictus-ios develop).
 * 2. The keyboard (toolbar + rows) takes at most 40 % of the height in portrait and 50 % in
 *    landscape, as AOSP LatinIME caps its keyboard at 46 % without its suggestion strip
 *    (`config_max_keyboard_height`).
 * 3. A row is never under 40 dp (36 dp keys) nor over 64 dp. On a phone in landscape the floor
 *    wins: 216 dp, about 55 % of a 393 dp tall screen, close to iOS's 227 pt of 390 pt.
 *
 * The toolbar stays 52 dp, as iOS. The navigation bar is not part of this height: the surface
 * adds its inset below the keys. The system font size scales the labels, not the keys, and only
 * up to [MAX_LABEL_FONT_SCALE] so a label never overflows its key.
 */
internal data class KeyboardSizing(
    val rowHeightDp: Float,
    val labelFontScale: Float,
) {
    /** The key grid, and the emoji picker in its place: four rows plus the grid's 2 + 2 dp. */
    val keyAreaHeightDp: Float get() = rowHeightDp * KEY_ROWS + KEY_AREA_PADDING_DP

    /** Toolbar plus key area, the same in every state (#139). */
    val totalHeightDp: Float get() = TOOLBAR_HEIGHT_DP + keyAreaHeightDp

    val rowHeight: Dp get() = rowHeightDp.dp
    val keyAreaHeight: Dp get() = keyAreaHeightDp.dp
    val totalHeight: Dp get() = totalHeightDp.dp
}

internal const val MIN_ROW_HEIGHT_DP = 40f
internal const val MAX_ROW_HEIGHT_DP = 64f
internal const val MAX_LABEL_FONT_SCALE = 1.3f

private const val KEY_ROWS = 4
private const val KEY_AREA_PADDING_DP = 4f
private const val TOOLBAR_HEIGHT_DP = 52f
private const val ROW_SHARE_OF_NARROW_SIDE = 0.135f
private const val MAX_SHARE_PORTRAIT = 0.40f
private const val MAX_SHARE_LANDSCAPE = 0.50f

internal fun keyboardSizing(screenWidthDp: Float, screenHeightDp: Float, fontScale: Float): KeyboardSizing {
    val isLandscape = screenWidthDp > screenHeightDp
    val preferredRow = ROW_SHARE_OF_NARROW_SIDE * minOf(screenWidthDp, screenHeightDp)
    val maxShare = if (isLandscape) MAX_SHARE_LANDSCAPE else MAX_SHARE_PORTRAIT
    val cappedRow = (maxShare * screenHeightDp - TOOLBAR_HEIGHT_DP - KEY_AREA_PADDING_DP) / KEY_ROWS
    val row = minOf(preferredRow, cappedRow, MAX_ROW_HEIGHT_DP).coerceAtLeast(MIN_ROW_HEIGHT_DP)
    return KeyboardSizing(rowHeightDp = row, labelFontScale = minOf(fontScale, MAX_LABEL_FONT_SCALE))
}

/** Read by the keys, the emoji picker and the dictation screens; the IME root provides it. */
internal val LocalKeyboardSizing = staticCompositionLocalOf {
    // Previews and tests without a provider: the Pixel 4 in portrait.
    keyboardSizing(screenWidthDp = 393f, screenHeightDp = 830f, fontScale = 1f)
}

/** The sizing for the current configuration; the IME recreates its view on rotation. */
@Composable
internal fun rememberKeyboardSizing(): KeyboardSizing {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp, configuration.fontScale) {
        keyboardSizing(
            screenWidthDp = configuration.screenWidthDp.toFloat(),
            screenHeightDp = configuration.screenHeightDp.toFloat(),
            fontScale = configuration.fontScale,
        )
    }
}
