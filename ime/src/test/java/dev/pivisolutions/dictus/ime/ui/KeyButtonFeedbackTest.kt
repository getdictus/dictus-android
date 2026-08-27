package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.BackspaceRepeatPolicy
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyButtonFeedbackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `pressed keycap color differs and released color stays exact`() {
        val released = Color(0xFF202838)
        val text = Color(0xFFF2F5FA)

        assertEquals(released, pressedKeyBackground(released, text, isPressed = false))
        assertNotEquals(released, pressedKeyBackground(released, text, isPressed = true))
    }

    @Test
    fun `character preview appears on touch down and disappears on release`() {
        setKey(KeyDefinition(label = "a"))

        composeRule.onAllNodesWithText("a").assertCountEquals(1)
        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { down(center) }
        composeRule.onAllNodesWithText("a").assertCountEquals(2)

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { up() }
        composeRule.onAllNodesWithText("a").assertCountEquals(1)
    }

    @Test
    fun `special key uses pressed keycap feedback without a balloon`() {
        setKey(KeyDefinition(label = "space", type = KeyType.SPACE, widthMultiplier = 4f))
        composeRule.onNodeWithTag(KEY_TAG).assert(
            SemanticsMatcher.expectValue(KeyPressedSemantics, false),
        )

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { down(center) }
        composeRule.onNodeWithTag(KEY_TAG).assert(
            SemanticsMatcher.expectValue(KeyPressedSemantics, true),
        )
        composeRule.onAllNodesWithText("space").assertCountEquals(1)

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { up() }
        composeRule.onNodeWithTag(KEY_TAG).assert(
            SemanticsMatcher.expectValue(KeyPressedSemantics, false),
        )
    }

    @Test
    fun `space tap commits once without entering trackpad`() {
        var commits = 0
        val activeStates = mutableListOf<Boolean>()
        setKey(
            key = KeyDefinition(label = "space", type = KeyType.SPACE, widthMultiplier = 4f),
            onPress = { commits++ },
            onTrackpadActiveChange = activeStates::add,
        )

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, commits)
            assertTrue(activeStates.isEmpty())
        }
    }

    @Test
    fun `space hold enters trackpad moves cursor and does not commit space`() {
        var commits = 0
        val activeStates = mutableListOf<Boolean>()
        val moves = mutableListOf<Int>()
        setKey(
            key = KeyDefinition(label = "space", type = KeyType.SPACE, widthMultiplier = 4f),
            onPress = { commits++ },
            onTrackpadActiveChange = activeStates::add,
            onTrackpadMove = moves::add,
        )

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            down(center)
            advanceEventTime(310L)
        }
        composeRule.mainClock.advanceTimeBy(310L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            moveTo(center + Offset(24f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, commits)
            assertEquals(listOf(true, false), activeStates)
            assertTrue("Horizontal movement must produce cursor steps", moves.sum() > 0)
        }
    }

    @Test
    fun `disposing active space gesture always exits trackpad`() {
        val showKey = mutableStateOf(true)
        val activeStates = mutableListOf<Boolean>()
        composeRule.setContent {
            DictusTheme {
                if (showKey.value) {
                    KeyButton(
                        key = KeyDefinition(label = "space", type = KeyType.SPACE),
                        isShifted = false,
                        onPress = {},
                        hapticsEnabled = false,
                        onTrackpadActiveChange = activeStates::add,
                        modifier = Modifier.testTag(KEY_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            down(center)
            advanceEventTime(310L)
        }
        composeRule.mainClock.advanceTimeBy(310L)
        composeRule.waitForIdle()
        composeRule.runOnIdle { showKey.value = false }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf(true, false), activeStates) }
    }

    @Test
    fun `accent popup replaces character balloon during long press`() {
        setKey(
            key = KeyDefinition(label = "e"),
            accents = listOf("é", "è"),
        )

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            down(center)
            advanceEventTime(450L)
        }
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("e").assertCountEquals(1)
        composeRule.onAllNodesWithText("é").assertCountEquals(1)
        composeRule.onAllNodesWithText("è").assertCountEquals(1)

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { up() }
    }

    @Test
    fun `held delete stays one pressed keycap while repeat continues`() {
        var deletes = 0
        setKey(
            key = KeyDefinition(label = "⌫", type = KeyType.DELETE),
            onPress = { deletes++ },
        )

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput {
            down(center)
            advanceEventTime(BackspaceRepeatPolicy.INITIAL_DELAY_MS + 250L)
        }
        composeRule.mainClock.advanceTimeBy(BackspaceRepeatPolicy.INITIAL_DELAY_MS + 250L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("⌫").assertCountEquals(1)
        composeRule.runOnIdle { assertTrue("Delete repeat must continue while held", deletes > 1) }

        composeRule.onNodeWithTag(KEY_TAG).performTouchInput { up() }
    }

    private fun setKey(
        key: KeyDefinition,
        accents: List<String>? = null,
        onPress: () -> Unit = {},
        onTrackpadActiveChange: (Boolean) -> Unit = {},
        onTrackpadMove: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            DictusTheme {
                KeyButton(
                    key = key,
                    isShifted = false,
                    onPress = onPress,
                    accentChars = accents,
                    onAccentSelected = {},
                    hapticsEnabled = false,
                    onTrackpadActiveChange = onTrackpadActiveChange,
                    onTrackpadMove = onTrackpadMove,
                    modifier = Modifier.testTag(KEY_TAG),
                )
            }
        }
    }


    private companion object {
        const val KEY_TAG = "feedback-key"
    }
}
