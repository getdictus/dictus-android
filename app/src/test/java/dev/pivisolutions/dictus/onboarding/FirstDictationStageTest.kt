package dev.pivisolutions.dictus.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of iOS `FirstDictationStageTests` (#199). */
class FirstDictationStageTest {

    // Stage

    @Test
    fun `no keyboard asks for a tap`() {
        assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(false, isImeVisible = false, isDictusKeyboard = false, isAwaitingAutoFocus = false))
        // The last selected keyboard does not matter while none is up.
        assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(false, isImeVisible = false, isDictusKeyboard = true, isAwaitingAutoFocus = false))
    }

    @Test
    fun `while the auto-focus is pending the keyboard is opening`() {
        assertEquals(FirstDictationStage.Opening, FirstDictationStage.of(false, isImeVisible = false, isDictusKeyboard = false, isAwaitingAutoFocus = true))
        assertEquals(FirstDictationStage.Opening, FirstDictationStage.of(false, isImeVisible = false, isDictusKeyboard = true, isAwaitingAutoFocus = true))
    }

    @Test
    fun `a keyboard up shows which one it is`() {
        for (awaiting in listOf(true, false)) {
            assertEquals(FirstDictationStage.OtherKeyboard, FirstDictationStage.of(true, isImeVisible = true, isDictusKeyboard = false, isAwaitingAutoFocus = awaiting))
            assertEquals(FirstDictationStage.DictusKeyboard, FirstDictationStage.of(true, isImeVisible = true, isDictusKeyboard = true, isAwaitingAutoFocus = awaiting))
        }
    }

    @Test
    fun `a focused field without a keyboard never shows the mic banner`() {
        // #379: the Pixel 4 showed the cursor and the blue outline, no keyboard, and the
        // banner pointing at the Dictus mic.
        for (dictus in listOf(true, false)) {
            assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(true, isImeVisible = false, isDictusKeyboard = dictus, isAwaitingAutoFocus = false))
            assertEquals(FirstDictationStage.Opening, FirstDictationStage.of(true, isImeVisible = false, isDictusKeyboard = dictus, isAwaitingAutoFocus = true))
        }
    }

    @Test
    fun `a keyboard window over an unfocused field is not this page's keyboard`() {
        assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(false, isImeVisible = true, isDictusKeyboard = true, isAwaitingAutoFocus = false))
    }

    // Asking for the keyboard (arrival, back to the front)

    @Test
    fun `the page asks for the keyboard when none is up`() {
        assertTrue(FirstDictationStage.asksForKeyboard(isKeyboardUp = false, userClosedKeyboard = false, isLeaving = false))
    }

    @Test
    fun `the page does not ask when the keyboard is up`() {
        assertFalse(FirstDictationStage.asksForKeyboard(isKeyboardUp = true, userClosedKeyboard = false, isLeaving = false))
    }

    @Test
    fun `the page does not ask again once the user closed the keyboard`() {
        assertFalse(FirstDictationStage.asksForKeyboard(isKeyboardUp = false, userClosedKeyboard = true, isLeaving = false))
    }

    @Test
    fun `the page does not ask while leaving`() {
        assertFalse(FirstDictationStage.asksForKeyboard(isKeyboardUp = false, userClosedKeyboard = false, isLeaving = true))
    }

    @Test
    fun `a keyboard going down on the page in front was closed by the user`() {
        assertTrue(FirstDictationStage.keyboardClosedByUser(wasKeyboardUp = true, isKeyboardUp = false, isPageInFront = true))
    }

    @Test
    fun `a keyboard going down on the way out was not closed by the user`() {
        // Home, the app switcher, or the keyboard picker over the page.
        assertFalse(FirstDictationStage.keyboardClosedByUser(wasKeyboardUp = true, isKeyboardUp = false, isPageInFront = false))
    }

    @Test
    fun `no keyboard going down is no closing`() {
        assertFalse(FirstDictationStage.keyboardClosedByUser(wasKeyboardUp = false, isKeyboardUp = false, isPageInFront = true))
        assertFalse(FirstDictationStage.keyboardClosedByUser(wasKeyboardUp = true, isKeyboardUp = true, isPageInFront = true))
    }

    // Auto-focus fallback

    @Test
    fun `the keyboard is still awaited before one second`() {
        assertFalse(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 0, isKeyboardUp = false))
        assertFalse(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 999, isKeyboardUp = false))
    }

    @Test
    fun `the auto-focus has failed at and after one second`() {
        assertTrue(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 1_000, isKeyboardUp = false))
        assertTrue(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 5_000, isKeyboardUp = false))
    }

    @Test
    fun `a keyboard up never fails the auto-focus`() {
        assertFalse(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 1_000, isKeyboardUp = true))
        assertFalse(FirstDictationStage.autoFocusHasFailed(msSinceRequest = 60_000, isKeyboardUp = true))
    }

    // Continue fallback

    @Test
    fun `continue is hidden before fifteen seconds`() {
        assertFalse(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 0, hasSeenDictusKeyboard = false))
        assertFalse(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 14_900, hasSeenDictusKeyboard = false))
    }

    @Test
    fun `continue shows at and after fifteen seconds`() {
        assertTrue(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 15_000, hasSeenDictusKeyboard = false))
        assertTrue(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 120_000, hasSeenDictusKeyboard = false))
    }

    @Test
    fun `continue needs another keyboard to have been up`() {
        assertFalse(FirstDictationStage.showsContinue(msSinceOtherKeyboard = null, hasSeenDictusKeyboard = false))
    }

    @Test
    fun `continue is hidden once Dictus has been seen`() {
        assertFalse(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 15_000, hasSeenDictusKeyboard = true))
        assertFalse(FirstDictationStage.showsContinue(msSinceOtherKeyboard = 120_000, hasSeenDictusKeyboard = true))
    }

    // Completion

    @Test
    fun `text before the Dictus keyboard does not complete`() {
        assertFalse(FirstDictationStage.completes("Bonjour à tous", hasSeenDictusKeyboard = false))
    }

    @Test
    fun `three characters complete once Dictus has been up`() {
        assertTrue(FirstDictationStage.completes("oui", hasSeenDictusKeyboard = true))
        assertTrue(FirstDictationStage.completes("Bonjour à tous", hasSeenDictusKeyboard = true))
    }

    @Test
    fun `fewer than three characters do not complete`() {
        assertFalse(FirstDictationStage.completes("", hasSeenDictusKeyboard = true))
        assertFalse(FirstDictationStage.completes("ok", hasSeenDictusKeyboard = true))
        // Whitespace does not count.
        assertFalse(FirstDictationStage.completes("  a \n ", hasSeenDictusKeyboard = true))
    }
}
