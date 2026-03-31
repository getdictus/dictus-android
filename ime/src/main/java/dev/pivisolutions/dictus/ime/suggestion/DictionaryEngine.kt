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
 * Performance: accent-stripped lowercase forms are pre-computed at load time and words
 * are indexed by first character. This reduces per-keystroke work from O(n) NFD
 * normalizations to a simple HashMap lookup + prefix scan of ~2k candidates.
 *
 * @param context Android context for asset loading.
 * @param dataStore DataStore instance for language preference and personal dictionary.
 * @param coroutineScope IME lifecycle scope (MainScope from DictusImeService).
 * @param assetName Override asset filename for testing (null = auto-select by language).
 * @param ioDispatcher Dispatcher for dictionary file loading. Defaults to Dispatchers.IO.
 */
class DictionaryEngine(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope,
    private val assetName: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SuggestionEngine {

    // Prefix index: first char of strippedLower → entries sorted by frequency desc.
    // Reduces scan from 50k to ~2k per keystroke.
    private var prefixIndex: Map<Char, List<WordEntry>> = emptyMap()

    private val _isReady = MutableStateFlow(false)

    val personalDictionary = PersonalDictionary(dataStore, coroutineScope)

    private val COMBINING_DIACRITICALS = Regex("\\p{InCombiningDiacriticalMarks}+")

    init {
        coroutineScope.launch(ioDispatcher) {
            val name = assetName ?: run {
                val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "fr"
                if (lang == "en") "dict_en.txt" else "dict_fr.txt"
            }
            val entries = context.assets.open(name).bufferedReader().useLines { lines ->
                lines.mapNotNull { parseLine(it) }
                    .sortedByDescending { it.frequency }
                    .toList()
            }
            // Build prefix index grouped by first character of strippedLower
            prefixIndex = entries.groupBy { entry ->
                entry.strippedLower.firstOrNull() ?: ' '
            }
            _isReady.value = true
        }
    }

    /**
     * Return up to [maxResults] word suggestions for the given [input] prefix.
     *
     * Called on the main thread from onUpdateSelection() — must be fast.
     * With prefix index + pre-computed strippedLower, this is ~0.1ms for 50k words.
     */
    override fun getSuggestions(input: String, maxResults: Int): List<String> {
        if (input.isBlank() || !_isReady.value) return emptyList()
        val lowerInput = input.lowercase()
        val strippedInput = lowerInput.stripAccents()
        val firstChar = strippedInput.firstOrNull() ?: return emptyList()
        val learned = personalDictionary.learnedWords

        // Look up only dictionary words sharing the same first character
        val candidates = prefixIndex[firstChar] ?: emptyList()

        // Two-pass: learned words first, then non-learned. Both groups are already
        // sorted by frequency desc (from load time). This avoids re-sorting.
        val learnedResults = mutableListOf<String>()
        val normalResults = mutableListOf<String>()

        for (entry in candidates) {
            if (learnedResults.size + normalResults.size >= maxResults &&
                learnedResults.isNotEmpty()
            ) break

            if (!entry.strippedLower.startsWith(strippedInput)) continue
            if (entry.word.lowercase() == lowerInput) continue // exclude exact typed word

            if (learned.contains(entry.word.lowercase())) {
                if (learnedResults.size < maxResults) learnedResults.add(entry.word)
            } else {
                if (normalResults.size < maxResults) normalResults.add(entry.word)
            }
        }

        // Also include learned words NOT in the dictionary (user-typed words like "dictus").
        // These go at the front since the user explicitly taught them.
        for (word in learned) {
            if (learnedResults.size >= maxResults) break
            val strippedWord = word.stripAccents()
            if (strippedWord.startsWith(strippedInput) &&
                word != lowerInput &&
                !learnedResults.contains(word)
            ) {
                learnedResults.add(0, word) // front of learned list
            }
        }

        // Learned first, then fill with normal up to maxResults
        val result = learnedResults.take(maxResults).toMutableList()
        for (word in normalResults) {
            if (result.size >= maxResults) break
            result.add(word)
        }
        return result
    }

    private fun parseLine(line: String): WordEntry? {
        if (!line.startsWith("word=")) return null
        val parts = line.split(",")
        val word = parts.firstOrNull { it.startsWith("word=") }
            ?.removePrefix("word=") ?: return null
        val freq = parts.firstOrNull { it.startsWith("f=") }
            ?.removePrefix("f=")?.toIntOrNull() ?: 0
        if (word.isBlank()) return null
        val stripped = word.lowercase().stripAccents()
        return WordEntry(word, freq, stripped)
    }

    private fun String.stripAccents(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(COMBINING_DIACRITICALS, "")
}
