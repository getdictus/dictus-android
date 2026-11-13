package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.ui.onboarding.OnboardingShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The language step on the #339 audit's 360x800 dp phone (budget65: 720x1600 at 320 dpi,
 * Android 12, 3-button navigation, #358): the keyboard preview ran under « Continuer ». It now
 * shrinks to end above the button, and keeps its size where the page has the room.
 *
 * The page is the window less 24 dp of status bar and 48 dp of navigation bar: 728 dp. The
 * model is Base, the one a phone below 6 GB gets, as on the audit's capture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w360dp-h800dp-xhdpi")
// Real text layout: the legacy mode measures text far shorter than a device and hides the overflow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLanguageShortScreenFitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `on a 360x800 dp phone the preview ends above Continuer, with a gap`() {
        val (preview, button) = setPage(height = 728.dp)

        assertTrue(
            "preview bottom ${preview.bottom} must sit 16 dp above the button top ${button.top}",
            preview.bottom + 16.dp <= button.top,
        )
    }

    @Test
    fun `with the room for it the preview keeps its full size`() {
        val (preview, _) = setPage(height = 900.dp)

        // 0.8 of the keyboard, as before #358, and 4 dp above and below it.
        assertEquals(FULL_PREVIEW_HEIGHT.value, preview.bottom.value - preview.top.value, 0.5f)
    }

    private fun setPage(height: Dp): Pair<DpRect, DpRect> {
        composeTestRule.setContent {
            DictusTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = height)) {
                    OnboardingShell(step = OnboardingStep.Language, onBack = {}, onSkip = {}) {
                        OnboardingLanguageScreen(
                            setup = LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY),
                            model = ModelCatalog.findByKey("base")!!,
                            onLanguage = {},
                            onLayout = {},
                            onContinue = {},
                        )
                    }
                }
            }
        }
        val preview = composeTestRule.onNodeWithTag(LANGUAGE_PREVIEW_TAG).getUnclippedBoundsInRoot()
        val button = composeTestRule.onNode(hasText("Continuer") and hasClickAction()).getUnclippedBoundsInRoot()
        return preview to button
    }

    private companion object {
        /** Measured on develop before #358, on this configuration. */
        val FULL_PREVIEW_HEIGHT = 181.dp
    }
}
