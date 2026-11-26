package dev.pivisolutions.dictus.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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

/** Settings' « Dictus Pro » row (#207), iOS SettingsView section 0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "fr")
class SettingsProRowTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the row is absent unless visible, then shows Pro active and opens the hub`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dataStore = FakeDataStore()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context), ProStatus(dataStore, debugBuild = true))
        val visible = mutableStateOf(false)
        var opened = 0
        composeRule.setContent {
            DictusTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    proRowVisible = visible.value,
                    isPro = true,
                    onNavigateToProHub = { opened++ },
                )
            }
        }

        composeRule.onNodeWithTag("settings_pro_row").assertDoesNotExist()

        visible.value = true
        composeRule.onNodeWithContentDescription("Pro actif").assertExists()
        composeRule.onNodeWithTag("settings_pro_row").performClick()
        assertEquals(1, opened)
    }
}
