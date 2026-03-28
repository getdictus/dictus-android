package dev.pivisolutions.dictus.ime.suggestion

/**
 * Interface for word prediction/suggestion.
 *
 * This is the contract for drop-in replacement with a production engine.
 * Target architecture (per CONTEXT.md): AOSP LatinIME native C++ prediction
 * engine with binary dictionaries via NDK/JNI build. Any future implementation
 * should implement this interface — the SuggestionBar UI requires no changes.
 *
 * Phase 5 MVP: StubSuggestionEngine provides basic prefix matching from
 * a hardcoded ~40-word dictionary. This is NOT production quality.
 */
interface SuggestionEngine {
    /**
     * Return up to [maxResults] word suggestions for the given [input] prefix.
     *
     * @param input Current word being typed (may be partial).
     * @param maxResults Maximum number of suggestions to return.
     * @return List of suggested complete words, ordered by relevance.
     */
    fun getSuggestions(input: String, maxResults: Int = 3): List<String>
}

/**
 * MVP stub: simple prefix-matching against a small hardcoded dictionary.
 *
 * NOT a production predictor — placeholder for AOSP LatinIME JNI integration.
 * The SuggestionEngine interface is designed so this class can be replaced
 * with a real implementation without any UI or wiring changes.
 */
class StubSuggestionEngine : SuggestionEngine {
    private val dictionary = listOf(
        // French common words
        "bonjour", "bonsoir", "merci", "comment", "pourquoi",
        "aujourd'hui", "demain", "maintenant", "vraiment", "beaucoup",
        "toujours", "jamais", "quelque", "quelqu'un", "parce que",
        "aussi", "alors", "encore", "depuis", "pendant",
        "salut", "super", "bonne", "nouvelle", "message",
        // English common words
        "hello", "thanks", "please", "sorry", "could",
        "would", "should", "about", "because", "before",
        "after", "always", "never", "today", "tomorrow",
        "meeting", "message", "morning", "afternoon", "evening",
    )

    override fun getSuggestions(input: String, maxResults: Int): List<String> {
        if (input.isBlank()) return emptyList()
        val lower = input.lowercase()
        return dictionary
            .filter { it.startsWith(lower) && it != lower }
            .take(maxResults)
    }
}
