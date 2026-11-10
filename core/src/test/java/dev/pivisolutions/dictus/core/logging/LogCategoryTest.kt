package dev.pivisolutions.dictus.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogCategoryTest {

    private fun category(className: String) = LogCategory.forClass("dev.pivisolutions.dictus.$className")

    @Test
    fun `the mic-tap path maps to one category per stage`() {
        assertEquals(LogCategory.IME, category("ime.DictusImeService"))
        assertEquals(LogCategory.SERVICE, category("service.DictationService"))
        assertEquals(LogCategory.AUDIO, category("service.AudioCaptureManager"))
        assertEquals(LogCategory.STT, category("service.DictationTranscriber"))
        assertEquals(LogCategory.STT, category("service.WhisperProvider"))
        assertEquals(LogCategory.STT, category("service.SttProviderSlot"))
        assertEquals(LogCategory.STT, category("whisper.WhisperContext"))
        assertEquals(LogCategory.STT, category("asr.ParakeetProvider"))
    }

    @Test
    fun `the two Bound to DictationService lines no longer share a category`() {
        assertEquals(LogCategory.LIFECYCLE, category("MainActivity"))
        assertEquals(LogCategory.IME, category("ime.DictusImeService"))
    }

    @Test
    fun `typing, models and screens have their own categories`() {
        assertEquals(LogCategory.KEYBOARD, category("ime.ui.KeyboardScreen"))
        assertEquals(LogCategory.KEYBOARD, category("ime.suggestion.DictionaryEngine"))
        assertEquals(LogCategory.MODEL, category("service.ModelDownloader"))
        assertEquals(LogCategory.MODEL, category("service.ResumableDownload"))
        assertEquals(LogCategory.MODEL, category("models.ModelsViewModel"))
        assertEquals(LogCategory.UI, category("recording.RecordingScreenKt"))
        assertEquals(LogCategory.LIFECYCLE, category("DictusApplication"))
        assertEquals(LogCategory.APP, LogCategory.forClass("com.example.Other"))
    }

    @Test
    fun `only category names are read as an explicit category tag`() {
        assertEquals(LogCategory.INSERT, LogCategory.fromTag("INSERT"))
        assertNull(LogCategory.fromTag("DictusImeService"))
        assertNull(LogCategory.fromTag(null))
    }
}
