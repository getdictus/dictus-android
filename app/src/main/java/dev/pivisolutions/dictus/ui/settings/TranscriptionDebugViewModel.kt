package dev.pivisolutions.dictus.ui.settings

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.debug.LanguageResolution
import dev.pivisolutions.dictus.debug.TranscriptionDebugEntry
import dev.pivisolutions.dictus.debug.TranscriptionDebugExporter
import dev.pivisolutions.dictus.debug.TranscriptionDebugMetrics
import dev.pivisolutions.dictus.debug.TranscriptionDebugStore
import dev.pivisolutions.dictus.ime.language.KeyboardPreferenceResolver
import dev.pivisolutions.dictus.model.ModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * The transcription debug screen's state (#295), as iOS `PolishDebugView` reads `PolishMetricsRing`.
 *
 * @property entries the most recent [RECENT_LIMIT] entries, newest first.
 * @property storedCount every entry of the 7-day window, which the export ships.
 */
@HiltViewModel
class TranscriptionDebugViewModel @Inject constructor(
    private val store: TranscriptionDebugStore,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    data class State(
        val entries: List<TranscriptionDebugEntry> = emptyList(),
        val storedCount: Int = 0,
        val refreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.readAll() }
            _state.value = State(entries = all.takeLast(RECENT_LIMIT).reversed(), storedCount = all.size)
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            refresh()
        }
    }

    /** Writes the export of the whole 7-day window into [directory] and returns the file. */
    suspend fun export(directory: File): File = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val activeModel = prefs[PreferenceKeys.ACTIVE_MODEL]
        val settings = TranscriptionDebugExporter.Settings(
            keyboardLanguage = KeyboardPreferenceResolver.language(prefs[PreferenceKeys.KEYBOARD_LANGUAGE]).code,
            transcriptionLanguageMode = LanguageResolution.transcriptionModeDescription(
                TranscriptionLanguage.resolve(prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE]),
            ),
            activeModelID = activeModel,
            activeModelEngine = activeModel?.let(ModelCatalog::findByKey)
                ?.let { TranscriptionDebugMetrics.sttEngineCode(it.provider.name.lowercase()) },
        )
        val device = TranscriptionDebugExporter.Device(
            hardware = Build.DEVICE,
            modelClass = "${Build.MANUFACTURER} ${Build.MODEL}",
            systemName = "Android",
            systemVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE.toString(),
        )
        val now = System.currentTimeMillis()
        val export = TranscriptionDebugExporter.makeExport(store.readAll(), device, settings, now)
        TranscriptionDebugExporter.write(export, directory, now)
    }

    companion object {
        /** iOS `PolishMetricsRing.memoryCacheLimit`. */
        const val RECENT_LIMIT = 200
    }
}
