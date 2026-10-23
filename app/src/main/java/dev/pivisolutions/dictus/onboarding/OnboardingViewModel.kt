package dev.pivisolutions.dictus.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.service.ModelInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the 7-step onboarding flow.
 *
 * Manages state transitions between onboarding steps with validation guards:
 * - Step 2 (Mic): user must grant RECORD_AUDIO permission
 * - Step 3 (Keyboard): user must enable the Dictus IME
 * - Step 5 (Download): model install must complete before advancing
 * - Step 7 (Success): tapping "Commencer" writes HAS_COMPLETED_ONBOARDING=true to DataStore
 *
 * WHY ViewModel (not stateless composable): Onboarding state survives configuration changes
 * (e.g. screen rotation during download).
 *
 * WHY the install is not run here: it takes minutes for Parakeet, and a coroutine in this
 * ViewModel stopped with the screen and lost its result when the ViewModel was cleared
 * (#169). [ModelInstaller] runs it in WorkManager; this ViewModel only observes its state.
 *
 * WHY SavedStateHandle for currentStep/micPermissionGranted: The system can kill
 * the app process when showing a system dialog (e.g. the mic permission prompt on step 2).
 * SavedStateHandle persists these values in the Activity's saved instance state bundle so that
 * when the user returns to the app, the onboarding resumes at the correct step instead of
 * resetting to step 1.
 *
 * WHY regular MutableStateFlow for selectedLayout: transient, the layout can be re-chosen.
 * The install state needs no saving either: WorkManager keeps it across process death.
 *
 * WHY active model at step 5 but onboarding completion at step 7: The test recording in step 6
 * must use the exact recommended model that step 5 downloaded. Selecting that model when the
 * download completes makes it available to DictationService without prematurely marking the
 * onboarding flow complete; HAS_COMPLETED_ONBOARDING is still written only on the success screen.
 *
 * @param context Application context for device capability checks (RAM-based model recommendation).
 * @param dataStore Application-scoped DataStore for persistent onboarding state.
 * @param modelInstaller Starts and observes the install of the recommended model.
 * @param savedStateHandle Hilt auto-provides this for @HiltViewModel; persists step/flags across
 *   process death caused by system dialogs (mic permission, etc.).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>,
    private val modelInstaller: ModelInstaller,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // --- Recommended model based on device RAM ---

    /** Stable key for the recommended model (e.g. "parakeet-tdt-0.6b-v3" or "small-q5_1"). */
    val recommendedKey: String = ModelCatalog.recommendedModelKey(context)

    /** Full ModelInfo for the recommended model, used by the UI to display name/size/quality. */
    val recommendedModelInfo: ModelInfo = ModelCatalog.findByKey(recommendedKey)!!

    // --- Step state machine (SavedStateHandle-backed for process-death survival) ---

    /** Current onboarding step (1–7). Survives process death via SavedStateHandle. */
    val currentStep: StateFlow<Int> = savedStateHandle.getStateFlow("currentStep", 1)

    // --- Permission / activation flags (SavedStateHandle-backed) ---

    /** True once the user has granted RECORD_AUDIO permission. Survives process death. */
    val micPermissionGranted: StateFlow<Boolean> =
        savedStateHandle.getStateFlow("micPermissionGranted", false)


    // --- Keyboard layout selection (transient — user can re-select after process death) ---

    private val _selectedLayout = MutableStateFlow("azerty")

    /** The keyboard layout chosen by the user. Defaults to "azerty". */
    val selectedLayout: StateFlow<String> = _selectedLayout.asStateFlow()

    // --- Model install (owned by WorkManager, observed here) ---

    /**
     * Install state of the recommended model.
     *
     * WHY Eagerly and the ACTIVE_MODEL write here: the write must happen when the install
     * completes even if the screen is not collecting at that moment, and again harmlessly if
     * this ViewModel is recreated after the worker finished.
     */
    val installState: StateFlow<ModelInstallState> = modelInstaller.state(recommendedKey)
        .onEach { state ->
            if (state is ModelInstallState.Done) {
                dataStore.edit { prefs -> prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelInstallState.Idle)

    // --- Public actions ---

    /** Grant or revoke microphone permission state. Persisted via SavedStateHandle. */
    fun setMicPermissionGranted(granted: Boolean) {
        savedStateHandle["micPermissionGranted"] = granted
    }


    /** Change the selected keyboard layout preference. */
    fun setLayout(layout: String) {
        _selectedLayout.value = layout
    }

    /**
     * Advance to the next onboarding step, respecting validation guards:
     * - Step 5: blocked until model download completes.
     * - Step 7: triggers DataStore persistence and completes onboarding.
     */
    fun advanceStep() {
        val step = currentStep.value
        when {
            step == 5 && installState.value !is ModelInstallState.Done -> return // Block: install not complete
            step == 7 -> completeOnboarding()
            step < 7 -> savedStateHandle["currentStep"] = step + 1
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
     * Start installing the recommended model in the background.
     *
     * Progress, completion and failure arrive through [installState]. A tap while an install
     * is already running is ignored by [ModelInstaller].
     */
    fun startModelDownload() = modelInstaller.install(recommendedKey)

    /** Retry a failed install. Equivalent to calling startModelDownload() again. */
    fun retryDownload() = startModelDownload()

    // --- Private ---

    /**
     * Persist the selected layout, active model, and onboarding completion flag to DataStore.
     *
     * Called when the user taps "Commencer" on step 7. The DataStore write causes AppNavHost
     * to recompose and switch from the onboarding flow to the main tab layout.
     */
    private fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.KEYBOARD_LAYOUT] = _selectedLayout.value
                prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey
                prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true
            }
        }
    }
}
