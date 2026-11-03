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
    fun `missing preferences resolve as the service and the keyboard resolve them`() {
        assertEquals(
            // The stored default is the resolver's to decide ("follow" once #292 lands).
            DictationContext("auto", TranscriptionLanguage.resolve(null), "fr", DictationSource.APP),
            DictationContext.from(emptyPreferences(), sttLanguage = "auto", source = DictationSource.APP),
        )
    }
}
