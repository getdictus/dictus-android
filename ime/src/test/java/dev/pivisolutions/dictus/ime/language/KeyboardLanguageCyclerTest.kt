package dev.pivisolutions.dictus.ime.language

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardLanguageCyclerTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `cycle persists registry order while preserving layout and transcription language`() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(tempFolder.root, "cycle.preferences_pb") },
        )
        store.edit { preferences ->
            preferences[PreferenceKeys.KEYBOARD_LANGUAGE] = "fr"
            preferences[PreferenceKeys.KEYBOARD_LAYOUT] = "azerty"
            preferences[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "de"
        }

        assertEquals(SupportedLanguage.ENGLISH, cycleKeyboardLanguage(store))
        assertEquals(SupportedLanguage.SPANISH, cycleKeyboardLanguage(store))
        assertEquals(SupportedLanguage.GERMAN, cycleKeyboardLanguage(store))
        assertEquals(SupportedLanguage.FRENCH, cycleKeyboardLanguage(store))

        val persisted = store.data.first()
        assertEquals("fr", persisted[PreferenceKeys.KEYBOARD_LANGUAGE])
        assertEquals("azerty", persisted[PreferenceKeys.KEYBOARD_LAYOUT])
        assertEquals("de", persisted[PreferenceKeys.TRANSCRIPTION_LANGUAGE])
    }
}
