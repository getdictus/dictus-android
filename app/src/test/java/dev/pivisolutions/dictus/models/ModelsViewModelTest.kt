package dev.pivisolutions.dictus.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for ModelsViewModel — Parakeet activation dialog and language mismatch logic.
 *
 * Uses FakeDataStore (in-memory) so no real file I/O occurs. Robolectric provides
 * an Application context so ModelManager and ActivityManager APIs are available.
 *
 * WHY UnconfinedTestDispatcher: viewModelScope coroutines execute eagerly on the same
 * thread, making state changes visible immediately without extra advanceUntilIdle() calls
 * in most tests (though some flows still need it for SharingStarted.WhileSubscribed).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var viewModel: ModelsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataStore = FakeDataStore()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val modelManager = ModelManager(context)
        val modelDownloader = ModelDownloader(modelManager)
        viewModel = ModelsViewModel(modelManager, modelDownloader, fakeDataStore, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requestSetActiveModel shows dialog for parakeet when language is fr`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "fr" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("parakeet-ctc-110m-int8")
        advanceUntilIdle()

        assertEquals("parakeet-ctc-110m-int8", viewModel.showParakeetLanguageDialog.value)
    }

    @Test
    fun `requestSetActiveModel shows dialog for parakeet when language is auto`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "auto" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("parakeet-ctc-110m-int8")
        advanceUntilIdle()

        assertEquals("parakeet-ctc-110m-int8", viewModel.showParakeetLanguageDialog.value)
    }

    @Test
    fun `requestSetActiveModel does NOT show dialog for parakeet when language is en`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "en" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("parakeet-ctc-110m-int8")
        advanceUntilIdle()

        assertNull(viewModel.showParakeetLanguageDialog.value)
    }

    @Test
    fun `requestSetActiveModel does NOT show dialog for whisper models`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "fr" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("tiny")
        advanceUntilIdle()

        assertNull(viewModel.showParakeetLanguageDialog.value)
    }

    @Test
    fun `confirmParakeetActivation sets active model and clears dialog`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "fr" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("parakeet-ctc-110m-int8")
        advanceUntilIdle()
        assertNotNull(viewModel.showParakeetLanguageDialog.value)

        viewModel.confirmParakeetActivation()
        advanceUntilIdle()

        assertNull(viewModel.showParakeetLanguageDialog.value)
        // Verify the model was written to DataStore directly — activeModelKey uses
        // SharingStarted.WhileSubscribed which may not propagate without a collector in tests
        val storedKey = fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
        assertEquals("parakeet-ctc-110m-int8", storedKey)
    }

    @Test
    fun `dismissParakeetDialog clears dialog without changing active model`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "fr" }
        advanceUntilIdle()

        viewModel.requestSetActiveModel("parakeet-ctc-110m-int8")
        advanceUntilIdle()

        viewModel.dismissParakeetDialog()
        advanceUntilIdle()

        assertNull(viewModel.showParakeetLanguageDialog.value)
        // Verify active model was NOT changed to parakeet
        val storedKey = fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
        // DataStore should be null (never set) — parakeet was not activated
        assertNull(storedKey)
    }
}

/**
 * Fake in-memory DataStore for testing.
 * Identical to the one used in SettingsViewModelTest.
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
