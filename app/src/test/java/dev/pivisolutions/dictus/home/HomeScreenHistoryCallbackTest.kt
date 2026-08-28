package dev.pivisolutions.dictus.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenHistoryCallbackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `history hint and new dictation invoke independent callbacks`() {
        var historyCalls = 0
        var dictationCalls = 0
        composeRule.setContent {
            DictusTheme {
                HomeScreen(
                    dataStore = FakeDataStore(),
                    onNewDictation = { dictationCalls++ },
                    onOpenHistory = { historyCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("Swipe up for history").performClick()
        composeRule.onNodeWithText("New dictation").performClick()
        assertEquals(1, historyCalls)
        assertEquals(1, dictationCalls)
    }

    @Test
    fun `directional upward swipe invokes history callback`() {
        var historyCalls = 0
        composeRule.setContent {
            DictusTheme {
                HomeScreen(FakeDataStore(), {}, { historyCalls++ })
            }
        }
        composeRule.onNodeWithTag("home_screen").performTouchInput { swipeUp() }
        assertEquals(1, historyCalls)
    }

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
