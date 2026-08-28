package dev.pivisolutions.dictus.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable, app-private metadata for one successful transcription. */
@Entity(tableName = "transcription_history")
data class TranscriptionHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val requestedLanguage: String,
    val durationMillis: Long,
    val modelKey: String,
    val provider: String,
    val createdAtEpochMillis: Long,
)
