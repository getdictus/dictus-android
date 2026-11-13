package dev.pivisolutions.dictus.navigation

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ui.navigation.DictusBottomNavBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tab bar keeps clear of the 3-button navigation bar of an edge-to-edge window (#343)
 * and keeps its place over the gesture handle.
 */
@RunWith(RobolectricTestRunner::class)
class DictusBottomNavBarInsetsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val navBar = 48.dp
    private val gestureHandle = 24.dp

    private fun setBar() {
        composeTestRule.setContent {
            DictusTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        DictusBottomNavBar(currentRoute = AppDestination.Home.route, onNavigate = {})
                    }
                }
            }
        }
    }

    private fun px(dp: Dp): Int = with(composeTestRule.density) { dp.roundToPx() }

    /** Hands the window insets of an edge-to-edge window to the composition. */
    private fun dispatch(insets: WindowInsetsCompat) {
        composeTestRule.runOnUiThread {
            val content = composeTestRule.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        composeTestRule.waitForIdle()
    }

    private fun threeButtonBar(sides: Insets) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.navigationBars(), sides)
        .setInsets(WindowInsetsCompat.Type.tappableElement(), sides)
        .build()

    private fun rootBounds() = composeTestRule.onRoot().getBoundsInRoot()
    private fun tabBounds(label: String) = composeTestRule.onNodeWithText(label).getBoundsInRoot()

    @Test
    fun `the tabs sit above a 3-button navigation bar at the bottom`() {
        setBar()
        dispatch(threeButtonBar(Insets.of(0, 0, 0, px(navBar))))

        val clearOfNavBar = rootBounds().bottom - navBar
        for (label in listOf("Home", "Models", "Settings")) {
            val bottom = tabBounds(label).bottom
            assertTrue("$label ends at $bottom, under the navigation bar from $clearOfNavBar", bottom <= clearOfNavBar)
        }
    }

    @Test
    fun `the tabs stay clear of a 3-button navigation bar on the side in landscape`() {
        setBar()
        dispatch(threeButtonBar(Insets.of(0, 0, px(navBar), 0)))

        val clearOfNavBar = rootBounds().right - navBar
        val right = tabBounds("Settings").right
        assertTrue("Settings ends at $right, under the navigation bar from $clearOfNavBar", right <= clearOfNavBar)
    }

    @Test
    fun `gesture navigation keeps the bar where it was`() {
        setBar()
        val withoutInsets = tabBounds("Models")

        // The gesture handle is a navigation bar and a gesture area, not a tappable element.
        val handle = Insets.of(0, 0, 0, px(gestureHandle))
        dispatch(
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), handle)
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), handle)
                .setInsets(WindowInsetsCompat.Type.systemGestures(), handle)
                .build(),
        )

        assertEquals(withoutInsets, tabBounds("Models"))
    }
}
