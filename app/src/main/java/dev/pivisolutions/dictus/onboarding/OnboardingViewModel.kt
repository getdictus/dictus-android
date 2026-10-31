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
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.models.ModelPreparationTarget
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.service.ModelInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow ([OnboardingStep]).
 *
 * Manages state transitions between onboarding steps with validation guards:
 * - Microphone: user must grant RECORD_AUDIO permission
 * - Keyboard setup: user must enable the Dictus IME
 * - Model preparation: model install must complete before advancing
 * - Completion: tapping "Commencer" writes HAS_COMPLETED_ONBOARDING=true to DataStore
 *
 * WHY ViewModel (not stateless composable): Onboarding state survives configuration changes
 * (e.g. screen rotation during download).
 *
 * WHY the install is not run here: it takes minutes for Parakeet, and a coroutine in this
 * ViewModel stopped with the screen and lost its result when the ViewModel was cleared
 * (#169). [ModelInstaller] runs it in WorkManager; this ViewModel only observes its state.
 *
 * WHY DataStore for currentStep (#196): the step has to survive a force-stop and a reboot,
 * not only the system killing the process, which is all SavedStateHandle covered. Every step
 * change is written under the step's stable name ([OnboardingStep.storedName]) and read back
 * when the ViewModel is created, as iOS does with `OnboardingStep.save` and `current()`.
 *
 * WHY SavedStateHandle for micPermissionGranted: The system can kill the app process when
 * showing the mic permission prompt. The flag only mirrors the system's permission, which
 * the microphone step re-reads on entry, so it needs no more than that.
 *
 * The install state needs no saving: WorkManager keeps it across process death.
 *
 * WHY active model at step 4 but onboarding completion at step 6: The test recording in step 5
 * must use the exact recommended model that step 4 downloaded. Selecting that model when the
 * download completes makes it available to DictationService without prematurely marking the
 * onboarding flow complete; HAS_COMPLETED_ONBOARDING is still written only on the success screen.
 *
 * @param context Application context for device capability checks (RAM-based model recommendation).
 * @param dataStore Application-scoped DataStore for persistent onboarding state.
 * @param modelInstaller Starts and observes the install of the recommended model.
 * @param modelManager Says whether the recommended model's files are already on disk.
 * @param savedStateHandle Hilt auto-provides this for @HiltViewModel; persists the mic flag
 *   across process death caused by the mic permission dialog.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>,
    private val modelInstaller: ModelInstaller,
    private val modelManager: ModelManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // --- Recommended model based on device RAM ---

    /** Stable key for the recommended model (e.g. "parakeet-tdt-0.6b-v3" or "small-q5_1"). */
    val recommendedKey: String = ModelCatalog.recommendedModelKey(context)

    /** Full ModelInfo for the recommended model, used by the UI to display name/size/quality. */
    val recommendedModelInfo: ModelInfo = ModelCatalog.findByKey(recommendedKey)!!

    // --- Step state machine (DataStore-backed, #196) ---

    private val _currentStep = MutableStateFlow<OnboardingStep?>(null)

    /**
     * The step on screen; null until the persisted step has been read, which the screen
     * shows as the bare background.
     *
     * WHY HELD HERE AND WRITTEN TO DATASTORE (not read back from it): a tap moves the step at
     * once, without waiting for the write, and the write only matters to the next launch.
     */
    val currentStep: StateFlow<OnboardingStep?> = _currentStep.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = try {
                dataStore.data.first()[PreferenceKeys.ONBOARDING_STEP]
            } catch (e: IOException) {
                // An unreadable store starts at the welcome screen rather than crashing.
                Timber.w(e, "Cannot read the onboarding step")
                null
            }
            // A step change made before the read finished wins over the stored step.
            _currentStep.compareAndSet(null, OnboardingStep.resumed(stored))
        }
    }

    // --- Permission / activation flags (SavedStateHandle-backed) ---

    /** True once the user has granted RECORD_AUDIO permission. Survives process death. */
    val micPermissionGranted: StateFlow<Boolean> =
        savedStateHandle.getStateFlow("micPermissionGranted", false)


    // --- Model install (owned by WorkManager, observed here) ---

    /** An install was asked for and WorkManager still reports the previous one (see [ModelPreparationTarget]). */
    private val _awaitingInstall = MutableStateFlow(false)

    /**
     * Install state of the recommended model.
     *
     * WHY Eagerly and the ACTIVE_MODEL write here: the write must happen when the install
     * completes even if the screen is not collecting at that moment, and again harmlessly if
     * this ViewModel is recreated after the worker finished.
     */
    val installState: StateFlow<ModelInstallState> = modelInstaller.state(recommendedKey)
        .onEach { state ->
            // The first state reported after an install was asked for is that install's.
            _awaitingInstall.value = false
            if (state is ModelInstallState.Done) {
                dataStore.edit { prefs -> prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelInstallState.Idle)

    /** What step 4's preparation screen draws (#181). */
    val preparation: StateFlow<ModelPreparationTarget> = combine(installState, _awaitingInstall) { install, awaiting ->
        ModelPreparationTarget(
            info = recommendedModelInfo,
            install = install,
            awaitingInstall = awaiting,
            filesOnDisk = modelManager.isDownloaded(recommendedKey),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ModelPreparationTarget(recommendedModelInfo, ModelInstallState.Idle, filesOnDisk = modelManager.isDownloaded(recommendedKey)),
    )

    // --- Public actions ---

    /** Grant or revoke microphone permission state. Persisted via SavedStateHandle. */
    fun setMicPermissionGranted(granted: Boolean) {
        savedStateHandle["micPermissionGranted"] = granted
    }


    /**
     * Advance to the next onboarding step, respecting validation guards:
     * - Model preparation: blocked until model download completes.
     * - Completion: triggers DataStore persistence and completes onboarding.
     */
    fun advanceStep() {
        val step = currentStep.value ?: return
        when {
            // Block: install not complete
            step == OnboardingStep.ModelPreparation && installState.value !is ModelInstallState.Done && !modelManager.isDownloaded(recommendedKey) -> return
            step == OnboardingStep.Completion -> completeOnboarding()
            else -> step.next?.let { show(it) }
        }
    }

    /**
     * [advanceStep] on behalf of the page of [step]; ignored once [step] is no longer on screen.
     *
     * WHY: the shell slides the pages, so the page that just moved on stays composed for the
     * length of the slide and can still run its effects. The microphone step's auto-advance
     * fired a second time from there and skipped the keyboard setup.
     */
    fun advanceFrom(step: OnboardingStep) {
        if (currentStep.value == step) advanceStep()
    }

    /**
     * The shell's back control and the system back: moves to [OnboardingStep.stepBack] and
     * records it, so an app killed now resumes on the step the user went back to. Does
     * nothing on a step without a back control.
     */
    fun goBack() {
        val back = currentStep.value?.stepBack ?: return
        show(back)
    }

    /**
     * The shell's « Passer », on the steps that offer it ([OnboardingStep.isSkippable]):
     * moves on as the step's own button would. On the first dictation that is the
     * completion screen, as a finished dictation goes.
     */
    fun skipStep() {
        val step = currentStep.value ?: return
        if (!step.isSkippable) return
        step.next?.let { show(it) }
    }

    /**
     * Step 5 opened: start the install unless one runs, failed or already finished (#181).
     *
     * WHY no « Télécharger » tap: like iOS, the step is the preparation screen and the download
     * starts with it. A failure is not retried here: the screen shows it with « Réessayer ».
     * Reads WorkManager's state itself: [installState] starts as Idle before WorkManager answers.
     */
    fun startPreparation() {
        viewModelScope.launch {
            if (modelManager.isDownloaded(recommendedKey)) {
                // Installed outside this run's WorkManager history: select it so the engine loads it.
                dataStore.edit { prefs -> prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey }
                return@launch
            }
            val state = modelInstaller.state(recommendedKey).first()
            if (!state.isInProgress && state != ModelInstallState.Failed) startModelDownload()
        }
    }

    /**
     * Start installing the recommended model in the background.
     *
     * Progress, completion and failure arrive through [installState]. A tap while an install
     * is already running is ignored by [ModelInstaller].
     */
    fun startModelDownload() {
        _awaitingInstall.value = true
        modelInstaller.install(recommendedKey)
    }

    /** Retry a failed install. Equivalent to calling startModelDownload() again. */
    fun retryDownload() = startModelDownload()

    // --- Private ---

    /** Puts [step] on screen and records it as the step to resume at. */
    private fun show(step: OnboardingStep) {
        _currentStep.value = step
        viewModelScope.launch {
            try {
                dataStore.edit { prefs -> prefs[PreferenceKeys.ONBOARDING_STEP] = step.storedName }
            } catch (e: IOException) {
                // The flow goes on from memory; only a relaunch would miss this step.
                Timber.w(e, "Cannot save the onboarding step ${step.storedName}")
            }
        }
    }

    /**
     * Persist the active model and onboarding completion flag to DataStore.
     *
     * Called when the user taps "Commencer" on the completion step. The DataStore write causes
     * AppNavHost to recompose and switch from the onboarding flow to the main tab layout.
     *
     * The stored step goes back to the welcome screen, as on iOS, so a later reset of the
     * onboarding starts from the beginning instead of resuming on the completion screen.
     */
    private fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey
                prefs[PreferenceKeys.ONBOARDING_STEP] = OnboardingStep.Welcome.storedName
                prefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true
            }
        }
    }
}
