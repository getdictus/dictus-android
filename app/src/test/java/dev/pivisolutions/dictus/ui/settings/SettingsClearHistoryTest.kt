package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings' « Effacer l'historique » row (#202), iOS SettingsView.clearHistoryRow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "fr")
class SettingsClearHistoryTest {

    @get:Rule val composeRule = createComposeRule()

    private fun setSettings(visible: Boolean, count: Int, enabled: Boolean = count > 0, onClear: () -> Unit = {}) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dataStore = FakeDataStore()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context), ProStatus(dataStore, debugBuild = true))
        composeRule.setContent {
            DictusTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    clearHistoryVisible = visible,
                    clearHistoryEnabled = enabled,
                    historyCount = count,
                    onClearHistory = onClear,
                )
            }
        }
    }

    @Test
    fun `hidden when there is nothing to clear and no entitlement`() {
        setSettings(visible = false, count = 0)
        composeRule.onNodeWithTag("settings_clear_history").assertDoesNotExist()
    }

    @Test
    fun `shows the count and clears only after the confirmation`() {
        var cleared = 0
        setSettings(visible = true, count = 3, onClear = { cleared++ })

        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNodeWithTag("settings_clear_history").performClick()
        composeRule.onNodeWithText("Effacer l’historique ?").assertExists()
        assertEquals(0, cleared)

        composeRule.onNodeWithText("Tout supprimer").performClick()
        assertEquals(1, cleared)
    }

    /** iOS #906: no history (no Pro), but a debug log. The row deletes it, with no « 0 » beside it. */
    @Test
    fun `a debug log alone keeps the row usable, without a count`() {
        var cleared = 0
        setSettings(visible = true, count = 0, enabled = true, onClear = { cleared++ })

        composeRule.onNodeWithText("0").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_clear_history").performClick()
        composeRule.onNodeWithText(
            "Toutes les transcriptions enregistrées sur cet appareil seront supprimées. Cette action est irréversible.",
        ).assertExists()
        composeRule.onNodeWithText("Tout supprimer").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `cancel keeps the history`() {
        var cleared = 0
        setSettings(visible = true, count = 1, onClear = { cleared++ })

        composeRule.onNodeWithTag("settings_clear_history").performClick()
        composeRule.onNodeWithText("Annuler").performClick()
        assertEquals(0, cleared)
    }
}
