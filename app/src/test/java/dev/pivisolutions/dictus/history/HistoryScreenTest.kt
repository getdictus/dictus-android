package dev.pivisolutions.dictus.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pivisolutions.dictus.core.theme.DictusTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private var previousLocale: Locale = Locale.getDefault()

    @Before fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun tearDown() = Locale.setDefault(previousLocale)

    @Test
    fun `loading is not briefly presented as empty`() {
        composeRule.setContent {
            DictusTheme { HistoryScreen(HistoryUiState(), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithTag("history_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("history_empty").assertDoesNotExist()
    }

    @Test
    fun `swipe down from loading invokes back`() {
        assertNonListStateSwipeInvokesBack(HistoryUiState(), "history_loading")
    }

    @Test
    fun `empty and list states create only their intended content`() {
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(HistoryUiState(isLoading = false), {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("history_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("history_list").assertDoesNotExist()
    }

    @Test
    fun `swipe down from empty invokes back`() {
        assertNonListStateSwipeInvokesBack(
            HistoryUiState(isLoading = false),
            "history_empty",
        )
    }

    @Test
    fun `load failure is generic and is not presented as empty`() {
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    HistoryUiState(isLoading = false, failure = HistoryFailure.LOAD),
                    {}, {}, {}, {},
                )
            }
        }
        composeRule.onNodeWithTag("history_error").assertIsDisplayed()
        composeRule.onNodeWithTag("history_empty").assertDoesNotExist()
    }

    @Test
    fun `swipe down from load failure invokes back`() {
        assertNonListStateSwipeInvokesBack(
            HistoryUiState(isLoading = false, failure = HistoryFailure.LOAD),
            "history_error",
        )
    }

    @Test
    fun `list scroll remains available and only an unconsumed pull at top invokes back`() {
        var backCalls = 0
        var deleteRequests = 0
        val entries = (1L..30L).map { entry(it, "preview $it") }
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    state = HistoryUiState(isLoading = false, entries = entries),
                    onBack = { backCalls++ },
                    onRequestDelete = { deleteRequests++ },
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_card_1").assertDoesNotExist()
        assertEquals(0, backCalls)
        assertEquals(0, deleteRequests)

        composeRule.onNodeWithTag("history_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("history_list").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertEquals(1, backCalls)
        assertEquals(0, deleteRequests)
    }

    @Test
    fun `card shows requested automatic duration and accessible confirmed deletion`() {
        var requestedId: Long? = null
        var confirmed = 0
        val entry = entry(42, "A private preview which is deliberately long enough to occupy two lines")
        var state by mutableStateOf(HistoryUiState(isLoading = false, entries = listOf(entry)))
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    state = state,
                    onBack = {},
                    onRequestDelete = { requestedId = it },
                    onCancelDelete = {},
                    onConfirmDelete = { confirmed++ },
                )
            }
        }

        composeRule.onNodeWithTag("history_card_42").assertExists()
        composeRule.onNodeWithText("Automatic", substring = true).assertExists()
        composeRule.onNodeWithText("12 seconds", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Delete transcription").performClick()
        assertEquals(42L, requestedId)

        composeRule.runOnIdle { state = state.copy(pendingDeleteId = 42) }
        composeRule.onNodeWithText("Delete this transcription?").assertExists()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `cancel confirmation does not delete`() {
        var cancelled = 0
        var confirmed = 0
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    HistoryUiState(isLoading = false, entries = listOf(entry(7, "preview")), pendingDeleteId = 7),
                    {}, {}, { cancelled++ }, { confirmed++ },
                )
            }
        }
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    private fun entry(id: Long, text: String) = TranscriptionHistoryEntry(
        id = id,
        text = text,
        requestedLanguage = "auto",
        durationMillis = 12_000,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = 1_700_000_000_000,
    )

    private fun assertNonListStateSwipeInvokesBack(state: HistoryUiState, tag: String) {
        var backCalls = 0
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(state, { backCalls++ }, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag(tag).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, backCalls)
    }
}
