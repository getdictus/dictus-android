package dev.pivisolutions.dictus.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingKeyboardSetupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `not enabled opens Android keyboard settings`() {
        var settingsOpened = false
        var pickerOpened = false
        var advanced = false

        setScreen(
            imeEnabled = false,
            imeSelected = false,
            onOpenSettings = { settingsOpened = true },
            onOpenPicker = { pickerOpened = true },
            onNext = { advanced = true },
        )

        composeTestRule.onNodeWithText("Open Settings").assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.runOnIdle {
            assertTrue(settingsOpened)
            assertFalse(pickerOpened)
            assertFalse(advanced)
        }
    }

    @Test
    fun `enabled but not selected opens system input method picker`() {
        var settingsOpened = false
        var pickerOpened = false
        var advanced = false

        setScreen(
            imeEnabled = true,
            imeSelected = false,
            onOpenSettings = { settingsOpened = true },
            onOpenPicker = { pickerOpened = true },
            onNext = { advanced = true },
        )

        composeTestRule.onNodeWithText("Select Dictus").assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.runOnIdle {
            assertFalse(settingsOpened)
            assertTrue(pickerOpened)
            assertFalse(advanced)
        }
    }

    @Test
    fun `enabled and selected is the only state that can continue`() {
        var advanced = false

        setScreen(
            imeEnabled = true,
            imeSelected = true,
            onOpenSettings = {},
            onOpenPicker = {},
            onNext = { advanced = true },
        )

        composeTestRule.onNodeWithText("Continue").assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText("Open Settings").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select Dictus").assertDoesNotExist()
        composeTestRule.runOnIdle { assertTrue(advanced) }
    }

    private fun setScreen(
        imeEnabled: Boolean,
        imeSelected: Boolean,
        onOpenSettings: () -> Unit,
        onOpenPicker: () -> Unit,
        onNext: () -> Unit,
    ) {
        composeTestRule.setContent {
            DictusTheme {
                OnboardingKeyboardSetupScreen(
                    imeEnabled = imeEnabled,
                    imeSelected = imeSelected,
                    onOpenSettings = onOpenSettings,
                    onOpenPicker = onOpenPicker,
                    onNext = onNext,
                )
            }
        }
    }
}
