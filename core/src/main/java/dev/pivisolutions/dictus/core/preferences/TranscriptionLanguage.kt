package dev.pivisolutions.dictus.core.preferences

/**
 * Dictation (STT) language choices stored in [PreferenceKeys.TRANSCRIPTION_LANGUAGE].
 *
 * WHY a single resolver: the stored value is read by Settings, Models and the dictation
 * service. Keeping the parsing here guarantees that an unknown or corrupted value
 * degrades to [FOLLOW] everywhere, instead of each caller inventing its own fallback
 * (the service used to silently force "fr").
 *
 * Mirrors iOS `TranscriptionLanguageMode` (dictus-ios#226), minus Chinese. As on iOS,
 * a missing or unknown value follows the keyboard, while an explicit "auto" stays auto.
 */
object TranscriptionLanguage {
    /** Dictate in the keyboard's current language. Default for a missing or unknown value. */
    const val FOLLOW = "follow"

    /** Let the engine detect the spoken language. */
    const val AUTO = "auto"

    /** Stored values offered in Settings, in display order. */
    val SUPPORTED: List<String> = listOf(FOLLOW, AUTO, "fr", "en", "es", "de")

    /** Normalize a stored value: missing or unknown values resolve to [FOLLOW]. */
    fun resolve(stored: String?): String {
        val normalized = stored?.trim()?.lowercase()
        return SUPPORTED.firstOrNull { it == normalized } ?: FOLLOW
    }

    /**
     * Language code handed to `SttProvider.transcribe`.
     *
     * @param keyboardLanguage the keyboard's resolved language code, used in [FOLLOW] mode.
     *   Passed in by the caller because the keyboard resolver lives in :ime.
     *
     * WHY "auto" is passed through rather than null: the JNI bridge takes a non-null
     * string, and whisper.cpp itself treats "auto" as "detect the language"
     * (`whisper_full_with_state`). Parakeet ignores the parameter entirely.
     */
    fun engineLanguage(stored: String?, keyboardLanguage: String): String =
        when (val resolved = resolve(stored)) {
            FOLLOW -> keyboardLanguage
            else -> resolved
        }
}
