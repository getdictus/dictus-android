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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages user-configurable preferences: transcription language, active model,
 * haptics toggle, and sound toggle. All writes go through DataStore for persistence.
 *
 * Full implementation is in Phase 04 Plan 04 (Settings & Navigation).
 * This stub satisfies the SettingsViewModelTest written during planning.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _language = MutableStateFlow("auto")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _activeModel = MutableStateFlow(ModelCatalog.DEFAULT_KEY)
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _soundEnabled = MutableStateFlow(false)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "auto"
            }.collect { _language.value = it }
        }
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY
            }.collect { _activeModel.value = it }
        }
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[PreferenceKeys.HAPTICS_ENABLED] ?: true
            }.collect { _hapticsEnabled.value = it }
        }
        viewModelScope.launch {
            dataStore.data.map { prefs ->
                prefs[PreferenceKeys.SOUND_ENABLED] ?: false
            }.collect { _soundEnabled.value = it }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE] = lang
            }
        }
    }

    fun setActiveModel(modelKey: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.ACTIVE_MODEL] = modelKey
            }
        }
    }

    fun toggleHaptics() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.HAPTICS_ENABLED] = !(_hapticsEnabled.value)
            }
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SOUND_ENABLED] = !(_soundEnabled.value)
            }
        }
    }
}
