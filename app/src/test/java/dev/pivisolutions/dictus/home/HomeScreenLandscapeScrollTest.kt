package dev.pivisolutions.dictus.home

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone in landscape is shorter than Home's content (#347): it scrolls to « New dictation ». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h360dp-land")
class HomeScreenLandscapeScrollTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `new dictation is reachable at full height in landscape`() {
        composeRule.setContent {
            DictusTheme {
                HomeScreen(FakeDataStore(), onNewDictation = {}, isModelDownloaded = { true })
            }
        }

        val button = composeRule.onNodeWithText("New dictation").performScrollTo()
        val buttonBounds = button.getBoundsInRoot()
        val screenBounds = composeRule.onNodeWithTag("home_screen").getBoundsInRoot()
        assertEquals(56.dp.value, (buttonBounds.bottom - buttonBounds.top).value, 0.5f)
        assertTrue(buttonBounds.top >= screenBounds.top && buttonBounds.bottom <= screenBounds.bottom)
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
