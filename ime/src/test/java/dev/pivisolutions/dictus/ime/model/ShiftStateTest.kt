package dev.pivisolutions.dictus.ime.model

import dev.pivisolutions.dictus.ime.input.AutoCapMode
import dev.pivisolutions.dictus.ime.input.AutoCapitalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftStateTest {
    private val normal = ShiftState()
    private val autoShift = ShiftState(isShifted = true)
    private val manualShift = ShiftState(isShifted = true, isManual = true)
    private val capsLock = ShiftState(isShifted = true, isCapsLock = true)

    @Test
    fun `sentence start arms an automatic shift`() {
        assertEquals(autoShift, normal.afterAutoCapCheck(shouldCapitalize = true))
    }

    @Test
    fun `automatic shift drops after one letter and is not re-armed mid sentence`() {
        val afterLetter = autoShift.afterLetterTyped()
        assertEquals(normal, afterLetter)
        assertEquals(normal, afterLetter.afterAutoCapCheck(shouldCapitalize = false))
    }

    @Test
    fun `automatic shift is withdrawn when the cursor leaves a sentence start`() {
        assertEquals(normal, autoShift.afterAutoCapCheck(shouldCapitalize = false))
    }

    @Test
    fun `manual shift mid sentence survives the check and drops after one letter`() {
        val tapped = normal.afterShiftTap(isDoubleTap = false)
        assertEquals(manualShift, tapped)
        assertEquals(manualShift, tapped.afterAutoCapCheck(shouldCapitalize = false))
        assertEquals(normal, tapped.afterLetterTyped())
    }

    @Test
    fun `tapping shift off at a sentence start wins until the editor changes`() {
        assertEquals(normal, autoShift.afterShiftTap(isDoubleTap = false))
    }

    @Test
    fun `caps lock is never cleared by autocapitalization or by typing`() {
        assertEquals(capsLock, capsLock.afterAutoCapCheck(shouldCapitalize = false))
        assertEquals(capsLock, capsLock.afterAutoCapCheck(shouldCapitalize = true))
        assertEquals(capsLock, capsLock.afterLetterTyped())
    }

    @Test
    fun `double tap toggles caps lock from an automatic shift, and off again`() {
        val firstTap = autoShift.afterShiftTap(isDoubleTap = false)
        val locked = firstTap.afterShiftTap(isDoubleTap = true)
        assertTrue(locked.isCapsLock)
        assertTrue(locked.isShifted)
        assertEquals(normal, locked.afterShiftTap(isDoubleTap = true))
        assertEquals(normal, locked.afterShiftTap(isDoubleTap = false))
    }

    @Test
    fun `typing a sentence then deleting back to its start re-arms shift`() {
        val mode = AutoCapMode.SENTENCES
        var state = normal
        // Field start, type "B" then "on. " as the keyboard would, then backspace once.
        state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, ""))
        assertTrue(state.isShifted)
        state = state.afterLetterTyped().afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, "B"))
        assertFalse(state.isShifted)
        state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, "Bon. "))
        assertTrue(state.isShifted)
        state = state.afterLetterTyped().afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, "Bon. J"))
        assertFalse(state.isShifted)
        state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, "Bon. "))
        assertTrue(state.isShifted)
        state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, "Bon."))
        assertFalse(state.isShifted)
        state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(mode, ""))
        assertTrue(state.isShifted)
    }

    @Test
    fun `characters mode re-arms shift after every letter`() {
        var state = normal
        repeat(3) {
            state = state.afterAutoCapCheck(AutoCapitalization.shouldCapitalize(AutoCapMode.CHARACTERS, "AB"))
            assertTrue(state.isShifted)
            assertFalse(state.isCapsLock)
            state = state.afterLetterTyped()
        }
    }
}
