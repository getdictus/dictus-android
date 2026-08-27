package dev.pivisolutions.dictus.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelLoadingOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `loading overlay exposes accessible gating semantics`() {
        composeRule.setContent {
            DictusTheme {
                ModelLoadingOverlay(SttEngineState.Loading("tiny"), {}, {})
            }
        }
        composeRule.onNodeWithContentDescription("Speech recognition model is loading. Microphone unavailable.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test fun `failed overlay removes loading message and offers retry and cancel`() {
        composeRule.setContent {
            DictusTheme {
                ModelLoadingOverlay(SttEngineState.Failed("tiny"), {}, {})
            }
        }
        composeRule.onNodeWithText("Preparing speech recognition").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }
}