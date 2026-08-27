package dev.pivisolutions.dictus.ui.models

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
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
}
