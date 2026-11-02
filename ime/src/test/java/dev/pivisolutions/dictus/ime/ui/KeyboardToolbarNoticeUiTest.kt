package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class KeyboardToolbarNoticeUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setBar(notice: DictationNotice.Message?, isDictationUndoAvailable: Boolean = false) {
        composeRule.setContent {
            DictusTheme {
                KeyboardToolbar(
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    currentWord = "bonj",
                    suggestions = listOf("bonjour"),
                    isDictationUndoAvailable = isDictationUndoAvailable,
                    dictationNotice = notice,
                )
            }
        }
    }

    @Test
    fun `notice replaces the suggestion slots`() {
        setBar(DictationNotice.Message.NoWordsDetected)

        composeRule.onNodeWithText("No words detected").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `notice uses the iOS French sentence`() {
        setBar(DictationNotice.Message.NoWordsDetected)

        composeRule.onNodeWithText("Aucun mot détecté").assertIsDisplayed()
    }

    @Test
    fun `failure notice replaces the suggestion slots with the iOS sentence`() {
        setBar(DictationNotice.Message.Failed)

        composeRule.onNodeWithText("The dictation failed. Tap the microphone to try again.").assertIsDisplayed()
        composeRule.onNodeWithText("No words detected").assertDoesNotExist()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `failure notice uses the iOS French sentence`() {
        setBar(DictationNotice.Message.Failed)

        composeRule.onNodeWithText("La dictée a échoué. Touchez le micro pour réessayer.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `an app dictation says why the mic is off`() {
        setBar(DictationNotice.Message.AppDictationInProgress)

        composeRule.onNodeWithText("Dictée en cours dans Dictus").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
    }

    // #139, iOS ToolbarCentreSlot.resolve: the error outranks the undo, and takes ☰'s place.
    @Test
    fun `an error outranks the undo and takes the menu's place`() {
        setBar(DictationNotice.Message.Failed, isDictationUndoAvailable = true)

        composeRule.onNodeWithText("The dictation failed. Tap the microphone to try again.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Undo dictation insertion").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Keyboard menu").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Microphone").assertIsDisplayed()
    }

    @Test
    fun `suggestions show when there is no notice`() {
        setBar(null)

        composeRule.onNodeWithText("bonjour").assertIsDisplayed()
        composeRule.onNodeWithText("No words detected").assertDoesNotExist()
    }
}
