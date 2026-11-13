package dev.pivisolutions.dictus.ime.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the keyboard goes fullscreen (#355). The rooms are what the #339 audit's phones leave
 * above the landscape keyboard once the status bar is out: `budget65` 120 dp (where Android's
 * default left Google Messages blank), the Pixel 4 105 dp, and the large-font `police` profile
 * ~54 dp.
 */
class KeyboardFullscreenModeTest {

    @Test fun `a phone in landscape keeps the app's field when the keyboard leaves it room`() {
        // Android's default said fullscreen here: Messages on Android 12 went blank.
        assertFalse(usesFullscreenMode(systemDefault = true, roomAboveKeyboardDp = 120f))
        assertFalse(usesFullscreenMode(systemDefault = true, roomAboveKeyboardDp = 105f))
        assertFalse(usesFullscreenMode(systemDefault = true, roomAboveKeyboardDp = 56f))
    }

    @Test fun `below one text field Android's default stands`() {
        assertTrue(usesFullscreenMode(systemDefault = true, roomAboveKeyboardDp = 54f))
        assertTrue(usesFullscreenMode(systemDefault = true, roomAboveKeyboardDp = 0f))
    }

    @Test fun `never fullscreen where Android would not be, in portrait or with IME_FLAG_NO_FULLSCREEN`() {
        assertFalse(usesFullscreenMode(systemDefault = false, roomAboveKeyboardDp = 120f))
        assertFalse(usesFullscreenMode(systemDefault = false, roomAboveKeyboardDp = 0f))
    }
}
