package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.premium.ProStatus
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Transcription → Vocabulary (#208): shown to Pro users only, and opens the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class SettingsVocabularyRowTest {

    @get:Rule val composeRule = createComposeRule()

    private val dataStore = FakeDataStore()
    private var opened = 0

    private fun show(pro: Boolean) {
        val proStatus = ProStatus(dataStore, debugBuild = true)
        if (pro) runBlocking { proStatus.setDebugProEntitlementForced(true) }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context), proStatus)
        composeRule.setContent {
            DictusTheme { SettingsScreen(viewModel = viewModel, onNavigateToVocabulary = { opened++ }) }
        }
    }

    @Test
    fun `without Pro there is no vocabulary row`() {
        show(pro = false)
        composeRule.onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun `with Pro the row opens the vocabulary`() {
        show(pro = true)
        composeRule.onNodeWithText("Vocabulary").performClick()
        assertEquals(1, opened)
    }
}
