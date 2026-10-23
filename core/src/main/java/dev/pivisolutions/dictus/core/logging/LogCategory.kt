package dev.pivisolutions.dictus.core.logging

/**
 * The subsystem a log line belongs to, written as `[CATEGORY]` on every line of `dictus.log`.
 *
 * WHY (#112): every line used to read `[D/null]`, so `"Bound to DictationService"` from the app
 * and from the keyboard were byte-identical in an export. The category says which part of Dictus
 * spoke, the class name next to it says exactly who. iOS does the same with its `Subsystem`.
 *
 * Most lines get their category from the calling class ([forClass]); a caller can force one with
 * `Timber.tag(LogCategory.INSERT.name)`, which is how the insertion event is kept apart from the
 * rest of the keyboard.
 */
enum class LogCategory {
    LIFECYCLE,
    UI,
    IME,
    KEYBOARD,
    SERVICE,
    AUDIO,
    STT,
    MODEL,
    INSERT,
    APP,
    ;

    companion object {
        private const val ROOT = "dev.pivisolutions.dictus."

        /** The category named by an explicit Timber tag, or null for any other tag. */
        fun fromTag(tag: String?): LogCategory? = entries.firstOrNull { it.name == tag }

        /**
         * Category for a fully qualified class name. Package first, then class name inside
         * the flat `service` package, which holds the dictation service, the audio capture,
         * the STT providers and the model downloader side by side.
         */
        fun forClass(className: String): LogCategory {
            val name = className.removePrefix(ROOT)
            val simple = name.substringAfterLast('.')
            return when {
                name.startsWith("ime.ui.") || name.startsWith("ime.suggestion.") ||
                    name.startsWith("ime.input.") -> KEYBOARD
                name.startsWith("ime.") -> IME
                name.startsWith("whisper.") || name.startsWith("asr.") -> STT
                name.startsWith("model.") || name.startsWith("models.") -> MODEL
                name.startsWith("audio.") -> AUDIO
                name.startsWith("service.") -> when {
                    simple.startsWith("Audio") -> AUDIO
                    simple.startsWith("Model") || simple.contains("Download") -> MODEL
                    simple.contains("Provider") || simple.contains("Transcri") -> STT
                    else -> SERVICE
                }
                name.startsWith("core.logging.") || simple == "DictusApplication" ||
                    simple == "MainActivity" -> LIFECYCLE
                name.startsWith("ui.") || name.startsWith("onboarding.") ||
                    name.startsWith("recording.") || name.startsWith("history.") ||
                    name.startsWith("navigation.") || name.startsWith("home.") ||
                    name.startsWith("permission.") -> UI
                else -> APP
            }
        }
    }
}
