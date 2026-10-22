package dev.pivisolutions.dictus.recording

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.R
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A dictation that produces no text says so and never passes older text off as its result (#153). */
@RunWith(RobolectricTestRunner::class)
class RecordingScreenNoWordsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dataStore = FakeDataStore()

    private fun stopDictation(controller: FakeDictationController) {
        composeTestRule.setContent {
            DictusTheme {
                RecordingScreen(dictationController = controller, dataStore = dataStore, onBack = {})
            }
        }
        composeTestRule.waitForIdle()
        val stop = ApplicationProvider.getApplicationContext<Context>().getString(R.string.recording_stop_cd)
        composeTestRule.onNodeWithContentDescription(stop).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `no words shows the notice and drops the previous preview`() {
        runBlocking { LastTranscriptionStore.save(dataStore, "older dictation. ") }
        val controller = FakeDictationController(TranscriptionOutcome.NoWordsDetected)

        stopDictation(controller)

        composeTestRule.onNodeWithText("No words detected").assertIsDisplayed()
        composeTestRule.onNodeWithText("older dictation. ").assertDoesNotExist()
        composeTestRule.onNodeWithText("(No result)").assertDoesNotExist()
        assertNull(runBlocking { dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION] })
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `no words notice uses the iOS French sentence`() {
        stopDictation(FakeDictationController(TranscriptionOutcome.NoWordsDetected))

        composeTestRule.onNodeWithText("Aucun mot détecté").assertIsDisplayed()
    }

    @Test
    fun `tapping the mic after no words starts a new dictation and hides the notice`() {
        val controller = FakeDictationController(TranscriptionOutcome.NoWordsDetected)
        stopDictation(controller)

        composeTestRule.onNodeWithContentDescription("Record").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, controller.startRecordingCallCount)
        composeTestRule.onNodeWithText("No words detected").assertDoesNotExist()
    }

    @Test
    fun `text result is shown and saved as the preview, without the notice`() {
        stopDictation(FakeDictationController(TranscriptionOutcome.Text("Bonjour. ")))

        composeTestRule.onNodeWithText("Bonjour. ").assertExists()
        composeTestRule.onNodeWithText("No words detected").assertDoesNotExist()
        assertEquals("Bonjour. ", runBlocking { dataStore.data.first()[PreferenceKeys.LAST_TRANSCRIPTION] })
    }

    @Test
    fun `engine failure keeps the no result card, not the no words notice`() {
        stopDictation(FakeDictationController(TranscriptionOutcome.Failed))

        composeTestRule.onNodeWithText("(No result)").assertExists()
        composeTestRule.onNodeWithText("No words detected").assertDoesNotExist()
    }

    private class FakeDictationController(
        private val outcome: TranscriptionOutcome,
    ) : DictationController {
        private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = mutableState
        override val engineState: StateFlow<SttEngineState> = MutableStateFlow(SttEngineState.Ready("tiny"))

        var startRecordingCallCount = 0
            private set

        override fun prewarmEngine() = Unit

        override fun startRecording() {
            startRecordingCallCount++
            mutableState.value = DictationState.Recording()
        }

        override fun stopRecording(): FloatArray = FloatArray(0)

        override fun cancelRecording() {
            mutableState.value = DictationState.Idle
        }

        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome {
            mutableState.value = DictationState.Idle
            return outcome
        }
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
