package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.models.ModelPreparationPhase
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.service.ModelInstaller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for OnboardingViewModel step state machine, download orchestration, and DataStore persistence.
 *
 * Uses FakeDataStore (in-memory) and FakeModelInstaller to isolate the ViewModel from real I/O
 * and WorkManager. Robolectric provides the Android Context for the RAM-based recommendation.
 * StandardTestDispatcher gives deterministic coroutine execution via advanceUntilIdle().
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var context: android.content.Context
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var fakeInstaller: FakeModelInstaller
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeDataStore = FakeDataStore()
        fakeInstaller = FakeModelInstaller(context)
        viewModel = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), SavedStateHandle())
    }

    // --- Initial state ---

    @Test
    fun `initial state - step is 1`() = runTest(testDispatcher) {
        assertEquals(1, viewModel.currentStep.value)
    }

    @Test
    fun `initial state - micPermissionGranted is false`() = runTest(testDispatcher) {
        assertFalse(viewModel.micPermissionGranted.value)
    }

    @Test
    fun `initial state - selectedLayout is azerty`() = runTest(testDispatcher) {
        assertEquals("azerty", viewModel.selectedLayout.value)
    }


    @Test
    fun `initial state - install is Idle`() = runTest(testDispatcher) {
        assertEquals(ModelInstallState.Idle, viewModel.installState.value)
    }

    // --- SavedStateHandle restoration ---

    @Test
    fun `savedStateHandle - restores currentStep from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeInstaller,
            ModelManager(context),
            SavedStateHandle(mapOf("currentStep" to 3)),
        )
        assertEquals(3, restoredVm.currentStep.value)
    }

    @Test
    fun `savedStateHandle - restores micPermissionGranted from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeInstaller,
            ModelManager(context),
            SavedStateHandle(mapOf("micPermissionGranted" to true)),
        )
        assertTrue(restoredVm.micPermissionGranted.value)
    }


    @Test
    fun `savedStateHandle - advanceStep writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), ssh)
        vm.advanceStep()
        assertEquals(2, ssh.get<Int>("currentStep"))
    }

    @Test
    fun `savedStateHandle - setMicPermissionGranted writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), ssh)
        vm.setMicPermissionGranted(true)
        assertEquals(true, ssh.get<Boolean>("micPermissionGranted"))
    }

    // --- Step navigation ---

    @Test
    fun `advanceStep from step 1 moves to step 2`() = runTest(testDispatcher) {
        viewModel.advanceStep()
        assertEquals(2, viewModel.currentStep.value)
    }

    @Test
    fun `advanceStep from step 2 moves to step 3`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> 2
        viewModel.advanceStep() // -> 3
        assertEquals(3, viewModel.currentStep.value)
    }

    @Test
    fun `advanceStep from step 5 is blocked when download not complete`() =
        runTest(testDispatcher) {
            // Move to step 5
            repeat(4) { viewModel.advanceStep() }
            assertEquals(5, viewModel.currentStep.value)

            // Attempt advance — blocked since download not complete
            viewModel.advanceStep()
            assertEquals(5, viewModel.currentStep.value)
        }

    @Test
    fun `advanceStep from step 5 advances when download is complete`() =
        runTest(testDispatcher) {
            // Move to step 5
            repeat(4) { viewModel.advanceStep() }

            viewModel.startModelDownload()
            fakeInstaller.state.value = ModelInstallState.Downloading(50)
            fakeInstaller.state.value = ModelInstallState.Done
            advanceUntilIdle()

            assertEquals(ModelInstallState.Done, viewModel.installState.value)

            // Now advance should work
            viewModel.advanceStep()
            assertEquals(6, viewModel.currentStep.value)
        }

    @Test
    fun `advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING to DataStore`() =
        runTest(testDispatcher) {
            // Move to step 5, complete download, advance to 6
            repeat(4) { viewModel.advanceStep() }
            viewModel.startModelDownload()
            fakeInstaller.state.value = ModelInstallState.Done
            advanceUntilIdle()
            viewModel.advanceStep() // -> 6

            // Tap "Commencer" on step 6 -> moves to step 7
            viewModel.advanceStep()
            // Step 7 triggers completeOnboarding()
            viewModel.advanceStep()
            advanceUntilIdle()

            assertTrue(
                "HAS_COMPLETED_ONBOARDING should be true",
                fakeDataStore.editedPrefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] == true
            )
        }

    @Test
    fun `goBack from step 2 moves to step 1`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> 2
        viewModel.goBack()
        assertEquals(1, viewModel.currentStep.value)
    }

    @Test
    fun `goBack from step 1 stays at step 1`() = runTest(testDispatcher) {
        viewModel.goBack()
        assertEquals(1, viewModel.currentStep.value)
    }

    // --- State mutations ---

    @Test
    fun `setLayout changes selectedLayout to qwerty`() = runTest(testDispatcher) {
        viewModel.setLayout("qwerty")
        assertEquals("qwerty", viewModel.selectedLayout.value)
    }

    @Test
    fun `setMicPermissionGranted true sets micPermissionGranted to true`() =
        runTest(testDispatcher) {
            viewModel.setMicPermissionGranted(true)
            assertTrue(viewModel.micPermissionGranted.value)
        }


    // --- Install (observed from the installer, #169) ---

    @Test
    fun `startModelDownload asks the installer for the recommended model`() = runTest(testDispatcher) {
        viewModel.startModelDownload()

        assertEquals(listOf(viewModel.recommendedKey), fakeInstaller.installed)
    }

    @Test
    fun `install progress reaches the screen`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Extracting(37)
        advanceUntilIdle()

        assertEquals(ModelInstallState.Extracting(37), viewModel.installState.value)
    }

    @Test
    fun `step 5 stays blocked while the install runs`() = runTest(testDispatcher) {
        repeat(4) { viewModel.advanceStep() }
        fakeInstaller.state.value = ModelInstallState.Verifying
        advanceUntilIdle()

        viewModel.advanceStep()
        assertEquals(5, viewModel.currentStep.value)
    }

    @Test
    fun `successful install selects recommended model before test recording`() =
        runTest(testDispatcher) {
            fakeInstaller.state.value = ModelInstallState.Done
            advanceUntilIdle()

            val activeModel = fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
            assertEquals(viewModel.recommendedKey, activeModel)
        }

    @Test
    fun `an install finished while the ViewModel was gone still selects the model`() =
        runTest(testDispatcher) {
            // The worker succeeded before this ViewModel existed (screen left, process restarted)
            fakeInstaller.state.value = ModelInstallState.Done
            val recreated = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), SavedStateHandle())
            advanceUntilIdle()

            assertEquals(ModelInstallState.Done, recreated.installState.value)
            assertEquals(recreated.recommendedKey, fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL])
        }

    @Test
    fun `failed install does not change active model`() =
        runTest(testDispatcher) {
            fakeDataStore.edit { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] = "existing-model"
            }
            fakeInstaller.state.value = ModelInstallState.Failed
            advanceUntilIdle()

            val activeModel = fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
            assertEquals("existing-model", activeModel)
            assertEquals(ModelInstallState.Failed, viewModel.installState.value)
        }

    @Test
    fun `incomplete install does not change active model`() =
        runTest(testDispatcher) {
            fakeDataStore.edit { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] = "existing-model"
            }
            fakeInstaller.state.value = ModelInstallState.Downloading(50)
            advanceUntilIdle()

            val activeModel = fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
            assertEquals("existing-model", activeModel)
        }

    @Test
    fun `retryDownload starts the install again`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Failed
        viewModel.retryDownload()

        assertEquals(listOf(viewModel.recommendedKey), fakeInstaller.installed)
    }

    // --- Preparation screen (#181) ---

    /** Run the test coroutines, then the main looper: viewModelScope's combine posts to it. */
    private fun kotlinx.coroutines.test.TestScope.settle() {
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }


    @Test
    fun `opening step 5 starts the install when nothing ran yet`() = runTest(testDispatcher) {
        viewModel.startPreparation()
        settle()

        assertEquals(listOf(viewModel.recommendedKey), fakeInstaller.installed)
    }

    @Test
    fun `opening step 5 does not restart a failed install, the screen offers Retry`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Failed
        viewModel.startPreparation()
        settle()

        assertTrue(fakeInstaller.installed.isEmpty())
        assertEquals(ModelPreparationPhase.Failed, ModelPreparationPhase.of(viewModel.preparation.value, null))
    }

    @Test
    fun `opening step 5 does not start a second install over a running one`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Downloading(20)
        viewModel.startPreparation()
        settle()

        assertTrue(fakeInstaller.installed.isEmpty())
        assertEquals(ModelPreparationPhase.Downloading(20), ModelPreparationPhase.of(viewModel.preparation.value, null))
    }

    @Test
    fun `retry masks the failed run until the new install reports`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Failed
        settle()

        viewModel.retryDownload()
        settle()
        assertEquals(ModelPreparationPhase.Downloading(0), ModelPreparationPhase.of(viewModel.preparation.value, null))

        fakeInstaller.state.value = ModelInstallState.Downloading(3)
        settle()
        assertEquals(ModelPreparationPhase.Downloading(3), ModelPreparationPhase.of(viewModel.preparation.value, null))
    }
}

// ---------------------------------------------------------------------------
// Fake collaborators
// ---------------------------------------------------------------------------

/**
 * In-memory DataStore for tests.
 *
 * Tracks all key-value pairs written via [updateData] so tests can assert on
 * persisted values without real file I/O or Android Context.
 */
class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    /** All preference keys and their written values, for test assertion. */
    val editedPrefs: MutableMap<Preferences.Key<*>, Any?> = mutableMapOf()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        // Record every key that was written into the prefs
        @Suppress("UNCHECKED_CAST")
        for ((key, value) in updated.asMap()) {
            editedPrefs[key as Preferences.Key<Any>] = value
        }
        return updated
    }
}

/**
 * Fake ModelInstaller: records install requests; tests drive [state] as the worker would.
 */
class FakeModelInstaller(context: android.content.Context) : ModelInstaller(context) {
    val state = MutableStateFlow<ModelInstallState>(ModelInstallState.Idle)
    val installed = mutableListOf<String>()

    override fun install(modelKey: String) {
        installed += modelKey
    }

    override fun state(modelKey: String): Flow<ModelInstallState> = state
}
