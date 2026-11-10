package dev.pivisolutions.dictus

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.ui.KeyboardScreen
import dev.pivisolutions.dictus.ui.settings.FakeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #288: the "open on 123" setting is gone; a user who had it on gets the letters back. */
@RunWith(RobolectricTestRunner::class)
class RetiredPreferencesTest {

    @get:Rule val composeRule = createComposeRule()

    // The name an older version wrote on disk, spelled out so a renamed constant cannot hide it.
    private val storedKeyboardMode = stringPreferencesKey("keyboard_mode")

    @Test
    fun `a stored 123 page is removed at start and the keyboard opens on letters`() {
        val dataStore = FakeDataStore()
        runBlocking {
            dataStore.edit {
                it[storedKeyboardMode] = "123"
                it[PreferenceKeys.THEME] = "light"
            }
            RetiredPreferences.remove(dataStore)
        }

        val prefs = runBlocking { dataStore.data.first() }
        assertNull(prefs[storedKeyboardMode])
        assertEquals("light", prefs[PreferenceKeys.THEME])

        composeRule.setContent { DictusTheme { KeyboardScreen({}, {}, onSendReturn = {}) } }
        // AZERTY letters row, not the digits of the 123 page.
        composeRule.onNodeWithText("a", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("z", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the last transcription an older version stored is still removed`() {
        val dataStore = FakeDataStore()
        runBlocking {
            dataStore.edit {
                it[PreferenceKeys.LAST_TRANSCRIPTION] = "x"
                it[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT] = 1L
            }
            RetiredPreferences.remove(dataStore)
        }

        val prefs = runBlocking { dataStore.data.first() }
        assertNull(prefs[PreferenceKeys.LAST_TRANSCRIPTION])
        assertNull(prefs[PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT])
    }
}
