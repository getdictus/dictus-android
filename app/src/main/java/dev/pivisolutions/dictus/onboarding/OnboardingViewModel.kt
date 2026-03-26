package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.service.DownloadProgress
import dev.pivisolutions.dictus.service.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the 6-step onboarding flow.
 *
 * Manages state transitions between onboarding steps with validation guards:
 * - Step 2 (Mic): user must grant RECORD_AUDIO permission
 * - Step 3 (Keyboard): user must enable the Dictus IME
 * - Step 5 (Download): model download must complete before advancing
 * - Step 6 (Success): tapping "Commencer" writes HAS_COMPLETED_ONBOARDING=true to DataStore
 *
 * WHY ViewModel (not stateless composable): Onboarding state survives configuration changes
 * (e.g. screen rotation during download). The ViewModel holds the download coroutine so a
 * rotation does not cancel an in-progress download.
 *
 * WHY SavedStateHandle for currentStep/micPermissionGranted/imeActivated: The system can kill
 * the app process when showing a system dialog (e.g. the mic permission prompt on step 2).
 * SavedStateHandle persists these values in the Activity's saved instance state bundle so that
 * when the user returns to the app, the onboarding resumes at the correct step instead of
 * resetting to step 1.
 *
 * WHY regular MutableStateFlow for selectedLayout/downloadProgress/modelDownloadComplete/downloadError:
 * These are transient: layout can be re-chosen, and a partial download restarts anyway on process
 * death. Persisting them would add complexity with no user-visible benefit.
 *
 * WHY DataStore write at step 6 (not step 5): The user has completed the flow and seen the
 * success screen — only then is onboarding considered "done". Writing earlier would skip the
 * success screen if the app is killed and relaunched.
 *
 * @param dataStore Application-scoped DataStore for persistent onboarding state.
 * @param modelDownloader Injectable downloader for the default Whisper model.
 * @param savedStateHandle Hilt auto-provides this for @HiltViewModel; persists step/flags across
 *   process death caused by system dialogs (mic permission, etc.).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val modelDownloader: ModelDownloader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // --- Step state machine (SavedStateHandle-backed for process-death survival) ---

    /** Current onboarding step (1–6). Survives process death via SavedStateHandle. */
    val currentStep: StateFlow<Int> = savedStateHandle.getStateFlow("currentStep", 1)

    // --- Permission / activation flags (SavedStateHandle-backed) ---

    /** True once the user has granted RECORD_AUDIO permission. Survives process death. */
    val micPermissionGranted: StateFlow<Boolean> =
        savedStateHandle.getStateFlow("micPermissionGranted", false)

    /** True once the Dictus IME appears in the system's enabled input method list. Survives process death. */
    val imeActivated: StateFlow<Boolean> =
        savedStateHandle.getStateFlow("imeActivated", false)

    // --- Keyboard layout selection (transient — user can re-select after process death) ---

    private val _selectedLayout = MutableStateFlow("azerty")

    /** The keyboard layout chosen by the user. Defaults to "azerty". */
    val selectedLayout: StateFlow<String> = _selectedLayout.asStateFlow()

    // --- Model download progress (transient — download restarts on process death) ---

    /** -1 = not started, 0–99 = in progress, 100 = complete. */
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _modelDownloadComplete = MutableStateFlow(false)
    val modelDownloadComplete: StateFlow<Boolean> = _modelDownloadComplete.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    // --- Public actions ---

    /** Grant or revoke microphone permission state. Persisted via SavedStateHandle. */
    fun setMicPermissionGranted(granted: Boolean) {
        savedStateHandle["micPermissionGranted"] = granted
    }

    /** Mark whether the Dictus IME is enabled in system settings. Persisted via SavedStateHandle. */
    fun setImeActivated(activated: Boolean) {
        savedStateHandle["imeActivated"] = activated
    }

    /** Change the selected keyboard layout preference. */
    fun setLayout(layout: String) {
        _selectedLayout.value = layout
    }

    /**
     * Advance to the next onboarding step, respecting validation guards:
     * - Step 5: blocked until model download completes.
     * - Step 6: triggers DataStore persistence and completes onboarding.
     */
    fun advanceStep() {
        val step = currentStep.value
        when {
            step == 5 && !_modelDownloadComplete.value -> return // Block: download not complete
            step == 6 -> completeOnboarding()
            step < 6 -> savedStateHandle["currentStep"] = step + 1
        }
    }

    /**
     * Navigate back to the previous step.
     * Does nothing when already on step 1.
     */
    fun goBack() {
        val step = currentStep.value
        if (step > 1) savedStateHandle["currentStep"] = step - 1
    }

    /**
     * Begin downloading the default Whisper model and emit progress updates.
     *
     * Sets downloadProgress from 0 to 100, then sets modelDownloadComplete to true.
     * On error, sets downloadError and resets downloadProgress to -1.
     */
    fun startModelDownload() {
        _downloadProgress.value = 0
        _downloadError.value = null
        viewModelScope.launch {
            modelDownloader.downloadWithProgress(ModelCatalog.DEFAULT_KEY)
                .collect { progress ->
                    when (progress) {
                        is DownloadProgress.Progress -> {
                            _downloadProgress.value = progress.percent
                        }
                        is DownloadProgress.Complete -> {
                            _downloadProgress.value = 100
                            _modelDownloadComplete.value = true
                        }
                        is DownloadProgress.Error -> {
                            _downloadError.value = progress.message
                            _downloadProgress.value = -1
                        }
                    }
                }
        }
    }

    /** Retry a failed download. Equivalent to calling startModelDownload() again. */
    fun retryDownload() = startModelDownload()

    // --- Private ---

    /**
     * Persist the selected layout, active model, and onboarding completion flag to DataStore.
     *
     * Called when the user taps "Commencer" on step 6. The DataStore write causes AppNavHost
     * to recompose and switch from the onboarding flow to the main tab layout.
     */
    private fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.KEYBOARD_LAYOUT] = _selectedLayout.value
                prefs[PreferenceKeys.ACTIVE_MODEL] = ModelCatalog.DEFAULT_KEY
                prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true
            }
        }
    }
}
