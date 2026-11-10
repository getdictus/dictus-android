package dev.pivisolutions.dictus.ui.models

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelCatalog
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelLanguagesSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `parakeet v3 detail shows its summary then the tested languages under a heading`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!

        composeTestRule.setContent { DictusTheme { ModelLanguagesContent(model) } }

        composeTestRule.onNodeWithText("Parakeet 0.6B v3").assertIsDisplayed()
        composeTestRule.onNodeWithText("25 European languages").assertIsDisplayed()
        composeTestRule.onNode(hasText("Main languages") and isHeading()).assertIsDisplayed()
        composeTestRule.onNodeWithText("French").assertIsDisplayed()
        composeTestRule.onNodeWithText("German").assertIsDisplayed()
    }

    @Test
    fun `whisper detail shows the multilingual summary and the quality note`() {
        val model = ModelCatalog.findByKey("small-q5_1")!!

        composeTestRule.setContent { DictusTheme { ModelLanguagesContent(model) } }

        composeTestRule.onNodeWithText("~99 languages").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quality varies", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun `language names follow the UI language and are capitalized`() {
        assertEquals("Français", languageName("fr", Locale.FRENCH))
        assertEquals("French", languageName("fr", Locale.ENGLISH))
        assertEquals("Allemand", languageName("de", Locale.FRENCH))
    }

    @Test
    fun `other languages are sorted by their localized name`() {
        assertEquals(
            listOf("Bulgare", "Grec", "Italien"),
            sortedByName(listOf("it", "el", "bg"), Locale.FRENCH),
        )
    }
}
