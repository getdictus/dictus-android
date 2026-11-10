package dev.pivisolutions.dictus.core.whisper

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Non-speech markers written on silence are never inserted as text (#161, iOS #768). */
class NonSpeechMarkersTest {

    private fun outcomeOf(raw: String) =
        TranscriptionOutcome.fromProcessedText(TextPostProcessor.process(NonSpeechMarkers.spokenText(raw)))

    // --- Rule 1: markers and punctuation only are no words ---

    @Test
    fun `French and English markers alone in both bracket types are no words`() {
        listOf(
            "(musique)", "[Musique].", "[Music]", "(music)", "[BLANK_AUDIO]", "(rires) [Applaudissements]",
            "[Musique] (musique) [Music].", " [Silence] ",
        ).forEach { assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it)) }
    }

    @Test
    fun `an unknown marker alone is no words, like iOS`() {
        listOf("[MUSIC PLAYING]", "(voir page 3)").forEach {
            assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it))
        }
    }

    @Test
    fun `punctuation or whitespace only is no words`() {
        listOf("...", " ... ", "…", ".", "?!", " \n\t ").forEach {
            assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it))
        }
    }

    // --- Rule 2: in mixed text only known markers go ---

    @Test
    fun `a known marker is dropped from mixed text`() {
        assertEquals("Bonjour ça va", NonSpeechMarkers.spokenText("Bonjour [Musique] ça va"))
        assertEquals("Bonjour à tous.", NonSpeechMarkers.spokenText("[Musique]. Bonjour à tous."))
        assertEquals("See you tomorrow.", NonSpeechMarkers.spokenText("See you tomorrow [Music]."))
        assertEquals("On se voit demain.", NonSpeechMarkers.spokenText("On se voit demain. [Musique]"))
        assertEquals(TranscriptionOutcome.Text("Bonjour ça va. "), outcomeOf("Bonjour [Musique] ça va"))
    }

    @Test
    fun `an unknown parenthesis or bracket in mixed text is kept`() {
        listOf("Il est parti (voir page 3).", "Bonjour [note] ça va", "Bonjour [Musique douce] ça va").forEach {
            assertEquals(it, NonSpeechMarkers.spokenText(it))
        }
    }

    @Test
    fun `markers are matched case-insensitively`() {
        assertEquals("Bonjour", NonSpeechMarkers.spokenText("Bonjour [MUSIQUE]"))
        assertEquals("Hello everyone", NonSpeechMarkers.spokenText("Hello (Music) everyone"))
        assertEquals("Ok", NonSpeechMarkers.spokenText("Ok [blank_audio]"))
    }

    @Test
    fun `German and Spanish markers are dropped from mixed text`() {
        assertEquals("Guten Tag", NonSpeechMarkers.spokenText("Guten Tag (Geräusch)"))
        assertEquals("Guten Tag", NonSpeechMarkers.spokenText("Guten [Musik] Tag"))
        assertEquals("Hola.", NonSpeechMarkers.spokenText("Hola [Música]."))
        assertEquals("Hola amigos", NonSpeechMarkers.spokenText("Hola (risas) amigos"))
        listOf("[Lachen]", "(Applaus)", "[Stille]", "(aplausos)", "[Silencio]", "(Ruido)").forEach {
            assertEquals(it, "Ok", NonSpeechMarkers.spokenText("Ok $it"))
        }
    }

    @Test
    fun `accented markers match in any case but need their accent, like iOS`() {
        assertEquals("Guten Tag", NonSpeechMarkers.spokenText("Guten Tag [GERÄUSCH]"))
        assertEquals("Hola", NonSpeechMarkers.spokenText("Hola (MÚSICA)"))
        assertEquals("Hola (musica)", NonSpeechMarkers.spokenText("Hola (musica)"))
        assertEquals("Guten Tag [Gerausch]", NonSpeechMarkers.spokenText("Guten Tag [Gerausch]"))
    }

    @Test
    fun `a decomposed accent still matches`() {
        assertEquals("Hola", NonSpeechMarkers.spokenText("Hola [Mu\u0301sica]"))
        assertEquals("Guten Tag", NonSpeechMarkers.spokenText("Guten Tag (GERA\u0308USCH)"))
    }

    @Test
    fun `whitespace and punctuation inside a known marker are tolerated`() {
        assertEquals("Bonjour à tous", NonSpeechMarkers.spokenText("Bonjour [ Rires... ] à tous"))
        assertEquals("Hello.", NonSpeechMarkers.spokenText("Hello (- music -)."))
        assertEquals("Bonjour [Rires et cris] à tous", NonSpeechMarkers.spokenText("Bonjour [Rires et cris] à tous"))
    }

    // --- Asterisks (Whisper Small on iPhone: « *musique* », « *Bruit de chute* ») ---

    @Test
    fun `an asterisk marker alone is no words, known or not`() {
        listOf("*musique*", "*Musique*.", "*Bruit de chute*", "*musique* *Bruit de chute*").forEach {
            assertEquals(it, TranscriptionOutcome.NoWordsDetected, outcomeOf(it))
        }
    }

    @Test
    fun `a known word between asterisks is dropped from mixed text`() {
        assertEquals("Bonjour à tous", NonSpeechMarkers.spokenText("Bonjour *musique* à tous"))
        assertEquals("Bonjour.", NonSpeechMarkers.spokenText("*Rires...* Bonjour."))
        assertEquals("Bonjour monde", NonSpeechMarkers.spokenText("Bonjour*Music*monde"))
        assertEquals("Il tombe *Bruit de chute* ici", NonSpeechMarkers.spokenText("Il tombe *Bruit de chute* ici"))
    }

    @Test
    fun `a multiplication stays text`() {
        assertEquals("2 * 3 * 4", NonSpeechMarkers.spokenText("2 * 3 * 4"))
        assertEquals(TranscriptionOutcome.Text("2 * 3 * 4. "), outcomeOf("2 * 3 * 4"))
    }

    @Test
    fun `asterisks with a space right inside or a line break are not a marker`() {
        assertEquals("Bonjour * musique * à tous", NonSpeechMarkers.spokenText("Bonjour * musique * à tous"))
        assertEquals(TranscriptionOutcome.Text("* musique *. "), outcomeOf("* musique *"))
        assertEquals("Bonjour *musique\n* à tous", NonSpeechMarkers.spokenText("Bonjour *musique\n* à tous"))
        assertEquals(TranscriptionOutcome.Text("*mu\nsique*. "), outcomeOf("*mu\nsique*"))
    }

    // --- Rule 3: an unclosed marker is not a marker ---

    @Test
    fun `an unclosed marker is not filtered`() {
        listOf("[Musique", "Bonjour (music").forEach { assertEquals(it, NonSpeechMarkers.spokenText(it)) }
        assertEquals(TranscriptionOutcome.Text("[Musique. "), outcomeOf("[Musique"))
    }

    // --- Rule 4: glued words and whitespace ---

    @Test
    fun `words glued to a marker stay apart`() {
        assertEquals("Bonjour monde", NonSpeechMarkers.spokenText("Bonjour[Music]monde"))
    }

    @Test
    fun `no double space and no space before punctuation`() {
        assertEquals("Bonjour ça va", NonSpeechMarkers.spokenText("Bonjour  [Musique]  ça va"))
        assertEquals("Bonjour, ça va", NonSpeechMarkers.spokenText("Bonjour [Musique] , ça va"))
        assertEquals("Bonjour. Ça va", NonSpeechMarkers.spokenText("Bonjour [Musique]. Ça va"))
    }

    // --- Rule 5: a leading ellipsis is the speaker's ---

    @Test
    fun `a leading ellipsis in mixed text is kept`() {
        assertEquals("... bonjour", NonSpeechMarkers.spokenText("... bonjour [Musique]"))
        assertEquals("... bonjour", NonSpeechMarkers.spokenText("... bonjour"))
        assertEquals(TranscriptionOutcome.Text("... bonjour. "), outcomeOf("... bonjour (music)"))
    }

    // --- Normal text ---

    @Test
    fun `normal text is unchanged`() {
        listOf("Bonjour, ça va ?", "Hello  world", "La musique est forte.", "Etc...", "« Oui », dit-il.").forEach {
            assertEquals(it, NonSpeechMarkers.spokenText(it))
        }
        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcomeOf(" Bonjour "))
    }
}
