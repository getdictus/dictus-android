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
        assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(false, isDictusKeyboard = false, isAwaitingAutoFocus = false))
        // The last selected keyboard does not matter while none is up.
        assertEquals(FirstDictationStage.BeforeTap, FirstDictationStage.of(false, isDictusKeyboard = true, isAwaitingAutoFocus = false))
    }

    @Test
    fun `while the auto-focus is pending the keyboard is opening`() {
        assertEquals(FirstDictationStage.Opening, FirstDictationStage.of(false, isDictusKeyboard = false, isAwaitingAutoFocus = true))
        assertEquals(FirstDictationStage.Opening, FirstDictationStage.of(false, isDictusKeyboard = true, isAwaitingAutoFocus = true))
    }

    @Test
    fun `a keyboard up shows which one it is`() {
        for (awaiting in listOf(true, false)) {
            assertEquals(FirstDictationStage.OtherKeyboard, FirstDictationStage.of(true, isDictusKeyboard = false, isAwaitingAutoFocus = awaiting))
            assertEquals(FirstDictationStage.DictusKeyboard, FirstDictationStage.of(true, isDictusKeyboard = true, isAwaitingAutoFocus = awaiting))
        }
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
