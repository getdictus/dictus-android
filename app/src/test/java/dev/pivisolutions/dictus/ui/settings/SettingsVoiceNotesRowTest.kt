package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings has no Voice notes row, even for Pro users: the notes open from the Dictus Pro hub
 * and after a share, as on iOS (#204, `SettingsView`'s only Pro row is « Dictus Pro »).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class SettingsVoiceNotesRowTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `with Pro there is no voice notes row, only the Dictus Pro row`() {
        val dataStore = FakeDataStore()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context), ProStatus(dataStore, debugBuild = true))
        composeRule.setContent {
            DictusTheme { SettingsScreen(viewModel = viewModel, proRowVisible = true, isPro = true) }
        }

        composeRule.onNodeWithText("Voice notes").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_pro_row").assertExists()
    }
}
