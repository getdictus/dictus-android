package dev.pivisolutions.dictus.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.service.ModelInstaller
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    private lateinit var fakeInstaller: FakeModelInstaller
    private lateinit var viewModel: ModelsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDataStore = FakeDataStore()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        fakeInstaller = FakeModelInstaller(context)
        viewModel = ModelsViewModel(ModelManager(context), fakeInstaller, fakeDataStore, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Build a ViewModel on a device reporting [totalMemGib] of RAM to the OS. */
    private fun viewModelWithRam(totalMemGib: Double): ModelsViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo().apply {
            totalMem = (totalMemGib * 1024 * 1024 * 1024).toLong()
        }
        shadowOf(activityManager).setMemoryInfo(memInfo)
        val modelManager = ModelManager(context)
        return ModelsViewModel(modelManager, FakeModelInstaller(context), fakeDataStore, context)
    }

    @Test
    fun `each card shows its own install state`() {
        fakeInstaller.states.getValue("base").value = ModelInstallState.Extracting(12)
        fakeInstaller.states.getValue("small").value = ModelInstallState.Failed

        val byKey = viewModel.models.value.associate { it.info.key to it.installState }
        assertEquals(ModelInstallState.Extracting(12), byKey["base"])
        assertEquals(ModelInstallState.Failed, byKey["small"])
        assertEquals(ModelInstallState.Idle, byKey["tiny"])
    }

    @Test
    fun `download and retry ask the installer`() {
        viewModel.downloadModel("base")
        viewModel.retryDownload("small")

        assertEquals(listOf("base", "small"), fakeInstaller.installed)
    }

    @Test
    fun `models the device cannot run stay listed with the RAM they need`() {
        val vm = viewModelWithRam(3.6)

        assertEquals(ModelCatalog.ALL.size, vm.models.value.size)
        val parakeet = vm.models.value.single { it.info.key == "parakeet-tdt-0.6b-v3" }
        assertEquals(6, parakeet.requiredRamGb)
        val tiny = vm.models.value.single { it.info.key == "tiny" }
        assertNull(tiny.requiredRamGb)
    }

    @Test
    fun `6 GB phone reporting 5_3 GiB gets Parakeet 0_6B as available`() {
        val vm = viewModelWithRam(5.3)

        val parakeet = vm.models.value.single { it.info.key == "parakeet-tdt-0.6b-v3" }
        assertNull(parakeet.requiredRamGb)
    }

    @Test
    fun `the recommended mark follows onboarding's rule`() {
        // #188: one model carries it, the one onboarding installs on this phone
        val recommendedOn = { gib: Double -> viewModelWithRam(gib).models.value.filter { it.isRecommended }.map { it.info.key } }

        assertEquals(listOf("small-q5_1"), recommendedOn(3.6))
        assertEquals(listOf("parakeet-tdt-0.6b-v3"), recommendedOn(5.3))
    }

    @Test
    fun `requestSetActiveModel refuses a model the device lacks RAM for`() = testScope.runTest {
        val vm = viewModelWithRam(3.6)

        vm.requestSetActiveModel("parakeet-tdt-0.6b-v3")
        advanceUntilIdle()

        assertFalse(fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL] == "parakeet-tdt-0.6b-v3")
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

    // --- Preparation screen (#181) ---

    @Test
    fun `download raises the preparation screen and masks the previous run`() = testScope.runTest {
        fakeInstaller.states.getValue("base").value = ModelInstallState.Failed
        viewModel.closePreparation()

        viewModel.downloadModel("base")
        advanceUntilIdle()

        val target = viewModel.preparation.value
        assertEquals("base", target?.info?.key)
        assertEquals(true, target?.awaitingInstall)
        assertEquals(ModelPreparationPhase.Downloading(0), ModelPreparationPhase.of(target!!, null))
    }

    @Test
    fun `the new install's first report ends the mask and drives the screen`() = testScope.runTest {
        viewModel.downloadModel("base")
        fakeInstaller.states.getValue("base").value = ModelInstallState.Downloading(42)
        advanceUntilIdle()

        val target = viewModel.preparation.value!!
        assertFalse(target.awaitingInstall)
        assertEquals(ModelPreparationPhase.Downloading(42), ModelPreparationPhase.of(target, null))
    }

    @Test
    fun `a finished download becomes the active model`() = testScope.runTest {
        viewModel.downloadModel("base")
        fakeInstaller.states.getValue("base").value = ModelInstallState.Downloading(99)
        fakeInstaller.states.getValue("base").value = ModelInstallState.Done
        advanceUntilIdle()

        assertEquals("base", fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL])
    }

    @Test
    fun `a download while another model is loaded shows the loading step until the new model is loaded`() = testScope.runTest {
        viewModel.downloadModel("base")
        fakeInstaller.states.getValue("base").value = ModelInstallState.Downloading(99)
        fakeInstaller.states.getValue("base").value = ModelInstallState.Done
        advanceUntilIdle()
        val target = viewModel.preparation.value!!.copy(filesOnDisk = true)

        // Tiny is still the loaded model: not ready yet (iOS ModelLoadingOverlay.rawPhase bug)
        assertEquals(ModelPreparationPhase.Loading, ModelPreparationPhase.of(target, SttEngineState.Ready("tiny")))
        assertEquals(ModelPreparationPhase.Ready, ModelPreparationPhase.of(target, SttEngineState.Ready("base")))
    }

    @Test
    fun `an English-only download that needs confirmation is not activated`() = testScope.runTest {
        fakeDataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = "fr" }
        viewModel.downloadModel("parakeet-ctc-110m-int8")
        fakeInstaller.states.getValue("parakeet-ctc-110m-int8").value = ModelInstallState.Done
        advanceUntilIdle()

        assertFalse(viewModel.preparation.value!!.loadsEngine)
        assertNull(fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL])
    }

    @Test
    fun `selecting a model raises the screen for its engine load`() = testScope.runTest {
        viewModel.setActiveModel("tiny")
        advanceUntilIdle()

        val target = viewModel.preparation.value!!
        assertEquals("tiny", target.info.key)
        assertEquals(true, target.loadsEngine)
        assertEquals("tiny", fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL])
    }

    @Test
    fun `an install already running raises the screen again`() = testScope.runTest {
        fakeInstaller.states.getValue("small").value = ModelInstallState.Extracting(5)
        advanceUntilIdle()

        assertEquals("small", viewModel.preparation.value?.info?.key)
        assertEquals(ModelPreparationPhase.Preparing, ModelPreparationPhase.of(viewModel.preparation.value!!, null))
    }

    @Test
    fun `closing the preparation clears it`() = testScope.runTest {
        viewModel.setActiveModel("tiny")
        viewModel.closePreparation()
        advanceUntilIdle()

        assertNull(viewModel.preparation.value)
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

/** Fake ModelInstaller: one state flow per model key, driven by the test. */
class FakeModelInstaller(context: android.content.Context) : ModelInstaller(context) {
    val states = ModelCatalog.ALL.associate { it.key to MutableStateFlow<ModelInstallState>(ModelInstallState.Idle) }
    val installed = mutableListOf<String>()

    override fun install(modelKey: String) {
        installed += modelKey
    }

    override fun state(modelKey: String): Flow<ModelInstallState> = states.getValue(modelKey)
}
