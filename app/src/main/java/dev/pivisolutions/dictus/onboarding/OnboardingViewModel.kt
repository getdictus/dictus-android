package dev.pivisolutions.dictus.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 * WHY DataStore write at step 6 (not step 5): The user has completed the flow and seen the
 * success screen — only then is onboarding considered "done". Writing earlier would skip the
 * success screen if the app is killed and relaunched.
 *
 * @param dataStore Application-scoped DataStore for persistent onboarding state.
 * @param modelDownloader Injectable downloader for the default Whisper model.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val modelDownloader: ModelDownloader,
) : ViewModel() {

    // --- Step state machine ---

    private val _currentStep = MutableStateFlow(1)

    /** Current onboarding step (1–6). */
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    // --- Permission / activation flags ---

    private val _micPermissionGranted = MutableStateFlow(false)

    /** True once the user has granted RECORD_AUDIO permission. */
    val micPermissionGranted: StateFlow<Boolean> = _micPermissionGranted.asStateFlow()

    private val _imeActivated = MutableStateFlow(false)

    /** True once the Dictus IME appears in the system's enabled input method list. */
    val imeActivated: StateFlow<Boolean> = _imeActivated.asStateFlow()

    // --- Keyboard layout selection ---

    private val _selectedLayout = MutableStateFlow("azerty")

    /** The keyboard layout chosen by the user. Defaults to "azerty". */
    val selectedLayout: StateFlow<String> = _selectedLayout.asStateFlow()

    // --- Model download progress ---

    /** -1 = not started, 0–99 = in progress, 100 = complete. */
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _modelDownloadComplete = MutableStateFlow(false)
    val modelDownloadComplete: StateFlow<Boolean> = _modelDownloadComplete.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    // --- Public actions ---

    /** Grant or revoke microphone permission state. */
    fun setMicPermissionGranted(granted: Boolean) {
        _micPermissionGranted.value = granted
    }

    /** Mark whether the Dictus IME is enabled in system settings. */
    fun setImeActivated(activated: Boolean) {
        _imeActivated.value = activated
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
        val step = _currentStep.value
        when {
            step == 5 && !_modelDownloadComplete.value -> return // Block: download not complete
            step == 6 -> completeOnboarding()
            step < 6 -> _currentStep.value = step + 1
        }
    }

    /**
     * Navigate back to the previous step.
     * Does nothing when already on step 1.
     */
    fun goBack() {
        if (_currentStep.value > 1) _currentStep.value -= 1
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
