package dev.pivisolutions.dictus

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys

/** Preferences an older version wrote and nothing reads any more, removed at app start. */
object RetiredPreferences {

    suspend fun remove(dataStore: DataStore<Preferences>) {
        dataStore.edit { preferences ->
            // Home's card lives in memory since #245; this drops the text an older version left on disk.
            preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION)
            preferences.remove(PreferenceKeys.LAST_TRANSCRIPTION_SAVED_AT)
            // The keyboard always opens on letters since #288: a "123" choice must not linger.
            preferences.remove(PreferenceKeys.KEYBOARD_MODE)
        }
    }
}
