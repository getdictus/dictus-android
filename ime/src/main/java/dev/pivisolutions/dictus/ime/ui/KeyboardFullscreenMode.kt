package dev.pivisolutions.dictus.ime.ui

import android.view.inputmethod.EditorInfo

/**
 * Whether the keyboard takes the whole screen, with Android's extract view above the keys
 * showing the text being typed (#355).
 *
 * Android's default ([android.inputmethodservice.InputMethodService.onEvaluateFullscreenMode])
 * goes fullscreen in any landscape, unless the field sets `IME_FLAG_NO_FULLSCREEN`. That was
 * made for keyboards that filled a landscape phone. Since #127 the keyboard leaves room for the
 * app in landscape, so the app's own field is the better view of the text. Rule, in order:
 * 1. Never where Android's default would not go fullscreen: portrait, `IME_FLAG_NO_FULLSCREEN`.
 * 2. Never when the field sets `IME_FLAG_NO_EXTRACT_UI`. Android still goes fullscreen then but
 *    hides the extract view, so the screen above the keys was left blank, with no field and no
 *    text: Google Messages on Android 12 (#339). AOSP LatinIME reads the flag the same way
 *    (`LatinIME.onEvaluateFullscreenMode`).
 * 3. Otherwise only when the keyboard leaves less than [MIN_ROOM_ABOVE_KEYBOARD_DP] above it:
 *    the app could not show even its focused field there, and the extract view can.
 *
 * @param systemDefault what Android's default decides for this field and orientation.
 * @param roomAboveKeyboardDp the screen's height between the status bar and the top of the
 *   keyboard (toolbar included, navigation bar band below the keys included in the keyboard).
 */
internal fun usesFullscreenMode(systemDefault: Boolean, imeOptions: Int, roomAboveKeyboardDp: Float): Boolean =
    systemDefault &&
        imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI == 0 &&
        roomAboveKeyboardDp < MIN_ROOM_ABOVE_KEYBOARD_DP

/**
 * One line of text field: Android's minimum touch target (48 dp, Material and the platform's
 * accessibility guidelines), the height of the smallest field an app can show and the user tap.
 */
internal const val MIN_ROOM_ABOVE_KEYBOARD_DP = 48f
