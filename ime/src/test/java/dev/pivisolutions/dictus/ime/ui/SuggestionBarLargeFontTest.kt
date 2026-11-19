package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With the largest font, a suggestion is drawn smaller before it is cut (#350, defect C2 of the
 * #339 audit: « Bonjours » became « Bonjou… »), never smaller than at the default font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
// Real text layout: the legacy mode measures text far shorter than a device and hides the overflow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestionBarLargeFontTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `with font scale 1_3 on a 341 dp screen Bonjours is not cut`() {
        // police: 341 dp wide; the bar is what the toolbar leaves beside the mic:
        // 341 - 2 x 8 dp padding - 4 dp spacing - 56 dp mic = 265 dp.
        setBar(width = 265.dp, fontScale = 1.3f)

        listOf("Bonj", "Bonjour", "Bonjours").forEach {
            assertFalse("« $it » is cut", layout(it).isLineEllipsized(0))
        }
    }

    @Test
    fun `at the default font the suggestions keep their 14 sp`() {
        // The Pixel 4: 393 - 76 = 317 dp.
        setBar(width = 317.dp, fontScale = 1f)

        listOf("Bonj", "Bonjour", "Bonjours").forEach {
            assertEquals("« $it » size", 14f, layout(it).layoutInput.style.fontSize.value, 0.01f)
        }
    }

    @Test
    fun `the scale is 1 when the word fits`() {
        assertEquals(1f, suggestionTextScale(available = 100, fontScale = 1.3f) { (80 * it).toInt() })
    }

    @Test
    fun `a word too wide shrinks to fit, down to its default-font size`() {
        assertEquals(0.9f, suggestionTextScale(available = 90, fontScale = 1.3f) { (100 * it).toInt() }, 0.001f)
        assertEquals(1f / 1.3f, suggestionTextScale(available = 90, fontScale = 1.3f) { (200 * it).toInt() }, 0.001f)
    }

    @Test
    fun `a word wider than its scaled width steps down until it fits`() {
        // Rounded glyph positions: 3 px wider than linear scaling predicts.
        val scale = suggestionTextScale(available = 90, fontScale = 1.3f) { (100 * it).toInt() + 3 }

        assertEquals(true, (100 * scale).toInt() + 3 <= 90)
    }

    @Test
    fun `at the default font a word too wide is never shrunk`() {
        assertEquals(1f, suggestionTextScale(available = 90, fontScale = 1f) { (200 * it).toInt() })
        assertEquals(1f, suggestionTextScale(available = 90, fontScale = 0.85f) { (200 * it).toInt() })
    }

    private fun setBar(width: Dp, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DictusTheme {
                    Box(modifier = Modifier.width(width)) {
                        SuggestionBar(
                            currentWord = "Bonj",
                            suggestions = listOf("Bonjour", "Bonjours"),
                            onSuggestionSelected = { _, _ -> },
                            onCurrentWordSelected = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }
}
