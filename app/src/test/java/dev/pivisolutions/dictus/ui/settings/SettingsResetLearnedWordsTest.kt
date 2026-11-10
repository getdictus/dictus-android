package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** « Réinitialiser les mots appris » in Settings → Clavier asks first, then forgets (#194). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class SettingsResetLearnedWordsTest {

    @get:Rule val composeRule = createComposeRule()

    private val dataStore = FakeDataStore()

    @Before
    fun setUp() {
        runBlocking {
            dataStore.edit { it[PreferenceKeys.PERSONAL_DICTIONARY] = setOf("dictus", "bonzai") }
        }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context))
        composeRule.setContent { DictusTheme { SettingsScreen(viewModel = viewModel) } }
    }

    @Test
    fun `cancel keeps the learned words`() {
        composeRule.onNodeWithText("Reset learned words").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset learned words?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reset learned words?").assertDoesNotExist()
        assertEquals(setOf("dictus", "bonzai"), learnedWords())
    }

    @Test
    fun `confirm forgets every learned word`() {
        composeRule.onNodeWithText("Reset learned words").performScrollTo().performClick()
        composeRule.onNodeWithText("Forget 2 learned words").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Reset learned words?").assertDoesNotExist()
        assertNull(learnedWords())
    }

    private fun learnedWords() = runBlocking { dataStore.data.first()[PreferenceKeys.PERSONAL_DICTIONARY] }
}
