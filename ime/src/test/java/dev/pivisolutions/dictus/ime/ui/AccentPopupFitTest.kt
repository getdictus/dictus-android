package dev.pivisolutions.dictus.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentPopupFitTest {

    @Test
    fun `a short list keeps one key-wide cell per option`() {
        assertEquals(44f, AccentPopupFit.optionWidth(cellWidth = 44f, availableWidth = 385f, count = 5))
    }

    @Test
    fun `a long list narrows its options to fill the row exactly`() {
        // English a, 11 options, on a 393 dp phone less a 4 dp inset on each side.
        assertEquals(35f, AccentPopupFit.optionWidth(cellWidth = 44f, availableWidth = 385f, count = 11))
        assertEquals(385f, 11 * AccentPopupFit.optionWidth(44f, 385f, 11), 0.001f)
    }

    @Test
    fun `an unmeasured screen keeps the cell width`() {
        assertEquals(44f, AccentPopupFit.optionWidth(cellWidth = 44f, availableWidth = 0f, count = 11))
    }
}
