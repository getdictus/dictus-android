package dev.pivisolutions.dictus.recording

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordingScreenControllerBindingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `cold controller prewarms then starts exactly once when ready`() {
        var controller by mutableStateOf<DictationController?>(null)
        val lateController = FakeDictationController()

        composeTestRule.setContent {
            DictusTheme {
                RecordingScreen(
                    dictationController = controller,
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(0, lateController.startRecordingCallCount)

        controller = lateController
        composeTestRule.waitForIdle()

        assertEquals(1, lateController.prewarmEngineCallCount)
        assertEquals(0, lateController.startRecordingCallCount)

        lateController.emitEngineState(SttEngineState.Loading("tiny"))
        composeTestRule.waitForIdle()
        assertEquals(0, lateController.startRecordingCallCount)

        lateController.emitEngineState(SttEngineState.Ready("tiny"))
        composeTestRule.waitForIdle()
        assertEquals(1, lateController.startRecordingCallCount)

        composeTestRule.onNodeWithContentDescription("Stop").performClick()
        composeTestRule.waitForIdle()
        assertEquals(TranscriptionRetention.PERSIST_HISTORY, lateController.lastRetention)

        lateController.emitEngineState(SttEngineState.Ready("tiny"))
        composeTestRule.waitForIdle()
        assertEquals(1, lateController.startRecordingCallCount)
    }

    private class FakeDictationController : DictationController {
        private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = mutableState
        private val mutableEngineState = MutableStateFlow<SttEngineState>(SttEngineState.Cold)
        override val engineState: StateFlow<SttEngineState> = mutableEngineState

        var startRecordingCallCount = 0
            private set
        var prewarmEngineCallCount = 0
            private set
        var lastRetention: TranscriptionRetention? = null
            private set

        override fun prewarmEngine() {
            prewarmEngineCallCount++
        }

        fun emitEngineState(state: SttEngineState) {
            mutableEngineState.value = state
        }

        override fun startRecording() {
            startRecordingCallCount++
            mutableState.value = DictationState.Recording()
        }

        override fun stopRecording(): FloatArray = FloatArray(0)

        override fun cancelRecording() {
            mutableState.value = DictationState.Idle
        }

        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): String? {
            lastRetention = retention
            mutableState.value = DictationState.Idle
            return null
        }
    }
}
