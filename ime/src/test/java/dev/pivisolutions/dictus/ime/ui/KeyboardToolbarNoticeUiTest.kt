package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class KeyboardToolbarNoticeUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setBar(
        notice: DictationNotice.Message?,
        isDictationUndoAvailable: Boolean = false,
        isAppDictationInProgress: Boolean = false,
        onRequestMicrophone: () -> Unit = {},
    ) {
        composeRule.setContent {
            DictusTheme {
                KeyboardToolbar(
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    currentWord = "bonj",
                    suggestions = listOf("bonjour"),
                    isDictationUndoAvailable = isDictationUndoAvailable,
                    dictationNotice = notice,
                    onRequestMicrophone = onRequestMicrophone,
                    isAppDictationInProgress = isAppDictationInProgress,
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
        setBar(notice = null, isAppDictationInProgress = true)

        composeRule.onNodeWithText("Dictée en cours dans Dictus").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Menu du clavier").assertDoesNotExist()
    }

    // D2: the #248 notice ranks as iOS recoveredTranscription, between the error and the undo.
    @Test
    fun `an app dictation outranks the undo`() {
        setBar(notice = null, isDictationUndoAvailable = true, isAppDictationInProgress = true)

        composeRule.onNodeWithText("Dictation in progress in Dictus").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Undo dictation insertion").assertDoesNotExist()
    }

    @Test
    fun `an error outranks the app dictation notice`() {
        setBar(DictationNotice.Message.Failed, isAppDictationInProgress = true)

        composeRule.onNodeWithText("The dictation failed. Tap the microphone to try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Dictation in progress in Dictus").assertDoesNotExist()
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

    // #307: the mic tap without the permission used to leave only a log line.
    @Test
    fun `a refused microphone says so and offers one action`() {
        var requests = 0
        setBar(DictationNotice.Message.MicrophoneRefused, onRequestMicrophone = { requests++ })

        composeRule.onNodeWithText("Microphone access required").assertIsDisplayed()
        composeRule.onNodeWithText("bonjour").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Microphone").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open Dictus").assertIsDisplayed().performClick()

        assertEquals(1, requests)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a refused microphone uses the French wording`() {
        setBar(DictationNotice.Message.MicrophoneRefused)

        composeRule.onNodeWithText("Accès au micro requis").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ouvrir Dictus").assertIsDisplayed()
    }

    @Test
    fun `suggestions show when there is no notice`() {
        setBar(null)

        composeRule.onNodeWithText("bonjour").assertIsDisplayed()
        composeRule.onNodeWithText("No words detected").assertDoesNotExist()
    }
}
