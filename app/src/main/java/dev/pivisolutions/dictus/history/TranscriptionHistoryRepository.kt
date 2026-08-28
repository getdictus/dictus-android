package dev.pivisolutions.dictus.history

import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

interface TranscriptionHistoryRepository {
    suspend fun insert(entry: TranscriptionHistoryEntry): Long
    fun observeAll(): Flow<List<TranscriptionHistoryEntry>>
}

class RoomTranscriptionHistoryRepository(
    private val dao: TranscriptionHistoryDao,
) : TranscriptionHistoryRepository {
    override suspend fun insert(entry: TranscriptionHistoryEntry): Long = dao.insert(entry)
    override fun observeAll(): Flow<List<TranscriptionHistoryEntry>> = dao.observeAll()
}

data class TranscriptionHistoryMetadata(
    val requestedLanguage: String,
    val durationMillis: Long,
    val modelKey: String,
    val provider: String,
)

/** Isolates durable-history failure from an otherwise successful transcription. */
class TranscriptionHistoryWriter(
    private val repository: TranscriptionHistoryRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun persist(
        retention: TranscriptionRetention,
        text: String,
        metadata: TranscriptionHistoryMetadata,
    ) {
        if (retention != TranscriptionRetention.PERSIST_HISTORY || text.isBlank()) return

        try {
            repository.insert(
                TranscriptionHistoryEntry(
                    text = text,
                    requestedLanguage = metadata.requestedLanguage,
                    durationMillis = metadata.durationMillis,
                    modelKey = metadata.modelKey,
                    provider = metadata.provider,
                    createdAtEpochMillis = nowMillis(),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // Never pass the exception or entity to the logger: either may contain bound text.
            Timber.e("Transcription history write failed (%s)", failure::class.java.simpleName)
        }
    }
}
