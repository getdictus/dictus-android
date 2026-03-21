package dev.pivisolutions.dictus.core.preferences

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for Dictus settings.
 *
 * DataStore is Android's type-safe replacement for SharedPreferences.
 * Each key is typed (string, int, boolean, etc.) which prevents
 * type-mismatch bugs at compile time. These keys define what
 * settings are persisted across app restarts.
 */
object PreferenceKeys {
    val KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")
    val ACTIVE_MODEL = stringPreferencesKey("active_model")
    val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
}
