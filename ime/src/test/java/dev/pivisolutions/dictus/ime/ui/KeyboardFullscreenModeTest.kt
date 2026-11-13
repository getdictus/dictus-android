package dev.pivisolutions.dictus.ime.ui

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the keyboard goes fullscreen (#355). The rooms are what the #339 audit's phones leave
 * above the 216 dp landscape keyboard once the status bar is out (`budget65` 120 dp, `fold-ferme`
 * 49 dp with its 48 dp navigation band), and a smaller screen, under the 48 dp floor.
 */
class KeyboardFullscreenModeTest {

    private val messages = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    private val plainField = EditorInfo.IME_ACTION_DONE

    @Test fun `a field that refuses the extract view never goes fullscreen, even with no room`() {
        // Google Messages on Android 12: fullscreen with the extract view hidden left the screen blank.
        assertFalse(usesFullscreenMode(systemDefault = true, imeOptions = messages, roomAboveKeyboardDp = 120f))
        assertFalse(usesFullscreenMode(systemDefault = true, imeOptions = messages, roomAboveKeyboardDp = 20f))
    }

    @Test fun `a phone in landscape keeps the app's field when the keyboard leaves it room`() {
        assertFalse(usesFullscreenMode(systemDefault = true, imeOptions = plainField, roomAboveKeyboardDp = 120f))
        assertFalse(usesFullscreenMode(systemDefault = true, imeOptions = plainField, roomAboveKeyboardDp = 49f))
    }

    @Test fun `below one field line the extract view shows the text`() {
        assertTrue(usesFullscreenMode(systemDefault = true, imeOptions = plainField, roomAboveKeyboardDp = 40f))
        assertTrue(usesFullscreenMode(systemDefault = true, imeOptions = plainField, roomAboveKeyboardDp = 0f))
    }

    @Test fun `never fullscreen where Android would not be, in portrait or with IME_FLAG_NO_FULLSCREEN`() {
        assertFalse(usesFullscreenMode(systemDefault = false, imeOptions = plainField, roomAboveKeyboardDp = 0f))
    }
}
