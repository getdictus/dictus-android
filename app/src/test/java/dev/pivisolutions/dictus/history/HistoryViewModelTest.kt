package dev.pivisolutions.dictus.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loading remains distinct until first empty emission`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.entries.isEmpty())

        repository.emit(emptyList())
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun `entries are deterministically newest first`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        repository.emit(listOf(entry(1, 100), entry(2, 200), entry(3, 200)))
        advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun `load failure remains a generic error and cannot become false empty`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            loadFailure = IllegalStateException("private detail")
        }
        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals(HistoryFailure.LOAD, viewModel.uiState.value.failure)

        viewModel.consumeFailure()
        assertEquals(HistoryFailure.LOAD, viewModel.uiState.value.failure)
    }

    @Test
    fun `load cancellation is not converted to failure`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            loadFailure = CancellationException("private detail")
        }
        val viewModel = HistoryViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `delete confirmation cancellation and stable id deletion`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        repository.emit(listOf(entry(10, 100), entry(20, 200)))
        advanceUntilIdle()

        viewModel.requestDelete(10)
        assertEquals(10L, viewModel.uiState.value.pendingDeleteId)
        viewModel.cancelDelete()
        assertNull(viewModel.uiState.value.pendingDeleteId)
        assertTrue(repository.deletedIds.isEmpty())

        viewModel.requestDelete(20)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(listOf(20L), repository.deletedIds)
    }

    @Test
    fun `unknown id is not offered for deletion`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        repository.emit(listOf(entry(1, 1)))
        advanceUntilIdle()
        viewModel.requestDelete(999)
        assertNull(viewModel.uiState.value.pendingDeleteId)
    }

    @Test
    fun `detail selection accepts only a current entry and closes when entry disappears`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        repository.emit(listOf(entry(1, 1), entry(2, 2)))
        advanceUntilIdle()

        viewModel.openDetail(999)
        assertNull(viewModel.uiState.value.selectedEntryId)

        viewModel.openDetail(2)
        assertEquals(2L, viewModel.uiState.value.selectedEntryId)

        repository.emit(listOf(entry(1, 1)))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedEntryId)

        viewModel.openDetail(1)
        viewModel.closeDetail()
        assertNull(viewModel.uiState.value.selectedEntryId)
    }

    @Test
    fun `delete failure is generic and cancellation does not become failure`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HistoryViewModel(repository)
        repository.emit(listOf(entry(1, 1)))
        advanceUntilIdle()

        repository.deleteResult = false
        viewModel.requestDelete(1)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(HistoryFailure.DELETE, viewModel.uiState.value.failure)
        viewModel.consumeFailure()

        repository.deleteResult = true
        repository.deleteFailure = IllegalStateException("private detail")
        viewModel.requestDelete(1)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(HistoryFailure.DELETE, viewModel.uiState.value.failure)
        viewModel.consumeFailure()

        repository.deleteFailure = CancellationException("private detail")
        viewModel.requestDelete(1)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.failure)
    }

    private class FakeRepository : TranscriptionHistoryRepository {
        private val history = MutableSharedFlow<List<TranscriptionHistoryEntry>>(replay = 1)
        val deletedIds = mutableListOf<Long>()
        var deleteResult = true
        var deleteFailure: Throwable? = null
        var loadFailure: Throwable? = null
        override suspend fun insert(entry: TranscriptionHistoryEntry): Long = entry.id
        override fun observeAll(): Flow<List<TranscriptionHistoryEntry>> = loadFailure?.let { failure ->
            flow { throw failure }
        } ?: history
        override suspend fun deleteById(id: Long): Boolean {
            deleteFailure?.let { throw it }
            deletedIds += id
            return deleteResult
        }
        suspend fun emit(entries: List<TranscriptionHistoryEntry>) = history.emit(entries)
    }

    private fun entry(id: Long, createdAt: Long) = TranscriptionHistoryEntry(
        id = id,
        text = "text $id",
        requestedLanguage = "auto",
        durationMillis = 1_000,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = createdAt,
    )
}
