package dev.pivisolutions.dictus.core.voicenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The queue on disk and its audio copies (#204). Robolectric for `org.json`. */
@RunWith(RobolectricTestRunner::class)
class VoiceNoteQueueStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun arrival(store: VoiceNoteQueueStore, at: Long): Pair<File, VoiceNote> {
        val incoming = store.incomingFile().apply { writeText("audio $at") }
        return incoming to VoiceNote(receivedAt = at, audioFileName = null, format = SharedAudioFormat.OGG, durationSeconds = 3)
    }

    @Test
    fun `admitted notes and their audio survive a restart, a transcript survives as written`() {
        val store = VoiceNoteQueueStore(folder.root)
        val note = store.admit(listOf(arrival(store, 1))).single()
        assertTrue(store.audioFile(note)!!.exists())
        store.mutate { it.complete(note.id, "Bonjour à tous") }

        val reopened = VoiceNoteQueueStore(folder.root)
        val stored = reopened.queue.value.notes.single()
        assertEquals("Bonjour à tous", stored.transcript)
        assertEquals(VoiceNoteState.Done, stored.state)
        assertEquals(3, stored.durationSeconds)
        // The transcript is in: the copy is gone.
        assertEquals(0, File(folder.root, "audio").listFiles()!!.size)
    }

    @Test
    fun `a note cut short by the process waits again on the next start`() {
        val store = VoiceNoteQueueStore(folder.root)
        val note = store.admit(listOf(arrival(store, 1))).single()
        store.mutate { queue -> queue.update(note.id) { it.copy(state = VoiceNoteState.Transcribing(0.4)) } }
        assertFalse(store.delete(note.id))

        assertEquals(VoiceNoteState.Waiting, VoiceNoteQueueStore(folder.root).queue.value.notes.single().state)
    }

    @Test
    fun `a copy left in incoming by a crash is deleted, an unreadable queue is empty`() {
        val store = VoiceNoteQueueStore(folder.root)
        val (orphan, _) = arrival(store, 1)
        File(folder.root, "queue.json").writeText("{not json")

        val reopened = VoiceNoteQueueStore(folder.root)
        assertFalse(orphan.exists())
        assertTrue(reopened.queue.value.notes.isEmpty())
    }

    @Test
    fun `deleting a note deletes its audio`() {
        val store = VoiceNoteQueueStore(folder.root)
        val note = store.admit(listOf(arrival(store, 1))).single()
        assertTrue(store.delete(note.id))
        assertFalse(store.audioFile(note)!!.exists())
    }

    @Test
    fun `audio no note refers to is deleted when the store opens`() {
        val store = VoiceNoteQueueStore(folder.root)
        val note = store.admit(listOf(arrival(store, 1))).single()
        val orphan = File(folder.root, "audio/left-by-a-crash.ogg").apply { writeText("x") }

        VoiceNoteQueueStore(folder.root)
        assertFalse(orphan.exists())
        assertTrue(store.audioFile(note)!!.exists())
    }
}
