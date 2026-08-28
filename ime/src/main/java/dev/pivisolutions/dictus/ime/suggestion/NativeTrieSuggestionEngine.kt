package dev.pivisolutions.dictus.ime.suggestion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.KeyboardPreferenceResolver
import dev.pivisolutions.dictus.ime.language.LanguageProfile
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.ime.language.toNativeTrieLayout
import dev.pivisolutions.dictus.trie.NativeTrie
import dev.pivisolutions.dictus.trie.TrieKeyboardLayout
import java.io.Closeable
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/** Narrow native handle contract. Unit tests inject this and never initialize JNI. */
interface NativeTrieHandle : Closeable {
    val hasNgram: Boolean
        get() = false

    fun wordExists(word: String): Boolean = false

    fun complete(prefix: String, maxResults: Int): List<String>

    fun correct(word: String, maxEditDistance: Float = 2f, maxResults: Int): List<String>

    fun predictAfterWord(word: String, maxResults: Int): List<String> = emptyList()

    fun predictAfterWords(firstWord: String, secondWord: String, maxResults: Int): List<String> =
        emptyList()
}

/** Opens a fully loaded handle for one binary asset and keyboard layout. */
fun interface NativeTrieOpener {
    fun open(assetName: String, layout: TrieKeyboardLayout): NativeTrieHandle
}

/** Production JNI boundary, kept out of the engine's deterministic JVM tests. */
class AndroidNativeTrieOpener(private val context: Context) : NativeTrieOpener {
    override fun open(assetName: String, layout: TrieKeyboardLayout): NativeTrieHandle =
        NativeTrieHandleAdapter(NativeTrie.open(context, assetName, layout))

    private class NativeTrieHandleAdapter(private val trie: NativeTrie) : NativeTrieHandle {
        override val hasNgram: Boolean = trie.hasNgram

        override fun wordExists(word: String): Boolean = trie.wordExists(word)

        override fun complete(prefix: String, maxResults: Int): List<String> =
            trie.complete(prefix, maxResults)

        override fun correct(
            word: String,
            maxEditDistance: Float,
            maxResults: Int,
        ): List<String> = trie.correct(word, maxEditDistance, maxResults)

        override fun predictAfterWord(word: String, maxResults: Int): List<String> =
            trie.predictAfterWord(word, maxResults).map { it.word }

        override fun predictAfterWords(
            firstWord: String,
            secondWord: String,
            maxResults: Int,
        ): List<String> = trie.predictAfterWords(firstWord, secondWord, maxResults).map { it.word }

        override fun close() = trie.close()
    }
}

/**
 * Lifecycle-safe suggestion engine backed by one native mmap trie at a time.
 *
 * Opening happens off-thread. A generation token makes publication latest-wins even when an
 * opener ignores cancellation. Handle queries, activation swaps, and close share one lock so a
 * native handle cannot be destroyed while JNI is using it.
 */
