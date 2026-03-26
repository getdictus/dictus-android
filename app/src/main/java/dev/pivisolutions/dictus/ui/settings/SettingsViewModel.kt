package dev.pivisolutions.dictus.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Reads all user preferences from DataStore and exposes them as StateFlows
 * that the Composable collects. Write operations go back to DataStore via
 * viewModelScope coroutines.
 *
 * WHY @HiltViewModel: Hilt manages the ViewModel lifecycle and injects DataStore
 * and ModelManager. This avoids passing DataStore down through the Composable tree
 * and matches the Compose-with-Hilt recommended pattern.
 *
 * WHY SharingStarted.Eagerly: The 5-second grace period keeps the
 * upstream DataStore flow active while the screen is briefly backgrounded (e.g.,
 * during a system dialog), preventing an unnecessary re-read from disk.
 *
 * @param dataStore Application-scoped DataStore for persistent preferences.
 * @param modelManager ModelManager to query which models are currently on disk.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val modelManager: ModelManager,
) : ViewModel() {

    /** Currently selected transcription language ("auto", "fr", or "en"). */
    val language: StateFlow<String> = dataStore.data
        .map { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "auto" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "auto")

    /** Key of the currently active Whisper model. */
    val activeModel: StateFlow<String> = dataStore.data
        .map { it[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelCatalog.DEFAULT_KEY)

    /** Whether haptic feedback is enabled on dictation actions. */
    val hapticsEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.HAPTICS_ENABLED] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether the audio dictation start/stop sound is enabled. */
    val soundEnabled: StateFlow<Boolean> = dataStore.data
        .map { it[PreferenceKeys.SOUND_ENABLED] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Currently selected keyboard layout key ("azerty" or "qwerty"). */
    val keyboardLayout: StateFlow<String> = dataStore.data
        .map { it[PreferenceKeys.KEYBOARD_LAYOUT] ?: "azerty" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "azerty")

    /**
     * List of model keys currently downloaded on disk.
     *
     * Refreshed on each subscription by scanning the models directory via IO dispatcher.
     * In a future iteration this could be driven by a FileObserver for live updates.
     */
    val downloadedModels: StateFlow<List<String>> = flow {
        emit(modelManager.getDownloadedModels())
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Persist the selected transcription language to DataStore. */
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            dataStore.edit { it[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = lang }
        }
    }

    /** Persist the selected active model key to DataStore. */
    fun setActiveModel(key: String) {
        viewModelScope.launch {
            dataStore.edit { it[PreferenceKeys.ACTIVE_MODEL] = key }
        }
    }

    /** Toggle haptic feedback on/off and persist the new value. */
    fun toggleHaptics() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.HAPTICS_ENABLED] = !(prefs[PreferenceKeys.HAPTICS_ENABLED] ?: true)
            }
        }
    }

    /** Toggle dictation sound on/off and persist the new value. */
    fun toggleSound() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SOUND_ENABLED] = !(prefs[PreferenceKeys.SOUND_ENABLED] ?: false)
            }
        }
    }

    /** Persist the selected keyboard layout key to DataStore. */
    fun setKeyboardLayout(layout: String) {
        viewModelScope.launch {
            dataStore.edit { it[PreferenceKeys.KEYBOARD_LAYOUT] = layout }
        }
    }
}
