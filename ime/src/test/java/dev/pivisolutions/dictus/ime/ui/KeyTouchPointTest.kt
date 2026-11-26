package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import dev.pivisolutions.dictus.ime.spatial.KeyTouches
import dev.pivisolutions.dictus.ime.spatial.LocalKeyTouches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The point a letter key reports to the spatial decoder (#385) is where the finger landed, in
 * window pixels (#413): the key's gestures run on its touch area (#400), which starts before the
 * drawn key, so the point must not carry that margin. AZERTY letters on the Pixel 4 screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h829dp-440dpi")
class KeyTouchPointTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a letter tap reports the window point the finger landed on`() {
        val keyTouches = KeyTouches()
        lateinit var density: Density
        var gridOrigin = Offset.Zero
        var gridWidth = 0f
        composeRule.setContent {
            density = LocalDensity.current
            DictusTheme {
                CompositionLocalProvider(LocalKeyTouches provides keyTouches) {
                    KeyboardView(
                        layer = KeyboardLayer.LETTERS,
                        isShifted = false,
                        layout = "azerty",
                        onKeyPress = {},
                        onAccentSelected = {},
                        modifier = Modifier
                            .testTag(GRID)
                            .onGloballyPositioned {
                                gridOrigin = it.positionInWindow()
                                gridWidth = it.size.width.toFloat()
                            }
                            .height(LocalKeyboardSizing.current.keyAreaHeight),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // The middle of `s`, the second row's second key: its touch area reaches past the drawn key
        // on the left and at the top, by half a gap and by half the gap between two rows.
        val px = density.density
        val unit = (gridWidth - 2 * ROW_EDGE_PADDING.value * px - 9 * KEY_GAP.value * px) / 10f
        val tap = Offset(
            x = ROW_EDGE_PADDING.value * px + KEY_GAP.value * px + 1.5f * unit,
            y = 2.dp.value * px + keyboardSizing(393f, 829f, 1f).rowHeight.value * px * 1.5f,
        )
        composeRule.onNodeWithTag(GRID).performTouchInput { click(tap) }
        composeRule.waitForIdle()

        val point = keyTouches.take("s")
        assertNotNull(point)
        assertEquals((gridOrigin.x + tap.x).toDouble(), point!!.x, 0.5)
        assertEquals((gridOrigin.y + tap.y).toDouble(), point.y, 0.5)
    }

    private companion object {
        const val GRID = "grid"
    }
}
