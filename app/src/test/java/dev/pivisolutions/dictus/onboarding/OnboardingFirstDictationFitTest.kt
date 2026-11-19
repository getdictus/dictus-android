package dev.pivisolutions.dictus.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ui.onboarding.LocalOnboardingSkip
import dev.pivisolutions.dictus.ui.onboarding.OnboardingShell
import dev.pivisolutions.dictus.ui.onboarding.OnboardingSkipAction
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first dictation with the Dictus keyboard up, on the screens of the #339 audit (#344):
 * the field keeps two lines and the mic banner and « Passer » stay above the keyboard. Turned
 * to landscape the keyboard leaves less than that, and the field fills what is left (#355).
 *
 * Each page is the height the audit's emulator left between the onboarding's top row and the
 * keyboard, with the keyboard of the audit (before #127 sized it from the screen): the tighter
 * case, about 90 dp shorter than today on the small phone. Android 11, as the large-font profile: below Android 15 the focused field counts
 * as the keyboard being up, which Robolectric cannot show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "fr-w411dp-h914dp-420dpi")
// Real text layout: the legacy mode measures text far shorter than a device and hides the overflow.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingFirstDictationFitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `on a 360x640 dp phone the field, the banner and Passer stay above the keyboard`() {
        // petit55: 540x960 at 240 dpi, keyboard top at 276 dp, 24 dp of status bar, 40 dp of top row.
        assertFits(width = 360.dp, height = 212.dp, fontScale = 1f)
    }

    @Test
    fun `with font scale 1_3 and a larger display size they stay above the keyboard`() {
        // police: 1080x2340 at 506 dpi (341 dp wide), keyboard top at 376 dp.
        assertFits(width = 341.dp, height = 312.dp, fontScale = 1.3f)
    }

    @Test
    fun `on a standard phone the title block is whole above the field`() {
        // std61: 1080x2400 at 420 dpi, keyboard top at 550 dp.
        val page = setPage(width = 411.dp, height = 486.dp, fontScale = 1f)
        val title = bounds(composeTestRule.onNodeWithText("Essayez maintenant").getUnclippedBoundsInRoot())
        val subtitle = bounds(composeTestRule.onNodeWithText(SUBTITLE).getUnclippedBoundsInRoot())
        val field = bounds(composeTestRule.onNodeWithTag(FIRST_DICTATION_FIELD_TAG).getUnclippedBoundsInRoot())

        assertTrue("title top ${title.top} must be on the page", title.top >= 0f)
        assertTrue("subtitle bottom ${subtitle.bottom} must be above the field top ${field.top}", subtitle.bottom <= field.top)
        assertFieldAndBottomFit(page, fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "fr-w640dp-h360dp-240dpi")
    fun `on a 360 dp tall phone in landscape the field fills the page and the step scrolls`() {
        // petit55 turned: 640x360 dp, 24 dp of status bar, keyboard 216 dp (#127).
        assertLandscapeField(width = 640.dp, height = 120.dp, fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "fr-w740dp-h341dp-506dpi")
    fun `in landscape with font scale 1_3 one line of the field stays above the keyboard`() {
        // police turned: 740x341 dp, keyboard 216 dp plus the 48 dp navigation band.
        assertLandscapeField(width = 740.dp, height = 53.dp, fontScale = 1.3f)
    }

    @Test
    @Config(qualifiers = "fr-w841dp-h337dp-420dpi")
    fun `on a folded phone in landscape one line of the field stays above the keyboard`() {
        // fold-ferme turned: 841x337 dp, keyboard 216 dp plus the 48 dp navigation band.
        assertLandscapeField(width = 841.dp, height = 49.dp, fontScale = 1f)
    }

    /**
     * The step in the shell, in a window of [width] x [height] below the status bar and above
     * the keyboard (#355): the top row folds, the field fills the window and keeps a whole line,
     * and the title and « Passer » are a scroll away.
     */
    private fun assertLandscapeField(width: Dp, height: Dp, fontScale: Float) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DictusTheme {
                    Box(modifier = Modifier.size(width = width, height = height)) {
                        OnboardingShell(step = OnboardingStep.FirstDictation, onBack = {}, onSkip = {}) {
                            OnboardingTestRecordingScreen(isDictusKeyboard = true, onNext = {})
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val window = height.value
        val card = bounds(composeTestRule.onNodeWithTag(FIRST_DICTATION_FIELD_TAG).getUnclippedBoundsInRoot())
        val line = Density(1f, fontScale).run { 22.sp.toDp() }.value

        composeTestRule.onAllNodesWithContentDescription("Étape", substring = true).assertCountEquals(0)
        assertTrue("field text top ${card.top} must be in the window", card.top >= 0f)
        assertTrue("field text bottom ${card.bottom} must be above the keyboard ($window)", card.bottom <= window + 0.5f)
        assertTrue("field text height ${card.height} must hold a line ($line)", card.height >= line - 0.5f)

        composeTestRule.onNodeWithText("Passer").performScrollTo().assertIsDisplayed()
        // The title sits in its own scroll inside the page's: swipe down as a user would.
        composeTestRule.onRoot().performTouchInput { repeat(12) { swipeDown() } }
        composeTestRule.onNodeWithText("Essayez maintenant").assertIsDisplayed()
    }

    private fun assertFits(width: Dp, height: Dp, fontScale: Float) {
        val page = setPage(width, height, fontScale)
        assertFieldAndBottomFit(page, fontScale)
    }

    private fun assertFieldAndBottomFit(page: Rect, fontScale: Float) {
        val field = bounds(composeTestRule.onNodeWithTag(FIRST_DICTATION_FIELD_TAG).getUnclippedBoundsInRoot())
        val banner = bounds(composeTestRule.onNodeWithText(BANNER).getUnclippedBoundsInRoot())
        val skip = bounds(composeTestRule.onNodeWithText("Passer").getUnclippedBoundsInRoot())
        // The tagged text field sits inside the card's padding: two 22 sp lines, which Compose
        // scales non-linearly (22 sp is 25 dp at 1.3, not 28.6).
        val twoLines = 2 * Density(1f, fontScale).run { 22.sp.toDp() }.value

        assertTrue("field height ${field.height} must hold two lines ($twoLines)", field.height >= twoLines - 0.5f)
        assertTrue("field top ${field.top} must be on the page", field.top >= page.top)
        assertTrue("field bottom ${field.bottom} must be above the banner top ${banner.top}", field.bottom <= banner.top)
        assertTrue("banner bottom ${banner.bottom} must be above Passer top ${skip.top}", banner.bottom <= skip.top)
        assertTrue("Passer bottom ${skip.bottom} must be above the keyboard (${page.bottom})", skip.bottom <= page.bottom)
    }

    /** Draws the step in a page of [width] x [height], as above the keyboard; returns the page in dp. */
    private fun setPage(width: Dp, height: Dp, fontScale: Float): Rect {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalOnboardingSkip provides OnboardingSkipAction("Passer") {},
            ) {
                DictusTheme {
                    Box(modifier = Modifier.size(width = width, height = height)) {
                        OnboardingTestRecordingScreen(isDictusKeyboard = true, onNext = {})
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        return Rect(0f, 0f, width.value, height.value)
    }

    private fun bounds(rect: DpRect) = Rect(rect.left.value, rect.top.value, rect.right.value, rect.bottom.value)

    private companion object {
        const val SUBTITLE = "Touchez le micro bleu et parlez. Le texte arrive ici."
        const val BANNER = "Touchez le micro bleu, puis parlez"
    }
}
