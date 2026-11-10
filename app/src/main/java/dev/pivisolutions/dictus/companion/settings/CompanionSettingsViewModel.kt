package dev.pivisolutions.dictus.companion.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanionSettingsState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val floating: Boolean = false,
    val automaticInsertion: Boolean = false,
    val dockRight: Boolean = true,
    val haptics: Boolean = true,
    val sound: Boolean = false,
)

@HiltViewModel
class CompanionSettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {
    val state = dataStore.data.map {
        CompanionSettingsState(
            loaded = true,
            enabled = it[PreferenceKeys.COMPANION_ENABLED] ?: false,
            floating = it[PreferenceKeys.COMPANION_FLOATING] ?: false,
            automaticInsertion = it[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION] ?: false,
            dockRight = it[PreferenceKeys.COMPANION_DOCK_RIGHT] ?: true,
            haptics = it[PreferenceKeys.HAPTICS_ENABLED] ?: true,
            sound = it[PreferenceKeys.SOUND_ENABLED] ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CompanionSettingsState())

    fun setEnabled(value: Boolean) = set(PreferenceKeys.COMPANION_ENABLED, value)
    fun setFloating(value: Boolean) = set(PreferenceKeys.COMPANION_FLOATING, value)
    fun setAutomaticInsertion(value: Boolean) = set(PreferenceKeys.COMPANION_AUTOMATIC_INSERTION, value)
    fun setDockRight(value: Boolean) = set(PreferenceKeys.COMPANION_DOCK_RIGHT, value)
    fun setHaptics(value: Boolean) = set(PreferenceKeys.HAPTICS_ENABLED, value)
    fun setSound(value: Boolean) = set(PreferenceKeys.SOUND_ENABLED, value)

    private fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch { dataStore.edit { it[key] = value } }
    }

    /** Alternative onboarding never changes keyboard preferences or system IME state. */
    fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.edit {
                it[PreferenceKeys.COMPANION_ENABLED] = true
                it[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true
            }
        }
    }
}
