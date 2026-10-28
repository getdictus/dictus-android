package dev.pivisolutions.dictus.ime.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MicButtonRowSuccessFlashTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `each armed token is shown once, including one armed after the pill appears`() {
        val shown = mutableListOf<Int>()
        var token by mutableStateOf<Int?>(1)
        composeRule.setContent {
            DictusTheme {
                MicButtonRow(
                    languageShortCode = "FR",
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    successFlashToken = token,
                    onSuccessFlashShown = { shown += it; token = null },
                )
            }
        }
        composeRule.waitForIdle()

        token = 2
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf(1, 2), shown) }
    }
}
