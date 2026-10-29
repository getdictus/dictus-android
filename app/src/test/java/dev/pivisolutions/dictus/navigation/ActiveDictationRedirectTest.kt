package dev.pivisolutions.dictus.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A recording in progress always comes back to the recording screen (#243). */
@RunWith(RobolectricTestRunner::class)
class ActiveDictationRedirectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val lifecycleOwner = TestLifecycleOwner()
    private val controller = StateController()
    private var onRecordingScreen by mutableStateOf(false)
    private var shown = 0

    private fun setRedirect() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ActiveDictationRedirect(controller, onRecordingScreen) {
                    shown++
                    onRecordingScreen = true
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun setLifecycle(state: Lifecycle.State) {
        composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = state }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `opening the app during a keyboard dictation shows it`() {
        controller.state.value = DictationState.Recording(DictationSource.KEYBOARD)
        setRedirect()

        assertEquals(1, shown)
    }

    @Test
    fun `coming back to the app shows the dictation again`() {
        controller.state.value = DictationState.Recording(DictationSource.KEYBOARD)
        setRedirect()
        assertEquals(1, shown)

        setLifecycle(Lifecycle.State.CREATED)
        onRecordingScreen = false
        setLifecycle(Lifecycle.State.RESUMED)

        assertEquals(2, shown)
    }

    @Test
    fun `reaching Home while it records shows the dictation again`() {
        controller.state.value = DictationState.Recording(DictationSource.KEYBOARD)
        setRedirect()

        onRecordingScreen = false
        composeTestRule.waitForIdle()

        assertEquals(2, shown)
    }

    @Test
    fun `a dictation that starts while the app is in front is shown`() {
        setRedirect()
        assertEquals(0, shown)

        controller.state.value = DictationState.Recording(DictationSource.KEYBOARD)
        composeTestRule.waitForIdle()

        assertEquals(1, shown)
    }

    @Test
    fun `nothing recording, nothing shown`() {
        setRedirect()
        controller.state.value = DictationState.Transcribing
        composeTestRule.waitForIdle()
        setLifecycle(Lifecycle.State.CREATED)
        setLifecycle(Lifecycle.State.RESUMED)

        assertEquals(0, shown)
    }

    @Test
    fun `in the background nothing is shown`() {
        lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        setRedirect()
        controller.state.value = DictationState.Recording(DictationSource.KEYBOARD)
        composeTestRule.waitForIdle()

        assertEquals(0, shown)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle = registry
    }

    private class StateController : DictationController {
        override val state = MutableStateFlow<DictationState>(DictationState.Idle)
        override val engineState: StateFlow<SttEngineState> = MutableStateFlow(SttEngineState.Cold)
        override fun prewarmEngine() = Unit
        override fun startRecording(source: DictationSource): Boolean = false
        override fun stopRecording(): FloatArray = FloatArray(0)
        override fun cancelRecording() = Unit
        override suspend fun confirmAndTranscribe(retention: TranscriptionRetention): TranscriptionOutcome =
            TranscriptionOutcome.Failed
    }
}