class NativeTrieSuggestionEngine(
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope,
    private val opener: NativeTrieOpener,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SuggestionEngine, Closeable {
    enum class SuggestionMode { COMPLETION, PREDICTION }

    data class SuggestionResult(
        val requestId: Long,
        val mode: SuggestionMode,
        val input: String,
        val isKnownWord: Boolean,
        val primaryCorrection: String?,
        val suggestions: List<String>,
    )
    class Activation internal constructor(
        val language: SupportedLanguage,
        val profile: LanguageProfile,
        val layout: KeyboardLayout,
        internal val handle: NativeTrieHandle,
    ) {
        val hasNgram: Boolean = handle.hasNgram
    }

    private data class RequestedActivation(
        val language: SupportedLanguage,
        val layout: KeyboardLayout,
    )

    private data class SuggestionRequest(
        val generation: Long,
        val mode: SuggestionMode,
        val words: List<String>,
        val maxResults: Int,
    ) {
        val input: String = words.joinToString(" ")
    }

    private val lock = Any()
    private val _activation = MutableStateFlow<Activation?>(null)
    val activation: StateFlow<Activation?> = _activation.asStateFlow()
    private val _suggestionResults = MutableStateFlow(
        SuggestionResult(
            0L,
            SuggestionMode.COMPLETION,
            "",
            isKnownWord = false,
            primaryCorrection = null,
            emptyList(),
        ),
    )
    val suggestionResults: StateFlow<SuggestionResult> = _suggestionResults.asStateFlow()

    val personalDictionary = PersonalDictionary(dataStore, coroutineScope)

    private var generation = 0L
    private var queryGeneration = 0L
    // Exactly one worker may enter JNI. While it is busy, CONFLATED retains only the newest
    // request, so cancellation-insensitive @Synchronized native calls cannot build a stale queue.
    private val queryRequests = Channel<SuggestionRequest>(Channel.CONFLATED)
    private val queryWorker: Job
    private var inFlightQueries = 0
    private val retiredHandles = mutableListOf<NativeTrieHandle>()
    private var closed = false
    private val preferenceJob: Job

    init {
        queryWorker = coroutineScope.launch(ioDispatcher) {
            for (request in queryRequests) {
                // A newer request may have replaced this one before the worker was scheduled.
                val current = synchronized(lock) {
                    !closed && request.generation == queryGeneration
                }
                if (!current) continue

                if (request.words.any(String::isBlank) || request.maxResults <= 0) {
                    publishIfCurrent(request, QueryResult(false, null, emptyList()))
                    continue
                }

                val result = when (request.mode) {
                    SuggestionMode.COMPLETION -> query(request.input, request.maxResults)
                    SuggestionMode.PREDICTION -> queryPredictions(request.words, request.maxResults)
                }
                publishIfCurrent(request, result)
            }
        }
        preferenceJob = coroutineScope.launch {
            dataStore.data
                .map { preferences ->
                    val language = KeyboardPreferenceResolver.language(
                        preferences[PreferenceKeys.KEYBOARD_LANGUAGE],
                    )
                    RequestedActivation(
                        language,
                        KeyboardPreferenceResolver.layout(
                            preferences[PreferenceKeys.KEYBOARD_LAYOUT],
                            language,
                        ),
                    )
                }
                .distinctUntilChanged()
                .collect(::requestActivation)
        }
    }

    private fun requestActivation(request: RequestedActivation) {
        val requestGeneration = synchronized(lock) {
            if (closed) return
            ++generation
        }
        // Do not use withContext here: prompt cancellation can discard a newly opened handle.
        // This non-suspending launch body always reaches publish-or-close after open returns.
        coroutineScope.launch(ioDispatcher) {
            val candidate = try {
                opener.open(
                    request.language.profile.nativeDictionaryAssetName,
                    request.layout.toNativeTrieLayout(),
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Timber.w(
                    error,
                    "Native keyboard dictionary activation failed: language=%s layout=%s",
                    request.language.code,
                    request.layout.persistedValue,
                )
                return@launch
            }

            var replaced: NativeTrieHandle? = null
            val accepted = synchronized(lock) {
                if (closed || requestGeneration != generation) {
                    false
                } else {
                    replaced = _activation.value?.handle
                    _activation.value = Activation(
                        request.language,
                        request.language.profile,
                        request.layout,
                        candidate,
                    )
                    // A request already executing against the replaced handle must not publish
                    // after this language/layout becomes active.
                    queryGeneration++
                    true
                }
            }
            if (accepted) {
                replaced?.let(::closeOrRetire)
                Timber.d(
                    "Native keyboard dictionary activated: language=%s layout=%s ngram=%s",
                    request.language.code,
                    request.layout.persistedValue,
                    candidate.hasNgram,
                )
            } else {
                candidate.close()
            }
        }
    }

    override fun getSuggestions(input: String, maxResults: Int): List<String> =
        query(input, maxResults).suggestions

    private data class QueryResult(
        val isKnownWord: Boolean,
        val primaryCorrection: String?,
        val suggestions: List<String>,
    )

    private fun query(input: String, maxResults: Int): QueryResult {
        if (input.isBlank() || maxResults <= 0) return QueryResult(false, null, emptyList())
        val candidateLimit = maxResults
            .coerceIn(1, MAX_NATIVE_RESULTS / CANDIDATE_MULTIPLIER) * CANDIDATE_MULTIPLIER
        val live = synchronized(lock) {
            val live = if (closed) null else _activation.value
            if (live != null) inFlightQueries++
            live
        }
        val nativeQuery = if (live == null) {
            Triple(false, emptyList(), emptyList())
        } else {
            try {
                Triple(
                    live.handle.wordExists(input),
                    live.handle.complete(input, candidateLimit),
                    live.handle.correct(input, MAX_EDIT_DISTANCE, candidateLimit),
                )
            } finally {
                releaseQuery()
            }
        }
        val (isKnownWord, completions, corrections) = nativeQuery
        val nativeCandidates = completions + corrections
        if (nativeCandidates.isEmpty() && personalDictionary.learnedWords.value.isEmpty()) {
            return QueryResult(isKnownWord, null, emptyList())
        }

        val normalizedInput = input.canonicalLookupKey()
        val primaryCorrection = corrections.firstOrNull {
            it.canonicalLookupKey() != normalizedInput
        }
        val prefixInput = normalizedInput.stripAccents()
        val learned = personalDictionary.learnedWords.value
        val learnedKeys = learned.mapTo(mutableSetOf()) { it.canonicalLookupKey() }
        val seen = mutableSetOf(normalizedInput)
        val learnedPrefixWords = learned.asSequence()
            .filter { it.canonicalLookupKey().stripAccents().startsWith(prefixInput) }
            .filter { seen.add(it.canonicalLookupKey()) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toMutableList()
        val learnedNative = mutableListOf<String>()
        val normalNative = mutableListOf<String>()

        nativeCandidates.forEach { word ->
            val normalized = word.canonicalLookupKey()
            if (!seen.add(normalized)) return@forEach
            if (normalized in learnedKeys) learnedNative += word else normalNative += word
        }

        return QueryResult(
            isKnownWord = isKnownWord,
            primaryCorrection = primaryCorrection,
            suggestions = (learnedPrefixWords + learnedNative + normalNative).take(maxResults),
        )
    }

    private fun queryPredictions(words: List<String>, maxResults: Int): QueryResult {
        if (words.size !in 1..2 || maxResults <= 0) return QueryResult(false, null, emptyList())
        val limit = maxResults.coerceIn(1, MAX_PREDICTION_RESULTS)
        val live = synchronized(lock) {
            val live = if (closed) null else _activation.value?.takeIf { it.hasNgram }
            if (live != null) inFlightQueries++
            live
        } ?: return QueryResult(false, null, emptyList())
        val predictions = try {
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            if (words.size == 2) {
                live.handle.predictAfterWords(words[0], words[1], limit).forEach { word ->
                    if (seen.add(word.canonicalLookupKey())) result += word
                }
            }
            if (result.size < limit) {
                live.handle.predictAfterWord(words.last(), limit).forEach { word ->
                    if (seen.add(word.canonicalLookupKey())) result += word
                }
            }
            result.take(limit)
        } finally {
            releaseQuery()
        }
        return QueryResult(false, null, predictions)
    }

    /**
     * Conflates rapid input into a single latest-wins worker. At most the currently executing
     * native query can become stale; queued stale requests are discarded before entering JNI.
     */
    fun requestSuggestions(input: String, maxResults: Int = 3): Long? {
        return synchronized(lock) {
            if (closed) return@synchronized null
            val request = SuggestionRequest(
                ++queryGeneration,
                SuggestionMode.COMPLETION,
                listOf(input),
                maxResults,
            )
            // Sending under the generation lock preserves ordering even for concurrent callers.
            check(queryRequests.trySend(request).isSuccess)
            request.generation
        }
    }

    /** Requests bounded trigram predictions with bigram backoff. */
    fun requestPredictions(words: List<String>, maxResults: Int = 3): Long? = synchronized(lock) {
        if (closed) return@synchronized null
        require(words.size in 1..2)
        val request = SuggestionRequest(
            ++queryGeneration,
            SuggestionMode.PREDICTION,
            words.toList(),
            maxResults,
        )
        check(queryRequests.trySend(request).isSuccess)
        request.generation
    }

    /** Invalidates queued and in-flight work even when native cancellation is ignored. */
    fun invalidateSuggestions() {
        synchronized(lock) {
            if (!closed) queryGeneration++
        }
    }

    private fun publishIfCurrent(request: SuggestionRequest, result: QueryResult) {
        synchronized(lock) {
            if (!closed && request.generation == queryGeneration) {
                _suggestionResults.value = SuggestionResult(
                    requestId = request.generation,
                    mode = request.mode,
                    input = request.input,
                    isKnownWord = result.isKnownWord,
                    primaryCorrection = result.primaryCorrection,
                    suggestions = result.suggestions,
                )
            }
        }
    }

    override fun close() {
        val handle = synchronized(lock) {
            if (closed) return
            closed = true
            generation++
            queryGeneration++
            queryRequests.close()
            _activation.value?.handle.also { _activation.value = null }
        }
        preferenceJob.cancel()
        queryWorker.cancel()
        handle?.let(::closeOrRetire)
    }

    private fun closeOrRetire(handle: NativeTrieHandle) {
        val closeNow = synchronized(lock) {
            if (inFlightQueries == 0) true else {
                retiredHandles += handle
                false
            }
        }
        if (closeNow) handle.close()
    }

    private fun releaseQuery() {
        val handles = synchronized(lock) {
            check(inFlightQueries > 0)
            inFlightQueries--
            if (inFlightQueries == 0) retiredHandles.toList().also { retiredHandles.clear() }
            else emptyList()
        }
        handles.forEach(NativeTrieHandle::close)
    }

    private fun String.stripAccents(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_DIACRITICALS, "")

    private fun String.canonicalLookupKey(): String =
        Normalizer.normalize(this, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    private companion object {
        val COMBINING_DIACRITICALS = Regex("\\p{InCombiningDiacriticalMarks}+")
        const val CANDIDATE_MULTIPLIER = 2
        const val MAX_NATIVE_RESULTS = 20
        const val MAX_EDIT_DISTANCE = 2f
        const val MAX_PREDICTION_RESULTS = 3
    }
}
