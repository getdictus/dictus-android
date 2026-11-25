package dev.pivisolutions.dictus.voicenotes

import dev.pivisolutions.dictus.core.voicenotes.SharedAudioDecoding
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioFormat
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteDecodeException
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteFailure
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The queue's run, iOS `VoiceNoteProcessor.transcribe` (#204), against a fake engine. */
@RunWith(RobolectricTestRunner::class)
class VoiceNoteProcessorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = SharedAudioDecoding.SAMPLE_RATE
    private val chunks = mutableListOf<Int>()
    private var answers: (Int) -> String? = { "partie ${it + 1}" }

    private val engine = object : VoiceNoteEngine {
        override suspend fun awaitNoDictation() = Unit
        override suspend fun transcribeChunk(samples: FloatArray): String? {
            chunks += samples.size
            return answers(chunks.size - 1)
        }
    }

    private fun TestScope.processor(
        store: VoiceNoteQueueStore,
        entitled: Boolean = true,
        decode: (File) -> FloatArray = { FloatArray(100 * rate) { if (it % 2 == 0) 0.3f else -0.3f } },
    ) = VoiceNoteProcessor(store, engine, mayTranscribe = { entitled }, decode = decode, scope = this)

    private fun storeWithOneNote(): Pair<VoiceNoteQueueStore, VoiceNote> {
        val store = VoiceNoteQueueStore(folder.root)
        val incoming = store.incomingFile().apply { writeText("ogg") }
        val note = store.admit(listOf(incoming to VoiceNote(receivedAt = 1, audioFileName = null, format = SharedAudioFormat.OGG))).single()
        return store to note
    }

    @Test
    fun `a 100 s note goes through in chunks, joined with a space`() = runTest {
        val (store, note) = storeWithOneNote()
        processor(store).kick().join()

        // Cut just before 45 s and 90 s (a steady signal has no quieter place), then the 10 s tail.
        assertEquals(3, chunks.size)
        assertEquals(100 * rate, chunks.sum())
        val done = store.queue.value.note(note.id)!!
        assertEquals(VoiceNoteState.Done, done.state)
        assertEquals("partie 1 partie 2 partie 3", done.transcript)
        assertEquals(100, done.durationSeconds)
    }

    @Test
    fun `a silent chunk is skipped, a silent note has no words`() = runTest {
        val (store, note) = storeWithOneNote()
        answers = { "" }
        processor(store).kick().join()
        assertEquals(VoiceNoteState.Failed(VoiceNoteFailure.NO_SPEECH), store.queue.value.note(note.id)!!.state)
    }

    @Test
    fun `an engine failure keeps the audio for a retry, which then succeeds`() = runTest {
        val (store, note) = storeWithOneNote()
        answers = { null }
        val processor = processor(store)
        processor.kick().join()
        val failed = store.queue.value.note(note.id)!!
        assertEquals(VoiceNoteState.Failed(VoiceNoteFailure.TRANSCRIPTION_FAILED), failed.state)
        assertNotNull(store.audioFile(failed))

        answers = { "ok" }
        processor.retry(failed)
        processor.kick().join()
        assertEquals(VoiceNoteState.Done, store.queue.value.note(note.id)!!.state)
    }

    @Test
    fun `a decode refusal names the failure`() = runTest {
        val (store, note) = storeWithOneNote()
        processor(store, decode = { throw VoiceNoteDecodeException.TooLong(700) }).kick().join()
        assertEquals(VoiceNoteState.Failed(VoiceNoteFailure.TOO_LONG), store.queue.value.note(note.id)!!.state)
    }

    @Test
    fun `without the entitlement the note keeps waiting`() = runTest {
        val (store, note) = storeWithOneNote()
        processor(store, entitled = false).kick().join()
        assertEquals(VoiceNoteState.Waiting, store.queue.value.note(note.id)!!.state)
        assertEquals(emptyList<Int>(), chunks)
    }
}
