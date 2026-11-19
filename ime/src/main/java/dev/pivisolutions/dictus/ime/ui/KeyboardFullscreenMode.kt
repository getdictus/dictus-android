package dev.pivisolutions.dictus.ime.ui

/**
 * Whether the keyboard takes the whole screen (#355).
 *
 * Android's default ([android.inputmethodservice.InputMethodService.onEvaluateFullscreenMode])
 * goes fullscreen in any landscape, unless the field sets `IME_FLAG_NO_FULLSCREEN`. In
 * fullscreen Android does not resize the app: it shows its extract view above the keys, or
 * nothing when the field sets `IME_FLAG_NO_EXTRACT_UI`. Google Messages sets that flag, so on
 * Android 12 the screen above the keys was left blank, with no field and no text (#339). That
 * default was made for keyboards that filled a landscape phone; since #127 Dictus leaves room
 * for the app. Rule:
 * 1. Never where Android's default would not go fullscreen: portrait, `IME_FLAG_NO_FULLSCREEN`.
 * 2. Never when the keyboard leaves at least [MIN_ROOM_ABOVE_KEYBOARD_DP] above it: the app is
 *    resized to that room and shows its own field there, extract view or not.
 * 3. Below it, Android's default stands. With an extract view, the text shows in it. With
 *    `IME_FLAG_NO_EXTRACT_UI` the app is not resized and keeps drawing its field over the
 *    keyboard itself: on a large-font Android 11 phone (~54 dp left), Messages shows its field
 *    that way, and resized to the room it covered the field with its toolbar.
 *
 * @param systemDefault what Android's default decides for this field and orientation.
 * @param roomAboveKeyboardDp the screen's height between the status bar and the top of the
 *   keyboard (the keyboard counting the navigation bar band under its keys).
 */
internal fun usesFullscreenMode(systemDefault: Boolean, roomAboveKeyboardDp: Float): Boolean =
    systemDefault && roomAboveKeyboardDp < MIN_ROOM_ABOVE_KEYBOARD_DP

/**
 * One single-line text field: the 56 dp of Material's text field container, the smallest room
 * in which an app can show the field being typed into.
 */
internal const val MIN_ROOM_ABOVE_KEYBOARD_DP = 56f
