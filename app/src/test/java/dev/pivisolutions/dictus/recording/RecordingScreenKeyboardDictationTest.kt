package dev.pivisolutions.dictus.recording

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationStartGuard
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app shows a running keyboard dictation instead of starting its own (#237). */
@RunWith(RobolectricTestRunner::class)
class RecordingScreenKeyboardDictationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val lifecycleOwner = TestLifecycleOwner()
    private var showScreen by mutableStateOf(true)
    private var backCount = 0

    private fun setRecordingScreen(controller: DictationController) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                DictusTheme {
                    if (showScreen) RecordingScreen(dictationController = controller, onBack = { backCount++ })
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a keyboard dictation is shown with only Stop and a single close, never restarted`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)

        assertEquals(1, controller.startRecordingCallCount)
        composeTestRule.onNodeWithContentDescription("Stop").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Record").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("New dictation").assertDoesNotExist()
    }

    @Test
    fun `Stop shows the keyboard dictation's result in the app without keeping it`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)

        composeTestRule.onNodeWithContentDescription("Stop").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(TranscriptionRetention.EPHEMERAL), controller.confirmRetentions)
        composeTestRule.onNodeWithText(TRANSCRIPT).assertExists()
        composeTestRule.onNodeWithContentDescription("Copy").assertExists()
    }

    @Test
    fun `close cancels the keyboard dictation, starts nothing and leaves`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(1, controller.startRecordingCallCount)
        assertEquals(1, backCount)
    }

    @Test
    fun `system back does what close does`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(DictationState.Idle, controller.state.value)
        assertEquals(1, backCount)
    }

    @Test
    fun `close cancels the app's own dictation too`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        setRecordingScreen(controller)
        assertEquals(DictationSource.APP, (controller.state.value as DictationState.Recording).source)

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(1, backCount)
    }

    @Test
    fun `close after a result only leaves`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)
        composeTestRule.onNodeWithContentDescription("Stop").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, controller.cancelRecordingCallCount)
        assertEquals(1, backCount)
    }

    @Test
    fun `leaving without close does not cancel the keyboard dictation`() {
        val controller = GuardedController(SttEngineState.Ready("tiny"))
        controller.startRecording(DictationSource.KEYBOARD)
        setRecordingScreen(controller)

        showScreen = false
        composeTestRule.waitForIdle()

        assertEquals(0, controller.cancelRecordingCallCount)
        assertEquals(DictationSource.KEYBOARD, (controller.state.value as DictationState.Recording).source)
    }

    @Test
    fun `a start refused after the engine wait leaves the keyboard dictation alone`() {
        // The screen asks while the engine loads; the keyboard starts before it is ready.
        val controller = GuardedController(SttEngineState.Loading("tiny"))
        setRecordingScreen(controller)
        controller.startRecording(DictationSource.KEYBOARD)
        controller.engine.value = SttEngineState.Ready("tiny")
        composeTestRule.waitForIdle()

        assertEquals(DictationSource.KEYBOARD, (controller.state.value as DictationState.Recording).source)

        showScreen = false
        composeTestRule.waitForIdle()
        assertEquals(0, controller.cancelRecordingCallCount)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle = registry
    }

    /** Refuses a start like the service does. */
    private class GuardedController(engineState: SttEngineState) : DictationController {
        private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = mutableState
        val engine = MutableStateFlow(engineState)
        override val engineState: StateFlow<SttEngineState> = engine

        var startRecordingCallCount = 0
            private set
        var cancelRecordingCallCount = 0
            private set
        val confirmRetentions = mutableListOf<TranscriptionRetention>()

        override fun prewarmEngine() = Unit

        override fun startRecording(source: DictationSource): Boolean {
            startRecordingCallCount++
            if (!DictationStartGuard.canStartNewDictation(mutableState.value)) return false
            mutableState.value = DictationState.Recording(source)
            return true
        }

        override fun stopRecording(): FloatArray = FloatArray(0)

        override fun cancelRecording() {
            cancelRecordingCallCount++
            mutableState.value = DictationState.Idle
        }

        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome {
            confirmRetentions += retention
            mutableState.value = DictationState.Idle
            return TranscriptionOutcome.Text(TRANSCRIPT)
        }
    }

    private companion object {
        const val TRANSCRIPT = "transcript"
    }
}
