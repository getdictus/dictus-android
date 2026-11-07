package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.KeyboardLayoutPreference
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.model.ModelCatalog
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
import java.util.Locale

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
    fun `initial state - a fresh install starts at the welcome screen`() = runTest(testDispatcher) {
        assertEquals(OnboardingStep.Welcome, viewModel.currentStep.value)
    }

    @Test
    fun `initial state - micPermissionGranted is false`() = runTest(testDispatcher) {
        assertFalse(viewModel.micPermissionGranted.value)
    }


    @Test
    fun `initial state - install is Idle`() = runTest(testDispatcher) {
        assertEquals(ModelInstallState.Idle, viewModel.installState.value)
    }

    // --- Resume after a force-stop (#196) ---

    /** A new ViewModel on the same DataStore: what a relaunch after a force-stop reads. */
    private fun relaunch() = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), SavedStateHandle())

    /** The system's microphone permission, as a second run or an earlier grant leaves it. */
    private fun grantMicrophone() {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.RECORD_AUDIO)
    }

    /** Every file of the recommended model at its exact size: what [ModelManager.isDownloaded] reads. */
    private fun placeRecommendedModelOnDisk() {
        val key = viewModel.recommendedKey
        val dir = java.io.File(ModelManager(context).getTargetPath(key)!!).parentFile!!.apply { mkdirs() }
        for (file in ModelCatalog.findByKey(key)!!.files) {
            // Sparse: the size is all ModelManager checks.
            java.io.RandomAccessFile(java.io.File(dir, file.name), "rw").use { it.setLength(file.sizeBytes) }
        }
        assertTrue(ModelManager(context).isDownloaded(key))
    }

    @Test
    fun `resume - a relaunch resumes on the step the user left`() = runTest(testDispatcher) {
        repeat(3) { viewModel.advanceStep() } // -> ModelPreparation
        advanceUntilIdle()

        assertEquals(OnboardingStep.ModelPreparation, relaunch().currentStep.value)
    }

    @Test
    fun `resume - every step is written under its stable name`() = runTest(testDispatcher) {
        viewModel.advanceStep()
        advanceUntilIdle()

        assertEquals("language", fakeDataStore.data.first()[PreferenceKeys.ONBOARDING_STEP])
    }

    @Test
    fun `resume - every stored step but the microphone is restored`() = runTest(testDispatcher) {
        for (step in OnboardingStep.entries - OnboardingStep.Microphone) {
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
    fun `resume - a microphone stored by the order before #200 resumes at the keyboard setup`() =
        runTest(testDispatcher) {
            // Written while the microphone came before the keyboard: the keyboard is not set up.
            fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = "microphone" }

            assertEquals(OnboardingStep.KeyboardSetup, relaunch().currentStep.value)
        }

    @Test
    fun `resume - going back is what a relaunch resumes on`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Language
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

    @Test
    fun `resume - an unreadable store starts at the welcome screen`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(context, FailingDataStore(), fakeInstaller, ModelManager(context), SavedStateHandle())

        assertEquals(OnboardingStep.Welcome, vm.currentStep.value)
    }

    @Test
    fun `resume - a failed write keeps the flow moving`() = runTest(testDispatcher) {
        val vm = OnboardingViewModel(context, FailingDataStore(), fakeInstaller, ModelManager(context), SavedStateHandle())

        vm.advanceStep()
        advanceUntilIdle()

        assertEquals(OnboardingStep.Language, vm.currentStep.value)
    }

    // --- Language step (#198) ---

    /** A ViewModel on a phone whose languages are [tags], on the language step. */
    private suspend fun onLanguageStep(vararg tags: String): OnboardingViewModel {
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.Language.storedName }
        return OnboardingViewModel(
            context, fakeDataStore, fakeInstaller, ModelManager(context), SavedStateHandle(),
            tags.map(Locale::forLanguageTag),
        )
    }

    @Test
    fun `language - the step opens on the phone's language`() = runTest(testDispatcher) {
        val vm = onLanguageStep("de-DE", "en-US")

        assertEquals(LanguageSetup(SupportedLanguage.GERMAN, KeyboardLayout.QWERTZ), vm.languageSetup.value)
    }

    @Test
    fun `language - the step keeps a layout the user already chose`() = runTest(testDispatcher) {
        fakeDataStore.edit { KeyboardLayoutPreference.setLayout(it, KeyboardLayout.QWERTY, SupportedLanguage.FRENCH) }

        val vm = onLanguageStep("fr-FR")

        assertEquals(LanguageSetup(SupportedLanguage.FRENCH, KeyboardLayout.QWERTY), vm.languageSetup.value)
    }

    @Test
    fun `language - Continue saves the keyboard language and layout`() = runTest(testDispatcher) {
        val vm = onLanguageStep("fr-FR")
        vm.setKeyboardLanguage(SupportedLanguage.SPANISH)
        vm.setKeyboardLayout(KeyboardLayout.AZERTY)

        vm.confirmLanguage()
        advanceUntilIdle()

        val prefs = fakeDataStore.data.first()
        assertEquals("es", prefs[PreferenceKeys.KEYBOARD_LANGUAGE])
        assertEquals(KeyboardLayout.AZERTY, KeyboardLayoutPreference.layout(prefs, SupportedLanguage.SPANISH))
        assertEquals(null, prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE])
    }

    @Test
    fun `language - Continue starts the recommended model's download and says so before moving on`() =
        runTest(testDispatcher) {
            val vm = onLanguageStep("fr-FR")

            vm.confirmLanguage()
            advanceUntilIdle()

            assertEquals(listOf(vm.recommendedKey), fakeInstaller.installed)
            assertTrue(vm.downloadNotice.value)
            assertEquals(OnboardingStep.Language, vm.currentStep.value)

            vm.dismissDownloadNotice()

            assertFalse(vm.downloadNotice.value)
            assertEquals(OnboardingStep.KeyboardSetup, vm.currentStep.value)
        }

    @Test
    fun `language - Continue does not start a second download over a running one`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Downloading(10)
        val vm = onLanguageStep("fr-FR")

        vm.confirmLanguage()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), fakeInstaller.installed)
        assertFalse(vm.downloadNotice.value)
        assertEquals(OnboardingStep.KeyboardSetup, vm.currentStep.value)
    }

    @Test
    fun `language - Continue with the model already on disk moves on without the notice`() = runTest(testDispatcher) {
        placeRecommendedModelOnDisk()
        val vm = onLanguageStep("fr-FR")

        vm.confirmLanguage()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), fakeInstaller.installed)
        assertFalse(vm.downloadNotice.value)
        assertEquals(OnboardingStep.KeyboardSetup, vm.currentStep.value)
    }

    @Test
    fun `language - Continue after a failed install moves on without the notice`() = runTest(testDispatcher) {
        // The preparation step shows the failure with « Réessayer »; nothing starts here.
        fakeInstaller.state.value = ModelInstallState.Failed
        val vm = onLanguageStep("fr-FR")

        vm.confirmLanguage()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), fakeInstaller.installed)
        assertFalse(vm.downloadNotice.value)
        assertEquals(OnboardingStep.KeyboardSetup, vm.currentStep.value)
    }

    @Test
    fun `language - nothing is saved before Continue`() = runTest(testDispatcher) {
        val vm = onLanguageStep("fr-FR")

        vm.setKeyboardLanguage(SupportedLanguage.GERMAN)
        advanceUntilIdle()

        assertEquals(null, fakeDataStore.data.first()[PreferenceKeys.KEYBOARD_LANGUAGE])
        assertEquals(emptyList<String>(), fakeInstaller.installed)
    }

    @Test
    fun `language - going back to the intro keeps the choices`() = runTest(testDispatcher) {
        val vm = onLanguageStep("fr-FR")
        vm.setKeyboardLanguage(SupportedLanguage.GERMAN)

        vm.goBack()
        vm.advanceStep()

        assertEquals(OnboardingStep.Language, vm.currentStep.value)
        assertEquals(SupportedLanguage.GERMAN, vm.languageSetup.value?.language)
    }

    // --- SavedStateHandle restoration ---

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
    fun `savedStateHandle - setMicPermissionGranted writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeInstaller, ModelManager(context), ssh)
        vm.setMicPermissionGranted(true)
        assertEquals(true, ssh.get<Boolean>("micPermissionGranted"))
    }

    // --- Step navigation ---

    @Test
    fun `advanceStep from the welcome screen moves to the language step`() = runTest(testDispatcher) {
        viewModel.advanceStep()
        assertEquals(OnboardingStep.Language, viewModel.currentStep.value)
    }

    @Test
    fun `the new order on a fresh install - every step is shown`() = runTest(testDispatcher) {
        // #200: nothing ready, nothing granted. The model wait is left by its own Done below.
        val walked = mutableListOf(viewModel.currentStep.value)
        repeat(3) { viewModel.advanceStep(); walked += viewModel.currentStep.value }
        fakeInstaller.state.value = ModelInstallState.Done
        advanceUntilIdle()
        repeat(3) { viewModel.advanceStep(); walked += viewModel.currentStep.value }

        assertEquals(
            listOf(
                OnboardingStep.Welcome, OnboardingStep.Language, OnboardingStep.KeyboardSetup,
                OnboardingStep.ModelPreparation, OnboardingStep.Microphone, OnboardingStep.FirstDictation,
                OnboardingStep.Completion,
            ),
            walked,
        )
    }

    @Test
    fun `the keyboard setup passes over the model wait once the model is on disk`() = runTest(testDispatcher) {
        placeRecommendedModelOnDisk()
        repeat(3) { viewModel.advanceStep() } // -> KeyboardSetup, then past the model wait

        assertEquals(OnboardingStep.Microphone, viewModel.currentStep.value)
    }

    @Test
    fun `the keyboard setup waits for the model while it is still installing`() = runTest(testDispatcher) {
        fakeInstaller.state.value = ModelInstallState.Downloading(40)
        advanceUntilIdle()
        repeat(3) { viewModel.advanceStep() }

        assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
    }

    @Test
    fun `the model wait passes over a microphone already granted`() = runTest(testDispatcher) {
        grantMicrophone()
        repeat(3) { viewModel.advanceStep() } // -> ModelPreparation
        fakeInstaller.state.value = ModelInstallState.Done
        advanceUntilIdle()

        viewModel.advanceStep()
        assertEquals(OnboardingStep.FirstDictation, viewModel.currentStep.value)
    }

    @Test
    fun `a ready model and a granted microphone go from the keyboard to the first dictation`() =
        runTest(testDispatcher) {
            placeRecommendedModelOnDisk()
            grantMicrophone()
            repeat(3) { viewModel.advanceStep() }

            assertEquals(OnboardingStep.FirstDictation, viewModel.currentStep.value)
            advanceUntilIdle()
            assertEquals("firstDictation", fakeDataStore.data.first()[PreferenceKeys.ONBOARDING_STEP])
        }

    @Test
    fun `advanceStep from the model wait is blocked when download not complete`() =
        runTest(testDispatcher) {
            repeat(3) { viewModel.advanceStep() }
            assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)

            // Attempt advance — blocked since download not complete
            viewModel.advanceStep()
            assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
        }

    @Test
    fun `advanceStep from the model wait moves to the microphone when download is complete`() =
        runTest(testDispatcher) {
            repeat(3) { viewModel.advanceStep() }

            viewModel.startModelDownload()
            fakeInstaller.state.value = ModelInstallState.Downloading(50)
            fakeInstaller.state.value = ModelInstallState.Done
            advanceUntilIdle()

            assertEquals(ModelInstallState.Done, viewModel.installState.value)

            viewModel.advanceStep()
            assertEquals(OnboardingStep.Microphone, viewModel.currentStep.value)
        }

    @Test
    fun `the microphone moves to the first dictation`() = runTest(testDispatcher) {
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.ModelPreparation.storedName }
        fakeInstaller.state.value = ModelInstallState.Done
        val vm = relaunch()
        advanceUntilIdle()
        vm.advanceStep() // -> Microphone

        vm.advanceFrom(OnboardingStep.Microphone)
        assertEquals(OnboardingStep.FirstDictation, vm.currentStep.value)
    }

    @Test
    fun `advanceStep from the completion writes HAS_COMPLETED_ONBOARDING to DataStore`() =
        runTest(testDispatcher) {
            repeat(3) { viewModel.advanceStep() } // -> ModelPreparation
            viewModel.startModelDownload()
            fakeInstaller.state.value = ModelInstallState.Done
            advanceUntilIdle()
            viewModel.advanceStep() // -> Microphone
            viewModel.advanceStep() // -> FirstDictation
            viewModel.advanceStep() // -> Completion
            assertEquals(OnboardingStep.Completion, viewModel.currentStep.value)

            // « Commencer » on the completion screen
            viewModel.advanceStep()
            advanceUntilIdle()

            assertTrue(
                "HAS_COMPLETED_ONBOARDING should be true",
                fakeDataStore.editedPrefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] == true
            )
            // #134: a fresh install follows each language's default layout, as on iOS.
            assertEquals(null, fakeDataStore.editedPrefs[PreferenceKeys.KEYBOARD_LAYOUT])
        }

    @Test
    fun `goBack from the language step returns to the welcome screen`() = runTest(testDispatcher) {
        viewModel.advanceStep() // -> Language
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
        // From the keyboard setup on, as on iOS: the language step has started the download.
        for (step in OnboardingStep.entries.filter { it.ordinal > OnboardingStep.Language.ordinal }) {
            fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = step.storedName }
            val vm = relaunch()
            val shown = vm.currentStep.value
            vm.goBack()
            assertEquals("$step", shown, vm.currentStep.value)
        }
    }

    @Test
    fun `advanceFrom a page no longer on screen is ignored`() = runTest(testDispatcher) {
        // The microphone page, sliding away after the grant moved the flow on, runs its
        // auto-advance a second time: it must not skip the first dictation.
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.ModelPreparation.storedName }
        fakeInstaller.state.value = ModelInstallState.Done
        val vm = relaunch()
        advanceUntilIdle()
        vm.advanceStep() // -> Microphone
        vm.advanceFrom(OnboardingStep.Microphone) // -> FirstDictation
        vm.advanceFrom(OnboardingStep.Microphone)
        assertEquals(OnboardingStep.FirstDictation, vm.currentStep.value)
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
    fun `not now on the microphone moves to the completion`() = runTest(testDispatcher) {
        // #220: declining the microphone passes over the first dictation, which needs it.
        fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.ModelPreparation.storedName }
        fakeInstaller.state.value = ModelInstallState.Done
        val vm = relaunch()
        advanceUntilIdle()
        vm.advanceStep() // -> Microphone

        vm.skipStep()
        advanceUntilIdle()

        assertEquals(OnboardingStep.Completion, vm.currentStep.value)
    }

    @Test
    fun `skip does nothing on a step that does not offer it`() = runTest(testDispatcher) {
        repeat(3) { viewModel.advanceStep() } // -> ModelPreparation, install not done
        viewModel.skipStep()
        assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
    }

    // --- State mutations ---

    @Test
    fun `the keyboard setup leads straight to the model preparation`() = runTest(testDispatcher) {
        // #270: no ABC/123 step in between, as on iOS (#213).
        repeat(2) { viewModel.advanceStep() } // -> KeyboardSetup
        viewModel.advanceStep()
        assertEquals(OnboardingStep.ModelPreparation, viewModel.currentStep.value)
    }

    @Test
    fun `completing the onboarding leaves the keyboard preferences to Settings`() =
        runTest(testDispatcher) {
            // #270: the onboarding writes no layout (and no default page, a setting removed by #288).
            fakeDataStore.edit { it[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.Completion.storedName }
            val vm = relaunch()

            vm.advanceStep()
            advanceUntilIdle()

            val prefs = fakeDataStore.data.first()
            assertEquals(true, prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING])
            assertEquals(null, prefs[PreferenceKeys.KEYBOARD_LAYOUT])
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
    fun `the model wait stays blocked while the install runs`() = runTest(testDispatcher) {
        repeat(3) { viewModel.advanceStep() }
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

/** DataStore whose disk fails: every read and write throws [java.io.IOException]. */
class FailingDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = kotlinx.coroutines.flow.flow { throw java.io.IOException("disk") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw java.io.IOException("disk")
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
