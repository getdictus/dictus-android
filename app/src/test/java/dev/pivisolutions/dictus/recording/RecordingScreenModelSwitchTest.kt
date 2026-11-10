package dev.pivisolutions.dictus.recording

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dev.pivisolutions.dictus.core.R as CoreR

/** The first dictation after a model switch never runs under the preparation overlay (#175). */
@RunWith(RobolectricTestRunner::class)
class RecordingScreenModelSwitchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val loadingOverlay = context.getString(CoreR.string.model_loading_accessibility)

    private fun show(controller: FakeDictationController, selectedModel: String) {
        val dataStore = FakeDataStore(selectedModel)
        composeTestRule.setContent {
            DictusTheme {
                RecordingScreen(dictationController = controller, dataStore = dataStore, onBack = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `engine still on the previous model loads the selected one before recording`() {
        val controller = FakeDictationController(SttEngineState.Ready("small-q5_1"))

        show(controller, selectedModel = "tiny")

        assertEquals(1, controller.prewarmEngineCallCount)
        assertEquals(0, controller.startRecordingCallCount)

        // The load runs at tap time, before any recording: the overlay may show it.
        controller.engine.value = SttEngineState.Loading("tiny")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(loadingOverlay).assertExists()
        assertEquals(0, controller.startRecordingCallCount)

        controller.engine.value = SttEngineState.Ready("tiny")
        composeTestRule.waitForIdle()
        assertEquals(1, controller.startRecordingCallCount)
        composeTestRule.onNodeWithContentDescription(loadingOverlay).assertDoesNotExist()
    }

    @Test
    fun `engine ready on the selected model records at once`() {
        val controller = FakeDictationController(SttEngineState.Ready("tiny"))

        show(controller, selectedModel = "tiny")

        assertEquals(0, controller.prewarmEngineCallCount)
        assertEquals(1, controller.startRecordingCallCount)
    }

    @Test
    fun `model loading during the transcription keeps the overlay hidden`() {
        val controller = FakeDictationController(SttEngineState.Ready("tiny"))
        show(controller, selectedModel = "tiny")

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.recording_stop_cd))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(DictationState.Transcribing, controller.state.value)
        assertEquals(SttEngineState.Loading("base"), controller.engine.value)
        composeTestRule.onNodeWithContentDescription(loadingOverlay).assertDoesNotExist()

        controller.finishTranscription.complete(Unit)
        composeTestRule.waitForIdle()
        assertEquals(DictationState.Idle, controller.state.value)
        composeTestRule.onNodeWithContentDescription(loadingOverlay).assertDoesNotExist()
    }

    private class FakeDictationController(initialEngine: SttEngineState) : DictationController {
        private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = mutableState
        val engine = MutableStateFlow(initialEngine)
        override val engineState: StateFlow<SttEngineState> = engine
        val finishTranscription = CompletableDeferred<Unit>()

        var startRecordingCallCount = 0
            private set
        var prewarmEngineCallCount = 0
            private set

        override fun prewarmEngine() {
            prewarmEngineCallCount++
        }

        override fun startRecording(source: DictationSource): Boolean {
            startRecordingCallCount++
            mutableState.value = DictationState.Recording(source)
            return true
        }

        override fun stopRecording(): FloatArray = FloatArray(0)

        override fun cancelRecording() {
            mutableState.value = DictationState.Idle
        }

        // Like DictationService after a switch: the transcription loads the new model.
        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome {
            mutableState.value = DictationState.Transcribing
            engine.value = SttEngineState.Loading("base")
            finishTranscription.await()
            engine.value = SttEngineState.Ready("base")
            mutableState.value = DictationState.Idle
            return TranscriptionOutcome.NoWordsDetected
        }
    }

    private class FakeDataStore(selectedModel: String) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(
            mutablePreferencesOf(PreferenceKeys.ACTIVE_MODEL to selectedModel),
        )
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
