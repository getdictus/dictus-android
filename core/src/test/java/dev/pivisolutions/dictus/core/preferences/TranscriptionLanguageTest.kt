package dev.pivisolutions.dictus.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionLanguageTest {

    @Test
    fun `settings offer follow keyboard first, then auto, French, English, Spanish and German`() {
        assertEquals(listOf("follow", "auto", "fr", "en", "es", "de"), TranscriptionLanguage.SUPPORTED)
    }

    @Test
    fun `Spanish and German reach the engine unchanged, whatever the keyboard`() {
        assertEquals("es", TranscriptionLanguage.engineLanguage("es", keyboardLanguage = "fr"))
        assertEquals("de", TranscriptionLanguage.engineLanguage("de", keyboardLanguage = "fr"))
    }

    @Test
    fun `French and English reach the engine unchanged, whatever the keyboard`() {
        assertEquals("fr", TranscriptionLanguage.engineLanguage("fr", keyboardLanguage = "de"))
        assertEquals("en", TranscriptionLanguage.engineLanguage("en", keyboardLanguage = "de"))
    }

    @Test
    fun `auto lets whisper detect the language instead of following the keyboard`() {
        assertEquals("auto", TranscriptionLanguage.engineLanguage("auto", keyboardLanguage = "de"))
    }

    @Test
    fun `follow hands the keyboard language to the engine`() {
        assertEquals("fr", TranscriptionLanguage.engineLanguage("follow", keyboardLanguage = "fr"))
        assertEquals("de", TranscriptionLanguage.engineLanguage("follow", keyboardLanguage = "de"))
    }

    // Migration (#186): only a missing or unknown value moves to follow, as on iOS.

    @Test
    fun `missing preference resolves to follow keyboard`() {
        assertEquals("follow", TranscriptionLanguage.resolve(null))
        assertEquals("de", TranscriptionLanguage.engineLanguage(null, keyboardLanguage = "de"))
    }

    @Test
    fun `explicit auto stays auto`() {
        assertEquals("auto", TranscriptionLanguage.resolve("auto"))
    }

    @Test
    fun `explicit languages are kept`() {
        listOf("fr", "en", "es", "de").forEach { stored ->
            assertEquals(stored, TranscriptionLanguage.resolve(stored))
        }
    }

    @Test
    fun `unknown or corrupted preference resolves to follow keyboard`() {
        listOf("", "zh", "it", "french", "xx-YY", "  ").forEach { stored ->
            assertEquals(stored, "follow", TranscriptionLanguage.resolve(stored))
            assertEquals(stored, "es", TranscriptionLanguage.engineLanguage(stored, keyboardLanguage = "es"))
        }
    }

    @Test
    fun `stored value is normalized for case and whitespace`() {
        assertEquals("de", TranscriptionLanguage.resolve(" DE "))
        assertEquals("es", TranscriptionLanguage.resolve("Es"))
        assertEquals("auto", TranscriptionLanguage.resolve(" AUTO"))
    }
}
