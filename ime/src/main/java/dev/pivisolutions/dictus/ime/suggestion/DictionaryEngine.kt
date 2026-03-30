package dev.pivisolutions.dictus.ime.suggestion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Production SuggestionEngine backed by AOSP FR+EN dictionaries loaded from APK assets.
 *
 * Drop-in replacement for StubSuggestionEngine — implements the same SuggestionEngine
 * interface with zero changes to UI or wiring code.
 *
 * Architecture:
 * - Dictionary loading: Background coroutine on Dispatchers.IO at init. Returns empty
 *   list until loading completes (typically < 500ms on first open). This prevents ANR
 *   on the main thread which calls getSuggestions() on every keystroke.
 * - Accent-insensitive matching: NFD normalization + combining diacritic removal via
 *   java.text.Normalizer (JDK stdlib, no dependency). "deja" matches "deja" etc.
 * - Apostrophe words: Preserved as-is (e.g., "aujourd'hui"). The word extractor in
 *   DictusImeService already splits only on space/newline — no change needed there.
 * - Personal dictionary boost: Learned words (from PersonalDictionary) are sorted to
 *   the front of the candidate list, ahead of higher base-frequency words.
 *
 * @param context Android context for asset loading.
 * @param dataStore DataStore instance for language preference and personal dictionary.
 * @param coroutineScope IME lifecycle scope (MainScope from DictusImeService).
 * @param assetName Override asset filename for testing (null = auto-select by language).
 * @param ioDispatcher Dispatcher used for dictionary file loading. Defaults to
 *   Dispatchers.IO in production; can be overridden in tests to run on the test dispatcher.
 */
class DictionaryEngine(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope,
    private val assetName: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SuggestionEngine {

    // Words sorted by frequency descending — loaded asynchronously in init.
    private var wordList: List<WordEntry> = emptyList()

    // Guard flag: getSuggestions returns empty until loading completes.
    // WHY MutableStateFlow (not plain Boolean): Future callers could observe readiness.
    private val _isReady = MutableStateFlow(false)

    // Personal dictionary for boost ranking. Exposed so tests can record words.
    val personalDictionary = PersonalDictionary(dataStore, coroutineScope)

    // Pre-compiled regex for combining diacritics — avoids recompiling on every call.
    private val COMBINING_DIACRITICALS = Regex("\\p{InCombiningDiacriticalMarks}+")

    init {
        // Load dictionary on IO thread to avoid blocking the IME main thread.
        // WHY ioDispatcher (default Dispatchers.IO): Asset reads are disk I/O. On the main
        // thread this would cause visible lag or ANR the first time the keyboard opens.
        // The dispatcher is injectable so tests can use the test dispatcher and drain with
        // advanceUntilIdle() instead of relying on real threads.
        coroutineScope.launch(ioDispatcher) {
            val name = assetName ?: run {
                val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "fr"
                if (lang == "en") "dict_en.txt" else "dict_fr.txt"
            }
            wordList = context.assets.open(name).bufferedReader().useLines { lines ->
                lines.mapNotNull { parseLine(it) }
                    .sortedByDescending { it.frequency }
                    .toList()
            }
            _isReady.value = true
        }
    }

    /**
     * Return up to [maxResults] word suggestions for the given [input] prefix.
     *
     * Algorithm:
     * 1. Normalize input to lowercase + strip accents.
     * 2. Filter wordList to entries whose stripped word starts with stripped input.
     * 3. Exclude the exact input word (user already typed it).
     * 4. Sort: learned words first, then by AOSP frequency descending.
     * 5. Take top [maxResults].
     *
     * Called on the main thread from onUpdateSelection() — must be fast.
     * All work is in-memory: O(n) scan of wordList. For 50k words this is ~1ms.
     */
    override fun getSuggestions(input: String, maxResults: Int): List<String> {
        if (input.isBlank() || !_isReady.value) return emptyList()
        val lowerInput = input.lowercase()
        val strippedInput = lowerInput.stripAccents()
        val learned = personalDictionary.learnedWords

        return wordList
            .filter { entry ->
                val strippedWord = entry.word.lowercase().stripAccents()
                strippedWord.startsWith(strippedInput) && entry.word.lowercase() != lowerInput
            }
            .sortedWith(
                // Learned words appear before all non-learned words,
                // then frequency is the tiebreaker within each group.
                compareByDescending<WordEntry> { learned.contains(it.word.lowercase()) }
                    .thenByDescending { it.frequency },
            )
            .take(maxResults)
            .map { it.word }
    }

    /**
     * Parse a single line from the AOSP .combined dictionary format.
     *
     * Expected format: `word=bonjour,f=130,flags=,originalFreq=130`
     * Returns null for comment lines, blank lines, or malformed entries.
     */
    private fun parseLine(line: String): WordEntry? {
        if (!line.startsWith("word=")) return null
        val parts = line.split(",")
        val word = parts.firstOrNull { it.startsWith("word=") }
            ?.removePrefix("word=") ?: return null
        val freq = parts.firstOrNull { it.startsWith("f=") }
            ?.removePrefix("f=")?.toIntOrNull() ?: 0
        return if (word.isNotBlank()) WordEntry(word, freq) else null
    }

    /**
     * Strip combining diacritical marks from a string using NFD normalization.
     *
     * NFD (Canonical Decomposition) breaks composed characters into base + combining marks.
     * For example: "é" → "e" + U+0301 (combining acute accent).
     * The regex then removes all combining marks, leaving only the base characters.
     *
     * This allows "deja" (typed without accents) to match "deja" in the dictionary.
     */
    private fun String.stripAccents(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(COMBINING_DIACRITICALS, "")
}
