package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which slot is bold: the word Space writes, and none when Space writes nothing (#388). */
class SuggestionBarBoldSlotTest {
    @Test
    fun `the first completion or correction is bold`() {
        assertEquals(1, boldSlot(SuggestionPresentationMode.COMPLETION, undoOffer = null, keepsTypedWord = false))
    }

    @Test
    fun `corrections offered for a kept word have no bold slot`() {
        // iOS `.candidates`: `"irning" | irving | inning`, none bold, Space keeps `irning`.
        assertNull(boldSlot(SuggestionPresentationMode.COMPLETION, undoOffer = null, keepsTypedWord = true))
        assertEquals(
            listOf("irning", "irving", "inning"),
            suggestionSlots("irning", listOf("irving", "inning"), SuggestionPresentationMode.COMPLETION, null),
        )
    }

    @Test
    fun `predictions and the undo chip have no bold slot`() {
        assertNull(boldSlot(SuggestionPresentationMode.PREDICTION, undoOffer = null, keepsTypedWord = false))
        assertNull(boldSlot(SuggestionPresentationMode.COMPLETION, AutocorrectUndoOffer("helo", 1L), false))
    }
}
