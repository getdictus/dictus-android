package dev.pivisolutions.dictus.voicenotes

import dev.pivisolutions.dictus.core.voicenotes.SharedAudioDecoding
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteChunker
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteDecodeException
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteFailure
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

/**
 * Runs the voice note queue: decode, transcribe chunk by chunk, keep the text. Port of iOS
 * `VoiceNoteProcessor` (#204), without its Smart Mode summary, Live Activity and keyboard
 * delivery, which Android does not have.
 *
 * One run at a time, oldest note first. Each note asks the gate again, as iOS does: Pro can end
 * while a queue runs, and the waiting notes then stay waiting until it is back.
 *
 * The run lives in the app's process scope: it keeps going when the screen that started it
 * closes, and stops with the process. A note cut short goes back to waiting on the next start
 * (`VoiceNoteQueue.recoverInterrupted`).
 *
 * @param mayTranscribe Pro and the Voice notes switch on, read before each note.
 * @param decode a file to 16 kHz mono samples, [dev.pivisolutions.dictus.core.voicenotes.SharedAudioDecoder]
 *   in the app; blocking, so it is called on [scope]'s dispatcher, which is not the main one.
 */
class VoiceNoteProcessor(
    private val store: VoiceNoteQueueStore,
    private val engine: VoiceNoteEngine,
    private val mayTranscribe: suspend () -> Boolean,
    private val decode: (File) -> FloatArray,
    private val scope: CoroutineScope,
) {
    private var run: Job? = null

    /**
     * Runs the queue after any run in progress. Safe to call on every arrival and every retry.
     *
     * WHY chained rather than skipped while a run is active: that run may be past its last look
     * at the queue, and a note added then would wait for the next arrival. A second pass that
     * finds nothing costs one read.
     */
    @Synchronized
    fun kick(): Job {
        val previous = run
        return scope.launch {
            previous?.join()
            drain()
        }.also { run = it }
    }

    fun retry(note: VoiceNote) {
        store.mutate { it.retry(note.id) }
        kick()
    }

    private suspend fun drain() {
        while (true) {
            val note = store.queue.value.next ?: return
            if (!mayTranscribe()) {
                Timber.i("Voice notes paused: not entitled, waiting=%d", store.queue.value.notes.count { it.state == VoiceNoteState.Waiting })
                return
            }
            transcribe(note)
        }
    }

    private suspend fun transcribe(note: VoiceNote) {
        val startedAt = System.nanoTime()
        store.mutate { queue -> queue.update(note.id) { it.copy(state = VoiceNoteState.Transcribing(0.0)) } }
        val file = store.audioFile(note) ?: return fail(note, VoiceNoteFailure.UNREADABLE, "audio missing")

        val samples = try {
            decode(file)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: VoiceNoteDecodeException) {
            return fail(note, failure(e), e.message ?: "decode")
        } catch (e: Exception) {
            return fail(note, VoiceNoteFailure.UNREADABLE, e.javaClass.simpleName)
        }
        val seconds = samples.size.toDouble() / SharedAudioDecoding.SAMPLE_RATE
        store.mutate { queue -> queue.update(note.id) { it.copy(durationSeconds = seconds.roundToInt()) } }

        val ranges = VoiceNoteChunker.ranges(samples)
        // Lengths and counts only: a voice note is someone else's words.
        Timber.i("Voice note started: id=%s, seconds=%d, chunks=%d", note.id.toString().take(8), seconds.roundToInt(), ranges.size)
        val parts = ArrayList<String>()
        for ((index, range) in ranges.withIndex()) {
            engine.awaitNoDictation()
            val text = try {
                engine.transcribeChunk(samples.copyOfRange(range.first, range.last + 1))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                null
            } ?: return fail(note, VoiceNoteFailure.TRANSCRIPTION_FAILED, "chunk ${index + 1}/${ranges.size}")
            // A chunk of silence inside a note is not a failure of the note.
            if (text.isNotEmpty()) parts.add(text)
            val progress = (index + 1).toDouble() / ranges.size
            store.mutate { queue -> queue.update(note.id) { it.copy(state = VoiceNoteState.Transcribing(progress)) } }
        }

        val transcript = parts.joinToString(" ")
        if (transcript.isEmpty()) return fail(note, VoiceNoteFailure.NO_SPEECH, "empty transcript")
        store.mutate { it.complete(note.id, transcript) }
        Timber.i(
            "Voice note finished: id=%s, chars=%d, elapsedMs=%d",
            note.id.toString().take(8), transcript.length, (System.nanoTime() - startedAt) / 1_000_000,
        )
    }

    private fun fail(note: VoiceNote, failure: VoiceNoteFailure, detail: String) {
        store.mutate { it.fail(note.id, failure) }
        Timber.w("Voice note failed: id=%s, failure=%s, detail=%s", note.id.toString().take(8), failure, detail)
    }

    private fun failure(error: VoiceNoteDecodeException): VoiceNoteFailure = when (error) {
        is VoiceNoteDecodeException.TooLong -> VoiceNoteFailure.TOO_LONG
        is VoiceNoteDecodeException.Empty -> VoiceNoteFailure.NO_SPEECH
        is VoiceNoteDecodeException.NoAudioTrack -> VoiceNoteFailure.UNSUPPORTED_FORMAT
        is VoiceNoteDecodeException.Unreadable -> VoiceNoteFailure.UNREADABLE
    }
}
