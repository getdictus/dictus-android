package dev.pivisolutions.dictus.ime.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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
 * computeKeyboardHeight), and no state touches the declared height (applyLayout, #166). The height
 * itself comes from [LocalKeyboardSizing] (#127): each test runs at the default (Pixel 4, portrait)
 * and at a phone in landscape.
 */
@RunWith(RobolectricTestRunner::class)
class KeyboardHeightTest {
    @get:Rule val composeRule = createComposeRule()

    private val landscape = keyboardSizing(screenWidthDp = 830f, screenHeightDp = 393f, fontScale = 1f)
    private var sizing = keyboardSizing(screenWidthDp = 393f, screenHeightDp = 830f, fontScale = 1f)
    private val expected: Dp get() = sizing.totalHeight

    private fun content(body: @Composable () -> Unit) = composeRule.setContent {
        CompositionLocalProvider(LocalKeyboardSizing provides sizing) { DictusTheme { body() } }
    }

    private fun rootHeight(): Dp = composeRule.onRoot().getUnclippedBoundsInRoot().height

    private fun assertDp(message: String, expected: Dp, actual: Dp) =
        assertEquals(message, expected.value, actual.value, 0.5f)

    @Test fun `keys are the toolbar plus the key grid`() {
        content { KeyboardScreen({}, {}, onSendReturn = {}) }

        assertDp("keyboard", 52.dp + sizing.keyAreaHeight, rootHeight())
        // The bottom row's labels sit in the middle of the fourth row.
        val label = composeRule.onNodeWithText("123").getUnclippedBoundsInRoot()
        assertDp("fourth row centre", 52.dp + 2.dp + sizing.rowHeight * 3.5f, (label.top + label.bottom) / 2)
    }

    // The Pixel 4 report (#127): in landscape the old 316 dp left 77 dp of the screen to the app.
    @Test fun `in landscape every state shrinks to the same height`() {
        sizing = landscape
        content { KeyboardScreen({}, {}, onSendReturn = {}) }

        assertDp("keyboard", 216.dp, rootHeight())
    }

    @Test fun `in landscape recording fits the timer under a shorter waveform`() {
        sizing = landscape
        setRecording()

        assertDp("recording", 216.dp, rootHeight())
        composeRule.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test fun `in landscape transcribing keeps the keyboard height`() {
        sizing = landscape
        content { TranscribingScreen() }

        assertDp("transcribing", 216.dp, rootHeight())
    }

    // iOS KeyboardRootView: the emoji picker is the area below the toolbar.
    @Test fun `emoji keeps the keyboard height and the toolbar above it`() {
        content { KeyboardScreen({}, {}, onSendReturn = {}, isEmojiPickerOpen = true) }

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
        content { TranscribingScreen() }

        assertDp("transcribing", expected, rootHeight())
    }

    // iOS RecordingOverlay.topBar: 10 + 36 + 6, so the pills do not move when recording starts.
    // Both bars put their pills 10dp from the top and 8dp from the sides.
    @Test fun `toolbar pills sit 10dp from the top and 8dp from the sides`() {
        content { KeyboardScreen({}, {}, onSendReturn = {}) }
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
        content {
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

    private fun bounds(description: String): DpRect =
        composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
}
