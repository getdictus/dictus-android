package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.suggestion.StubSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SuggestionEngine logic.
 *
 * Note: Compose UI tests for SuggestionBar composable require
 * a robolectric-compatible test runner. The engine logic tests
 * here run as pure JVM unit tests.
 */
class SuggestionBarTest {

    private val engine = StubSuggestionEngine()

    @Test
    fun `getSuggestions with bon prefix returns bonjour`() {
        val suggestions = engine.getSuggestions("bon")
        assertTrue(
            "Expected 'bonjour' in suggestions for 'bon', got: $suggestions",
            suggestions.contains("bonjour"),
        )
    }

    @Test
    fun `getSuggestions with empty string returns empty list`() {
        val suggestions = engine.getSuggestions("")
        assertEquals(
            "Expected empty list for empty input",
            emptyList<String>(),
            suggestions,
        )
    }

    @Test
    fun `getSuggestions with hel prefix returns hello`() {
        val suggestions = engine.getSuggestions("hel")
        assertTrue(
            "Expected 'hello' in suggestions for 'hel', got: $suggestions",
            suggestions.contains("hello"),
        )
    }

    @Test
    fun `getSuggestions respects maxResults limit`() {
        val suggestions = engine.getSuggestions("a", maxResults = 2)
        assertTrue(
            "Expected at most 2 results, got: ${suggestions.size}",
            suggestions.size <= 2,
        )
    }

    @Test
    fun `getSuggestions with blank string returns empty list`() {
        val suggestions = engine.getSuggestions("   ")
        assertEquals(
            "Expected empty list for blank input",
            emptyList<String>(),
            suggestions,
        )
    }

    @Test
    fun `getSuggestions does not return the exact input word as suggestion`() {
        val suggestions = engine.getSuggestions("bonjour")
        assertTrue(
            "Expected 'bonjour' not to appear as its own suggestion",
            !suggestions.contains("bonjour"),
        )
    }

    @Test
    fun `getSuggestions returns at most 3 results by default`() {
        val suggestions = engine.getSuggestions("a")
        assertTrue(
            "Expected at most 3 results by default, got: ${suggestions.size}",
            suggestions.size <= 3,
        )
    }
}
