package dev.pivisolutions.dictus.models

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.AiProvider
import dev.pivisolutions.dictus.model.DeviceRam
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.ModelInstallState
import dev.pivisolutions.dictus.service.ModelInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Represents the UI state of a single model in the model list.
 *
 * @param info            Full model descriptor from the catalog.
 * @param isDownloaded    True if every model file is present on disk with its exact size.
 * @param installState    State of the model's background install (progress, failure).
 * @param requiredRamGb   Non-null when the device has too little RAM for this model:
 *                        the card is greyed out and shows this requirement as the reason.
 */
data class ModelUiState(
    val info: ModelInfo,
    val isDownloaded: Boolean,
    val installState: ModelInstallState = ModelInstallState.Idle,
    val requiredRamGb: Int? = null,
)

/**
 * ViewModel for the Models screen (model download, selection, and deletion).
 *
 * WHY @HiltViewModel: Allows Hilt to inject ModelManager and ModelInstaller without
 * requiring a ViewModelFactory. Hilt generates the factory automatically.
 *
 * WHY separate models StateFlow: The list of models is derived from the catalog
 * (static) but augmented with disk state (dynamic — changes after download/delete)
 * and with each model's install state, observed from WorkManager (#169).
 *
 * @param modelManager   Manages model file presence and deletion on disk.
 * @param modelInstaller Starts installs in the background and reports their state.
 * @param dataStore       Application DataStore for reading/writing active model key.
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val modelInstaller: ModelInstaller,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // Map of modelKey → install state, as WorkManager reports it
    private val _installStates = MutableStateFlow<Map<String, ModelInstallState>>(emptyMap())

    // Derived StateFlow of full model UI state
    private val _models = MutableStateFlow<List<ModelUiState>>(emptyList())
    val models: StateFlow<List<ModelUiState>> = _models.asStateFlow()

    // Total bytes used on disk
    private val _storageUsedBytes = MutableStateFlow(0L)
    val storageUsedBytes: StateFlow<Long> = _storageUsedBytes.asStateFlow()

    // Active model key from DataStore
    val activeModelKey: StateFlow<String> = dataStore.data
        .map { it[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModelCatalog.DEFAULT_KEY,
        )

    /**
     * The model the full-screen preparation is showing, or null (#181).
     *
     * @param awaitingInstall see [ModelPreparationTarget.awaitingInstall].
     * @param loadsEngine     see [ModelPreparationTarget.loadsEngine].
     */
    private data class Preparation(val key: String, val awaitingInstall: Boolean, val loadsEngine: Boolean)

    private val _preparation = MutableStateFlow<Preparation?>(null)

    /** What the preparation screen draws, or null when it is closed. */
    val preparation: StateFlow<ModelPreparationTarget?> = combine(_preparation, _models) { preparation, models ->
        preparation?.let { p ->
            models.firstOrNull { it.info.key == p.key }?.let { model ->
                ModelPreparationTarget(
                    info = model.info,
                    install = model.installState,
                    awaitingInstall = p.awaitingInstall,
                    filesOnDisk = model.isDownloaded,
                    loadsEngine = p.loadsEngine,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Parakeet language mismatch dialog state — holds the pending model key, or null
    private val _showParakeetLanguageDialog = MutableStateFlow<String?>(null)
    val showParakeetLanguageDialog: StateFlow<String?> = _showParakeetLanguageDialog.asStateFlow()

    // Read once: total RAM does not change while the app runs
    private val totalMemBytes: Long = DeviceRam.totalMemBytes(appContext)

    init {
        Timber.d("Device totalMem=%d bytes (%d GB marketed)", totalMemBytes, DeviceRam.marketedGb(totalMemBytes))
        refreshModels()
        // The installs run in WorkManager: started here or in onboarding, they keep reporting
        // after this screen is left and come back with it.
        ModelCatalog.ALL.forEach { info ->
            viewModelScope.launch {
                modelInstaller.state(info.key).collect { state ->
                    _installStates.update { it + (info.key to state) }
                    refreshModels()
                    onInstallStateChanged(info.key, state)
                }
            }
        }
    }

    /**
     * Refresh the model list and storage usage from disk.
     *
     * Called on init and after any download or deletion to keep state current.
     */
    fun refreshModels() {
        val installStates = _installStates.value
        // WHY no filtering: a hidden model reads as a missing feature (issue #121).
        // Models the device cannot run stay listed, greyed out with the RAM they need.
        _models.value = ModelCatalog.ALL
            .map { info ->
                ModelUiState(
                    info = info,
                    isDownloaded = modelManager.isDownloaded(info.key),
                    installState = installStates[info.key] ?: ModelInstallState.Idle,
                    requiredRamGb = info.minRamGb.takeUnless { DeviceRam.meetsRequirement(info, totalMemBytes) },
                )
            }
        _storageUsedBytes.value = modelManager.storageUsedBytes()
    }

    /**
     * Start installing a model in the background (download, extraction, verification).
     *
     * Progress and the result reach [models] through the install state observed in init.
     *
     * @param modelKey Stable catalog key for the model to download.
     */
    fun downloadModel(modelKey: String) {
        viewModelScope.launch {
            // Like iOS, a downloaded model becomes the active one and loads, unless activating it
            // needs the English-only confirmation: then the screen ends with the install.
            val info = ModelCatalog.findByKey(modelKey) ?: return@launch
            val loadsEngine = !needsLanguageConfirmation(info)
            _preparation.value = Preparation(modelKey, awaitingInstall = true, loadsEngine = loadsEngine)
            modelInstaller.install(modelKey)
        }
    }

    /**
     * React to [modelKey]'s install state for the preparation screen.
     *
     * The first state reported after an install was asked for belongs to that install (the
     * flow only emits changes), so it ends [Preparation.awaitingInstall]. A finished install
     * activates the model when the screen expects a load. An install running while no screen
     * is up (started before this screen was recreated) raises the screen again, as iOS does.
     */
    private suspend fun onInstallStateChanged(modelKey: String, state: ModelInstallState) {
        val preparation = _preparation.value
        if (preparation == null) {
            if (state.isInProgress) _preparation.value = Preparation(modelKey, awaitingInstall = false, loadsEngine = false)
            return
        }
        if (preparation.key != modelKey) return
        if (preparation.awaitingInstall) {
            _preparation.value = preparation.copy(awaitingInstall = false)
        }
        if (state is ModelInstallState.Done && preparation.loadsEngine) {
            dataStore.edit { it[PreferenceKeys.ACTIVE_MODEL] = modelKey }
        }
    }

    /** The preparation screen left: after its ready state, or « Fermer » after a failure. */
    fun closePreparation() {
        _preparation.value = null
    }

    /**
     * Delete a downloaded model from disk.
     *
     * Does nothing if [ModelManager.canDelete] returns false (last model guard).
     * Logs a warning and sets an error state if deletion fails.
     *
     * @param modelKey Stable catalog key for the model to delete.
     */
    fun deleteModel(modelKey: String) {
        if (!modelManager.canDelete(modelKey)) {
            Timber.w("Cannot delete '$modelKey' — it is the last downloaded model")
            return
        }
        val deleted = modelManager.deleteModel(modelKey)
        if (!deleted) {
            Timber.e("Failed to delete model '$modelKey'")
        }
        // If we just deleted the active model, switch to the first remaining downloaded model
        viewModelScope.launch {
            val currentActive = dataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]
            if (currentActive == modelKey) {
                val fallback = modelManager.getDownloadedModels().firstOrNull()
                if (fallback != null) {
                    Timber.d("Active model deleted, falling back to '%s'", fallback)
                    dataStore.edit { it[PreferenceKeys.ACTIVE_MODEL] = fallback }
                }
            }
        }
        refreshModels()
    }

    /**
     * Set the active model key in DataStore.
     *
     * Only meaningful for downloaded models. The change is reflected immediately
     * via the [activeModelKey] StateFlow which is backed by DataStore.
     * Raises the preparation screen, which follows the engine loading the model (#181).
     *
     * @param key Stable catalog key for the model to make active.
     */
    fun setActiveModel(key: String) {
        _preparation.value = Preparation(key, awaitingInstall = false, loadsEngine = true)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] = key
            }
        }
    }

    /**
     * Request to set active model with safety checks for Parakeet models.
     *
     * WHY not direct setActiveModel: Parakeet is English-only. If the user's
     * transcription language is FR or auto, they need a confirmation dialog.
     * Models the device lacks the RAM for are refused (the card is disabled too).
     *
     * @param key Stable catalog key for the model to make active.
     */
    fun requestSetActiveModel(key: String) {
        viewModelScope.launch {
            val info = ModelCatalog.findByKey(key) ?: return@launch
            if (!DeviceRam.meetsRequirement(info, totalMemBytes)) return@launch

            if (needsLanguageConfirmation(info)) {
                _showParakeetLanguageDialog.value = key
                return@launch
            }

            setActiveModel(key)
        }
    }

    /** An English-only model while the transcription language is not English. */
    private suspend fun needsLanguageConfirmation(info: ModelInfo): Boolean =
        info.isEnglishOnly && (dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "auto") != "en"

    /** Confirm Parakeet activation despite language mismatch. */
    fun confirmParakeetActivation() {
        val key = _showParakeetLanguageDialog.value ?: return
        _showParakeetLanguageDialog.value = null
        setActiveModel(key)
    }

    /** Dismiss the Parakeet language mismatch dialog without activating the model. */
    fun dismissParakeetDialog() {
        _showParakeetLanguageDialog.value = null
    }

    /**
     * Retry a failed install. The failed work is finished, so a new one is enqueued.
     *
     * @param modelKey Stable catalog key for the model to retry.
     */
    fun retryDownload(modelKey: String) = downloadModel(modelKey)
}
