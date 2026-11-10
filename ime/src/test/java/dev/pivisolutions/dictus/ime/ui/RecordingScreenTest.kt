package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class RecordingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private var cancels = 0
    private var confirms = 0
    private var switches = 0

    private fun setRecordingScreen() {
        composeRule.setContent {
            DictusTheme {
                RecordingScreen(
                    elapsedMs = 3_000L,
                    energy = emptyList(),
                    onCancel = { cancels++ },
                    onConfirm = { confirms++ },
                    onSwitchKeyboard = { switches++ },
                    onMicTap = {},
                )
            }
        }
    }

    // The bottom-left control opens the keyboard picker, so it is a globe named for that,
    // not a "Settings" gear (#190).
    @Test fun `globe is labelled Switch keyboard and its tap switches keyboard only`() {
        setRecordingScreen()

        composeRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Switch keyboard", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithContentDescription("Switch keyboard").performClick()
        composeRule.runOnIdle {
            assertEquals(1, switches)
            assertEquals(0, cancels)
            assertEquals(0, confirms)
        }
    }

    @Config(qualifiers = "fr")
    @Test fun `globe is labelled Changer de clavier in French`() {
        setRecordingScreen()

        composeRule.onNodeWithContentDescription("Réglages").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Changer de clavier").performClick()
        composeRule.runOnIdle { assertEquals(1, switches) }
    }

    @Test fun `cancel and finish pills keep their labels, are enabled and reach their callbacks`() {
        setRecordingScreen()

        composeRule.onNodeWithContentDescription("Cancel dictation").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, cancels)
            assertEquals(0, confirms)
        }

        composeRule.onNodeWithContentDescription("Finish dictation").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, cancels)
            assertEquals(1, confirms)
            assertEquals(0, switches)
        }
    }
}
