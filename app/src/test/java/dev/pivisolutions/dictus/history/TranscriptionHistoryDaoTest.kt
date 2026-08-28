package dev.pivisolutions.dictus.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TranscriptionHistoryDaoTest {
    private lateinit var context: Context
    private lateinit var database: TranscriptionHistoryDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TranscriptionHistoryDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `empty database emits empty history`() = runBlocking {
        assertTrue(database.transcriptionHistoryDao().observeAll().first().isEmpty())
    }

    @Test
    fun `history is newest first with id tie break and exact metadata`() = runBlocking {
        val dao = database.transcriptionHistoryDao()
        dao.insert(entry("older", createdAt = 100))
        val firstTieId = dao.insert(entry("same-first", createdAt = 200))
        val secondTieId = dao.insert(entry("same-second", createdAt = 200))

        val rows = dao.getAll()

        assertEquals(listOf("same-second", "same-first", "older"), rows.map { it.text })
        assertEquals(secondTieId, rows[0].id)
        assertEquals(firstTieId, rows[1].id)
        assertEquals("auto", rows[0].requestedLanguage)
        assertEquals(750L, rows[0].durationMillis)
        assertEquals("small-q5_1", rows[0].modelKey)
        assertEquals("WHISPER", rows[0].provider)
    }

    @Test
    fun `file database survives reopen and supports deletion`() = runBlocking {
        database.close()
        val file = File(context.cacheDir, "history-reopen-${System.nanoTime()}.db")
        try {
            var fileDatabase = Room.databaseBuilder(
                context,
                TranscriptionHistoryDatabase::class.java,
                file.absolutePath,
            ).allowMainThreadQueries().build()
            val id = fileDatabase.transcriptionHistoryDao().insert(entry("persisted", 300))
            fileDatabase.close()

            fileDatabase = Room.databaseBuilder(
                context,
                TranscriptionHistoryDatabase::class.java,
                file.absolutePath,
            ).allowMainThreadQueries().build()
            assertEquals("persisted", fileDatabase.transcriptionHistoryDao().getAll().single().text)
            assertEquals(1, fileDatabase.transcriptionHistoryDao().deleteById(id))
            assertTrue(fileDatabase.transcriptionHistoryDao().getAll().isEmpty())
            fileDatabase.close()
        } finally {
            context.deleteDatabase(file.absolutePath)
            file.delete()
            database = Room.inMemoryDatabaseBuilder(
                context,
                TranscriptionHistoryDatabase::class.java,
            ).allowMainThreadQueries().build()
        }
    }

    private fun entry(text: String, createdAt: Long) = TranscriptionHistoryEntry(
        text = text,
        requestedLanguage = "auto",
        durationMillis = 750,
        modelKey = "small-q5_1",
        provider = "WHISPER",
        createdAtEpochMillis = createdAt,
    )
}
