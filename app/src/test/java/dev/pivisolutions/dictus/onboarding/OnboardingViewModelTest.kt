package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.DownloadProgress
import dev.pivisolutions.dictus.service.ModelDownloader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
 * Uses FakeDataStore (in-memory) and FakeModelDownloader to isolate the ViewModel from real I/O.
 * Robolectric provides the Android Context needed by ModelManager in FakeModelDownloader.
 * StandardTestDispatcher gives deterministic coroutine execution via advanceUntilIdle().
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var context: android.content.Context
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var fakeDownloader: FakeModelDownloader
    private lateinit var viewModel: OnboardingViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeDataStore = FakeDataStore()
        fakeDownloader = FakeModelDownloader(ModelManager(context))
        viewModel = OnboardingViewModel(context, fakeDataStore, fakeDownloader, SavedStateHandle())
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
    fun `initial state - imeActivated is false`() = runTest(testDispatcher) {
        assertFalse(viewModel.imeActivated.value)
    }

    @Test
    fun `initial state - downloadProgress is -1`() = runTest(testDispatcher) {
        assertEquals(-1, viewModel.downloadProgress.value)
    }

    @Test
    fun `initial state - modelDownloadComplete is false`() = runTest(testDispatcher) {
        assertFalse(viewModel.modelDownloadComplete.value)
    }

    // --- SavedStateHandle restoration ---

    @Test
    fun `savedStateHandle - restores currentStep from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeDownloader,
            SavedStateHandle(mapOf("currentStep" to 3)),
        )
        assertEquals(3, restoredVm.currentStep.value)
    }

    @Test
    fun `savedStateHandle - restores micPermissionGranted from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeDownloader,
            SavedStateHandle(mapOf("micPermissionGranted" to true)),
        )
        assertTrue(restoredVm.micPermissionGranted.value)
    }

    @Test
    fun `savedStateHandle - restores imeActivated from saved state`() = runTest(testDispatcher) {
        val restoredVm = OnboardingViewModel(
            context,
            fakeDataStore,
            fakeDownloader,
            SavedStateHandle(mapOf("imeActivated" to true)),
        )
        assertTrue(restoredVm.imeActivated.value)
    }

    @Test
    fun `savedStateHandle - advanceStep writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeDownloader, ssh)
        vm.advanceStep()
        assertEquals(2, ssh.get<Int>("currentStep"))
    }

    @Test
    fun `savedStateHandle - setMicPermissionGranted writes to savedStateHandle`() = runTest(testDispatcher) {
        val ssh = SavedStateHandle()
        val vm = OnboardingViewModel(context, fakeDataStore, fakeDownloader, ssh)
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

            // Simulate successful download
            fakeDownloader.nextFlow = flowOf(
                DownloadProgress.Progress(50),
                DownloadProgress.Complete("/path/to/model"),
            )
            viewModel.startModelDownload()
            advanceUntilIdle()

            assertTrue(viewModel.modelDownloadComplete.value)

            // Now advance should work
            viewModel.advanceStep()
            assertEquals(6, viewModel.currentStep.value)
        }

    @Test
    fun `advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING to DataStore`() =
        runTest(testDispatcher) {
            // Move to step 5, complete download, advance to 6
            repeat(4) { viewModel.advanceStep() }
            fakeDownloader.nextFlow = flowOf(DownloadProgress.Complete("/path/to/model"))
            viewModel.startModelDownload()
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

    @Test
    fun `setImeActivated true sets imeActivated to true`() = runTest(testDispatcher) {
        viewModel.setImeActivated(true)
        assertTrue(viewModel.imeActivated.value)
    }

    // --- Download ---

    @Test
    fun `startModelDownload updates downloadProgress to 100 on complete`() =
        runTest(testDispatcher) {
            fakeDownloader.nextFlow = flowOf(
                DownloadProgress.Progress(0),
                DownloadProgress.Progress(50),
                DownloadProgress.Progress(100),
                DownloadProgress.Complete("/path/to/model"),
            )

            viewModel.startModelDownload()
            advanceUntilIdle()

            assertEquals(100, viewModel.downloadProgress.value)
            assertTrue(viewModel.modelDownloadComplete.value)
        }

    @Test
    fun `startModelDownload on error sets downloadError and resets progress`() =
        runTest(testDispatcher) {
            fakeDownloader.nextFlow = flowOf(DownloadProgress.Error("Network failure"))

            viewModel.startModelDownload()
            advanceUntilIdle()

            assertEquals("Network failure", viewModel.downloadError.value)
            assertEquals(-1, viewModel.downloadProgress.value)
        }

    @Test
    fun `retryDownload starts download again`() = runTest(testDispatcher) {
        fakeDownloader.nextFlow = flowOf(DownloadProgress.Complete("/path/to/model"))

        viewModel.retryDownload()
        advanceUntilIdle()

        assertTrue(viewModel.modelDownloadComplete.value)
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
 * Configurable fake ModelDownloader for tests.
 *
 * Set [nextFlow] before triggering [downloadWithProgress] to control what events
 * the ViewModel collects. Defaults to an immediate Complete so most tests just work.
 *
 * Extends ModelDownloader (which is now `open`) and overrides the public entry point.
 */
class FakeModelDownloader(modelManager: ModelManager) : ModelDownloader(modelManager) {
    var nextFlow: Flow<DownloadProgress> = flowOf(DownloadProgress.Complete("/fake/path"))

    override fun downloadWithProgress(modelKey: String): Flow<DownloadProgress> = nextFlow
}
