package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
class KeyboardToolbarUndoUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `undo pill is shown with an accessible description and fires once per tap`() {
        var undoTaps = 0
        setBar(isDictationUndoAvailable = true, onUndoDictation = { undoTaps++ })

        composeRule.onNodeWithContentDescription("Undo dictation insertion")
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, undoTaps) }
    }

    @Test
    fun `undo pill reads Undo, as iOS`() {
        setBar(isDictationUndoAvailable = true)

        composeRule.onNodeWithText("Undo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `undo pill is translated in French, as iOS`() {
        setBar(isDictationUndoAvailable = true)

        composeRule.onNodeWithContentDescription("Annuler l’insertion de la dictée").assertHasClickAction()
        composeRule.onNodeWithText("Annuler", useUnmergedTree = true).assertIsDisplayed()
    }

    // #139, iOS ToolbarView.swift:254-259: right after a dictation, the undo wins over suggestions.
    @Test
    fun `undo takes the slot from the suggestions and the menu`() {
        setBar(isDictationUndoAvailable = true, currentWord = "bonj", suggestions = listOf("bonjour"))

        composeRule.onNodeWithContentDescription("Undo dictation insertion").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Keyboard menu").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Microphone").assertIsDisplayed()
    }

    @Test
    fun `no undo pill when nothing is pending`() {
        setBar(currentWord = "mot", suggestions = listOf("mots"))

        composeRule.onNodeWithContentDescription("Undo dictation insertion").assertDoesNotExist()
        composeRule.onNodeWithText("mot").assertHasClickAction()
    }

    private fun setBar(
        isDictationUndoAvailable: Boolean = false,
        onUndoDictation: () -> Unit = {},
        currentWord: String = "",
        suggestions: List<String> = emptyList(),
    ) {
        composeRule.setContent {
            DictusTheme {
                KeyboardToolbar(
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    currentWord = currentWord,
                    suggestions = suggestions,
                    isDictationUndoAvailable = isDictationUndoAvailable,
                    onUndoDictation = onUndoDictation,
                )
            }
        }
    }
}
