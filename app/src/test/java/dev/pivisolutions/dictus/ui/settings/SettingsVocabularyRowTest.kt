package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
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
 * Settings has no Vocabulary row, even for Pro users: the vocabulary opens from the Dictus Pro
 * hub only, as on iOS (#208, `SettingsView`'s only Pro row is « Dictus Pro »).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class SettingsVocabularyRowTest {

    @get:Rule val composeRule = createComposeRule()

    private val dataStore = FakeDataStore()

    private fun show(pro: Boolean) {
        val proStatus = ProStatus(dataStore, debugBuild = true)
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context), proStatus)
        composeRule.setContent {
            DictusTheme { SettingsScreen(viewModel = viewModel, proRowVisible = pro, isPro = pro) }
        }
    }

    @Test
    fun `without Pro there is no vocabulary row`() {
        show(pro = false)
        composeRule.onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun `with Pro there is still no vocabulary row, only the Dictus Pro row`() {
        show(pro = true)
        composeRule.onNodeWithText("Vocabulary").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_pro_row").assertExists()
    }
}
