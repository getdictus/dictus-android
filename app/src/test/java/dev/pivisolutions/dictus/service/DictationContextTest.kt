package dev.pivisolutions.dictus.service

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.core.service.DictationSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the debug log is told about a take, read from the preferences the service read (#295). */
class DictationContextTest {

    @Test
    fun `the stored language and the keyboard language are read from the snapshot`() {
        val prefs = preferencesOf(
            PreferenceKeys.TRANSCRIPTION_LANGUAGE to "de",
            PreferenceKeys.KEYBOARD_LANGUAGE to "en",
        )

        assertEquals(
            DictationContext("de", "de", "en", DictationSource.KEYBOARD),
            DictationContext.from(prefs, sttLanguage = "de", source = DictationSource.KEYBOARD),
        )
    }

    @Test
    fun `missing preferences follow the keyboard, French by default`() {
        assertEquals(
            DictationContext("fr", TranscriptionLanguage.FOLLOW, "fr", DictationSource.APP),
            DictationContext.from(emptyPreferences(), sttLanguage = "fr", source = DictationSource.APP),
        )
    }

    @Test
    fun `in follow mode the engine language and the logged keyboard language are the keyboard's`() {
        // The Pixel check of #292: FR then DE keyboard, sttLanguageCode fr then de.
        for (keyboard in listOf("fr", "de")) {
            val prefs = preferencesOf(
                PreferenceKeys.TRANSCRIPTION_LANGUAGE to TranscriptionLanguage.FOLLOW,
                PreferenceKeys.KEYBOARD_LANGUAGE to keyboard,
            )

            assertEquals(
                DictationContext(keyboard, TranscriptionLanguage.FOLLOW, keyboard, DictationSource.KEYBOARD),
                DictationContext.from(prefs, DictationLanguage.engineLanguage(prefs), DictationSource.KEYBOARD),
            )
        }
    }
}
