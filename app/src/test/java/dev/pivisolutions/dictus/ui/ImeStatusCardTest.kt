package dev.pivisolutions.dictus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for ImeStatusCard rendering in each of the three states:
 * not enabled, enabled but not selected, and active (enabled + selected).
 */
@RunWith(RobolectricTestRunner::class)
class ImeStatusCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `when not enabled shows not enabled text and enable button`() {
        composeTestRule.setContent {
            DictusTheme {
                ImeStatusCard(
                    isEnabled = false,
                    isSelected = false,
                    onOpenSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keyboard not enabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Keyboard").assertIsDisplayed()
    }

    @Test
    fun `when enabled but not selected shows enabled text and select button`() {
        composeTestRule.setContent {
            DictusTheme {
                ImeStatusCard(
                    isEnabled = true,
                    isSelected = false,
                    onOpenSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Keyboard enabled but not selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select Keyboard").assertIsDisplayed()
    }

    @Test
    fun `when enabled and selected shows active text with no action button`() {
        composeTestRule.setContent {
            DictusTheme {
                ImeStatusCard(
                    isEnabled = true,
                    isSelected = true,
                    onOpenSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Dictus keyboard active").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Keyboard").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select Keyboard").assertDoesNotExist()
    }
}
