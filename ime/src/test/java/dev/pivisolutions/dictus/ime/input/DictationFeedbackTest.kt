package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keyboard wiring of a finished dictation and of the field changes that withdraw it (#153). */
class DictationFeedbackTest {

    private val notice = NoWordsNotice(TestScope())
    private val undo = DictationUndo()
    private val feedback = DictationFeedback(undo, notice)
    private val inserted = mutableListOf<String>()

    @Test
    fun `no words shows the notice and inserts nothing`() {
        feedback.onOutcome(TranscriptionOutcome.NoWordsDetected) { inserted += it }

        assertTrue(notice.isVisible.value)
        assertEquals(emptyList<String>(), inserted)
    }

    @Test
    fun `text is inserted once without the notice`() {
        feedback.onOutcome(TranscriptionOutcome.Text("Bonjour. ")) { inserted += it }

        assertEquals(listOf("Bonjour. "), inserted)
        assertFalse(notice.isVisible.value)
    }

    @Test
    fun `failure neither inserts nor shows the notice`() {
        feedback.onOutcome(TranscriptionOutcome.Failed) { inserted += it }

        assertEquals(emptyList<String>(), inserted)
        assertFalse(notice.isVisible.value)
    }

    @Test
    fun `withdraw hides the notice, as typing, a mic tap or a new field do`() {
        feedback.onOutcome(TranscriptionOutcome.NoWordsDetected) { inserted += it }

        feedback.withdraw()

        assertFalse(notice.isVisible.value)
    }

    @Test
    fun `withdraw also drops the undo offer`() {
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        assertTrue(undo.isAvailable.value)

        feedback.withdraw()

        assertFalse(undo.isAvailable.value)
    }
}
