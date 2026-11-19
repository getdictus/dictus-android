package dev.pivisolutions.dictus.ime.model

import dev.pivisolutions.dictus.ime.model.KeyboardLayer.LETTERS
import dev.pivisolutions.dictus.ime.model.KeyboardLayer.NUMBERS
import dev.pivisolutions.dictus.ime.model.KeyboardLayer.SYMBOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolsRunTest {
    private val capsLock = ShiftState(isShifted = true, isCapsLock = true)

    private fun tap123(run: SymbolsRun = SymbolsRun(), isCapsLock: Boolean = false) =
        run.afterLayerSwitch(from = LETTERS, to = NUMBERS, isCapsLock = isCapsLock)

    @Test
    fun `space after a character typed on 123 returns to letters`() {
        val run = tap123().afterCharacterTyped(NUMBERS)

        val result = run.onSpace(NUMBERS)

        assertNotNull(result)
        assertEquals(ShiftState(), result!!.shiftState)
        assertFalse(result.run.typedOnSymbols)
    }

    @Test
    fun `space right after tapping 123 stays on the page`() {
        assertNull(tap123().onSpace(NUMBERS))
    }

    @Test
    fun `the 123 to #+= toggle keeps the run`() {
        val typedThenToggled = tap123()
            .afterCharacterTyped(NUMBERS)
            .afterLayerSwitch(from = NUMBERS, to = SYMBOLS, isCapsLock = false)
        assertNotNull(typedThenToggled.onSpace(SYMBOLS))

        val toggledThenTyped = tap123()
            .afterLayerSwitch(from = NUMBERS, to = SYMBOLS, isCapsLock = false)
            .afterCharacterTyped(SYMBOLS)
        assertNotNull(toggledThenTyped.onSpace(SYMBOLS))

        val toggledOnly = tap123()
            .afterLayerSwitch(from = NUMBERS, to = SYMBOLS, isCapsLock = false)
            .afterLayerSwitch(from = SYMBOLS, to = NUMBERS, isCapsLock = false)
        assertNull(toggledOnly.onSpace(NUMBERS))
    }

    @Test
    fun `Return and other keys leave the run armed for the next space`() {
        // Return types no character and is not a space: the page and the run stay as they were.
        val run = tap123().afterCharacterTyped(NUMBERS)
        assertEquals(run, run.afterLayerSwitch(from = NUMBERS, to = NUMBERS, isCapsLock = false))
        assertNotNull(run.onSpace(NUMBERS))
    }

    @Test
    fun `a second space after the return stays on letters`() {
        val returned = tap123().afterCharacterTyped(NUMBERS).onSpace(NUMBERS)!!
        assertNull(returned.run.onSpace(LETTERS))
    }

    @Test
    fun `space on letters never switches the page`() {
        val run = SymbolsRun().afterCharacterTyped(LETTERS)
        assertFalse(run.typedOnSymbols)
        assertNull(run.onSpace(LETTERS))
    }

    @Test
    fun `caps lock on when 123 was tapped is restored`() {
        val run = tap123(isCapsLock = true).afterCharacterTyped(NUMBERS)

        assertEquals(capsLock, run.onSpace(NUMBERS)!!.shiftState)
    }

    @Test
    fun `a new tap on 123 starts a new run`() {
        val returnedToLetters = tap123(isCapsLock = true).afterCharacterTyped(NUMBERS)
            .afterLayerSwitch(from = NUMBERS, to = LETTERS, isCapsLock = true)

        val fresh = tap123(returnedToLetters, isCapsLock = false)

        assertNull(fresh.onSpace(NUMBERS))
        assertEquals(ShiftState(), fresh.afterCharacterTyped(NUMBERS).onSpace(NUMBERS)!!.shiftState)
    }

    @Test
    fun `without caps lock autocapitalization decides after the return`() {
        // A manual shift tapped before 123 is cleared, as iOS clears isManualShift.
        val shiftState = tap123().afterCharacterTyped(NUMBERS).onSpace(NUMBERS)!!.shiftState

        assertEquals(ShiftState(isShifted = true), shiftState.afterAutoCapCheck(shouldCapitalize = true))
        assertEquals(ShiftState(), shiftState.afterAutoCapCheck(shouldCapitalize = false))
    }

    @Test
    fun `caps lock survives the autocapitalization check after the return`() {
        val shiftState = tap123(isCapsLock = true).afterCharacterTyped(NUMBERS).onSpace(NUMBERS)!!.shiftState

        assertEquals(capsLock, shiftState.afterAutoCapCheck(shouldCapitalize = false))
    }

    @Test
    fun `opening the keyboard resets the run`() {
        val typed = tap123().afterCharacterTyped(NUMBERS)

        val reopened = typed.afterKeyboardShown()

        assertNull(reopened.onSpace(NUMBERS))
        assertNotNull(reopened.afterCharacterTyped(NUMBERS).onSpace(NUMBERS))
    }
}
