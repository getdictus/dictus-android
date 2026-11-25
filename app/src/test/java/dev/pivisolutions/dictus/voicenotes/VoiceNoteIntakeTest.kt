package dev.pivisolutions.dictus.voicenotes

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioFormat
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** What the share target accepts and refuses (#204), iOS `ShareModel.drop`. */
@RunWith(RobolectricTestRunner::class)
class VoiceNoteIntakeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun intake(store: VoiceNoteQueueStore, duration: Double? = 1.5, video: Boolean = false) =
        VoiceNoteIntake(context.contentResolver, store, probeDurationSeconds = { duration }, hasVideoTrack = { video }, now = { 42L })

    private fun shared(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(folder.newFolder(), name).apply { writeBytes(bytes) })

    // An Ogg page header is all the intake reads: duration and video are probed by the lambdas
    // above, and decoding is the processor's job.
    private val ogg = "OggS".toByteArray() + ByteArray(60) { it.toByte() }

    @Test
    fun `an Ogg voice note joins the queue with a copy of its audio`() {
        val store = VoiceNoteQueueStore(folder.newFolder("store"))
        val result = intake(store).receive(listOf(shared("PTT-0001.opus", ogg)))

        val note = result.added.single()
        assertNull(result.refusal)
        assertEquals(SharedAudioFormat.OGG, note.format)
        assertEquals(2, note.durationSeconds)
        assertEquals(42L, note.receivedAt)
        assertTrue(store.audioFile(note)!!.readBytes().contentEquals(ogg))
        assertEquals(listOf(note), store.queue.value.notes)
    }

    @Test
    fun `text, a video and a note over ten minutes are refused, each with its reason`() {
        val store = VoiceNoteQueueStore(folder.newFolder("store"))
        assertEquals(VoiceNoteIntake.Refusal.NOT_AUDIO, intake(store).receive(listOf(shared("a.txt", "hello world!".toByteArray()))).refusal)
        val mp4 = byteArrayOf(0, 0, 0, 0x20) + "ftypisom".toByteArray()
        assertEquals(VoiceNoteIntake.Refusal.NOT_AUDIO, intake(store, video = true).receive(listOf(shared("v.mp4", mp4))).refusal)
        assertEquals(VoiceNoteIntake.Refusal.TOO_LONG, intake(store, duration = 602.0).receive(listOf(shared("long.ogg", ogg))).refusal)
        assertTrue(store.queue.value.notes.isEmpty())
    }

    @Test
    fun `several files at once, ten at most, the bad ones refused and the good ones kept`() {
        val store = VoiceNoteQueueStore(folder.newFolder("store"))
        val uris = listOf(shared("bad.txt", "nope nope nope".toByteArray())) + (1..11).map { shared("n$it.ogg", ogg) }
        val result = intake(store).receive(uris)
        assertEquals(9, result.added.size)
        assertEquals(VoiceNoteIntake.Refusal.NOT_AUDIO, result.refusal)
    }

    @Test
    fun `a file that cannot be opened is a reception failure`() {
        val store = VoiceNoteQueueStore(folder.newFolder("store"))
        val missing = Uri.fromFile(File(folder.root, "gone.ogg"))
        assertEquals(VoiceNoteIntake.Refusal.COULD_NOT_RECEIVE, intake(store).receive(listOf(missing)).refusal)
    }
}
