package dev.pivisolutions.dictus.pro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProHomeCardContent
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Dictus Pro hub and the Home Pro card (#207), in the default (English) locale. */
@RunWith(RobolectricTestRunner::class)
class ProHubScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val forced = ProViewModel.State(isPro = true, isPaid = false, debugForced = true)

    @Test
    fun `the hub shows the hero, the history and voice notes cards on, the Debug notice and the legal links`() {
        composeRule.setContent { DictusTheme { ProHubScreen(forced, onClose = {}, onOpenFeature = {}) } }

        composeRule.onNodeWithText("Dictus Pro").assertIsDisplayed()
        composeRule.onNodeWithText("Your voice, unlimited").assertIsDisplayed()
        composeRule.onNodeWithText("History search").assertIsDisplayed()
        composeRule.onNodeWithText("Voice notes").assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText("On").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Pro is forced on", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Terms of Service").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Smart Mode").assertDoesNotExist()
    }

    @Test
    fun `a switched-off feature reads Off and still opens`() {
        var opened: ProFeature? = null
        val state = forced.copy(switches = mapOf(ProFeature.HISTORY to false))
        composeRule.setContent { DictusTheme { ProHubScreen(state, onClose = {}, onOpenFeature = { opened = it }) } }

        composeRule.onNodeWithText("Off").assertIsDisplayed()
        composeRule.onNodeWithTag("pro_hub_feature_history").performClick()
        assertEquals(ProFeature.HISTORY, opened)
    }

    @Test
    fun `the voice notes card opens the voice notes`() {
        var opened: ProFeature? = null
        composeRule.setContent { DictusTheme { ProHubScreen(forced, onClose = {}, onOpenFeature = { opened = it }) } }

        composeRule.onNodeWithTag("pro_hub_feature_voice_notes").performClick()
        assertEquals(ProFeature.VOICE_NOTES, opened)
    }

    @Test
    fun `a purchase hides the Debug notice and the cross closes`() {
        var closed = 0
        val paid = ProViewModel.State(isPro = true, isPaid = true)
        composeRule.setContent { DictusTheme { ProHubScreen(paid, onClose = { closed++ }, onOpenFeature = {}) } }

        composeRule.onNodeWithText("Pro is forced on", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("pro_hub_close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `the member card counts the features on and opens the hub`() {
        var opened = 0
        composeRule.setContent {
            DictusTheme { ProHomeCardView(ProHomeCardContent.Member(1), onOpen = { opened++ }) }
        }

        composeRule.onNodeWithText("1 feature on").assertIsDisplayed()
        composeRule.onNodeWithTag("home_pro_card").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `no feature on reads as a choice, and a hidden card draws nothing`() {
        composeRule.setContent {
            DictusTheme {
                ProHomeCardView(ProHomeCardContent.Member(0), onOpen = {})
                ProHomeCardView(ProHomeCardContent.Hidden, onOpen = {})
            }
        }

        composeRule.onNodeWithText("All Pro features are off").assertIsDisplayed()
        composeRule.onAllNodesWithTagCount("home_pro_card", 1)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String, count: Int) {
        assertEquals(count, onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size)
    }
}
