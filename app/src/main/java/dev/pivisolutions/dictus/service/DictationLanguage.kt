package dev.pivisolutions.dictus.service

import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.ime.language.KeyboardPreferenceResolver

/**
 * The language a dictation is transcribed in, read from one preferences snapshot.
 *
 * WHY here and not in core: following the keyboard needs the keyboard's own resolver
 * (a missing key reads as French), which lives in :ime. Core stays a pure parser.
 */
object DictationLanguage {
    fun engineLanguage(prefs: Preferences): String = TranscriptionLanguage.engineLanguage(
        prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE],
        keyboardLanguage = KeyboardPreferenceResolver.language(prefs[PreferenceKeys.KEYBOARD_LANGUAGE]).code,
    )
}
