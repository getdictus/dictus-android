package dev.pivisolutions.dictus.core.service

import dev.pivisolutions.dictus.core.whisper.TextPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

/** An empty dictation is "no words detected", never text to insert (#153). */
class TranscriptionOutcomeTest {

    @Test
    fun `empty engine output is no words detected`() {
        val processed = TextPostProcessor.process("")

        assertEquals(TranscriptionOutcome.NoWordsDetected, TranscriptionOutcome.fromProcessedText(processed))
    }

    @Test
    fun `whitespace-only engine output is no words detected`() {
        val processed = TextPostProcessor.process(" \n\t ")

        assertEquals(TranscriptionOutcome.NoWordsDetected, TranscriptionOutcome.fromProcessedText(processed))
    }

    @Test
    fun `blank text is no words detected even without post-processing`() {
        assertEquals(TranscriptionOutcome.NoWordsDetected, TranscriptionOutcome.fromProcessedText("   "))
    }

    @Test
    fun `non-empty engine output is returned as text`() {
        val processed = TextPostProcessor.process(" Bonjour ")

        assertEquals(TranscriptionOutcome.Text("Bonjour. "), TranscriptionOutcome.fromProcessedText(processed))
    }
}
