package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuggestionBarPredictionUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `prediction mode fills three clickable slots without raw input echo`() {
        val selected = mutableListOf<String>()
        var rawTaps = 0
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "",
                    suggestions = listOf("one", "two", "three"),
                    mode = SuggestionPresentationMode.PREDICTION,
                    onSuggestionSelected = { word, _ -> selected.add(word) },
                    onCurrentWordSelected = { rawTaps++ },
                )
            }
        }

        listOf("one", "two", "three").forEach { word ->
            composeRule.onNodeWithText(word).assertHasClickAction()
        }
        composeRule.onNodeWithText("three").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("three"), selected)
            assertEquals(0, rawTaps)
        }
    }

    @Test
    fun `empty prediction mode exposes no clickable empty input node`() {
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "raw-should-not-appear",
                    suggestions = emptyList(),
                    mode = SuggestionPresentationMode.PREDICTION,
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("raw-should-not-appear").assertDoesNotExist()
    }
}
