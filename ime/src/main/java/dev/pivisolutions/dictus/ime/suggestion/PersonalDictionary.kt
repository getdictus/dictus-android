package dev.pivisolutions.dictus.ime.suggestion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tracks how often the user types each word and persists a "learned" set via DataStore.
 *
 * WHY two-layer design:
 * - typeCount (in-memory): Updated on every word type. Reset on IME process restart.
 *   We never write this to disk because DataStore writes are async I/O — doing so on
 *   every keystroke would cause lag.
 * - learnedWords (DataStore-backed): Only written when a word crosses the 2-tap threshold.
 *   Collected reactively via a Flow collector in init so it's always up-to-date without
 *   blocking reads inside getSuggestions().
 *
 * WHY 2-tap threshold (LEARN_THRESHOLD = 2): Avoids learning typos from a single mistype
 * while being responsive enough for frequent words the user actually wants suggested.
 */
class PersonalDictionary(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    companion object {
        /**
         * Number of times a word must be typed before it is considered "learned"
         * and boosted to the front of suggestions.
         */
        const val LEARN_THRESHOLD = 2
    }

    // In-memory counter — reset on IME process restart (acceptable per spec).
    // Key is lowercased word to normalize casing.
    private val typeCount = mutableMapOf<String, Int>()

    /**
     * Set of words the user has typed at least [LEARN_THRESHOLD] times.
     * Observed by DictionaryEngine for frequency boost ranking.
     * Updated reactively when DataStore emits new values.
     */
    var learnedWords: Set<String> = emptySet()
        private set

    init {
        // Collect DataStore changes reactively — no blocking reads needed in getSuggestions().
        // WHY scope.launch (not runBlocking): DataStore flows are cold flows on coroutines.
        // Blocking here would cause IME freeze. The reactive collect keeps learnedWords
        // current without blocking the main thread.
        scope.launch {
            dataStore.data.collect { prefs ->
                learnedWords = prefs[PreferenceKeys.PERSONAL_DICTIONARY] ?: emptySet()
            }
        }
    }

    /**
     * Record that the user typed [word]. If this is the [LEARN_THRESHOLD]-th time,
     * persist it to DataStore so it appears in future sessions and gets a suggestion boost.
     *
     * @param word The word that was committed. Blank/empty inputs are ignored.
     */
    fun recordWordTyped(word: String) {
        if (word.isBlank()) return
        val lower = word.lowercase()
        val count = (typeCount[lower] ?: 0) + 1
        typeCount[lower] = count
        if (count >= LEARN_THRESHOLD && !learnedWords.contains(lower)) {
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[PreferenceKeys.PERSONAL_DICTIONARY] =
                        (prefs[PreferenceKeys.PERSONAL_DICTIONARY] ?: emptySet()) + lower
                }
            }
        }
    }
}
