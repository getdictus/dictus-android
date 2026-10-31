package dev.pivisolutions.dictus.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ui.onboarding.OnboardingCenteredPage
import dev.pivisolutions.dictus.ui.onboarding.OnboardingPrimaryButton
import dev.pivisolutions.dictus.ui.onboarding.OnboardingShell
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the onboarding shell draws around each step (#196): the back chevron, the dashed
 * bar's position, the Skip, and the system back.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var step by mutableStateOf(OnboardingStep.Welcome)
    private val backs = mutableListOf<OnboardingStep>()
    private val skips = mutableListOf<OnboardingStep>()

    private fun setShell(initial: OnboardingStep) {
        step = initial
        composeTestRule.setContent {
            DictusTheme {
                OnboardingShell(
                    step = step,
                    onBack = { backs += step },
                    onSkip = { skips += step },
                ) { pageStep ->
                    OnboardingCenteredPage(
                        title = pageStep.storedName,
                        bottom = { OnboardingPrimaryButton(text = "Continue", onClick = {}) },
                    ) {}
                }
            }
        }
    }

    // --- Progress bar ---

    @Test
    fun `the bar says which step of four is on screen`() {
        setShell(OnboardingStep.Microphone)
        composeTestRule.onNodeWithContentDescription("Step 1 of 4").assertExists()

        step = OnboardingStep.ModelPreparation
        composeTestRule.onNodeWithContentDescription("Step 3 of 4").assertExists()

        step = OnboardingStep.FirstDictation
        composeTestRule.onNodeWithContentDescription("Step 4 of 4").assertExists()
    }

    @Test
    fun `the welcome and completion screens have no bar`() {
        setShell(OnboardingStep.Welcome)
        composeTestRule.onNodeWithContentDescription("of 4", substring = true).assertDoesNotExist()

        step = OnboardingStep.Completion
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("of 4", substring = true).assertDoesNotExist()
    }

    // --- Back ---

    @Test
    fun `the microphone step shows the back chevron and it goes back`() {
        setShell(OnboardingStep.Microphone)

        composeTestRule.onNodeWithContentDescription("Back").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.runOnIdle { assertEquals(listOf(OnboardingStep.Microphone), backs) }
    }

    @Test
    fun `steps without a way back show no chevron`() {
        setShell(OnboardingStep.Welcome)
        for (s in OnboardingStep.entries.filter { it.stepBack == null }) {
            step = s
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        }
    }

    @Test
    fun `the system back goes back a step where the chevron does`() {
        setShell(OnboardingStep.Microphone)

        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.runOnIdle { assertEquals(listOf(OnboardingStep.Microphone), backs) }
    }

    @Test
    fun `the system back is left to Android on a step without a way back`() {
        setShell(OnboardingStep.KeyboardSetup)

        composeTestRule.runOnUiThread {
            // No enabled callback of the shell: the dispatcher falls back to finishing the activity.
            assertEquals(false, composeTestRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
        composeTestRule.runOnIdle { assertEquals(emptyList<OnboardingStep>(), backs) }
    }

    // --- Skip ---

    @Test
    fun `the first dictation offers Skip and it skips`() {
        setShell(OnboardingStep.FirstDictation)

        composeTestRule.onNodeWithText("Skip").performSemanticsAction(SemanticsActions.OnClick)

        composeTestRule.runOnIdle { assertEquals(listOf(OnboardingStep.FirstDictation), skips) }
    }

    @Test
    fun `no other step offers Skip`() {
        setShell(OnboardingStep.Welcome)
        for (s in OnboardingStep.entries.filter { !it.isSkippable }) {
            step = s
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Skip").assertDoesNotExist()
        }
    }
}
