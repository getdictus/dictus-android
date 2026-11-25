package dev.pivisolutions.dictus.voicenotes

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioFormat
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteFailure
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « Messages vocaux »: one card per note and state, the copy and the retry (#204). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class VoiceNotesScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val retried = mutableListOf<VoiceNote>()

    private fun note(state: VoiceNoteState, transcript: String? = null, audio: String? = "a.ogg") =
        VoiceNote(receivedAt = 1_700_000_000_000, audioFileName = audio, format = SharedAudioFormat.OGG, durationSeconds = 185, state = state, transcript = transcript)

    private fun show(notes: List<VoiceNote>, switchOn: Boolean = true, available: Boolean = true) {
        composeRule.setContent {
            DictusTheme {
                VoiceNotesScreen(notes, switchOn, available, onBack = {}, onSwitch = {}, onRetry = { retried += it }, onDelete = {})
            }
        }
    }

    @Test
    fun `a transcribed note shows its text, and Copy puts it on the clipboard`() {
        show(listOf(note(VoiceNoteState.Done, transcript = "On se retrouve à midi.", audio = null)))
        composeRule.onNodeWithText("On se retrouve à midi.").assertIsDisplayed()
        composeRule.onNodeWithText("3m 05s", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("voice_note_copy").performClick()
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        assertEquals("On se retrouve à midi.", clipboard.primaryClip!!.getItemAt(0).text.toString())
        composeRule.onNodeWithText("Copied!").assertIsDisplayed()
    }

    @Test
    fun `waiting and transcribing notes say so`() {
        show(listOf(note(VoiceNoteState.Waiting), note(VoiceNoteState.Transcribing(0.5))))
        composeRule.onNodeWithText("Waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Transcribing…").assertIsDisplayed()
    }

    @Test
    fun `an engine failure offers a retry, a note without words does not`() {
        val failed = note(VoiceNoteState.Failed(VoiceNoteFailure.TRANSCRIPTION_FAILED))
        show(listOf(failed))
        composeRule.onNodeWithText("The transcription failed. Check that a model is installed, then try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(listOf(failed), retried)
    }

    @Test
    fun `no retry when a second try cannot help`() {
        show(listOf(note(VoiceNoteState.Failed(VoiceNoteFailure.NO_SPEECH), audio = null)))
        composeRule.onNodeWithText("No words were detected in this voice note.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `switched off, the screen and the waiting note say the switch is off, not that Pro is missing`() {
        show(listOf(note(VoiceNoteState.Waiting)), switchOn = false, available = false)
        assertEquals(
            2,
            composeRule.onAllNodesWithText("Voice notes are off: a voice message shared to Dictus is not transcribed. Your settings are kept.")
                .fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText("Transcribing voice notes is part of Dictus Pro.", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without Pro, a waiting note says Pro is needed`() {
        show(listOf(note(VoiceNoteState.Waiting)), switchOn = true, available = false)
        composeRule.onNodeWithText("Transcribing voice notes is part of Dictus Pro.", substring = true).assertIsDisplayed()
    }
}
