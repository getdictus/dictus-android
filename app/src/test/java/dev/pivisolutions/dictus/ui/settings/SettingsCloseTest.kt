package dev.pivisolutions.dictus.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings opened from the keyboard has a way out: a back arrow and the back gesture (#416). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class SettingsCloseTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var closes = 0

    private fun showSettings(onClose: (() -> Unit)?) {
        val viewModel = SettingsViewModel(
            FakeDataStore(),
            ModelManager(composeRule.activity),
            ProStatus(FakeDataStore(), debugBuild = true),
        )
        composeRule.setContent {
            DictusTheme { SettingsScreen(viewModel = viewModel, onClose = onClose) }
        }
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `the back arrow leaves Settings opened from the keyboard`() {
        showSettings(onClose = { closes++ })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, closes)
    }

    @Test
    fun `the system back gesture leaves Settings opened from the keyboard`() {
        showSettings(onClose = { closes++ })

        pressSystemBack()

        assertEquals(1, closes)
    }

    @Test
    fun `the Settings tab keeps no back arrow`() {
        showSettings(onClose = null)

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
