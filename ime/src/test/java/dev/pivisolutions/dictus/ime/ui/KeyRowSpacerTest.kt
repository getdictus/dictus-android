package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayouts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A spacer on row 3 of the numbers page types nothing and plays nothing, as on iOS, where
 * `DictusKeyboardBridge` ignores a `.spacer` trigger and `KeySoundPolicy` gives it no sound.
 */
@RunWith(RobolectricTestRunner::class)
class KeyRowSpacerTest {
    @get:Rule val composeRule = createComposeRule()

    private val pressed = mutableListOf<KeyDefinition>()
    private val sounds = mutableListOf<KeyType>()

    @Test
    fun `tap on a spacer types nothing and plays nothing`() {
        setRow()

        // Leading spacer: after the 1.5-unit #+= key and one gap, half of its 0.5 unit.
        tapAt(keysBefore = 1, unitsBefore = 1.5f + 0.25f)

        assertEquals(emptyList<KeyDefinition>(), pressed)
        assertEquals(emptyList<KeyType>(), sounds)
    }

    @Test
    fun `tap next to the spacer reaches the period key`() {
        setRow()

        // Period key: after #+=, the spacer and two gaps, half of its 1.2 units.
        tapAt(keysBefore = 2, unitsBefore = 1.5f + 0.5f + 0.6f)

        assertEquals(listOf("."), pressed.map { it.output })
        assertEquals(listOf(KeyType.CHARACTER), sounds)
    }

    private fun setRow() {
        composeRule.setContent {
            DictusTheme {
                KeyRow(
                    keys = KeyboardLayouts.numbersRows[2],
                    isShifted = false,
                    onKeyPress = { pressed += it },
                    onAccentSelected = {},
                    onKeySound = { sounds += it },
                    modifier = Modifier.testTag(ROW_TAG),
                )
            }
        }
    }

    /**
     * Taps the row at the point that sits [unitsBefore] width units and [keysBefore] 4 dp gaps
     * from its 2 dp start padding. The row's 10 units share its width minus the padding (2 x 2 dp)
     * and the 8 gaps between its 9 items, which is how KeyRow weighs them.
     */
    private fun tapAt(keysBefore: Int, unitsBefore: Float) {
        composeRule.onNodeWithTag(ROW_TAG).performTouchInput {
            val unit = (width - (4 + 8 * 4).dp.toPx()) / 10f
            val x = 2.dp.toPx() + keysBefore * 4.dp.toPx() + unitsBefore * unit
            click(Offset(x, height / 2f))
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val ROW_TAG = "row3"
    }
}
