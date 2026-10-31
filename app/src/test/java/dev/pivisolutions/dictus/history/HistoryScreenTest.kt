package dev.pivisolutions.dictus.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun `card opens full detail and explicit copy share use exact text`() {
        val fullText = "Synthetic complete transcription that is longer than its two-line card preview."
        var openedId: Long? = null
        var copied: String? = null
        var shared: String? = null
        var state by mutableStateOf(HistoryUiState(isLoading = false, entries = listOf(entry(9, fullText))))
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    state = state,
                    onBack = {},
                    onOpenDetail = { openedId = it },
                    onCloseDetail = { state = state.copy(selectedEntryId = null) },
                    onCopy = { copied = it },
                    onShare = { shared = it },
                    onRequestDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_card_9").performClick()
        assertEquals(9L, openedId)
        composeRule.runOnIdle { state = state.copy(selectedEntryId = 9) }

        composeRule.onNodeWithTag("history_detail").assertIsDisplayed()
        composeRule.onNodeWithText(fullText).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Copy transcription").performClick()
        composeRule.onNodeWithText("Transcription copied").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share transcription").performClick()
        composeRule.onNodeWithContentDescription("Delete transcription").assertDoesNotExist()
        assertEquals(fullText, copied)
        assertEquals(fullText, shared)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_list").assertIsDisplayed()
    }

    @Test
    fun `missing selected entry fails closed to list`() {
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    state = HistoryUiState(
                        isLoading = false,
                        entries = listOf(entry(1, "available")),
                        selectedEntryId = 999,
                    ),
                    onBack = {},
                    onRequestDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_detail").assertDoesNotExist()
        composeRule.onNodeWithTag("history_list").assertIsDisplayed()
    }

    @Test
    fun `search filters ignoring accents and case and shows no results with the query`() {
        val entries = listOf(entry(1, "Rendez-vous à l'École"), entry(2, "Liste de courses"))
        composeRule.setContent {
            DictusTheme { HistoryScreen(HistoryUiState(isLoading = false, entries = entries), {}, {}, {}, {}) }
        }

        composeRule.onNodeWithTag("history_search").performTextInput("ecole")
        composeRule.onNodeWithTag("history_card_1").assertIsDisplayed()
        composeRule.onNodeWithTag("history_card_2").assertDoesNotExist()

        composeRule.onNodeWithTag("history_search").performTextInput("xyz")
        composeRule.onNodeWithTag("history_no_results").assertIsDisplayed()
        composeRule.onNodeWithText("No results").assertIsDisplayed()
        composeRule.onNodeWithText("No transcription contains \u201cecolexyz\u201d.").assertIsDisplayed()
        composeRule.onNodeWithTag("history_search").assertIsDisplayed()
    }

    @Test
    fun `search and clear are absent over an empty history`() {
        composeRule.setContent {
            DictusTheme { HistoryScreen(HistoryUiState(isLoading = false), {}, {}, {}, {}) }
        }
        composeRule.onNodeWithTag("history_search").assertDoesNotExist()
        composeRule.onNodeWithTag("history_clear").assertDoesNotExist()
    }

    @Test
    fun `clear history asks for confirmation and only delete all confirms`() {
        var requested = 0
        var cancelled = 0
        var confirmed = 0
        var state by mutableStateOf(HistoryUiState(isLoading = false, entries = listOf(entry(1, "preview"))))
        composeRule.setContent {
            DictusTheme {
                HistoryScreen(
                    state = state,
                    onBack = {},
                    onRequestDelete = {},
                    onCancelDelete = {},
                    onConfirmDelete = {},
                    onRequestClear = { requested++ },
                    onCancelClear = { cancelled++ },
                    onConfirmClear = { confirmed++ },
                )
            }
        }

        composeRule.onNodeWithText("Clear history").performClick()
        assertEquals(1, requested)
        composeRule.runOnIdle { state = state.copy(isClearPending = true) }
        composeRule.onNodeWithText("Clear history?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Every transcription saved on this device will be deleted. This cannot be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)

        composeRule.onNodeWithText("Delete all").performClick()
        assertEquals(1, confirmed)
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
