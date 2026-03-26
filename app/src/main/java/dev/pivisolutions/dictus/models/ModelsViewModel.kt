package dev.pivisolutions.dictus.models

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.DownloadProgress
import dev.pivisolutions.dictus.service.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @param isDownloaded    True if the model file is present on disk with correct size.
 * @param downloadPercent Active download progress 0–100, or null if not downloading.
 * @param hasError        True if the last download attempt failed.
 */
data class ModelUiState(
    val info: ModelInfo,
    val isDownloaded: Boolean,
    val downloadPercent: Int?,
    val hasError: Boolean = false,
)

/**
 * ViewModel for the Models screen (model download, selection, and deletion).
 *
 * WHY @HiltViewModel: Allows Hilt to inject ModelManager and ModelDownloader without
 * requiring a ViewModelFactory. Hilt generates the factory automatically.
 *
 * WHY separate models StateFlow: The list of models is derived from the catalog
 * (static) but augmented with disk state (dynamic — changes after download/delete).
 * Keeping download progress in a separate Map avoids rebuilding the entire list on
 * every progress tick.
 *
 * @param modelManager   Manages model file presence and deletion on disk.
 * @param modelDownloader Provides downloadWithProgress() Flow for each model.
 * @param dataStore       Application DataStore for reading/writing active model key.
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val modelDownloader: ModelDownloader,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    // Map of modelKey → download progress (0–100), only for active downloads
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Map of modelKey → has error
    private val _downloadErrors = MutableStateFlow<Set<String>>(emptySet())

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

    init {
        refreshModels()
    }

    /**
     * Refresh the model list and storage usage from disk.
     *
     * Called on init and after any download or deletion to keep state current.
     */
    fun refreshModels() {
        val progress = _downloadProgress.value
        val errors = _downloadErrors.value
        _models.value = ModelCatalog.ALL.map { info ->
            ModelUiState(
                info = info,
                isDownloaded = modelManager.isDownloaded(info.key),
                downloadPercent = progress[info.key],
                hasError = info.key in errors,
            )
        }
        _storageUsedBytes.value = modelManager.storageUsedBytes()
    }

    /**
     * Start downloading a model and collect progress events into [models] state.
     *
     * The download runs in [viewModelScope] so it survives screen rotation.
     * Progress events are dispatched to [_downloadProgress] on each tick.
     * On completion, the model list is refreshed and progress is cleared.
     * On error, the error flag is set and the error message is logged.
     *
     * @param modelKey Stable catalog key for the model to download.
     */
    fun downloadModel(modelKey: String) {
        // Clear previous error for this key
        _downloadErrors.update { it - modelKey }

        viewModelScope.launch {
            modelDownloader.downloadWithProgress(modelKey).collect { event ->
                when (event) {
                    is DownloadProgress.Progress -> {
                        _downloadProgress.update { it + (modelKey to event.percent) }
                        refreshModels()
                    }
                    is DownloadProgress.Complete -> {
                        _downloadProgress.update { it - modelKey }
                        Timber.d("Model '$modelKey' downloaded to ${event.path}")
                        refreshModels()
                    }
                    is DownloadProgress.Error -> {
                        _downloadProgress.update { it - modelKey }
                        _downloadErrors.update { it + modelKey }
                        Timber.e("Download failed for '$modelKey': ${event.message}")
                        refreshModels()
                    }
                }
            }
        }
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
        refreshModels()
    }

    /**
     * Clear the error state for a model and retry its download.
     *
     * @param modelKey Stable catalog key for the model to retry.
     */
    fun retryDownload(modelKey: String) {
        _downloadErrors.update { it - modelKey }
        downloadModel(modelKey)
    }
}
