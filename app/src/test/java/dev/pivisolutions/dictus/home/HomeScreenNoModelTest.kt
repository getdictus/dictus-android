package dev.pivisolutions.dictus.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.service.ModelInstallState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Without a model on disk Home says where to get one and offers no dictation, as iOS (#187). */
@RunWith(RobolectricTestRunner::class)
class HomeScreenNoModelTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `no model on disk shows the download card and hides new dictation`() {
        showHome(downloaded = emptySet())

        composeRule.onNodeWithText("Download a model to get started").assertExists()
        composeRule.onNodeWithText("Go to the Models tab to download your first transcription model.")
            .assertExists()
        composeRule.onNodeWithText("New dictation").assertDoesNotExist()
        composeRule.onNodeWithText("Active model").assertDoesNotExist()
    }

    @Test
    fun `a downloaded active model shows the normal home`() {
        showHome(downloaded = setOf("base"))

        composeRule.onNodeWithText("Download a model to get started").assertDoesNotExist()
        composeRule.onNodeWithText("Active model").assertExists()
        composeRule.onNodeWithText("New dictation").assertExists()
    }

    @Test
    fun `another model on disk does not make the active one ready`() {
        // iOS isModelReady wants the elected model, not just any download.
        showHome(downloaded = setOf("tiny"))

        composeRule.onNodeWithText("Download a model to get started").assertExists()
        composeRule.onNodeWithText("New dictation").assertDoesNotExist()
    }

    @Test
    fun `an install that ends while home is on screen brings the normal home`() {
        val downloaded = mutableSetOf<String>()
        val install = MutableStateFlow<ModelInstallState>(ModelInstallState.Downloading(80))
        showHome(downloaded, install)
        composeRule.onNodeWithText("Download a model to get started").assertExists()

        downloaded += "base"
        install.value = ModelInstallState.Done
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Download a model to get started").assertDoesNotExist()
        composeRule.onNodeWithText("New dictation").assertExists()
    }

    private fun showHome(
        downloaded: Set<String>,
        install: Flow<ModelInstallState> = MutableStateFlow(ModelInstallState.Idle),
    ) {
        composeRule.setContent {
            DictusTheme {
                HomeScreen(
                    dataStore = FakeDataStore(activeModel = "base"),
                    onNewDictation = {},
                    isModelDownloaded = { it in downloaded },
                    installState = { install },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private class FakeDataStore(activeModel: String) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(
            mutablePreferencesOf(PreferenceKeys.ACTIVE_MODEL to activeModel),
        )
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
