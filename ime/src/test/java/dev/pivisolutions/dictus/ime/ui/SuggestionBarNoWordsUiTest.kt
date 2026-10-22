package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SuggestionBarNoWordsUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setBar(isNoWordsNoticeVisible: Boolean) {
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "bonj",
                    suggestions = listOf("bonjour"),
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
                    isNoWordsNoticeVisible = isNoWordsNoticeVisible,
                )
            }
        }
    }

    @Test
    fun `notice replaces the suggestion slots`() {
        setBar(isNoWordsNoticeVisible = true)

        composeRule.onNodeWithText("No words detected").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `notice uses the iOS French sentence`() {
        setBar(isNoWordsNoticeVisible = true)

        composeRule.onNodeWithText("Aucun mot détecté").assertIsDisplayed()
    }

    @Test
    fun `suggestions show when there is no notice`() {
        setBar(isNoWordsNoticeVisible = false)

        composeRule.onNodeWithText("bonjour").assertIsDisplayed()
        composeRule.onNodeWithText("No words detected").assertDoesNotExist()
    }
}
