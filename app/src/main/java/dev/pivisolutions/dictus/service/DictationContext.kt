package dev.pivisolutions.dictus.service

import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.ime.language.KeyboardPreferenceResolver

/**
 * What [DictationTranscriber] is told about one take beyond its audio.
 *
 * @param sttLanguage the code handed to the engine, or "auto".
 * @param transcriptionMode the stored transcription language, resolved: [TranscriptionLanguage.FOLLOW],
 *   [TranscriptionLanguage.AUTO] or a language code.
 * @param keyboardLanguage the keyboard's language when the take is transcribed.
 * @param source the surface that started the dictation, the debug log's `writer` (#295).
 */
internal data class DictationContext(
    val sttLanguage: String,
    val transcriptionMode: String,
    val keyboardLanguage: String,
    val source: DictationSource,
) {
    companion object {
        /** Read from the same preferences snapshot the engine language came from. */
        fun from(prefs: Preferences, sttLanguage: String, source: DictationSource) = DictationContext(
            sttLanguage = sttLanguage,
            transcriptionMode = TranscriptionLanguage.resolve(prefs[PreferenceKeys.TRANSCRIPTION_LANGUAGE]),
            keyboardLanguage = KeyboardPreferenceResolver.language(prefs[PreferenceKeys.KEYBOARD_LANGUAGE]).code,
            source = source,
        )
    }
}
