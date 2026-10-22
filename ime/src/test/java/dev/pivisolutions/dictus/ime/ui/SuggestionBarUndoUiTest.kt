package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SuggestionBarUndoUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `undo button is shown with an accessible description and fires once per tap`() {
        var undoTaps = 0
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "",
                    suggestions = emptyList(),
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
                    isDictationUndoAvailable = true,
                    onUndoDictation = { undoTaps++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Undo last dictation")
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, undoTaps) }
    }

    @Test
    @Config(qualifiers = "fr")
    fun `undo description is translated in French`() {
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "",
                    suggestions = emptyList(),
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
                    isDictationUndoAvailable = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Annuler la dernière dictée").assertHasClickAction()
    }

    @Test
    fun `no undo button when nothing is pending`() {
        composeRule.setContent {
            DictusTheme {
                SuggestionBar(
                    currentWord = "mot",
                    suggestions = emptyList(),
                    onSuggestionSelected = { _, _ -> },
                    onCurrentWordSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Undo last dictation").assertDoesNotExist()
        composeRule.onNodeWithText("mot").assertHasClickAction()
    }
}
