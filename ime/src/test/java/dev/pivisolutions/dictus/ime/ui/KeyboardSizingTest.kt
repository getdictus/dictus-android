package dev.pivisolutions.dictus.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The height rule of #127, on screen sizes in dp (what the device reports once its density is
 * applied). Profiles: small phone 360x640, Pixel 4 393x830, large phone 412x915, foldable inner
 * 673x841, tablet 800x1280, each also turned to landscape.
 */
class KeyboardSizingTest {

    private fun sizing(width: Float, height: Float, fontScale: Float = 1f) =
        keyboardSizing(screenWidthDp = width, screenHeightDp = height, fontScale = fontScale)

    @Test fun `the keyboard is the toolbar plus four rows and the grid padding`() {
        val s = sizing(393f, 830f)

        assertEquals(s.rowHeightDp * 4 + 4f, s.keyAreaHeightDp, 0.01f)
        assertEquals(52f + s.keyAreaHeightDp, s.totalHeightDp, 0.01f)
    }

    @Test fun `rows grow with the screen's narrow side`() {
        val small = sizing(360f, 640f).rowHeightDp
        val pixel4 = sizing(393f, 830f).rowHeightDp
        val large = sizing(412f, 915f).rowHeightDp
        val tablet = sizing(800f, 1280f).rowHeightDp

        assertTrue("small $small < pixel4 $pixel4", small < pixel4)
        assertTrue("pixel4 $pixel4 < large $large", pixel4 < large)
        assertTrue("large $large < tablet $tablet", large < tablet)
        assertEquals(MAX_ROW_HEIGHT_DP, tablet, 0.01f)
    }

    @Test fun `a phone in portrait keeps its rows near iOS's 54 per row`() {
        assertEquals(53f, sizing(393f, 830f).rowHeightDp, 0.5f)
        assertEquals(48.6f, sizing(360f, 640f).rowHeightDp, 0.5f)
    }

    @Test fun `portrait never takes more than 40 percent of the screen`() {
        // A short, wide screen: the width alone would ask for tall rows.
        val s = sizing(500f, 600f)

        assertTrue("total ${s.totalHeightDp}", s.totalHeightDp <= 0.40f * 600f + 0.01f)
    }

    // The Pixel 4 report (#127): the old 316 dp keyboard left 77 dp of a 393 dp screen.
    @Test fun `a phone in landscape leaves the app at least 40 percent of the height`() {
        for ((w, h) in listOf(640f to 360f, 830f to 393f, 915f to 412f)) {
            val s = sizing(w, h)
            assertTrue("$w x $h: total ${s.totalHeightDp}", s.totalHeightDp <= 0.60f * h)
            assertEquals("$w x $h rows hit the floor", MIN_ROW_HEIGHT_DP, s.rowHeightDp, 0.01f)
        }
    }

    @Test fun `a tablet in landscape stays under half the height`() {
        val s = sizing(1280f, 800f)

        assertEquals(MAX_ROW_HEIGHT_DP, s.rowHeightDp, 0.01f)
        assertTrue("total ${s.totalHeightDp}", s.totalHeightDp <= 0.50f * 800f)
    }

    @Test fun `rows never shrink below the floor, even on a tiny screen`() {
        assertEquals(MIN_ROW_HEIGHT_DP, sizing(568f, 320f).rowHeightDp, 0.01f)
        assertEquals(MIN_ROW_HEIGHT_DP, sizing(240f, 320f).rowHeightDp, 0.01f)
    }

    @Test fun `the height does not depend on the font size`() {
        assertEquals(sizing(393f, 830f).totalHeightDp, sizing(393f, 830f, fontScale = 2f).totalHeightDp, 0.01f)
    }

    @Test fun `key labels follow the font size up to 1_3 times`() {
        assertEquals(0.85f, sizing(393f, 830f, fontScale = 0.85f).labelFontScale, 0.001f)
        assertEquals(1.15f, sizing(393f, 830f, fontScale = 1.15f).labelFontScale, 0.001f)
        assertEquals(MAX_LABEL_FONT_SCALE, sizing(393f, 830f, fontScale = 2f).labelFontScale, 0.001f)
    }
}
