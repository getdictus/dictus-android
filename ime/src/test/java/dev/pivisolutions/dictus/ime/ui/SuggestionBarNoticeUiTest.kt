package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SuggestionBarNoticeUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setBar(notice: DictationNotice.Message?) {
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "bonj",
                    suggestions = listOf("bonjour"),
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
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
    fun `suggestions show when there is no notice`() {
        setBar(null)

        composeRule.onNodeWithText("bonjour").assertIsDisplayed()
        composeRule.onNodeWithText("No words detected").assertDoesNotExist()
    }
}
