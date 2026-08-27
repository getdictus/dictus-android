package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MicButtonRowLoadingTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `loading mic is disabled and exposes accessible reason`() {
        var taps = 0
        composeRule.setContent {
            DictusTheme {
                MicButtonRow(
                    onSwitchKeyboard = {},
                    onMicTap = { taps++ },
                    isMicEnabled = false,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Microphone unavailable while speech recognition loads",
        ).assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, taps) }
    }
}
