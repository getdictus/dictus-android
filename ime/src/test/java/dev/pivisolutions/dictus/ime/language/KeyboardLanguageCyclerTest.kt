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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardLanguageCyclerTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `cycle persists registry order while preserving transcription language`() = runTest {
        val store = store("cycle")
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

    /** #134: each step of the bar's cycle lands on that language's own layout. */
    @Test
    fun `cycle applies each language's layout and mirrors it to the legacy key`() = runTest {
        val store = store("layouts")
        store.edit { preferences ->
            preferences[PreferenceKeys.KEYBOARD_LANGUAGE] = "fr"
            preferences[PreferenceKeys.KEYBOARD_LAYOUT] = "azerty"
            preferences[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true
        }
        val seen = mutableListOf<Pair<SupportedLanguage, KeyboardLayout>>()
        repeat(4) {
            val language = cycleKeyboardLanguage(store)
            val preferences = store.data.first()
            val layout = KeyboardLayoutPreference.activeLayout(preferences)
            seen += language to layout
            assertEquals(layout.persistedValue, preferences[PreferenceKeys.KEYBOARD_LAYOUT])
        }

        assertEquals(
            listOf(
                SupportedLanguage.ENGLISH to KeyboardLayout.QWERTY,
                SupportedLanguage.SPANISH to KeyboardLayout.QWERTY,
                SupportedLanguage.GERMAN to KeyboardLayout.QWERTZ,
                SupportedLanguage.FRENCH to KeyboardLayout.AZERTY,
            ),
            seen,
        )
    }

    /** The freeze belongs to the language the user was typing on, not the one they switch to. */
    @Test
    fun `cycle migrates before moving the language`() = runTest {
        val store = store("migrate")
        store.edit { preferences ->
            preferences[PreferenceKeys.KEYBOARD_LANGUAGE] = "fr"
            preferences[PreferenceKeys.KEYBOARD_LAYOUT] = "qwerty"
        }

        cycleKeyboardLanguage(store)

        val preferences = store.data.first()
        assertEquals(
            KeyboardLayout.QWERTY,
            KeyboardLayoutPreference.explicitLayout(preferences, SupportedLanguage.FRENCH),
        )
        assertTrue(KeyboardLayoutPreference.isInherited(preferences, SupportedLanguage.ENGLISH))
    }

    private fun TestScope.store(name: String) = PreferenceDataStoreFactory.create(
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        produceFile = { File(tempFolder.root, "$name.preferences_pb") },
    )
}
