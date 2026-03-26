package dev.pivisolutions.dictus.core.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preference keys for Dictus settings.
 *
 * DataStore is Android's type-safe replacement for SharedPreferences.
 * Each key is typed (string, int, boolean, etc.) which prevents
 * type-mismatch bugs at compile time. These keys define what
 * settings are persisted across app restarts.
 *
 * Phase 4 additions: onboarding status, haptics, and sound toggles.
 */
object PreferenceKeys {
    // --- Keyboard settings (Phase 1-2) ---
    val KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")

    // --- Model settings (Phase 3) ---
    val ACTIVE_MODEL = stringPreferencesKey("active_model")
    val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")

    // --- Onboarding (Phase 4) ---
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")

    // --- Audio feedback settings (Phase 4) ---
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")

    // --- Appearance settings (Phase 4) ---
    val THEME = stringPreferencesKey("theme")
}
