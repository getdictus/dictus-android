package dev.pivisolutions.dictus.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The intro carousel of the welcome step (#197, iOS `WelcomePage.swift`): three pages
 * with their headline and subtitle, the dots following the page, and the button.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingWelcomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var nexts = 0

    private fun setWelcome() {
        composeTestRule.setContent {
            DictusTheme {
                OnboardingWelcomeScreen(onNext = { nexts++ })
            }
        }
    }

    private fun swipeToNextPage() {
        composeTestRule.onRoot().performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    private fun assertPage(headline: String, subtitle: String, dots: String) {
        composeTestRule.onNodeWithText(headline).assertIsDisplayed()
        composeTestRule.onNodeWithText(subtitle).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(dots).assertExists()
    }

    @Test
    fun `the three pages show the iOS copy and the dots follow the page`() {
        setWelcome()

        assertPage("Speak. Dictus writes.", "In all your apps, from the keyboard.", "Page 1 of 3")

        swipeToNextPage()
        assertPage(
            "Even offline.",
            "On the metro or on a plane, Dictus still writes. Everything happens on your phone.",
            "Page 2 of 3",
        )

        swipeToNextPage()
        assertPage(
            "In your keyboard.",
            "In your notes, messages or emails: tap the mic, speak, and the text appears.",
            "Page 3 of 3",
        )
    }

    @Test
    fun `get started moves on from any page`() {
        setWelcome()

        composeTestRule.onNodeWithText("Get started").performClick()
        swipeToNextPage()
        composeTestRule.onNodeWithText("Get started").performClick()

        composeTestRule.runOnIdle { assertEquals(2, nexts) }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the pages are in French on a French phone`() {
        setWelcome()

        assertPage("Parlez. Dictus écrit.", "Dans toutes vos apps, depuis le clavier.", "Page 1 sur 3")
        composeTestRule.onNodeWithText("Commencer").assertIsDisplayed()

        swipeToNextPage()
        assertPage(
            "Même sans réseau.",
            "Dans le métro ou en avion, Dictus écrit quand même. Tout se passe sur votre téléphone.",
            "Page 2 sur 3",
        )

        swipeToNextPage()
        assertPage(
            "Dans votre clavier.",
            "Dans vos notes, messages ou e-mails\u00a0: touchez le micro, parlez, le texte arrive.",
            "Page 3 sur 3",
        )
    }
}
