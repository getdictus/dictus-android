package dev.pivisolutions.dictus.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun `the illustration names the language and layout chosen at the language step (#378)`() {
        setScreen(keyboard = LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY))

        composeTestRule.onNodeWithText("AZERTY (Français)").assertExists()
        composeTestRule.onNodeWithText("QWERTY (English)").assertDoesNotExist()
    }

    @Test
    fun `the illustration follows a layout other than the language's default`() {
        setScreen(keyboard = LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTY))

        composeTestRule.onNodeWithText("QWERTY (Deutsch)").assertExists()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `in French the illustration names English when English was chosen (#378)`() {
        setScreen(keyboard = LanguageSetup(SupportedLanguage.ENGLISH, KeyboardLayout.QWERTY))

        composeTestRule.onNodeWithText("Ajouter le clavier").assertExists()
        composeTestRule.onNodeWithText("QWERTY (English)").assertExists()
        composeTestRule.onNodeWithText("AZERTY (Français)").assertDoesNotExist()
    }

    private fun setScreen(
        keyboard: LanguageSetup = LanguageSetup(SupportedLanguage.ENGLISH, KeyboardLayout.QWERTY),
        imeEnabled: Boolean = false,
        imeSelected: Boolean = false,
        onOpenSettings: () -> Unit = {},
        onOpenPicker: () -> Unit = {},
        onNext: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DictusTheme {
                OnboardingKeyboardSetupScreen(
                    keyboard = keyboard,
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
