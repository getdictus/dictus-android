package dev.pivisolutions.dictus.ui.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Settings tab follows the iOS layout: large title, sentence-case sections, inline choices (#189). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "fr")
class SettingsLayoutTest {

    @get:Rule val composeRule = createComposeRule()

    private val dataStore = FakeDataStore()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = SettingsViewModel(dataStore, ModelManager(context))
        composeRule.setContent { DictusTheme { SettingsScreen(viewModel = viewModel) } }
    }

    @Test
    fun `shows the large Réglages title`() {
        composeRule.onNodeWithText("Réglages").assertExists()
    }

    @Test
    fun `section headers are in sentence case, in the iOS order`() {
        val headers = listOf("Transcription", "Clavier", "Apparence", "À propos")
        val tops = headers.map { top(it) }
        assertEquals(tops.sorted(), tops)
        listOf("TRANSCRIPTION", "CLAVIER", "APPARENCE", "À PROPOS").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun `default mode is an inline ABC 123 segment writing the same preference`() {
        composeRule.onNodeWithText("ABC").assertIsSelected()

        composeRule.onNodeWithText("123").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("123", pref(PreferenceKeys.KEYBOARD_MODE))
        composeRule.onNodeWithText("123").assertIsSelected()
        // The former bottom sheet's title never shows.
        composeRule.onNodeWithText("Mode par défaut du clavier").assertDoesNotExist()

        composeRule.onNodeWithText("ABC").performClick()
        composeRule.waitForIdle()
        assertEquals("abc", pref(PreferenceKeys.KEYBOARD_MODE))
    }

    /** #134: each language has its own layout; the single row is gone until the keyboard picker (#191). */
    @Test
    fun `no single keyboard layout row`() {
        composeRule.onNodeWithText("Disposition du clavier").assertDoesNotExist()
        composeRule.onNodeWithText("Disposition").assertDoesNotExist()
    }

    @Test
    fun `theme picks inline from a menu, not a sheet`() {
        composeRule.onNodeWithText("Thème").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thème de l'application").assertDoesNotExist()

        composeRule.onNodeWithText("Clair").performClick()
        composeRule.waitForIdle()
        assertEquals("light", pref(PreferenceKeys.THEME))
    }

    @Test
    fun `suggestions toggle writes the preference in place`() {
        composeRule.onNodeWithText("Suggestions").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(false, pref(PreferenceKeys.SUGGESTIONS_ENABLED))
    }

    // positionInRoot, not boundsInRoot: the latter is clipped to the screen, and the lower
    // sections sit below the fold.
    private fun top(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun <T> pref(key: Preferences.Key<T>): T? = runBlocking { dataStore.data.first()[key] }
}
