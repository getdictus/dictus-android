package dev.pivisolutions.dictus.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionHistoryDao {
    @Insert
    suspend fun insert(entry: TranscriptionHistoryEntry): Long

    @Query("SELECT * FROM transcription_history ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<TranscriptionHistoryEntry>>

    @Query("SELECT * FROM transcription_history ORDER BY createdAtEpochMillis DESC, id DESC")
    suspend fun getAll(): List<TranscriptionHistoryEntry>

    @Query("DELETE FROM transcription_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
