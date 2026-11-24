package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every point of the key grid types the nearest key, as on iOS (#400): the gaps between two keys
 * and two rows, the screen edge and a key's rounded corner. AZERTY letters on the Pixel 4 screen.
 *
 * A key narrower or shorter than 48 dp already reaches past its drawing for a finger: Compose
 * gives it the minimum touch target. These taps sit where that does not reach, next to keys at
 * least 48 dp wide and tall (space, return, delete) and between two rows of 49 dp keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h829dp-440dpi")
class KeyTouchAreaTest {
    @get:Rule val composeRule = createComposeRule()

    private val typed = mutableListOf<String>()
    private lateinit var density: Density
    private var gridWidth = 0f

    @Before
    fun setKeyboard() {
        composeRule.setContent {
            density = LocalDensity.current
            DictusTheme {
                KeyboardView(
                    layer = KeyboardLayer.LETTERS,
                    isShifted = false,
                    layout = "azerty",
                    onKeyPress = { typed += if (it.type == KeyType.CHARACTER) it.output else it.type.name },
                    onAccentSelected = {},
                    modifier = Modifier
                        .testTag(GRID)
                        .onGloballyPositioned { gridWidth = it.size.width.toFloat() }
                        .height(LocalKeyboardSizing.current.keyAreaHeight),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap in the gap between two keys types the nearer one`() {
        tap(x = keyRight(BOTTOM, 2) + 1.dp.px, y = rowCentre(3))
        tap(x = keyLeft(BOTTOM, 3) - 1.dp.px, y = rowCentre(3))

        assertEquals(listOf("SPACE", "RETURN"), typed)
    }

    @Test
    fun `a tap in the gap between two rows types the nearer row`() {
        tap(x = keyCentre(LETTERS, 0), y = keyBottom(0) + 1.dp.px)
        tap(x = keyCentre(LETTERS, 0), y = keyTop(1) - 1.dp.px)

        assertEquals(listOf("a", "q"), typed)
    }

    @Test
    fun `a tap at the screen edge types the edge key`() {
        tap(x = gridWidth - 1.dp.px, y = rowCentre(2))

        assertEquals(listOf("DELETE"), typed)
    }

    @Test
    fun `a tap in a key's rounded corner types that key`() {
        tap(x = keyLeft(BOTTOM, 2) + 1.dp.px, y = keyTop(3) + 1.dp.px)

        assertEquals(listOf("SPACE"), typed)
    }

    @Test
    fun `the strip above the top row and the strip below the bottom row type nothing`() {
        tap(x = keyCentre(LETTERS, 0), y = keyTop(0) - 1.dp.px)
        tap(x = gridWidth / 2f, y = keyBottom(3) + 1.dp.px)

        assertEquals(emptyList<String>(), typed)
    }

    private val androidx.compose.ui.unit.Dp.px: Float get() = value * density.density

    private val rowHeight: Float get() = keyboardSizing(393f, 829f, 1f).rowHeight.px

    /** A row's keys in width units; every row totals 10. */
    private class Row(val units: List<Float>)

    /** The row's width less its 2 dp padding at each end and the gaps between its keys, per unit. */
    private fun unit(row: Row) = (gridWidth - 2 * ROW_EDGE_PADDING.px - (row.units.size - 1) * KEY_GAP.px) / 10f

    private fun keyLeft(row: Row, index: Int) =
        ROW_EDGE_PADDING.px + index * KEY_GAP.px + row.units.take(index).sum() * unit(row)
    private fun keyRight(row: Row, index: Int) = keyLeft(row, index) + row.units[index] * unit(row)
    private fun keyCentre(row: Row, index: Int) = (keyLeft(row, index) + keyRight(row, index)) / 2f

    /** Rows sit under the grid's own 2 dp top padding; each drawn key is its row less 2 dp a side. */
    private fun rowTop(row: Int) = 2.dp.px + row * rowHeight
    private fun keyTop(row: Int) = rowTop(row) + KEY_VERTICAL_PADDING.px
    private fun keyBottom(row: Int) = rowTop(row) + rowHeight - KEY_VERTICAL_PADDING.px
    private fun rowCentre(row: Int) = rowTop(row) + rowHeight / 2f

    private fun tap(x: Float, y: Float) {
        composeRule.onNodeWithTag(GRID).performTouchInput { click(Offset(x, y)) }
        composeRule.waitForIdle()
    }

    private companion object {
        const val GRID = "grid"
        val LETTERS = Row(List(10) { 1f })
        /** 123, emoji, space, return. */
        val BOTTOM = Row(listOf(1.25f, 1.25f, 5.5f, 2f))
    }
}
