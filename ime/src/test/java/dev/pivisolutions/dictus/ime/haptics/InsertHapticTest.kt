package dev.pivisolutions.dictus.ime.haptics

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.ime.input.DictationFeedback
import dev.pivisolutions.dictus.ime.input.DictationNotice
import dev.pivisolutions.dictus.ime.input.DictationUndo
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

/** The insertion haptic of a keyboard dictation (#238), wired as the IME wires it. */
class InsertHapticTest {

    private val feedback = DictationFeedback(DictationUndo(), DictationNotice(TestScope()))
    private var played = 0
    private val haptic = InsertHaptic { played++ }

    private fun finish(outcome: TranscriptionOutcome, committed: Boolean = true, hapticsEnabled: Boolean = true) {
        feedback.onOutcome(outcome) { haptic.onInserted(committed, hapticsEnabled) }
    }

    @Test
    fun `committed text plays the haptic once`() {
        finish(TranscriptionOutcome.Text("Bonjour. "))

        assertEquals(1, played)
    }

    @Test
    fun `setting off plays nothing`() {
        finish(TranscriptionOutcome.Text("Bonjour. "), hapticsEnabled = false)

        assertEquals(0, played)
    }

    @Test
    fun `text the editor did not take plays nothing`() {
        finish(TranscriptionOutcome.Text("Bonjour. "), committed = false)

        assertEquals(0, played)
    }

    @Test
    fun `no words and failure play nothing`() {
        finish(TranscriptionOutcome.NoWordsDetected)
        finish(TranscriptionOutcome.Failed)

        assertEquals(0, played)
    }
}
