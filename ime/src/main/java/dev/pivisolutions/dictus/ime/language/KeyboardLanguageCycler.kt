package dev.pivisolutions.dictus.ime.language

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys

/**
 * Atomically advances the keyboard language. The layout follows the new language's own one
 * (#134) through [KeyboardLayoutPreference.activate]; ASR remains independent.
 */
suspend fun cycleKeyboardLanguage(dataStore: DataStore<Preferences>): SupportedLanguage {
    var selected = SupportedLanguage.FRENCH
    dataStore.edit { preferences ->
        selected = SupportedLanguage.fromCodeOrDefault(
            preferences[PreferenceKeys.KEYBOARD_LANGUAGE],
        ).next()
        KeyboardLayoutPreference.activate(preferences, selected)
    }
    return selected
}
