package dev.pivisolutions.dictus.core.whisper

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Non-speech tags written on silence are never inserted as text (#161). */
class NonSpeechMarkersTest {

    private fun outcomeOf(raw: String) =
        TranscriptionOutcome.fromProcessedText(TextPostProcessor.process(NonSpeechMarkers.strip(raw)))

    // --- Tags alone: no words ---

    @Test
    fun `French tags alone are no words`() {
        listOf(
            "(musique)", "[Musique]", "[Musique].", " [MUSIQUE] ", "(Silence)", "[Applaudissements]",
            "(rires)", "*Rires*", "[Bruit]", "(bruits de pas)", "[Musique douce]", "(inaudible)",
        ).forEach { assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it)) }
    }

    @Test
    fun `English tags alone are no words`() {
        listOf(
            "[Music]", "(music)", "[BLANK_AUDIO]", "[blank audio]", "(silence)", "[Applause]",
            "(Laughter)", "*laughs*", "[Noise]", "[no speech]", "(Background noise)", "[MUSIC PLAYING]",
        ).forEach { assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it)) }
    }

    @Test
    fun `tags with trailing punctuation are no words`() {
        listOf("[Musique]...", "(music)!", "[Music]. [Music].", "(silence)…", "[Applause], [Laughter]?")
            .forEach { assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it)) }
    }

    @Test
    fun `ellipsis or punctuation alone is no words`() {
        listOf("...", " ... ", "…", ".", "?!", "- -", "♪").forEach {
            assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it))
        }
    }

    @Test
    fun `whitespace alone is no words`() {
        assertEquals(TranscriptionOutcome.NoWordsDetected, outcomeOf(" \n\t "))
    }

    // --- Mixed text: words kept, tags dropped ---

    @Test
    fun `a tag inside a sentence is dropped`() {
        assertEquals("Bonjour ça va", NonSpeechMarkers.strip("Bonjour [Musique] ça va"))
    }

    @Test
    fun `a leading or trailing tag is dropped with its punctuation`() {
        assertEquals("Bonjour.", NonSpeechMarkers.strip("[Musique]. Bonjour."))
        assertEquals("Hello", NonSpeechMarkers.strip("Hello (music)."))
        assertEquals("Bonjour. Ça va ?", NonSpeechMarkers.strip("Bonjour. (rires) Ça va ?"))
    }

    @Test
    fun `mixed text keeps its words as text`() {
        assertEquals(TranscriptionOutcome.Text("Bonjour ça va. "), outcomeOf("Bonjour [Musique] ça va"))
    }

    // --- Real text: unchanged ---

    @Test
    fun `text without tags is unchanged`() {
        listOf("Bonjour, ça va ?", "Hello  world", "La musique est forte.", "Etc...").forEach {
            assertEquals(it, NonSpeechMarkers.strip(it))
        }
    }

    @Test
    fun `a dictated parenthesis that is not a tag is kept`() {
        listOf("(voir page 3)", "Il est parti (silencieux).", "[note]", "(musicien)").forEach {
            assertEquals(it, NonSpeechMarkers.strip(it))
        }
    }

    @Test
    fun `normal text is post-processed as before`() {
        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcomeOf(" Bonjour "))
        assertEquals(TranscriptionOutcome.Text("Etc... "), outcomeOf("Etc..."))
    }
}
