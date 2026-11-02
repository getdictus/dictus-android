package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The keyboard's total height never changes (#139), as iOS: grid + 52 (KeyboardViewController
 * computeKeyboardHeight), and no state touches the declared height (applyLayout, #166).
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardHeightTest {
    @get:Rule val composeRule = createComposeRule()

    private val expected = 316.dp

    private fun rootHeight(): Dp = composeRule.onRoot().getUnclippedBoundsInRoot().height

    private fun assertDp(message: String, expected: Dp, actual: Dp) =
        assertEquals(message, expected.value, actual.value, 0.5f)

    @Test fun `keys are the toolbar plus the key grid`() {
        composeRule.setContent { DictusTheme { KeyboardScreen({}, {}, onSendReturn = {}) } }

        assertDp("keyboard", expected, rootHeight())
    }

    // iOS KeyboardRootView: the emoji picker is the area below the toolbar.
    @Test fun `emoji keeps the keyboard height and the toolbar above it`() {
        composeRule.setContent {
            DictusTheme { KeyboardScreen({}, {}, onSendReturn = {}, isEmojiPickerOpen = true) }
        }

        assertDp("emoji", expected, rootHeight())
        composeRule.onNodeWithContentDescription("Microphone").assertExists()
        composeRule.onNodeWithContentDescription("Keyboard menu").assertExists()
        composeRule.onNodeWithText("ABC").assertExists()
    }

    @Test fun `recording keeps the keyboard height`() {
        setRecording()

        assertDp("recording", expected, rootHeight())
    }

    @Test fun `transcribing keeps the keyboard height`() {
        composeRule.setContent { DictusTheme { TranscribingScreen() } }

        assertDp("transcribing", expected, rootHeight())
    }

    // iOS RecordingOverlay.topBar: 10 + 36 + 6, so the pills do not move when recording starts.
    // Both bars put their pills 10dp from the top and 8dp from the sides.
    @Test fun `toolbar pills sit 10dp from the top and 8dp from the sides`() {
        composeRule.setContent { DictusTheme { KeyboardScreen({}, {}, onSendReturn = {}) } }
        val width = composeRule.onRoot().getUnclippedBoundsInRoot().right

        val mic = bounds("Microphone")
        assertDp("mic top", 10.dp, mic.top)
        assertDp("mic height", 36.dp, mic.bottom - mic.top)
        assertDp("mic right", width - 8.dp, mic.right)
        // ☰'s node is its 44dp touch target, centred on the 36dp capsule.
        val menu = bounds("Keyboard menu")
        assertDp("menu centre", 28.dp, (menu.top + menu.bottom) / 2)
        assertDp("menu left", 8.dp, menu.left)
    }

    @Test fun `recording pills sit where the toolbar's menu and mic sit`() {
        setRecording()
        val width = composeRule.onRoot().getUnclippedBoundsInRoot().right

        val confirm = bounds("Finish dictation")
        assertDp("confirm top", 10.dp, confirm.top)
        assertDp("confirm height", 36.dp, confirm.bottom - confirm.top)
        assertDp("confirm right", width - 8.dp, confirm.right)
        val cancel = bounds("Cancel dictation")
        assertDp("cancel centre", 28.dp, (cancel.top + cancel.bottom) / 2)
        assertDp("cancel left", 8.dp, cancel.left)
    }

    private fun setRecording() {
        composeRule.setContent {
            DictusTheme {
                RecordingScreen(
                    elapsedMs = 0L,
                    energy = emptyList(),
                    onCancel = {},
                    onConfirm = {},
                    onSwitchKeyboard = {},
                    onMicTap = {},
                )
            }
        }
    }

    private fun bounds(description: String): DpRect =
        composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
}
