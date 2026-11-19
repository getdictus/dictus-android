package dev.pivisolutions.dictus.ime.ui

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The autocorrect undo chip in the keyboard bar (#276), iOS `SuggestionBarView.undoChip`. */
@RunWith(RobolectricTestRunner::class)
class AutocorrectUndoChipUiTest {
    @get:Rule val composeRule = createComposeRule()

    private var offer by mutableStateOf<AutocorrectUndoOffer?>(null)
    private var currentWord by mutableStateOf("")
    private var suggestions by mutableStateOf(emptyList<String>())
    private var mode by mutableStateOf(SuggestionPresentationMode.COMPLETION)
    private lateinit var view: View

    @Test
    fun `after a correction the first slot shows the typed word and the predictions follow`() {
        setBar(suggestions = listOf("monde", "à", "tous"), mode = SuggestionPresentationMode.PREDICTION)
        offer = AutocorrectUndoOffer("bonjuor", 1)

        composeRule.onNodeWithContentDescription("Undo correction, restore “bonjuor”")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("monde").assertIsDisplayed()
        composeRule.onNodeWithText("à").assertIsDisplayed()
        composeRule.onNodeWithText("tous").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Keyboard menu").assertDoesNotExist()
    }

    // The keyboard's own sequence on Space: completions, then an empty row, then the chip, then
    // the predictions. The chip once took half the row there and crushed the second slot.
    @Test
    @Config(qualifiers = "w411dp-h868dp-420dpi")
    fun `the chip takes one slot of three after the keyboard's own sequence, as iOS`() {
        setBar(currentWord = "Maisn", suggestions = listOf("Maison", "Maisons"))
        composeRule.waitForIdle()
        currentWord = ""
        suggestions = emptyList()
        offer = AutocorrectUndoOffer("Maisno", 1)
        composeRule.waitForIdle()
        mode = SuggestionPresentationMode.PREDICTION
        suggestions = listOf("de")

        val chip = composeRule.onNodeWithContentDescription("Undo correction, restore “Maisno”")
            .fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithText("de").fetchSemanticsNode().boundsInRoot
        assertEquals("chip=$chip next=$next", chip.width, next.width, 2f)
        assertEquals("chip=$chip next=$next", chip.right, next.left, 4f)
    }

    @Test
    fun `a tap on the chip asks for the undo once`() {
        var undos = 0
        setBar(onUndoAutocorrect = { undos++ })
        offer = AutocorrectUndoOffer("bonjuor", 1)

        composeRule.onNodeWithContentDescription("Undo correction, restore “bonjuor”").performClick()

        composeRule.runOnIdle { assertEquals(1, undos) }
    }

    @Test
    fun `the chip shows alone when there is nothing else to suggest`() {
        setBar()
        offer = AutocorrectUndoOffer("bonjuor", 1)

        composeRule.onNodeWithText("bonjuor", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Keyboard menu").assertDoesNotExist()
    }

    @Test
    fun `the chip appearing and pulsing plays no haptic, as iOS 224`() {
        setBar()
        composeRule.runOnIdle { assertEquals(NO_HAPTIC, shadowOf(view).lastHapticFeedbackPerformed()) }

        offer = AutocorrectUndoOffer("bonjuor", 1)
        composeRule.waitForIdle()
        offer = AutocorrectUndoOffer("bonjuor", 2)
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.runOnIdle { assertEquals(NO_HAPTIC, shadowOf(view).lastHapticFeedbackPerformed()) }
    }

    @Test
    fun `without an offer the first completion slot is the typed word, as before`() {
        setBar(currentWord = "bonj", suggestions = listOf("bonjour"))

        composeRule.onNodeWithText("bonj").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Undo correction, restore “bonj”").assertDoesNotExist()
    }

    private fun setBar(
        currentWord: String = "",
        suggestions: List<String> = emptyList(),
        mode: SuggestionPresentationMode = SuggestionPresentationMode.COMPLETION,
        onUndoAutocorrect: () -> Unit = {},
    ) {
        this.currentWord = currentWord
        this.suggestions = suggestions
        this.mode = mode
        composeRule.setContent {
            view = LocalView.current
            DictusTheme {
                KeyboardToolbar(
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    currentWord = this.currentWord,
                    suggestions = this.suggestions,
                    suggestionMode = this.mode,
                    autocorrectUndo = offer,
                    onUndoAutocorrect = onUndoAutocorrect,
                )
            }
        }
    }

    private companion object {
        // ShadowView's value when no haptic was ever performed.
        const val NO_HAPTIC = -1
    }
}
