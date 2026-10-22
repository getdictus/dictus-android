package dev.pivisolutions.dictus.core.preferences

/**
 * Dictation (STT) language choices stored in [PreferenceKeys.TRANSCRIPTION_LANGUAGE].
 *
 * WHY a single resolver: the stored value is read by Settings, the dictation service
 * and history. Keeping the parsing here guarantees that an unknown or corrupted value
 * degrades to [AUTO] everywhere, instead of each caller inventing its own fallback
 * (the service used to silently force "fr").
 *
 * Mirrors iOS `TranscriptionLanguageMode` (dictus-ios#226), minus Chinese and the
 * "follow keyboard" mode, which are out of scope on Android.
 */
object TranscriptionLanguage {
    /** Let the engine detect the spoken language. */
    const val AUTO = "auto"

    /** Stored values offered in Settings, in display order. */
    val SUPPORTED: List<String> = listOf(AUTO, "fr", "en", "es", "de")

    /** Normalize a stored value: missing or unknown values resolve to [AUTO]. */
    fun resolve(stored: String?): String {
        val normalized = stored?.trim()?.lowercase()
        return SUPPORTED.firstOrNull { it == normalized } ?: AUTO
    }

    /**
     * Language code handed to `SttProvider.transcribe`.
     *
     * WHY "auto" is passed through rather than null: the JNI bridge takes a non-null
     * string, and whisper.cpp itself treats "auto" as "detect the language"
     * (`whisper_full_with_state`). Parakeet ignores the parameter entirely.
     */
    fun engineLanguage(stored: String?): String = resolve(stored)
}
