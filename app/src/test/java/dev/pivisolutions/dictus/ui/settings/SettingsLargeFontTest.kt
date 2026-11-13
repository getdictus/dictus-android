package dev.pivisolutions.dictus.ui.settings

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
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With the largest font and display size, every Settings label stays readable on whole lines
 * (#350, defect V3 of the #339 audit): « Langue » was cut into « La / n » next to « Suivre la
 * langue du clavier », « Version » squeezed to a stroke next to the version number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "fr-w411dp-h914dp-420dpi")
// Real text layout: the legacy mode measures text far shorter than a device and hides the overflow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLargeFontTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `with font scale 1_3 on a 341 dp screen the short labels keep one line`() {
        // police: 1080x2340 at 506 dpi (341 dp wide), font scale 1.3.
        setSettings(width = 341.dp, fontScale = 1.3f)

        assertEquals(1, layout("Langue").lineCount)
        assertEquals(1, layout("Version").lineCount)
    }

    @Test
    fun `with font scale 1_3 on a 341 dp screen no label or value is cut`() {
        setSettings(width = 341.dp, fontScale = 1.3f)

        (LABELS + VALUES).forEach { assertWhole(it) }
        // The version is one identifier: it may wrap anywhere, but stays whole.
        assertWhole(VERSION, wordBreaks = false)
    }

    @Test
    fun `at the default font on a 393 dp screen every label and value keeps one line`() {
        // The Pixel 4 and the audit's normal profiles: nothing wraps, as before #350.
        setSettings(width = 393.dp, fontScale = 1f)

        (LABELS + VALUES + VERSION).forEach { assertEquals("« $it » lines", 1, layout(it).lineCount) }
    }

    private fun setSettings(width: Dp, fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(FakeDataStore(), ModelManager(context))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DictusTheme {
                    Box(modifier = Modifier.width(width)) { SettingsScreen(viewModel = viewModel) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Drawn in full: no line clipped by the row's height, no line broken inside a word. */
    private fun assertWhole(text: String, wordBreaks: Boolean = true) {
        val layout = layout(text)
        // Not hasVisualOverflow: the semantics layout keeps the max width, so any text narrower
        // than its slot reads as overflowing in width.
        assertFalse("« $text » is clipped by its row's height", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) {
            assertTrue("« $text » line $line runs past its width", layout.getLineRight(line) <= layout.size.width + 0.5f)
        }
        if (!wordBreaks) return
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val breaksAtWordEnd = text[end - 1] == ' ' || text[end - 1] == '-' || text.getOrNull(end) == ' '
            assertTrue("« $text » is broken inside a word after « ${text.substring(0, end)} »", breaksAtWordEnd)
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        val LABELS = listOf(
            "Langue", "Langue du clavier", "Retour haptique", "Sons du clavier", "Sons", "Suggestions",
            "Correction automatique", "Réinitialiser les mots appris", "Thème", "Langue de l'interface", "Version", "GitHub",
            "Politique de confidentialité", "Conditions d’utilisation", "Licences", "Debug Logs",
            "Exporter les logs de débogage",
        )
        val VALUES = listOf(
            "Suivre la langue du clavier", "Français", "Sombre", "Automatique",
        )
        val VERSION = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
}
