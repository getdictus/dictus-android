package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
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
        viewModel = OnboardingViewModel(context, fakeDataStore, fakeInstaller, SavedStateHandle())
    }

    // --- Initial state ---

    @Test
    fun `initial state - a fresh install starts at the welcome screen`() = runTest(testDispatcher) {
        assertEquals(OnboardingStep.Welcome, viewModel.currentStep.value)
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

    // --- Resume after a force-stop (#196) ---

    /** A new ViewModel on the same DataStore: what a relaunch after a force-stop reads. */
    private fun relaunch() = OnboardingViewModel(context, fakeDataStore, fakeInstaller, SavedStateHandle())

    @Test
    fun `resume - a relaunch resumes on the step the user left`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Microphone
        viewModel.advanceStep() // -> KeyboardSetup
        advanceUntilIdle()

        assertEquals(OnboardingStep.KeyboardSetup, relaunch().currentStep.value)
    }

    @Test
    fun `resume - every step is written under its stable name`() = runTest(testDispatcher) {
        viewModel.advanceStep()
        advanceUntilIdle()

        assertEquals("microphone", fakeDataStore.data.first()[PreferenceKeys.ONBOARDING_STEP])
    }

    @Test
    fun `resume - every stored step is restored`() = runTest(testDispatcher) {
        for (step in OnboardingStep.entries) {
            fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = step.storedName }
            assertEquals("$step", step, relaunch().currentStep.value)
        }
    }

    @Test
    fun `resume - an unknown stored name starts at the welcome screen`() = runTest(testDispatcher) {
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = "someFutureStep" }

        assertEquals(OnboardingStep.Welcome, relaunch().currentStep.value)
    }

    @Test
    fun `resume - going back is what a relaunch resumes on`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Microphone
        viewModel.goBack() // -> Welcome
        advanceUntilIdle()

        assertEquals(OnboardingStep.Welcome, relaunch().currentStep.value)
    }

    @Test
    fun `resume - completing the onboarding resets the stored step`() = runTest(testDispatcher) {
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.Completion.storedName }
        val vm = relaunch()

        vm.advanceStep()
        advanceUntilIdle()

        assertEquals("welcome", fakeDataStore.data.first()[PreferenceKeys.ONBOARDING_STEP])
        assertEquals(true, fakeDataStore.data.first()[PreferenceKeys.HAS_COMPLETED_ONBOARDING])
    }

    // --- SavedStateHandle restoration ---

    @Test
    fun `savedStateHandle - restores micPermissionGranted from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeInstaller,
            SavedStateHandle(mapOf("micPermissionGranted" to true)),
        )
        assertTrue(restoredVm.micPermissionGranted.value)
    }


    @Test
    fun `savedStateHandle - setMicPermissionGranted writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ssh)
        vm.setMicPermissionGranted(true)
        assertEquals(true, ssh.get<Boolean>("micPermissionGranted"))
    }

    // --- Step navigation ---

    @Test
    fun `advanceStep from the welcome screen moves to the microphone`() = runTest(testDispatcher) {
        viewModel.advanceStep()
        assertEquals(OnboardingStep.Microphone, viewModel.currentStep.value)
    }

    @Test
    fun `advanceStep from the microphone moves to the keyboard setup`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Microphone
        viewModel.advanceStep() // -> KeyboardSetup
        assertEquals(OnboardingStep.KeyboardSetup, viewModel.currentStep.value)
    }

    @Test
    fun `advanceStep from step 5 is blocked when download not complete`() =
        runTest(testDispatcher) {
            // Move to step 5
            repeat(4) { viewModel.advanceStep() }
            assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)

            // Attempt advance — blocked since download not complete
            viewModel.advanceStep()
            assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
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
            assertEquals(OnboardingStep.FirstDictation, viewModel.currentStep.value)
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
    fun `goBack from the microphone returns to the welcome screen`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Microphone
        viewModel.goBack()
        assertEquals(OnboardingStep.Welcome, viewModel.currentStep.value)
    }

    @Test
    fun `goBack from the welcome screen stays there`() = runTest(testDispatcher) {
        viewModel.goBack()
        assertEquals(OnboardingStep.Welcome, viewModel.currentStep.value)
    }

    @Test
    fun `goBack does nothing on a step without a way back`() = runTest(testDispatcher) {
        repeat(3) { viewModel.advanceStep() } // -> KeyboardLayout
        viewModel.goBack()
        assertEquals(OnboardingStep.KeyboardLayout, viewModel.currentStep.value)
    }

    // --- Skip (#196) ---

    @Test
    fun `skip on the first dictation moves to the completion`() = runTest(testDispatcher) {
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.FirstDictation.storedName }
        val vm = relaunch()

        vm.skipStep()
        advanceUntilIdle()

        assertEquals(OnboardingStep.Completion, vm.currentStep.value)
        assertEquals("completion", fakeDataStore.data.first()[PreferenceKeys.ONBOARDING_STEP])
    }

    @Test
    fun `skip does nothing on a step that does not offer it`() = runTest(testDispatcher) {
        repeat(4) { viewModel.advanceStep() } // -> ModelPreparation, install not done
        viewModel.skipStep()
        assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
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
        assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
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
            val recreated = OnboardingViewModel(context, fakeDataStore, fakeInstaller, SavedStateHandle())
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
