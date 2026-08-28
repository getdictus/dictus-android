package dev.pivisolutions.dictus.ime.language

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys

/** Atomically advances only the keyboard language preference. ASR remains independent. */
suspend fun cycleKeyboardLanguage(dataStore: DataStore<Preferences>): SupportedLanguage {
    var selected = SupportedLanguage.FRENCH
    dataStore.edit { preferences ->
        selected = SupportedLanguage.fromCodeOrDefault(
            preferences[PreferenceKeys.KEYBOARD_LANGUAGE],
        ).next()
        preferences[PreferenceKeys.KEYBOARD_LANGUAGE] = selected.code
    }
    return selected
}
