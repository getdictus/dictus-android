package dev.pivisolutions.dictus.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SettingsViewModel.
 *
 * Uses a fake in-memory DataStore to test DataStore read/write logic
 * without needing a real file system. Robolectric provides an Application
 * context so ModelManager can initialise its models directory.
 *
 * WHY UnconfinedTestDispatcher: SettingsViewModel uses viewModelScope (Dispatchers.Main).
 * We replace Main with UnconfinedTestDispatcher so that coroutines launched by the
 * ViewModel execute eagerly on the same thread, making state changes visible immediately
 * without needing to call advanceUntilIdle() for every assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var modelManager: ModelManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataStore = FakeDataStore()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        modelManager = ModelManager(context)
        viewModel = SettingsViewModel(fakeDataStore, modelManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `language defaults to auto`() = testScope.runTest {
        assertEquals("auto", viewModel.language.value)
    }

    @Test
    fun `setLanguage writes fr to DataStore`() = testScope.runTest {
        viewModel.setLanguage("fr")
        advanceUntilIdle()
        assertEquals("fr", viewModel.language.value)
    }

    @Test
    fun `activeModel defaults to tiny`() = testScope.runTest {
        assertEquals(ModelCatalog.DEFAULT_KEY, viewModel.activeModel.value)
    }

    @Test
    fun `setActiveModel writes base to DataStore`() = testScope.runTest {
        viewModel.setActiveModel("base")
        advanceUntilIdle()
        assertEquals("base", viewModel.activeModel.value)
    }

    @Test
    fun `hapticsEnabled defaults to true`() = testScope.runTest {
        assertTrue(viewModel.hapticsEnabled.value)
    }

    @Test
    fun `toggleHaptics flips hapticsEnabled to false`() = testScope.runTest {
        viewModel.toggleHaptics()
        advanceUntilIdle()
        assertFalse(viewModel.hapticsEnabled.value)
    }

    @Test
    fun `soundEnabled defaults to false`() = testScope.runTest {
        assertFalse(viewModel.soundEnabled.value)
    }

    @Test
    fun `toggleSound flips soundEnabled to true`() = testScope.runTest {
        viewModel.toggleSound()
        advanceUntilIdle()
        assertTrue(viewModel.soundEnabled.value)
    }
}

/**
 * Fake in-memory DataStore for testing.
 *
 * Stores preferences in a MutableStateFlow<Preferences> so that flows
 * emit new values when updateData() is called — exactly what the real DataStore does.
 */
class FakeDataStore : DataStore<Preferences> {
    private val _preferences = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = _preferences

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        return updated
    }
}
