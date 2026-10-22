package dev.pivisolutions.dictus.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionLanguageTest {

    @Test
    fun `settings offer auto, French, English, Spanish and German`() {
        assertEquals(listOf("auto", "fr", "en", "es", "de"), TranscriptionLanguage.SUPPORTED)
    }

    @Test
    fun `Spanish and German reach the engine unchanged`() {
        assertEquals("es", TranscriptionLanguage.engineLanguage("es"))
        assertEquals("de", TranscriptionLanguage.engineLanguage("de"))
    }

    @Test
    fun `French and English reach the engine unchanged`() {
        assertEquals("fr", TranscriptionLanguage.engineLanguage("fr"))
        assertEquals("en", TranscriptionLanguage.engineLanguage("en"))
    }

    @Test
    fun `auto lets whisper detect the language instead of forcing French`() {
        assertEquals("auto", TranscriptionLanguage.engineLanguage("auto"))
    }

    @Test
    fun `missing preference resolves to auto`() {
        assertEquals("auto", TranscriptionLanguage.resolve(null))
        assertEquals("auto", TranscriptionLanguage.engineLanguage(null))
    }

    @Test
    fun `unknown or corrupted preference resolves to auto, not French`() {
        listOf("", "zh", "it", "french", "xx-YY", "  ").forEach { stored ->
            assertEquals(stored, "auto", TranscriptionLanguage.engineLanguage(stored))
        }
    }

    @Test
    fun `stored value is normalized for case and whitespace`() {
        assertEquals("de", TranscriptionLanguage.resolve(" DE "))
        assertEquals("es", TranscriptionLanguage.resolve("Es"))
    }
}
