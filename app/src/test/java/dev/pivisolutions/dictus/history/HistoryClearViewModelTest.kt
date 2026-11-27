package dev.pivisolutions.dictus.history

import dev.pivisolutions.dictus.debug.LanguageResolution
import dev.pivisolutions.dictus.debug.Timings
import dev.pivisolutions.dictus.debug.TranscriptionDebugEntry
import dev.pivisolutions.dictus.debug.TranscriptionDebugMetrics
import dev.pivisolutions.dictus.debug.TranscriptionDebugStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * « Effacer l'historique » empties the debug log too (iOS `ClearHistoryTests`, #906). Robolectric
 * only for `org.json`, which the debug log writes with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryClearViewModelTest {

    @get:Rule val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()
    private val file by lazy { File(folder.root, TranscriptionDebugStore.FILE_NAME) }
    private val debugStore by lazy { TranscriptionDebugStore(file) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HistoryClearViewModel(repository, debugStore, dispatcher)

    @Test
    fun `clear empties the history and the debug log`() = runTest(dispatcher) {
        repository.entries.value = listOf(entry(1))
        debugStore.append(debugEntry("rendez-vous chez le dentiste"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.clear()
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
        assertEquals(0L, file.length())
        assertTrue("no dictated text left in the log", debugStore.readAll().isEmpty())
        assertFalse(viewModel.hasSomethingToDelete.value)
    }

    /** The case #906 names: no history (no Pro), but a debug log. */
    @Test
    fun `clear empties the debug log when the history is empty`() = runTest(dispatcher) {
        debugStore.append(debugEntry("un"))
        debugStore.append(debugEntry("deux"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.clear()
        advanceUntilIdle()

        assertFalse(debugStore.hasEntries())
    }

    @Test
    fun `nothing to delete without history or debug log`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.hasSomethingToDelete.value)
    }

    @Test
    fun `a debug log alone is something to delete`() = runTest(dispatcher) {
        debugStore.append(debugEntry("un"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue("a user without Pro still has dictated text to delete", viewModel.hasSomethingToDelete.value)
    }

    @Test
    fun `a history alone is something to delete`() = runTest(dispatcher) {
        repository.entries.value = listOf(entry(1))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.hasSomethingToDelete.value)
    }

    @Test
    fun `refresh sees a debug log written after Settings first opened`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        debugStore.append(debugEntry("un"))

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.hasSomethingToDelete.value)
    }

    private class FakeRepository : TranscriptionHistoryRepository {
        val entries = MutableStateFlow<List<TranscriptionHistoryEntry>>(emptyList())
        var clearCalls = 0
        override suspend fun insert(entry: TranscriptionHistoryEntry): Long = entry.id
        override fun observeAll(): Flow<List<TranscriptionHistoryEntry>> = entries
        override suspend fun deleteById(id: Long): Boolean = true
        override suspend fun clear() {
            clearCalls++
            entries.value = emptyList()
        }
    }

    private fun entry(id: Long) = TranscriptionHistoryEntry(
        id = id,
        text = "text $id",
        requestedLanguage = "auto",
        durationMillis = 1_000,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = 1,
    )

    private fun debugEntry(raw: String) = TranscriptionDebugEntry(
        id = raw,
        timestampMs = System.currentTimeMillis() / 1_000 * 1_000,
        raw = raw,
        writer = "<KBD>",
        metrics = TranscriptionDebugMetrics(
            detectedLanguage = null,
            languageResolution = LanguageResolution("followKeyboard", "fr", "fr", sttLanguageIsEffective = true),
            rawCharCount = raw.length,
            polishedCharCount = raw.length,
            latencyMs = 120,
            sttEngine = "WK",
            sttModelID = "tiny",
            timings = Timings(preprocessMs = 20, engineMs = 90, postprocessMs = 10),
        ),
    )
}
