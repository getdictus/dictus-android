package dev.pivisolutions.dictus.recording

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.haptics.HapticHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app recording screen vibrates like the keyboard (#244), with iOS `HapticFeedback` styles:
 * medium when the recording starts, light on stop and ✕, soft when the transcription arrives.
 */
@RunWith(RobolectricTestRunner::class)
class RecordingScreenHapticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val played = mutableListOf<Int>()
    private val view = object : View(ApplicationProvider.getApplicationContext()) {
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            played += feedbackConstant
            return true
        }
    }.also {
        // The click ripple looks for a ViewGroup above LocalView.
        FrameLayout(ApplicationProvider.getApplicationContext()).addView(it)
    }

    @After
    fun restoreSetting() {
        HapticHelper.isEnabled = true
    }

    private fun setRecordingScreen(controller: FakeDictationController) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalView provides view) {
                DictusTheme {
                    RecordingScreen(dictationController = controller, onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun tap(contentDescription: String) {
        composeTestRule.onNodeWithContentDescription(contentDescription).performClick()
        composeTestRule.waitForIdle()
    }

    private fun stopCd() =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.recording_stop_cd)

    @Test
    fun `auto-start from Home plays one medium haptic`() {
        setRecordingScreen(FakeDictationController(TranscriptionOutcome.Text("Bonjour")))

        assertEquals(listOf(MEDIUM), played)
    }

    @Test
    fun `stop plays light, then soft once the transcription is on the card`() {
        setRecordingScreen(FakeDictationController(TranscriptionOutcome.Text("Bonjour")))

        tap(stopCd())

        assertEquals(listOf(MEDIUM, LIGHT, SOFT), played)
    }

    @Test
    fun `close while recording plays one light haptic`() {
        setRecordingScreen(FakeDictationController(TranscriptionOutcome.Text("Bonjour")))

        tap("Close")

        assertEquals(listOf(MEDIUM, LIGHT), played)
    }

    @Test
    fun `mic tap after a result plays one medium haptic for the new dictation`() {
        val controller = FakeDictationController(TranscriptionOutcome.Text("Bonjour"))
        setRecordingScreen(controller)
        tap(stopCd())
        played.clear()

        tap("New dictation")

        assertEquals(2, controller.startRecordingCallCount)
        assertEquals(listOf(MEDIUM), played)
    }

    @Test
    fun `no words detected plays no soft haptic`() {
        setRecordingScreen(FakeDictationController(TranscriptionOutcome.NoWordsDetected))

        tap(stopCd())

        assertEquals(listOf(MEDIUM, LIGHT), played)
    }

    @Test
    fun `a start queued behind the engine plays medium once, when it starts`() {
        val controller = FakeDictationController(
            TranscriptionOutcome.Text("Bonjour"),
            engine = SttEngineState.Loading("tiny"),
        )
        setRecordingScreen(controller)
        assertEquals(emptyList<Int>(), played)

        composeTestRule.runOnIdle { controller.mutableEngineState.value = SttEngineState.Ready("tiny") }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.startRecordingCallCount)
        assertEquals(listOf(MEDIUM), played)
    }

    @Test
    fun `a keyboard dictation shown in the app plays no start haptic`() {
        setRecordingScreen(
            FakeDictationController(
                TranscriptionOutcome.Text("Bonjour"),
                initial = DictationState.Recording(DictationSource.KEYBOARD),
            ),
        )

        assertEquals(emptyList<Int>(), played)
    }

    @Test
    fun `haptic setting off plays nothing`() {
        HapticHelper.isEnabled = false
        setRecordingScreen(FakeDictationController(TranscriptionOutcome.Text("Bonjour")))

        tap(stopCd())
        tap("Close")

        assertEquals(emptyList<Int>(), played)
    }

    private class FakeDictationController(
        private val outcome: TranscriptionOutcome,
        engine: SttEngineState = SttEngineState.Ready("tiny"),
        initial: DictationState = DictationState.Idle,
    ) : DictationController {
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<DictationState> = mutableState
        val mutableEngineState = MutableStateFlow(engine)
        override val engineState: StateFlow<SttEngineState> = mutableEngineState

        var startRecordingCallCount = 0
            private set

        override fun prewarmEngine() = Unit

        override fun startRecording(source: DictationSource): Boolean {
            startRecordingCallCount++
            mutableState.value = DictationState.Recording(source)
            return true
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

    private companion object {
        // HapticHelper's mapping of the iOS styles (recordingStarted, recordingStopped, textInserted).
        const val MEDIUM = HapticFeedbackConstants.LONG_PRESS
        const val LIGHT = HapticFeedbackConstants.KEYBOARD_TAP
        const val SOFT = HapticFeedbackConstants.CONTEXT_CLICK
    }
}
