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
 * spoken label (#280).
 */
@RunWith(RobolectricTestRunner::class)
class ReturnKeyIconTest {
    @get:Rule val composeRule = createComposeRule()

    private val profile = mutableStateOf(frenchLanguageProfile)
    private val layer = mutableStateOf(KeyboardLayer.LETTERS)
    private val pressed = mutableListOf<KeyDefinition>()

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
                )
            }
        }
    }
}
