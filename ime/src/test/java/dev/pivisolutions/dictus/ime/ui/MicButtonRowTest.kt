package dev.pivisolutions.dictus.ime.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import dev.pivisolutions.dictus.core.theme.DictusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MicButtonRowTest {
    @get:Rule val composeRule = createComposeRule()

    // The mic has no loading state: a tap while the model loads starts the recording (#247).
    @Test fun `mic is always enabled and a tap reaches the keyboard`() {
        var taps = 0
        composeRule.setContent {
            DictusTheme {
                MicButtonRow(
                    languageShortCode = "FR",
                    onCycleLanguage = {},
                    onOpenSettings = {},
                    onMicTap = { taps++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Microphone").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun `tap cycles once while long press opens settings without cycling`() {
        var cycles = 0
        var settingsOpens = 0
        composeRule.setContent {
            DictusTheme {
                MicButtonRow(
                    languageShortCode = "FR",
                    onCycleLanguage = { cycles++ },
                    onOpenSettings = { settingsOpens++ },
                )
            }
        }

        composeRule.onNodeWithText("FR").performClick()
        composeRule.runOnIdle {
            assertEquals(1, cycles)
            assertEquals(0, settingsOpens)
        }

        composeRule.onNodeWithText("FR").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            up()
        }
        composeRule.runOnIdle {
            assertEquals(1, cycles)
            assertEquals(1, settingsOpens)
        }
    }
}
