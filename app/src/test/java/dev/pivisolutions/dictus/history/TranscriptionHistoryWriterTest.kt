package dev.pivisolutions.dictus.history

import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class TranscriptionHistoryWriterTest {
    private val metadata = TranscriptionHistoryMetadata(
        requestedLanguage = "auto",
        durationMillis = 500,
        modelKey = "tiny",
        provider = "WHISPER",
    )

    @Test
    fun `persistent successful result is inserted exactly once`() = runBlocking {
        val repository = FakeRepository()
        val writer = TranscriptionHistoryWriter(repository) { 1234 }

        writer.persist(TranscriptionRetention.PERSIST_HISTORY, "private words", metadata)

        assertEquals(1, repository.entries.size)
        assertEquals("private words", repository.entries.single().text)
        assertEquals(1234L, repository.entries.single().createdAtEpochMillis)
    }

    @Test
    fun `ephemeral and blank results are not inserted`() = runBlocking {
        val repository = FakeRepository()
        val writer = TranscriptionHistoryWriter(repository)

        writer.persist(TranscriptionRetention.EPHEMERAL, "onboarding sample", metadata)
        writer.persist(TranscriptionRetention.PERSIST_HISTORY, "   ", metadata)

        assertTrue(repository.entries.isEmpty())
    }

    @Test
    fun `database failure is isolated and log excludes private text and exception message`() = runBlocking {
        val canary = "SECRET_CANARY"
        val repository = FakeRepository(failure = IllegalStateException("failed binding $canary"))
        val messages = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                messages += message
                t?.message?.let(messages::add)
            }
        }
        Timber.plant(tree)
        try {
            TranscriptionHistoryWriter(repository).persist(
                TranscriptionRetention.PERSIST_HISTORY,
                canary,
                metadata,
            )
        } finally {
            Timber.uproot(tree)
        }

        assertEquals(1, messages.size)
        assertFalse(messages.single().contains(canary))
        assertTrue(messages.single().contains("IllegalStateException"))
    }

    @Test
    fun `cancellation is never swallowed`() {
        val repository = FakeRepository(failure = CancellationException("cancelled"))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                TranscriptionHistoryWriter(repository).persist(
                    TranscriptionRetention.PERSIST_HISTORY,
                    "text",
                    metadata,
                )
            }
        }
    }

    private class FakeRepository(
        private val failure: Exception? = null,
    ) : TranscriptionHistoryRepository {
        val entries = mutableListOf<TranscriptionHistoryEntry>()

        override suspend fun insert(entry: TranscriptionHistoryEntry): Long {
            failure?.let { throw it }
            entries += entry
            return entries.size.toLong()
        }

        override fun observeAll(): Flow<List<TranscriptionHistoryEntry>> = emptyFlow()
    }
}
