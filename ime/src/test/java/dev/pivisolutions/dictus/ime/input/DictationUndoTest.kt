package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.input.DictationUndo.Decision
import dev.pivisolutions.dictus.ime.input.DictationUndo.RefusalReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationUndoTest {

    // --- Safety rule ---

    @Test
    fun `verify deletes exactly the insertion when it ends the text before the caret`() {
        assertEquals(
            Decision.Delete(13),
            DictationUndo.verify("Déjà là. Bonjour Pierre. ", "Bonjour Pierre. ".takeLast(13)),
        )
        assertEquals(
            Decision.Delete("Bonjour. ".length),
            DictationUndo.verify("Texte tapé Bonjour. ", "Bonjour. "),
        )
    }

    @Test
    fun `verify accepts a field holding only the insertion`() {
        assertEquals(Decision.Delete(9), DictationUndo.verify("Bonjour. ", "Bonjour. "))
    }

    @Test
    fun `verify refuses when the text before the caret no longer ends with the insertion`() {
        assertEquals(
            Decision.Refused(RefusalReason.SUFFIX_MISMATCH),
            DictationUndo.verify("Bonjour. x", "Bonjour. "),
        )
    }

    @Test
    fun `verify refuses a window shorter than the insertion`() {
        assertEquals(
            Decision.Refused(RefusalReason.SUFFIX_MISMATCH),
            DictationUndo.verify("jour. ", "Bonjour. "),
        )
    }

    @Test
    fun `verify refuses when the editor returns no context`() {
        assertEquals(Decision.Refused(RefusalReason.NO_CONTEXT), DictationUndo.verify(null, "Bonjour. "))
        assertEquals(Decision.Refused(RefusalReason.NO_CONTEXT), DictationUndo.verify("", "Bonjour. "))
    }

    @Test
    fun `verify refuses an empty insertion`() {
        assertEquals(Decision.Refused(RefusalReason.NOTHING_PENDING), DictationUndo.verify("abc", ""))
    }

    // --- Offer state ---

    @Test
    fun `recording an insertion makes undo available`() {
        val undo = DictationUndo()
        assertFalse(undo.isAvailable.value)
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        assertTrue(undo.isAvailable.value)
    }

    @Test
    fun `empty insertion is never offered`() {
        val undo = DictationUndo()
        undo.recordInsertion("", cursorAfter = 0)
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `the insertion's own selection echo keeps the offer`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 14)
        undo.onSelectionChanged(14, 14)
        undo.onSelectionChanged(14, 14)
        assertTrue(undo.isAvailable.value)
    }

    @Test
    fun `a caret move withdraws the offer`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 14)
        undo.onSelectionChanged(13, 13)
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `a non-empty selection withdraws the offer`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 14)
        undo.onSelectionChanged(5, 14)
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `unknown caret is learned from the first echo, then any other move withdraws`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = null)
        undo.onSelectionChanged(20, 20)
        assertTrue(undo.isAvailable.value)
        undo.onSelectionChanged(21, 21)
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `clear withdraws the offer (keystroke, new dictation, finish input)`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        undo.clear()
        assertFalse(undo.isAvailable.value)
        assertEquals(Decision.Refused(RefusalReason.NOTHING_PENDING), undo.consume { "Bonjour. " })
    }

    @Test
    fun `a new dictation replaces the previous one`() {
        val undo = DictationUndo()
        undo.recordInsertion("Un. ", cursorAfter = 4)
        undo.recordInsertion("Deux. ", cursorAfter = 10)
        assertEquals(Decision.Delete(6), undo.consume { "Un. Deux. " })
    }

    // --- Consume ---

    @Test
    fun `consume reads exactly the insertion length and deletes it`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 20)
        var requested = -1
        val decision = undo.consume { n ->
            requested = n
            "Déjà tapé. Bonjour. ".takeLast(n)
        }
        assertEquals(9, requested)
        assertEquals(Decision.Delete(9), decision)
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `consume refuses and withdraws when the field changed`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        assertEquals(Decision.Refused(RefusalReason.SUFFIX_MISMATCH), undo.consume { "autre chose" })
        assertFalse(undo.isAvailable.value)
    }

    @Test
    fun `consume twice deletes only once`() {
        val undo = DictationUndo()
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        assertEquals(Decision.Delete(9), undo.consume { "Bonjour. " })
        assertEquals(Decision.Refused(RefusalReason.NOTHING_PENDING), undo.consume { "Bonjour. " })
    }

    @Test
    fun `decisions never carry the inserted text`() {
        val undo = DictationUndo()
        undo.recordInsertion("secret dicté. ", cursorAfter = 14)
        val decision = undo.consume { "secret dicté. " }
        assertFalse(decision.toString().contains("secret"))
    }
}
