package dev.pivisolutions.dictus.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The language step's page (#198): its row, « Changer », the layout segment and « Continuer ». */
@RunWith(RobolectricTestRunner::class)
class OnboardingLanguageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var setup by mutableStateOf(LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY))
    private var continued = false

    private fun setScreen() {
        composeTestRule.setContent {
            DictusTheme {
                OnboardingLanguageScreen(
                    setup = setup,
                    model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!,
                    onLanguage = { setup = setup.withLanguage(it) { language -> language.profile.defaultLayout } },
                    onLayout = { setup = setup.copy(layout = it) },
                    onContinue = { continued = true },
                )
            }
        }
    }

    @Test
    fun `one row for dictation and keyboard, on the language shown`() {
        setScreen()

        composeTestRule.onNodeWithText("Your language").assertExists()
        composeTestRule.onNodeWithText("Français").assertExists()
        composeTestRule.onNodeWithText("Dictation and keyboard").assertExists()
        composeTestRule.onNodeWithText("AZERTY").assertIsSelected()
    }

    @Test
    fun `Change lists the keyboard languages and picks one`() {
        setScreen()

        composeTestRule.onNodeWithText("Change").performSemanticsAction(SemanticsActions.OnClick)
        // Each language once in the menu; French also stays in the row behind it.
        for (language in SupportedLanguage.entries) {
            composeTestRule.onNode(hasText(language.profile.displayName) and hasClickAction()).assertExists()
        }
        composeTestRule.onNodeWithText("Deutsch").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.runOnIdle {
            assertEquals(LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTZ), setup)
        }
        composeTestRule.onNodeWithText("QWERTZ").assertIsSelected()
    }

    @Test
    fun `the segment picks the layout`() {
        setScreen()

        composeTestRule.onNodeWithText("QWERTY").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.runOnIdle { assertEquals(KeyboardLayout.QWERTY, setup.layout) }
        composeTestRule.onNodeWithText("QWERTY").assertIsSelected()
    }

    @Test
    fun `the model line names the model that downloads`() {
        setScreen()

        composeTestRule.onNodeWithText("Parakeet 0.6B v3 model", substring = true).assertExists()
    }

    @Test
    fun `Continue confirms`() {
        setScreen()

        composeTestRule.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.runOnIdle { assertTrue(continued) }
    }
}
