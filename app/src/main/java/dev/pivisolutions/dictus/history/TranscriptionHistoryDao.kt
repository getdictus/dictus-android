package dev.pivisolutions.dictus.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionHistoryDao {
    @Insert
    suspend fun insert(entry: TranscriptionHistoryEntry): Long

    /** Inserts [entry], then drops the oldest rows beyond [maxEntries], in one transaction. */
    @Transaction
    suspend fun insertCapped(entry: TranscriptionHistoryEntry, maxEntries: Int): Long {
        val id = insert(entry)
        deleteBeyond(maxEntries)
        return id
    }

    @Query(
        "DELETE FROM transcription_history WHERE id NOT IN (" +
            "SELECT id FROM transcription_history " +
            "ORDER BY createdAtEpochMillis DESC, id DESC LIMIT :maxEntries)",
    )
    suspend fun deleteBeyond(maxEntries: Int): Int

    @Query("SELECT * FROM transcription_history ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<TranscriptionHistoryEntry>>

    @Query("SELECT * FROM transcription_history ORDER BY createdAtEpochMillis DESC, id DESC")
    suspend fun getAll(): List<TranscriptionHistoryEntry>

    @Query("DELETE FROM transcription_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM transcription_history")
    suspend fun deleteAll(): Int
}
