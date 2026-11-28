package dev.pivisolutions.dictus.core.voicenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue's moves, iOS `VoiceNoteQueue` (#204). */
class VoiceNoteQueueTest {

    private fun note(at: Long, state: VoiceNoteState = VoiceNoteState.Waiting) =
        VoiceNote(receivedAt = at, audioFileName = "a$at.ogg", format = SharedAudioFormat.OGG, state = state)

    @Test
    fun `newest first, oldest waiting next, and a known note is not added twice`() {
        val first = note(1)
        val second = note(2)
        val queue = VoiceNoteQueue().add(listOf(first)).add(listOf(second, first))
        assertEquals(listOf(second, first), queue.notes)
        assertEquals(first, queue.next)
    }

    @Test
    fun `a transcript drops the audio`() {
        val waiting = note(1)
        val done = VoiceNoteQueue().add(listOf(waiting)).complete(waiting.id, "Bonjour").note(waiting.id)!!
        assertEquals(VoiceNoteState.Done, done.state)
        assertEquals("Bonjour", done.transcript)
        assertNull(done.audioFileName)
    }

    @Test
    fun `only an engine failure keeps the audio and can be retried`() {
        val engine = note(1)
        val noSpeech = note(2)
        var queue = VoiceNoteQueue().add(listOf(engine, noSpeech))
            .fail(engine.id, VoiceNoteFailure.TRANSCRIPTION_FAILED)
            .fail(noSpeech.id, VoiceNoteFailure.NO_SPEECH)
        assertEquals("a1.ogg", queue.note(engine.id)!!.audioFileName)
        assertNull(queue.note(noSpeech.id)!!.audioFileName)

        queue = queue.retry(engine.id).retry(noSpeech.id)
        assertEquals(VoiceNoteState.Waiting, queue.note(engine.id)!!.state)
        assertEquals(VoiceNoteState.Failed(VoiceNoteFailure.NO_SPEECH), queue.note(noSpeech.id)!!.state)
    }

    @Test
    fun `a note left transcribing by a dead process waits again`() {
        val queue = VoiceNoteQueue().add(listOf(note(1, VoiceNoteState.Transcribing(0.5)))).recoverInterrupted()
        assertEquals(VoiceNoteState.Waiting, queue.notes.single().state)
        assertTrue(queue.hasPendingWork)
    }

    @Test
    fun `beyond 20 finished notes the oldest go, pending ones never`() {
        val finished = (1L..21L).map { note(it, VoiceNoteState.Done) }
        val pending = note(0)
        val queue = VoiceNoteQueue().add(finished + pending).trimFinished()
        assertEquals(21, queue.notes.size)
        assertFalse(queue.notes.any { it.receivedAt == 1L })
        assertTrue(queue.notes.contains(pending))
    }

    @Test
    fun `an unknown failure name reads as the retryable one`() {
        assertEquals(VoiceNoteFailure.TRANSCRIPTION_FAILED, VoiceNoteFailure.fromName("somethingNew"))
    }
}
