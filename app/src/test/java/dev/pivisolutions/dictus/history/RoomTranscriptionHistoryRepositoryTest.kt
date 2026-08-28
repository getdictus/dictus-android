package dev.pivisolutions.dictus.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { repository.observeAll().take(3).toList() }
        }
        val id = repository.insert(entry())
        assertTrue(repository.deleteById(id))

        val values = emissions.await()
        assertEquals(1, values.first().size)
        assertEquals(0, values.last().size)
        assertFalse(repository.deleteById(id))
        assertFalse(repository.deleteById(Long.MAX_VALUE))
    }

    private fun entry() = TranscriptionHistoryEntry(
        text = "private",
        requestedLanguage = "auto",
        durationMillis = 500,
        modelKey = "model",
        provider = "provider",
        createdAtEpochMillis = 100,
    )
}
