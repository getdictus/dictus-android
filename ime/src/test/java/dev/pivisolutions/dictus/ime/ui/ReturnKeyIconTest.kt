package dev.pivisolutions.dictus.ime.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import dev.pivisolutions.dictus.core.theme.DictusTheme
import android.view.inputmethod.EditorInfo
import dev.pivisolutions.dictus.ime.input.ReturnKeyRole
import dev.pivisolutions.dictus.ime.input.ReturnKeyStyle
import dev.pivisolutions.dictus.ime.language.LanguageProfile
import dev.pivisolutions.dictus.ime.language.englishLanguageProfile
import dev.pivisolutions.dictus.ime.language.frenchLanguageProfile
import dev.pivisolutions.dictus.ime.language.germanLanguageProfile
import dev.pivisolutions.dictus.ime.language.spanishLanguageProfile
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import dev.pivisolutions.dictus.ime.model.KeyboardLayer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The return key draws a ↵ icon on every page and in every language, as iOS draws the SF
 * Symbol "return" on iPhone (`KeyView.setupReturnKey`), and keeps the profile's word as its
 * spoken label (#280). A field with an action gets that action's glyph and word instead
 * (iOS #1023).
 */
@RunWith(RobolectricTestRunner::class)
class ReturnKeyIconTest {
    @get:Rule val composeRule = createComposeRule()

    private val profile = mutableStateOf(frenchLanguageProfile)
    private val layer = mutableStateOf(KeyboardLayer.LETTERS)
    private val pressed = mutableListOf<KeyDefinition>()
    private val returnKeyStyle = mutableStateOf(ReturnKeyStyle.NEWLINE)

    @Test
    fun `return key shows an icon labelled with the spoken word on every page and language`() {
        setKeyboard()
        val profiles = listOf(
            frenchLanguageProfile,
            englishLanguageProfile,
            spanishLanguageProfile,
            germanLanguageProfile,
        )

        for (languageProfile in profiles) {
            for (keyboardLayer in KeyboardLayer.entries) {
                profile.value = languageProfile
                layer.value = keyboardLayer
                composeRule.waitForIdle()
                assertReturnKeyIsIcon(languageProfile, keyboardLayer)
            }
        }
    }

    @Test
    fun `tapping the icon still sends the return key`() {
        setKeyboard()

        composeRule.onNodeWithContentDescription("retour", useUnmergedTree = true)
            .onParent()
            .performClick()

        assertEquals(listOf(KeyType.RETURN), pressed.map { it.type })
    }

    /** The field's action names the key, in the keyboard's language (iOS #1023). */
    @Test
    fun `an action field names the key after its action on every page`() {
        setKeyboard()
        val cases = mapOf(
            EditorInfo.IME_ACTION_SEND to "envoyer",
            EditorInfo.IME_ACTION_GO to "accéder",
            EditorInfo.IME_ACTION_SEARCH to "rechercher",
            EditorInfo.IME_ACTION_DONE to "OK",
            EditorInfo.IME_ACTION_NEXT to "suivant",
        )
        cases.forEach { (action, word) ->
            for (keyboardLayer in KeyboardLayer.entries) {
                returnKeyStyle.value = ReturnKeyStyle.forEditor(action)
                layer.value = keyboardLayer
                composeRule.waitForIdle()
                composeRule.onNodeWithContentDescription(word, useUnmergedTree = true)
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
                composeRule.onAllNodesWithText(word, useUnmergedTree = true).assertCountEquals(0)
            }
        }
        profile.value = englishLanguageProfile
        returnKeyStyle.value = ReturnKeyStyle.forEditor(EditorInfo.IME_ACTION_GO)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("go", useUnmergedTree = true)
            .onParent()
            .performClick()
        assertEquals(listOf(KeyType.RETURN), pressed.map { it.type })
    }

    @Test
    fun `every role has its own glyph`() {
        val icons = ReturnKeyRole.entries.map(::returnKeyIcon)
        assertEquals(icons.size, icons.toSet().size)
    }

    private fun assertReturnKeyIsIcon(languageProfile: LanguageProfile, keyboardLayer: KeyboardLayer) {
        val word = languageProfile.returnLabel
        val where = "${languageProfile.code} $keyboardLayer"
        composeRule.onAllNodesWithText(word, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("↵", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription(word, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text), messagePrefixOnError = { where })
    }

    private fun setKeyboard() {
        composeRule.setContent {
            DictusTheme {
                KeyboardView(
                    layer = layer.value,
                    isShifted = false,
                    layout = "azerty",
                    languageProfile = profile.value,
                    onKeyPress = { pressed += it },
                    onAccentSelected = {},
                    returnKeyStyle = returnKeyStyle.value,
                )
            }
        }
    }
}
