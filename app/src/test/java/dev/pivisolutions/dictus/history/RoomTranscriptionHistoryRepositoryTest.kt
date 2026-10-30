package dev.pivisolutions.dictus.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTranscriptionHistoryRepositoryTest {
    private lateinit var database: TranscriptionHistoryDatabase
    private lateinit var repository: RoomTranscriptionHistoryRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TranscriptionHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTranscriptionHistoryRepository(database.transcriptionHistoryDao())
    }

    @After fun tearDown() = database.close()

    @Test
    fun `insert and delete re-emit and unknown id is false`() = runBlocking {
        // Room runs the first query and every re-query on its own executor, so each write
        // waits for the list it must produce instead of counting emissions (#157).
        val latest = MutableStateFlow<List<TranscriptionHistoryEntry>?>(null)
        val collector = launch { repository.observeAll().collect { latest.value = it } }
        try {
            // Room registers its invalidation observer before the first query, so once the
            // initial empty list is in, the writes below are observed.
            latest.awaitList { it.isEmpty() }
            val id = repository.insert(entry())
            assertEquals(listOf(id), latest.awaitList { it.isNotEmpty() }.map { it.id })
            assertTrue(repository.deleteById(id))
            latest.awaitList { it.isEmpty() }

            assertFalse(repository.deleteById(id))
            assertFalse(repository.deleteById(Long.MAX_VALUE))
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `the 201st insertion keeps 200 entries and drops the oldest`() = runBlocking {
        val dao = database.transcriptionHistoryDao()
        val ids = (0L..200L).map { repository.insert(entry(createdAt = 1_000 + it)) }

        val rows = dao.getAll()

        assertEquals(TranscriptionHistoryRepository.MAX_ENTRIES, rows.size)
        assertFalse(rows.any { it.id == ids.first() })
        assertEquals(ids.last(), rows.first().id)
        assertEquals(ids[1], rows.last().id)
    }

    @Test
    fun `clear deletes every entry`() = runBlocking {
        repeat(3) { repository.insert(entry(createdAt = it.toLong())) }
        repository.clear()
        assertTrue(database.transcriptionHistoryDao().getAll().isEmpty())
    }

    private suspend fun MutableStateFlow<List<TranscriptionHistoryEntry>?>.awaitList(
        predicate: (List<TranscriptionHistoryEntry>) -> Boolean,
    ): List<TranscriptionHistoryEntry> = withTimeout(5_000) { filterNotNull().first(predicate) }

    private fun entry(createdAt: Long = 100) = TranscriptionHistoryEntry(
        text = "private",
        requestedLanguage = "auto",
        durationMillis = 500,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = createdAt,
    )
}
