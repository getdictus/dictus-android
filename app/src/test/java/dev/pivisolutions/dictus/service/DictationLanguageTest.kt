package dev.pivisolutions.dictus.service

import androidx.datastore.preferences.core.preferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationLanguageTest {

    @Test
    fun `follow mode on the French keyboard dictates in French`() {
        val prefs = preferencesOf(
            PreferenceKeys.TRANSCRIPTION_LANGUAGE to "follow",
            PreferenceKeys.KEYBOARD_LANGUAGE to "fr",
        )
        assertEquals("fr", DictationLanguage.engineLanguage(prefs))
    }

    @Test
    fun `follow mode on the German keyboard dictates in German`() {
        val prefs = preferencesOf(
            PreferenceKeys.TRANSCRIPTION_LANGUAGE to "follow",
            PreferenceKeys.KEYBOARD_LANGUAGE to "de",
        )
        assertEquals("de", DictationLanguage.engineLanguage(prefs))
    }

    @Test
    fun `follow mode without a keyboard language dictates in French, like the keyboard`() {
        val prefs = preferencesOf(PreferenceKeys.TRANSCRIPTION_LANGUAGE to "follow")
        assertEquals("fr", DictationLanguage.engineLanguage(prefs))
    }

    @Test
    fun `an install that never chose a dictation language follows the keyboard`() {
        val prefs = preferencesOf(PreferenceKeys.KEYBOARD_LANGUAGE to "de")
        assertEquals("de", DictationLanguage.engineLanguage(prefs))
    }

    @Test
    fun `explicit auto ignores the keyboard`() {
        val prefs = preferencesOf(
            PreferenceKeys.TRANSCRIPTION_LANGUAGE to "auto",
            PreferenceKeys.KEYBOARD_LANGUAGE to "de",
        )
        assertEquals("auto", DictationLanguage.engineLanguage(prefs))
    }

    @Test
    fun `an explicit language ignores the keyboard`() {
        val prefs = preferencesOf(
            PreferenceKeys.TRANSCRIPTION_LANGUAGE to "es",
            PreferenceKeys.KEYBOARD_LANGUAGE to "de",
        )
        assertEquals("es", DictationLanguage.engineLanguage(prefs))
    }
}
