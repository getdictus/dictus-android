package dev.pivisolutions.dictus.core.whisper

/**
 * Post-processes transcribed text before insertion into the text field.
 *
 * Matches iOS behavior from DictationCoordinator.swift lines 368-372:
 * - Trim leading/trailing whitespace (Whisper sometimes adds extra spaces)
 * - If text ends with sentence-ending punctuation (. ! ? ...), append just a space
 * - Otherwise, append ". " (period + space)
 * This prevents chained dictations from sticking together.
 */
object TextPostProcessor {

    private val SENTENCE_ENDERS = setOf('.', '!', '?')

    fun process(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ""

        val lastChar = trimmed.last()
        return if (lastChar in SENTENCE_ENDERS || trimmed.endsWith("...")) {
            "$trimmed "
        } else {
            "$trimmed. "
        }
    }
}
