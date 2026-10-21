package dev.pivisolutions.dictus.recording

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Leaving the recording screen must release the microphone without transcribing (#142). */
@RunWith(RobolectricTestRunner::class)
class RecordingScreenLeaveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val controller = FakeDictationController(SttEngineState.Ready("tiny"))
    private val lifecycleOwner = TestLifecycleOwner()
    private var showScreen by mutableStateOf(true)

    private fun setRecordingScreen() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                DictusTheme {
                    if (showScreen) {
                        RecordingScreen(dictationController = controller, onBack = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `leaving while recording cancels without transcribing`() {
        setRecordingScreen()
        assertEquals(1, controller.startRecordingCallCount)

        // Back (arrow or system) pops the destination, which disposes the screen.
        showScreen = false
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(0, controller.confirmAndTranscribeCallCount)
        assertEquals(DictationState.Idle, controller.state.value)
    }

    @Test
    fun `going to the background while recording cancels without transcribing`() {
        setRecordingScreen()

        composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(0, controller.confirmAndTranscribeCallCount)

        // Leaving afterwards does not cancel a second time.
        showScreen = false
        composeTestRule.waitForIdle()
        assertEquals(1, controller.cancelRecordingCallCount)
    }

    @Test
    fun `leaving before the queued start lands still cancels it`() {
        // startForegroundService() is asynchronous: the state is still Idle when the user leaves.
        controller.startsImmediately = false
        setRecordingScreen()
        assertEquals(1, controller.startRecordingCallCount)
        assertEquals(DictationState.Idle, controller.state.value)

        showScreen = false
        composeTestRule.waitForIdle()

        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(0, controller.confirmAndTranscribeCallCount)
    }

    @Test
    fun `rotation keeps the recording and leaving afterwards still cancels`() {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        fun ComponentActivity.showRecordingScreen() = setContent {
            DictusTheme {
                if (showScreen) RecordingScreen(dictationController = controller, onBack = {})
            }
        }
        activityController.get().showRecordingScreen()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, controller.startRecordingCallCount)

        // A configuration change destroys the activity (ON_STOP, then dispose) and recreates it.
        activityController.recreate()
        activityController.get().showRecordingScreen()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, controller.cancelRecordingCallCount)
        assertEquals(1, controller.startRecordingCallCount)
        assertEquals(DictationState.Recording(), controller.state.value)

        // The recreated screen still owns the recording, so leaving (here: going to the
        // background, a real stop rather than a configuration change) still releases the mic.
        activityController.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, controller.cancelRecordingCallCount)
        assertEquals(0, controller.confirmAndTranscribeCallCount)
    }

    @Test
    fun `leaving after a normal stop does not cancel`() {
        setRecordingScreen()

        composeTestRule.onNodeWithContentDescription("Stop").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, controller.confirmAndTranscribeCallCount)

        showScreen = false
        composeTestRule.waitForIdle()
        assertEquals(0, controller.cancelRecordingCallCount)
    }

    @Test
    fun `leaving does not cancel a recording the keyboard owns`() {
        // Engine not ready: the screen only prewarms and never starts a recording itself.
        val sharedController = FakeDictationController(SttEngineState.Loading("tiny"))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                DictusTheme {
                    if (showScreen) {
                        RecordingScreen(dictationController = sharedController, onBack = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(0, sharedController.startRecordingCallCount)

        sharedController.startRecording()
        showScreen = false
        composeTestRule.waitForIdle()

        assertEquals(0, sharedController.cancelRecordingCallCount)
        assertEquals(DictationState.Recording(), sharedController.state.value)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle = registry
    }

    private class FakeDictationController(engine: SttEngineState) : DictationController {
        private val mutableState = MutableStateFlow<DictationState>(DictationState.Idle)
        override val state: StateFlow<DictationState> = mutableState
        override val engineState: StateFlow<SttEngineState> = MutableStateFlow(engine)

        /** False mimics the service before its queued ACTION_START is delivered. */
        var startsImmediately = true
        var startRecordingCallCount = 0
            private set
        var cancelRecordingCallCount = 0
            private set
        var confirmAndTranscribeCallCount = 0
            private set

        override fun prewarmEngine() = Unit

        override fun startRecording() {
            startRecordingCallCount++
            if (startsImmediately) mutableState.value = DictationState.Recording()
        }

        override fun stopRecording(): FloatArray = FloatArray(0)

        override fun cancelRecording() {
            cancelRecordingCallCount++
            mutableState.value = DictationState.Idle
        }

        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): String? {
            confirmAndTranscribeCallCount++
            mutableState.value = DictationState.Idle
            return null
        }
    }
}
