package dev.pivisolutions.dictus.voicenotes

import android.content.ContentResolver
import android.net.Uri
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioDecoding
import dev.pivisolutions.dictus.core.voicenotes.SharedAudioFormat
import dev.pivisolutions.dictus.core.voicenotes.VoiceNote
import dev.pivisolutions.dictus.core.voicenotes.VoiceNoteQueueStore
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * What the share target does with the files it is handed, iOS `ShareModel.drop` (#204): copy each
 * one into the queue's storage, refuse what is not audio Dictus can read, a video, or a note over
 * ten minutes, and add the rest to the queue.
 *
 * @param probeDurationSeconds the container's own duration, without decoding.
 */
class VoiceNoteIntake(
    private val contentResolver: ContentResolver,
    private val store: VoiceNoteQueueStore,
    private val probeDurationSeconds: (File) -> Double?,
    private val hasVideoTrack: (File) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Why a file was refused, worded by the share screen. */
    enum class Refusal { COULD_NOT_RECEIVE, NOT_AUDIO, TOO_LONG }

    /** The notes added, and the first refusal among the files that were not. */
    data class Result(val added: List<VoiceNote>, val refusal: Refusal?)

    /** Blocking: call it off the main thread. At most [MAXIMUM_ATTACHMENTS] files, as on iOS. */
    fun receive(uris: List<Uri>): Result {
        val arrivals = ArrayList<Pair<File, VoiceNote>>()
        var refusal: Refusal? = null
        for (uri in uris.take(MAXIMUM_ATTACHMENTS)) {
            when (val outcome = copy(uri)) {
                is Outcome.Accepted -> arrivals.add(outcome.file to outcome.note)
                is Outcome.Refused -> {
                    refusal = refusal ?: outcome.refusal
                    Timber.i("Voice note share refused: reason=%s", outcome.reason)
                }
            }
        }
        val added = store.admit(arrivals)
        if (arrivals.isNotEmpty() && added.isEmpty()) refusal = refusal ?: Refusal.COULD_NOT_RECEIVE
        Timber.i("Voice note share: received=%d, added=%d", uris.size, added.size)
        return Result(added, refusal)
    }

    private sealed interface Outcome {
        data class Accepted(val file: File, val note: VoiceNote) : Outcome
        data class Refused(val refusal: Refusal, val reason: String) : Outcome
    }

    private fun copy(uri: Uri): Outcome {
        val incoming = store.incomingFile()
        try {
            val stream = contentResolver.openInputStream(uri) ?: return refuse(incoming, Refusal.COULD_NOT_RECEIVE, "noStream")
            stream.use { input -> incoming.outputStream().use { input.copyTo(it) } }
        } catch (e: IOException) {
            return refuse(incoming, Refusal.COULD_NOT_RECEIVE, "copyFailed")
        } catch (e: SecurityException) {
            return refuse(incoming, Refusal.COULD_NOT_RECEIVE, "noPermission")
        }

        val head = ByteArray(SharedAudioFormat.SNIFF_LENGTH)
        val read = incoming.inputStream().use { it.read(head) }
        val format = SharedAudioFormat.sniff(head.copyOf(maxOf(read, 0)))
            ?: return refuse(incoming, Refusal.NOT_AUDIO, "unrecognisedContainer")
        if (!format.isSupported) return refuse(incoming, Refusal.NOT_AUDIO, "unsupportedCodec:$format")
        if ((format == SharedAudioFormat.MPEG4 || format == SharedAudioFormat.MATROSKA) && hasVideoTrack(incoming)) {
            return refuse(incoming, Refusal.NOT_AUDIO, "video")
        }
        val duration = probeDurationSeconds(incoming)
        if (duration != null && SharedAudioDecoding.exceedsCap(duration)) return refuse(incoming, Refusal.TOO_LONG, "tooLong")

        val note = VoiceNote(receivedAt = now(), audioFileName = null, format = format, durationSeconds = duration?.roundToInt())
        return Outcome.Accepted(incoming, note)
    }

    private fun refuse(incoming: File, refusal: Refusal, reason: String): Outcome {
        store.discard(incoming)
        return Outcome.Refused(refusal, reason)
    }

    companion object {
        const val MAXIMUM_ATTACHMENTS = 10
    }
}
