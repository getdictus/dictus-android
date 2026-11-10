package dev.pivisolutions.dictus.companion.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.theme.DictusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionSettingsSectionTest {
    @get:Rule val compose = createComposeRule()
    private val store = MemoryPreferences()
    private lateinit var viewModel: CompanionSettingsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CompanionSettingsViewModel(store)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `Models remains reachable before Companion is enabled`() {
        var modelsOpened = false
        compose.setContent {
            DictusTheme { CompanionSettingsSection(viewModel, onOpenModels = { modelsOpened = true }) }
        }
        compose.onNodeWithText("Models — download or choose").performClick()
        compose.runOnIdle {
            assertTrue(modelsOpened)
            assertFalse(viewModel.state.value.enabled)
            assertNull(store.data.value[PreferenceKeys.HAS_COMPLETED_ONBOARDING])
        }
    }

    @Test fun `copy choice dismisses consent without opening Accessibility settings`() {
        viewModel.setEnabled(true)
        compose.setContent {
            DictusTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompanionSettingsSection(viewModel)
                }
            }
        }
        compose.onNodeWithText("Automatic insertion").performScrollTo().performClick()
        compose.onNodeWithText("Allow automatic insertion?").assertExists()
        val application = ApplicationProvider.getApplicationContext<Application>()
        assertNull(shadowOf(application).nextStartedActivity)
        compose.onNodeWithText("Use copy mode").performClick()
        compose.runOnIdle {
            assertFalse(viewModel.state.value.automaticInsertion)
            assertNull(shadowOf(application).nextStartedActivity)
        }
    }

    @Test fun `five step copy onboarding completes without keyboard or permission setup`() {
        compose.setContent {
            DictusTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompanionSettingsSection(viewModel, onboarding = true)
                }
            }
        }
        for (step in 1..4) {
            compose.onNodeWithText("Step $step of 5").assertExists()
            compose.onNodeWithText("Continue").performScrollTo().performClick()
        }
        compose.onNodeWithText("Step 5 of 5").assertExists()
        compose.onNodeWithText("Continue with Companion").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(store.data.value[PreferenceKeys.HAS_COMPLETED_ONBOARDING] == true)
            assertTrue(store.data.value[PreferenceKeys.COMPANION_ENABLED] == true)
            assertNull(store.data.value[PreferenceKeys.KEYBOARD_LAYOUT])
            assertNull(store.data.value[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION])
            assertNull(store.data.value[PreferenceKeys.COMPANION_FLOATING])
            assertNull(shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity)
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            return transform(data.value).also { data.value = it }
        }
    }
}
