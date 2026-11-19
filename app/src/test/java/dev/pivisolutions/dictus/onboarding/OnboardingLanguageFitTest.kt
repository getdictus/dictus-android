package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.ui.onboarding.OnboardingShell
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The language step on Pierre's Pixel 4 (1080x2280 at 440 dpi, font scale 1.0, #198): the
 * keyboard preview must end above « Continuer », with the page's bottom gap between them.
 *
 * The Pixel's window is 393 x 829 dp, less 24 dp of status bar and 24 dp of navigation bar
 * (66 px each, read on the emulator at the same size): 781 dp for the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w393dp-h829dp-440dpi")
// Real text layout: the legacy mode measures text far shorter than a device and hides the overflow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLanguageFitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `on a Pixel 4 the preview ends above Continuer, with a gap`() {
        composeTestRule.setContent {
            DictusTheme {
                Box(modifier = Modifier.size(width = 393.dp, height = 781.dp)) {
                    OnboardingShell(step = OnboardingStep.Language, onBack = {}, onSkip = {}) {
                        OnboardingLanguageScreen(
                            setup = LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.AZERTY),
                            // The model the Pixel 4 (6 GB) gets: its line runs to three lines.
                            model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!,
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

        assertTrue(
            "preview bottom ${preview.bottom} must sit 16 dp above the button top ${button.top}",
            preview.bottom + 16.dp <= button.top,
        )
    }
}
