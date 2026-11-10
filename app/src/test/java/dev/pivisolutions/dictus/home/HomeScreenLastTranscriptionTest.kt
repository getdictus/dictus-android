package dev.pivisolutions.dictus.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Home's card shows what the store holds, whichever surface dictated it (#245). */
@RunWith(RobolectricTestRunner::class)
class HomeScreenLastTranscriptionTest {
    @get:Rule val composeRule = createComposeRule()

    @After
    fun tearDown() {
        LastTranscriptionStore.instance.clear()
    }

    @Test
    fun `the card shows the last dictation and goes when a new one starts`() {
        composeRule.setContent {
            DictusTheme {
                HomeScreen(dataStore = FakeDataStore(), onNewDictation = {})
            }
        }
        composeRule.onNodeWithText("Last transcription").assertDoesNotExist()

        LastTranscriptionStore.instance.save("Bonjour depuis le clavier. ")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Last transcription").assertExists()
        composeRule.onNodeWithText("Bonjour depuis le clavier. ").assertExists()

        LastTranscriptionStore.instance.clear()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Last transcription").assertDoesNotExist()
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
