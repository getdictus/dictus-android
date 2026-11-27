package dev.pivisolutions.dictus.ime.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.AutoCapSignal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A letter held while autocapitalization flips the case is still typed on release (#423).
 * `e` has accents, so the flip changes its accent list (é becomes É), which used to restart
 * the key's gesture and drop the letter.
 */
@RunWith(RobolectricTestRunner::class)
class HeldLetterCaseFlipTest {
    @get:Rule val composeRule = createComposeRule()

    private val committed = mutableListOf<String>()
    private var autoCapSignal by mutableStateOf<AutoCapSignal?>(null)

    private fun setKeyboard() {
        composeRule.setContent {
            DictusTheme {
                KeyboardScreen(
                    onCommitText = { committed += it },
                    onDeleteBackward = {},
                    onSendReturn = {},
                    autoCapSignal = autoCapSignal,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun flipCase(shouldCapitalize: Boolean, sequence: Int) {
        composeRule.runOnIdle { autoCapSignal = AutoCapSignal(shouldCapitalize, sequence) }
        composeRule.waitForIdle()
    }

    /** The held key now shows [label], and so does its preview: the key comes first. */
    private fun releaseHeldKey(label: String) {
        composeRule.onAllNodesWithText(label, useUnmergedTree = true).onFirst().performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun `letter held while the case flips to upper is typed once in the case it had at press`() {
        setKeyboard()

        composeRule.onNodeWithText("e", useUnmergedTree = true).performTouchInput { down(center) }
        flipCase(shouldCapitalize = true, sequence = 1)
        releaseHeldKey("E")

        composeRule.runOnIdle { assertEquals(listOf("e"), committed) }
    }

    @Test
    fun `letter held while the case flips to lower is typed once in the case it had at press`() {
        setKeyboard()
        flipCase(shouldCapitalize = true, sequence = 1)

        composeRule.onNodeWithText("E", useUnmergedTree = true).performTouchInput { down(center) }
        flipCase(shouldCapitalize = false, sequence = 2)
        releaseHeldKey("e")

        composeRule.runOnIdle { assertEquals(listOf("E"), committed) }
    }

    @Test
    fun `preview of a letter held while the case flips keeps the case it had at press`() {
        setKeyboard()

        composeRule.onNodeWithText("e", useUnmergedTree = true).performTouchInput { down(center) }
        flipCase(shouldCapitalize = true, sequence = 1)

        // The key now reads E; the preview keeps the e that was pressed, as iOS's activeKey.
        composeRule.onAllNodesWithText("e", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("E", useUnmergedTree = true).assertCountEquals(1)

        releaseHeldKey("E")
        composeRule.runOnIdle { assertEquals(listOf("e"), committed) }
    }

    @Test
    fun `tap after the case flipped types the new case`() {
        setKeyboard()
        flipCase(shouldCapitalize = true, sequence = 1)

        composeRule.onNodeWithText("E", useUnmergedTree = true).performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf("E"), committed) }
    }
}
