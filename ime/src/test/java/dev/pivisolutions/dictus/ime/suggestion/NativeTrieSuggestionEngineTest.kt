package dev.pivisolutions.dictus.ime.suggestion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.ime.language.KeyboardLayout
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import dev.pivisolutions.dictus.trie.TrieKeyboardLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class NativeTrieSuggestionEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private val engines = mutableListOf<NativeTrieSuggestionEngine>()

    @Before
    fun setUp() {
        scope = TestScope(dispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("native_trie.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        engines.forEach(NativeTrieSuggestionEngine::close)
        scope.cancel()
    }

    @Test
    fun `activation atomically publishes language resolved layout and native metadata`() =
        runTest(dispatcher) {
            dataStore.edit {
                it[PreferenceKeys.KEYBOARD_LANGUAGE] = "en"
                it[PreferenceKeys.KEYBOARD_LAYOUT] = "azerty"
            }
            val opener = FakeOpener()
            val engine = createEngine(opener)
            advanceUntilIdle()

            val activation = requireNotNull(engine.activation.value)
            assertEquals(SupportedLanguage.ENGLISH, activation.language)
            assertEquals(KeyboardLayout.AZERTY, activation.layout)
            assertEquals("en_spellcheck.dict", activation.profile.nativeDictionaryAssetName)
            assertEquals(
                OpenRequest("en_spellcheck.dict", TrieKeyboardLayout.AZERTY),
                opener.requests.single(),
            )
        }

    @Test
    fun `failed switch retains the previous complete activation and handle`() = runTest(dispatcher) {
        val french = FakeHandle(prefix = listOf("bonjour"))
        val opener = FakeOpener(mutableListOf(Result.success(french), Result.failure(Error("no en"))))
        val engine = createEngine(opener)
        advanceUntilIdle()
        val initial = requireNotNull(engine.activation.value)

        dataStore.edit { it[PreferenceKeys.KEYBOARD_LANGUAGE] = "en" }
        advanceUntilIdle()

        assertSame(initial, engine.activation.value)
        assertEquals(SupportedLanguage.FRENCH, engine.activation.value?.language)
        assertEquals(listOf("bonjour"), engine.getSuggestions("bo"))
        assertEquals(0, french.closeCount)
    }

    @Test
    fun `stale rapid switch cannot replace newer activation and stale handle closes once`() =
        runTest(dispatcher) {
            val french = FakeHandle(prefix = listOf("bonjour"))
            val stale = FakeHandle(prefix = listOf("hello"))
            val newest = FakeHandle(prefix = listOf("bonne"))
            val staleStarted = CountDownLatch(1)
            val releaseStale = CountDownLatch(1)
            val allOpened = CountDownLatch(3)
            val calls = AtomicInteger()
            val opener = object : NativeTrieOpener {
                override fun open(assetName: String, layout: TrieKeyboardLayout): NativeTrieHandle {
                    return when (calls.incrementAndGet()) {
                        1 -> french
                        2 -> {
                            staleStarted.countDown()
                            check(releaseStale.await(5, TimeUnit.SECONDS))
                            stale
                        }
                        else -> newest
                    }.also {
                        allOpened.countDown()
                    }
                }
            }
            val executor = Executors.newSingleThreadExecutor()
            val blockingDispatcher = executor.asCoroutineDispatcher()
            try {
                val engine = NativeTrieSuggestionEngine(dataStore, scope, opener, blockingDispatcher)
                    .also(engines::add)
                runCurrent()
                while (engine.activation.value == null) Thread.yield()

                dataStore.edit { it[PreferenceKeys.KEYBOARD_LANGUAGE] = "en" }
                runCurrent()
                assertTrue(staleStarted.await(5, TimeUnit.SECONDS))
                dataStore.edit { it[PreferenceKeys.KEYBOARD_LANGUAGE] = "fr" }
                runCurrent()
                releaseStale.countDown()
                assertTrue(allOpened.await(5, TimeUnit.SECONDS))

                assertEquals(SupportedLanguage.FRENCH, engine.activation.value?.language)
                assertEquals(listOf("bonne"), engine.getSuggestions("bo"))
                assertEquals(1, stale.closeCount)
            } finally {
                releaseStale.countDown()
                blockingDispatcher.close()
                executor.shutdownNow()
            }
        }

    @Test
    fun `query merging ranking exact filtering and limits preserve learned OOV prefixes`() =
        runTest(dispatcher) {
            val handle = FakeHandle(
                prefix = listOf("Bon", "bonjour", "bonsoir", "bonus"),
                fuzzy = listOf("BON", "bonne", "bond"),
            )
            val engine = createEngine(FakeOpener(mutableListOf(Result.success(handle))))
            advanceUntilIdle()
            engine.personalDictionary.recordWordTyped("bond")
            engine.personalDictionary.recordWordTyped("bond")
            engine.personalDictionary.recordWordTyped("bonzai")
            engine.personalDictionary.recordWordTyped("bonzai")
            advanceUntilIdle()

            assertEquals(listOf("bond", "bonzai", "bonjour"), engine.getSuggestions("bOn", 3))
            assertEquals(listOf(6, 6), handle.requestedLimits)
            assertTrue(engine.getSuggestions("bon", 0).isEmpty())
        }

    @Test
    fun `canonical equivalent native exact result is filtered`() = runTest(dispatcher) {
        val handle = FakeHandle(prefix = listOf("CA\u0301FE"))
        val engine = createEngine(FakeOpener(mutableListOf(Result.success(handle))))
        advanceUntilIdle()

        assertTrue(engine.getSuggestions("CÁFE").isEmpty())
    }

    @Test
    fun `canonical equivalent learned and native results are deduplicated`() = runTest(dispatcher) {
        val handle = FakeHandle(prefix = listOf("CÁFE"))
        val engine = createEngine(FakeOpener(mutableListOf(Result.success(handle))))
        advanceUntilIdle()
        engine.personalDictionary.recordWordTyped("CA\u0301FE")
        engine.personalDictionary.recordWordTyped("CA\u0301FE")
        advanceUntilIdle()

        assertEquals(listOf("ca\u0301fe"), engine.getSuggestions("ca"))
    }

    @Test
    fun `accepted activation prevents blocked old handle query from publishing`() =
        runTest(dispatcher) {
            val oldQueryEntered = CountDownLatch(1)
            val releaseOldQuery = CountDownLatch(1)
            val oldQueryTaskFinished = CountDownLatch(1)
            val queryThread = AtomicReference<Thread?>()
            val oldHandle = object : NativeTrieHandle {
                override fun complete(prefix: String, maxResults: Int): List<String> {
                    queryThread.set(Thread.currentThread())
                    oldQueryEntered.countDown()
                    check(releaseOldQuery.await(5, TimeUnit.SECONDS))
                    return listOf("ancienne")
                }

                override fun correct(
                    word: String,
                    maxEditDistance: Float,
                    maxResults: Int,
                ) = emptyList<String>()

                override fun close() = Unit
            }
            val newHandle = FakeHandle(prefix = listOf("hello"))
            val openCount = AtomicInteger()
            val opener = NativeTrieOpener { _, _ ->
                if (openCount.incrementAndGet() == 1) oldHandle else newHandle
            }
            val executor = object : ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                LinkedBlockingQueue(),
            ) {
                override fun afterExecute(runnable: Runnable?, throwable: Throwable?) {
                    if (Thread.currentThread() === queryThread.get()) {
                        oldQueryTaskFinished.countDown()
                    }
                    super.afterExecute(runnable, throwable)
                }
            }
            val ioDispatcher = executor.asCoroutineDispatcher()
            try {
                val engine = NativeTrieSuggestionEngine(
                    dataStore,
                    scope,
                    opener,
                    ioDispatcher,
                ).also(engines::add)
                runCurrent()
                while (engine.activation.value == null) Thread.yield()

                engine.requestSuggestions("an")
                assertTrue(oldQueryEntered.await(5, TimeUnit.SECONDS))
                dataStore.edit { it[PreferenceKeys.KEYBOARD_LANGUAGE] = "en" }
                runCurrent()
                while (engine.activation.value?.language != SupportedLanguage.ENGLISH) Thread.yield()

                releaseOldQuery.countDown()
                assertTrue(oldQueryTaskFinished.await(5, TimeUnit.SECONDS))
                assertEquals("", engine.suggestionResults.value.input)
                assertTrue(engine.suggestionResults.value.suggestions.isEmpty())
            } finally {
                releaseOldQuery.countDown()
                ioDispatcher.close()
                executor.shutdownNow()
            }
        }

    @Test
    fun `close and replacement close every owned handle exactly once`() = runTest(dispatcher) {
        val french = FakeHandle()
        val english = FakeHandle()
        val engine = createEngine(
            FakeOpener(mutableListOf(Result.success(french), Result.success(english))),
        )
        advanceUntilIdle()
        dataStore.edit { it[PreferenceKeys.KEYBOARD_LANGUAGE] = "en" }
        advanceUntilIdle()
        assertEquals(1, french.closeCount)

        engine.close()
        engine.close()
        assertEquals(1, english.closeCount)
        assertTrue(engine.getSuggestions("he").isEmpty())
    }

    @Test
    fun `close returns promptly but defers handle destruction until in flight query ends`() = runTest(dispatcher) {
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        val queryReturned = CountDownLatch(1)
        val closeReturned = CountDownLatch(1)
        val handle = object : NativeTrieHandle {
            var closeCount = 0

            override fun complete(prefix: String, maxResults: Int): List<String> {
                queryEntered.countDown()
                check(releaseQuery.await(5, TimeUnit.SECONDS))
                return listOf("hello")
            }

            override fun correct(
                word: String,
                maxEditDistance: Float,
                maxResults: Int,
            ) = emptyList<String>()

            override fun close() {
                closeCount++
            }
        }
        val engine = createEngine(NativeTrieOpener { _, _ -> handle })
        advanceUntilIdle()
        val executor = Executors.newFixedThreadPool(2)
        try {
            executor.execute {
                engine.getSuggestions("he")
                queryReturned.countDown()
            }
            assertTrue(queryEntered.await(5, TimeUnit.SECONDS))
            executor.execute {
                engine.close()
                closeReturned.countDown()
            }
            assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
            assertEquals(0, handle.closeCount)

            releaseQuery.countDown()
            assertTrue(queryReturned.await(5, TimeUnit.SECONDS))
            assertEquals(1, handle.closeCount)
        } finally {
            releaseQuery.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `conflated worker drops stale queued JNI calls and blank publication is generation safe`() =
        runTest(dispatcher) {
            val staleEntered = CountDownLatch(1)
            val releaseStale = CountDownLatch(1)
            val newestReturned = CountDownLatch(1)
            val completedPrefixes = Collections.synchronizedList(mutableListOf<String>())
            val handle = object : NativeTrieHandle {
                @Synchronized
                override fun complete(prefix: String, maxResults: Int): List<String> {
                    completedPrefixes += prefix
                    return when (prefix) {
                        "old" -> {
                        staleEntered.countDown()
                        check(releaseStale.await(5, TimeUnit.SECONDS))
                        listOf("older")
                    }
                        "new" -> listOf("newest").also { newestReturned.countDown() }
                        else -> error("stale queued JNI call executed: $prefix")
                    }
                }

                @Synchronized
                override fun correct(
                    word: String,
                    maxEditDistance: Float,
                    maxResults: Int,
                ) = emptyList<String>()

                override fun close() = Unit
            }
            val executor = Executors.newFixedThreadPool(2)
            val queryDispatcher = executor.asCoroutineDispatcher()
            try {
                val engine = NativeTrieSuggestionEngine(
                    dataStore,
                    scope,
                    NativeTrieOpener { _, _ -> handle },
                    queryDispatcher,
                ).also(engines::add)
                runCurrent()
                while (engine.activation.value == null) Thread.yield()

                engine.requestSuggestions("old")
                assertTrue(staleEntered.await(5, TimeUnit.SECONDS))
                engine.requestSuggestions("queued-1")
                engine.requestSuggestions("queued-2")
                engine.requestSuggestions("")
                engine.requestSuggestions("new")
                // One worker means the latest request cannot queue behind multiple native calls.
                assertEquals(listOf("old"), completedPrefixes.toList())
                releaseStale.countDown()
                assertTrue(newestReturned.await(5, TimeUnit.SECONDS))
                while (engine.suggestionResults.value.input != "new") Thread.yield()
                assertEquals(listOf("newest"), engine.suggestionResults.value.suggestions)
                assertEquals(listOf("old", "new"), completedPrefixes.toList())

                engine.requestSuggestions("   ")
                while (engine.suggestionResults.value.input != "   ") Thread.yield()
                assertTrue(engine.suggestionResults.value.suggestions.isEmpty())
                assertEquals(listOf("old", "new"), completedPrefixes.toList())
            } finally {
                releaseStale.countDown()
                queryDispatcher.close()
                executor.shutdownNow()
            }
        }

    private fun createEngine(opener: NativeTrieOpener) = NativeTrieSuggestionEngine(
        dataStore = dataStore,
        coroutineScope = scope,
        opener = opener,
        ioDispatcher = dispatcher,
    ).also(engines::add)

    private data class OpenRequest(val assetName: String, val layout: TrieKeyboardLayout)

    private class FakeOpener(
        private val results: MutableList<Result<FakeHandle>> = mutableListOf(Result.success(FakeHandle())),
    ) : NativeTrieOpener {
        val requests = mutableListOf<OpenRequest>()

        override fun open(assetName: String, layout: TrieKeyboardLayout): NativeTrieHandle {
            requests += OpenRequest(assetName, layout)
            return results.removeAt(0).getOrThrow()
        }
    }


    private class FakeHandle(
        private val prefix: List<String> = emptyList(),
        private val fuzzy: List<String> = emptyList(),
    ) : NativeTrieHandle {
        var closeCount = 0
        val requestedLimits = mutableListOf<Int>()

        override fun complete(prefix: String, maxResults: Int): List<String> {
            requestedLimits += maxResults
            return this.prefix.take(maxResults)
        }

        override fun correct(word: String, maxEditDistance: Float, maxResults: Int): List<String> {
            requestedLimits += maxResults
            return fuzzy.take(maxResults)
        }

        override fun close() {
            closeCount++
        }
    }
}
