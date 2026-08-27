package dev.pivisolutions.dictus.ime.suggestion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.language.LanguageProfile
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    private val dictionaryLoader: suspend (String) -> List<String> = { name ->
        context.assets.open(name).bufferedReader().use { it.readLines() }
    },
) : SuggestionEngine {

    /** Language/profile and its complete index are published as one immutable value. */
    class Activation internal constructor(
        val language: SupportedLanguage,
        val profile: LanguageProfile,
        internal val prefixIndex: Map<Char, List<WordEntry>>,
    )

    private val _activation = MutableStateFlow<Activation?>(null)
    val activation: StateFlow<Activation?> = _activation.asStateFlow()

    val personalDictionary = PersonalDictionary(dataStore, coroutineScope)

    private val COMBINING_DIACRITICALS = Regex("\\p{InCombiningDiacriticalMarks}+")

    init {
        coroutineScope.launch {
            dataStore.data
                .map { SupportedLanguage.fromCodeOrDefault(it[PreferenceKeys.KEYBOARD_LANGUAGE]) }
                .distinctUntilChanged()
                .collectLatest { language ->
                    try {
                        val nextActivation = withContext(ioDispatcher) {
                            val lines = dictionaryLoader(
                                assetName ?: language.profile.dictionaryAssetName,
                            )
                            val entries = lines.mapNotNull(::parseLine)
                                .sortedByDescending { it.frequency }
                            // An empty or wholly malformed file is not a complete dictionary.
                            // Retain the prior activation just as we do for an I/O failure.
                            if (entries.isEmpty()) return@withContext null
                            val index = entries.groupBy { entry ->
                                entry.strippedLower.firstOrNull() ?: ' '
                            }
                            Activation(language, language.profile, index)
                        }
                        // Publication is serialized in the collector context. collectLatest cancels
                        // a superseded load before it can return from withContext and publish.
                        _activation.value = nextActivation ?: return@collectLatest
                        Timber.d(
                            "Keyboard dictionary activated: language=%s, entries=%d",
                            language.code,
                            nextActivation.prefixIndex.values.sumOf { it.size },
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // Keep the last complete dictionary. Do not log dictionary/query content.
                        Timber.w(error, "Keyboard dictionary activation failed: language=%s", language.code)
                    }
                }
            }
    }

    /**
     * Return up to [maxResults] word suggestions for the given [input] prefix.
     *
     * Called on the main thread from onUpdateSelection() — must be fast.
     * With prefix index + pre-computed strippedLower, this is ~0.1ms for 50k words.
     */
    override fun getSuggestions(input: String, maxResults: Int): List<String> {
        return getSuggestions(input, maxResults, _activation.value)
    }

    /** Query a caller-owned activation so dictionary/profile/layout can advance atomically. */
    internal fun getSuggestions(
        input: String,
        maxResults: Int = 3,
        activation: Activation?,
    ): List<String> {
        val live = activation
        if (input.isBlank() || live == null) return emptyList()
        val lowerInput = input.lowercase()
        val strippedInput = lowerInput.stripAccents()
        val firstChar = strippedInput.firstOrNull() ?: return emptyList()
        val learned = personalDictionary.learnedWords.value

        // Look up only dictionary words sharing the same first character
        val candidates = live.prefixIndex[firstChar] ?: emptyList()

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
