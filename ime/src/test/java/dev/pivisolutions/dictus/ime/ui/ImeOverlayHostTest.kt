package dev.pivisolutions.dictus.ime.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImeOverlayHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `overlay matches keyboard height without expanding IME root`() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                ImeOverlayHost(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ime-root"),
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                        )
                    },
                    overlay = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("overlay"),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("ime-root").assertHeightIsEqualTo(100.dp)
        composeRule.onNodeWithTag("overlay").assertHeightIsEqualTo(100.dp)
    }
}
