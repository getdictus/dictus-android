package dev.pivisolutions.dictus.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingTestRecordingGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `cold test recording shows loading gate and starts only when ready`() {
        val controller = FakeController()
        composeRule.setContent {
            DictusTheme {
                OnboardingTestRecordingScreen(
                    dictationController = controller,
                    onNext = {},
                )
            }
        }

        composeRule.runOnIdle { assertEquals(1, controller.prewarmCalls) }
        composeRule.onNodeWithContentDescription("Record").performClick()
        composeRule.runOnIdle {
            assertEquals(2, controller.prewarmCalls)
            assertEquals(0, controller.startCalls)
        }

        controller.engine.value = SttEngineState.Loading("tiny")
        composeRule.onNodeWithText("Preparing speech recognition").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Record").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, controller.startCalls) }

        controller.engine.value = SttEngineState.Ready("tiny")
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, controller.startCalls) }
        composeRule.onNodeWithContentDescription("Stop").performClick()
        composeRule.waitForIdle()
        assertEquals(TranscriptionRetention.EPHEMERAL, controller.lastRetention)
    }

    @Test
    fun `entering test recording retries stale failure without starting microphone`() {
        val controller = FakeController(
            initialEngineState = SttEngineState.Failed("No model available"),
        )

        composeRule.setContent {
            DictusTheme {
                OnboardingTestRecordingScreen(
                    dictationController = controller,
                    onNext = {},
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, controller.prewarmCalls)
            assertEquals(0, controller.startCalls)
        }

        composeRule.onNodeWithText("Speech recognition could not start").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle {
            assertEquals(2, controller.prewarmCalls)
            assertEquals(0, controller.startCalls)
        }

        controller.engine.value = SttEngineState.Loading("small-q5_1")
        composeRule.onNodeWithText("Preparing speech recognition").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, controller.startCalls) }

        controller.engine.value = SttEngineState.Ready("small-q5_1")
        composeRule.onNodeWithContentDescription("Record").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, controller.startCalls) }
    }

    @Test
    fun `leaving the step mid-recording cancels the recording`() {
        // « Passer » (#196) takes the user off this step while the microphone may be recording.
        val controller = FakeController(initialEngineState = SttEngineState.Ready("tiny"))
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DictusTheme {
                if (shown) {
                    OnboardingTestRecordingScreen(dictationController = controller, onNext = {})
                }
            }
        }
        composeRule.onNodeWithContentDescription("Record").performClick()
        composeRule.runOnIdle { assertTrue(controller.state.value is DictationState.Recording) }

        shown = false
        composeRule.runOnIdle {
            assertEquals(1, controller.cancelCalls)
            assertEquals(DictationState.Idle, controller.state.value)
        }
    }

    @Test
    fun `leaving the step while idle cancels nothing`() {
        val controller = FakeController(initialEngineState = SttEngineState.Ready("tiny"))
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DictusTheme {
                if (shown) {
                    OnboardingTestRecordingScreen(dictationController = controller, onNext = {})
                }
            }
        }

        shown = false
        composeRule.runOnIdle { assertEquals(0, controller.cancelCalls) }
    }

    private class FakeController(
        initialEngineState: SttEngineState = SttEngineState.Cold,
    ) : DictationController {
        private val dictation = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = dictation
        val engine = MutableStateFlow(initialEngineState)
        override val engineState: StateFlow<SttEngineState> = engine

        var prewarmCalls = 0
        var startCalls = 0
        var lastRetention: TranscriptionRetention? = null

        override fun prewarmEngine() {
            prewarmCalls++
        }

        override fun startRecording(source: DictationSource): Boolean {
            startCalls++
            dictation.value = DictationState.Recording(source)
            return true
        }

        override fun stopRecording(): FloatArray = FloatArray(0)
        var cancelCalls = 0

        override fun cancelRecording() {
            cancelCalls++
            dictation.value = DictationState.Idle
        }
        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome {
            lastRetention = retention
            dictation.value = DictationState.Idle
            return TranscriptionOutcome.Failed
        }
    }
}
