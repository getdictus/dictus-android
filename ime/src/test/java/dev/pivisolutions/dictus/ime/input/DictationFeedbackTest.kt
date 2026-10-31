package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.ime.input.DictationNotice.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keyboard wiring of a finished dictation and of the field changes that withdraw it (#153, #182). */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationFeedbackTest {

    private val scope = TestScope()
    private val notice = DictationNotice(scope)
    private val undo = DictationUndo()
    private val feedback = DictationFeedback(undo, notice)
    private val inserted = mutableListOf<String>()

    @Test
    fun `no words shows the notice and inserts nothing`() {
        feedback.onOutcome(TranscriptionOutcome.NoWordsDetected) { inserted += it }

        assertEquals(Message.NoWordsDetected, notice.message.value)
        assertEquals(emptyList<String>(), inserted)
    }

    @Test
    fun `text is inserted once without the notice`() {
        feedback.onOutcome(TranscriptionOutcome.Text("Bonjour. ")) { inserted += it }

        assertEquals(listOf("Bonjour. "), inserted)
        assertNull(notice.message.value)
    }

    @Test
    fun `failure inserts nothing, shows the failure notice, then the bar returns to normal`() {
        feedback.onOutcome(TranscriptionOutcome.Failed) { inserted += it }

        assertEquals(emptyList<String>(), inserted)
        assertEquals(Message.Failed, notice.message.value)

        scope.advanceTimeBy(DictationNotice.DISPLAY_DURATION_MS - 1)
        scope.runCurrent()
        assertEquals(Message.Failed, notice.message.value)

        scope.advanceTimeBy(1)
        scope.runCurrent()
        assertNull(notice.message.value)
    }

    @Test
    fun `withdraw hides the notice, as typing, a mic tap or a new field do`() {
        feedback.onOutcome(TranscriptionOutcome.NoWordsDetected) { inserted += it }

        feedback.withdraw()

        assertNull(notice.message.value)
    }

    @Test
    fun `withdraw hides the failure notice too`() {
        feedback.onOutcome(TranscriptionOutcome.Failed) { inserted += it }

        feedback.withdraw()

        assertNull(notice.message.value)
    }

    @Test
    fun `withdraw also drops the undo offer`() {
        undo.recordInsertion("Bonjour. ", cursorAfter = 9)
        assertTrue(undo.isAvailable.value)

        feedback.withdraw()

        assertFalse(undo.isAvailable.value)
    }
}
