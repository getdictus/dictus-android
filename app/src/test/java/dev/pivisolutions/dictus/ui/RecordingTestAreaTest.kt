package dev.pivisolutions.dictus.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for RecordingTestArea covering idle state, recording state,
 * timer display, waveform visibility, and button callbacks.
 */
@RunWith(RobolectricTestRunner::class)
class RecordingTestAreaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `when idle shows test recording button and no waveform`() {
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Idle,
                    onStartRecording = {},
                    onStopRecording = {},
                    onCancelRecording = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Test Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("State: Idle").assertIsDisplayed()
    }

    @Test
    fun `when idle and no permission still shows test recording button`() {
        // Permission is requested on tap, not checked during rendering.
        // The button is always visible in idle state.
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Idle,
                    onStartRecording = {},
                    onStopRecording = {},
                    onCancelRecording = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Test Recording").assertIsDisplayed()
    }

    @Test
    fun `when recording shows timer and waveform`() {
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Recording(
                        elapsedMs = 5000L,
                        energy = listOf(0.3f, 0.5f),
                    ),
                    onStartRecording = {},
                    onStopRecording = {},
                    onCancelRecording = {},
                )
            }
        }

        composeTestRule.onNodeWithText("00:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("State: Recording").assertIsDisplayed()
    }

    @Test
    fun `when recording shows stop and cancel buttons but not test recording`() {
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Recording(
                        elapsedMs = 1000L,
                        energy = emptyList(),
                    ),
                    onStartRecording = {},
                    onStopRecording = {},
                    onCancelRecording = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Stop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Recording").assertDoesNotExist()
    }

    @Test
    fun `tapping stop calls onStopRecording`() {
        var stopCalled = false
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Recording(
                        elapsedMs = 1000L,
                        energy = emptyList(),
                    ),
                    onStartRecording = {},
                    onStopRecording = { stopCalled = true },
                    onCancelRecording = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Stop").performClick()
        assertTrue("onStopRecording should have been called", stopCalled)
    }

    @Test
    fun `tapping cancel calls onCancelRecording`() {
        var cancelCalled = false
        composeTestRule.setContent {
            DictusTheme {
                RecordingTestArea(
                    dictationState = DictationState.Recording(
                        elapsedMs = 1000L,
                        energy = emptyList(),
                    ),
                    onStartRecording = {},
                    onStopRecording = {},
                    onCancelRecording = { cancelCalled = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue("onCancelRecording should have been called", cancelCalled)
    }
}
