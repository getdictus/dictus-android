package dev.pivisolutions.dictus.ui.models

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.model.ModelCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `model card renders catalog description metric labels and five segment gauges`() {
        val model = ModelCatalog.findByKey("tiny")!!

        composeTestRule.setContent {
            DictusTheme {
                ModelCard(
                    model = model,
                    isDownloaded = false,
                    isActive = false,
                    downloadProgress = null,
                    canDelete = false,
                    onDownload = {},
                    onDelete = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText(model.description).assertIsDisplayed()
        composeTestRule.onNodeWithText("Precision").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speed").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("model_precision_segment").assertCountEquals(5)
        composeTestRule.onAllNodesWithTag("model_speed_segment").assertCountEquals(5)
    }

    @Test
    fun `unavailable model card shows the RAM it needs and no download button`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!

        composeTestRule.setContent {
            DictusTheme {
                ModelCard(
                    model = model,
                    isDownloaded = false,
                    isActive = false,
                    downloadProgress = null,
                    canDelete = false,
                    onDownload = {},
                    onDelete = {},
                    onRetry = {},
                    requiredRamGb = 8,
                )
            }
        }

        composeTestRule.onNodeWithText("Requires 8 GB of RAM or more").assertIsDisplayed()
        composeTestRule.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test
    fun `language summary opens the languages without selecting the model`() {
        val model = ModelCatalog.findByKey("tiny")!!
        var languagesOpened = 0
        var selected = 0

        composeTestRule.setContent {
            DictusTheme {
                ModelCard(
                    model = model,
                    isDownloaded = true,
                    isActive = false,
                    downloadProgress = null,
                    canDelete = false,
                    onDownload = {},
                    onDelete = {},
                    onRetry = {},
                    onSelect = { selected++ },
                    onShowLanguages = { languagesOpened++ },
                )
            }
        }

        composeTestRule.onNodeWithText("~99 languages").assertIsDisplayed()
        composeTestRule.onNodeWithTag("model_languages_summary").assertHasClickAction().performClick()
        assertEquals(1, languagesOpened)
        assertEquals(0, selected)
    }

    @Test
    fun `english only model card summarizes its single language`() {
        val model = ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!

        composeTestRule.setContent {
            DictusTheme {
                ModelCard(
                    model = model,
                    isDownloaded = false,
                    isActive = false,
                    downloadProgress = null,
                    canDelete = false,
                    onDownload = {},
                    onDelete = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("English only").assertIsDisplayed()
    }
}
