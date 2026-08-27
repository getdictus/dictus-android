package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.model.FrenchAdaptiveKey
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrenchAdaptiveKeyUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `AZERTY letters renders reactive policy label`() {
        composeRule.setContent {
            DictusTheme {
                KeyboardView(
                    layer = KeyboardLayer.LETTERS,
                    isShifted = false,
                    layout = "azerty",
                    onKeyPress = {},
                    onAccentSelected = {},
                    frenchAdaptiveKeyState = FrenchAdaptiveKey.fromContext("E"),
                )
            }
        }

        composeRule.onNodeWithText("É", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `adaptive label is absent from QWERTY letters`() {
        composeRule.setContent {
            DictusTheme {
                KeyboardView(
                    layer = KeyboardLayer.LETTERS,
                    isShifted = false,
                    layout = "qwerty",
                    onKeyPress = {},
                    onAccentSelected = {},
                    frenchAdaptiveKeyState = FrenchAdaptiveKey.fromContext("e"),
                )
            }
        }
        composeRule.onNodeWithText("é", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `adaptive label is absent from non-letter layer`() {
        composeRule.setContent {
            DictusTheme {
                KeyboardView(
                    layer = KeyboardLayer.NUMBERS,
                    isShifted = false,
                    layout = "azerty",
                    onKeyPress = {},
                    onAccentSelected = {},
                    frenchAdaptiveKeyState = FrenchAdaptiveKey.fromContext("e"),
                )
            }
        }
        composeRule.onNodeWithText("é", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `adaptive long press shows exact iOS variants`() {
        val state = FrenchAdaptiveKey.fromContext("e")
        composeRule.setContent {
            DictusTheme {
                KeyButton(
                    key = KeyDefinition(state.label, type = KeyType.ACCENT_ADAPTIVE),
                    isShifted = false,
                    onPress = {},
                    accentChars = state.variants,
                    onAccentSelected = {},
                    hapticsEnabled = false,
                    modifier = Modifier.testTag("adaptive"),
                )
            }
        }

        composeRule.onNodeWithTag("adaptive").performTouchInput {
            down(center)
            advanceEventTime(450L)
        }
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()

        val expectedCounts = mapOf("é" to 2, "è" to 1, "ê" to 1, "ë" to 1)
        expectedCounts.forEach { (variant, count) ->
            composeRule.onAllNodesWithText(variant).assertCountEquals(count)
        }
        composeRule.onNodeWithTag("adaptive").performTouchInput { up() }
    }
}
