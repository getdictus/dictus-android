package dev.pivisolutions.dictus.vocabulary

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.swipeLeft
import dev.pivisolutions.dictus.core.premium.ProFeature
import dev.pivisolutions.dictus.core.premium.ProFeatureSwitches
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.core.vocabulary.VocabularyEntry
import dev.pivisolutions.dictus.core.vocabulary.VocabularyStore
import dev.pivisolutions.dictus.ui.settings.FakeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Vocabulary screen (#208) against the real store and the real gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class VocabularyScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val dataStore = FakeDataStore()
    private val switches = ProFeatureSwitches(dataStore, debugBuild = true)
    private val store = VocabularyStore(dataStore, isEntitled = { switches.isAvailable(ProFeature.VOCABULARY).first() })

    private fun show(pro: Boolean, vararg stored: VocabularyEntry) {
        runBlocking {
            if (pro) ProStatus(dataStore, debugBuild = true).setDebugProEntitlementForced(true)
            stored.reversed().forEach { store.add(it) }
        }
        composeRule.setContent { DictusTheme { VocabularyRoute(onBack = {}, viewModel = VocabularyViewModel(store, switches)) } }
    }

    private fun entry(term: String, vararg variants: String) = requireNotNull(VocabularyEntry.create(term, variants.toList()))

    private fun terms() = runBlocking { store.loadEntries().map { it.term } }

    @Test
    fun `with Pro a term is added from the sheet and listed with what it replaces`() {
        show(pro = true)
        composeRule.onNodeWithText("No terms yet. Add the words Dictus gets wrong.").assertIsDisplayed()

        composeRule.onNodeWithTag("vocabulary_add").performClick()
        composeRule.onNodeWithTag("vocabulary_editor_term").performTextInput("Kubernetes")
        composeRule.onNodeWithTag("vocabulary_editor_variants").performTextInput("cubernetes, kuber netes")
        composeRule.onNodeWithTag("vocabulary_editor_term").assertTextContains("Kubernetes")
        composeRule.onNodeWithTag("vocabulary_editor_save").assertIsEnabled()
        // The click action rather than a touch: under Robolectric a touch injected into the
        // sheet's window does not reach its buttons. The emulator capture covers the real tap.
        composeRule.onNodeWithTag("vocabulary_editor_save").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("Kubernetes"), terms())
        composeRule.onNodeWithText("Replaces: cubernetes, kuber netes").assertIsDisplayed()
    }

    @Test
    fun `a term without a variant says it is waiting for misspellings`() {
        show(pro = true, entry("Claude"))
        composeRule.onNodeWithText("Waiting for misspellings").assertIsDisplayed()
    }

    @Test
    fun `swiping a row to the left deletes the term`() {
        show(pro = true, entry("Kubernetes", "cubernetes"), entry("Claude", "clode"))
        composeRule.onNodeWithTag("vocabulary_row_Claude").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf("Kubernetes"), terms())
    }

    @Test
    fun `the reset asks first, then forgets every term`() {
        show(pro = true, entry("Kubernetes", "cubernetes"), entry("Claude", "clode"))
        composeRule.onNodeWithTag("vocabulary_reset").performScrollTo().performClick()
        composeRule.onNodeWithText("Forget 2 terms").performClick()
        composeRule.waitForIdle()

        assertEquals(emptyList<String>(), terms())
    }

    @Test
    fun `switching the feature off keeps the terms, says so, and makes them inert`() {
        show(pro = true, entry("Kubernetes", "cubernetes"))
        composeRule.onNodeWithTag("vocabulary_switch").performClick()
        composeRule.waitForIdle()

        assertFalse(runBlocking { switches.values.first().getValue(ProFeature.VOCABULARY) })
        composeRule.onNodeWithText(
            "Vocabulary is off. Your terms are kept, and replace nothing until you turn it back on.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("vocabulary_add").performClick()
        composeRule.onNodeWithTag("vocabulary_editor_term").assertDoesNotExist()
        composeRule.onNodeWithTag("vocabulary_row_Kubernetes").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(listOf("Kubernetes"), terms())
    }

    @Test
    fun `without Pro the terms stay visible but cannot be edited`() {
        // Stored while Pro, then Pro lapsed: the store refuses to grow, the screen keeps showing them.
        show(pro = true, entry("Kubernetes", "cubernetes"))
        runBlocking { ProStatus(dataStore, debugBuild = true).setDebugProEntitlementForced(false) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Replaces: cubernetes").assertIsDisplayed()
        composeRule.onNodeWithTag("vocabulary_add").performClick()
        composeRule.onNodeWithTag("vocabulary_editor_term").assertDoesNotExist()
    }
}
