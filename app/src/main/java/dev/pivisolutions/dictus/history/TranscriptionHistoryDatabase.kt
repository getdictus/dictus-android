package dev.pivisolutions.dictus.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptionHistoryEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class TranscriptionHistoryDatabase : RoomDatabase() {
    abstract fun transcriptionHistoryDao(): TranscriptionHistoryDao

    companion object {
        const val NAME = "transcription-history.db"
    }
}
