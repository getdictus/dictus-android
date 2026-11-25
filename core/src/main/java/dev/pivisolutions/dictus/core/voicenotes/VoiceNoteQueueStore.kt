package dev.pivisolutions.dictus.core.voicenotes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The voice note queue on disk: `queue.json` and the audio copies beside it, in the app's
 * private storage. Port of iOS `VoiceNoteQueueStore` (#204).
 *
 * WHY a file and not the preferences DataStore: the queue holds transcripts up to ten minutes
 * long, and DataStore rewrites every preference on each write. One JSON file written whole and
 * atomically keeps iOS's load-once / write-whole discipline.
 *
 * WHY the audio is copied in: a shared `content://` URI is only readable while the sharing
 * app's grant lasts, which ends with the receiving activity. A copy lands in `incoming/` first
 * and moves to `audio/` in the same step that adds its note ([admit]), so the sweep of `audio/`
 * never sees a file whose note is not written yet. It is deleted as soon as no note refers to it
 * ([complete][VoiceNoteQueue.complete], a non-retryable failure, a deletion).
 *
 * Every method is synchronised: the share activity adds notes while the processor updates them.
 */
class VoiceNoteQueueStore(private val directory: File) {

    private val queueFile = File(directory, "queue.json")
    private val audioDirectory = File(directory, "audio")
    private val incomingDirectory = File(directory, "incoming")

    private val _queue = MutableStateFlow(read(queueFile).recoverInterrupted())
    val queue: StateFlow<VoiceNoteQueue> = _queue.asStateFlow()

    init {
        // Copies a share left behind when the process died between copy and admission, and audio
        // a note stopped referring to just before the process died.
        incomingDirectory.listFiles()?.forEach { it.delete() }
        sweepUnreferencedAudio()
    }

    fun audioFile(note: VoiceNote): File? = note.audioFileName?.let { File(audioDirectory, it) }

    /** A new file in `incoming/`, for a copy not yet admitted. Deleted by [admit] or [discard]. */
    @Synchronized
    fun incomingFile(): File {
        incomingDirectory.mkdirs()
        return File(incomingDirectory, UUID.randomUUID().toString())
    }

    fun discard(incoming: File) {
        incoming.delete()
    }

    /**
     * Moves each copy into `audio/` under a name with its format's extension and adds its note,
     * in one step. Returns the notes added; none when the queue could not be written, and then
     * the copies are deleted.
     */
    @Synchronized
    fun admit(arrivals: List<Pair<File, VoiceNote>>): List<VoiceNote> {
        if (arrivals.isEmpty()) return emptyList()
        audioDirectory.mkdirs()
        val notes = arrivals.mapNotNull { (incoming, note) ->
            val name = "${note.id}.${note.format.fileExtension}"
            if (incoming.renameTo(File(audioDirectory, name))) note.copy(audioFileName = name) else null.also { incoming.delete() }
        }
        if (notes.isEmpty() || mutate { it.add(notes) }) return notes
        notes.forEach { File(audioDirectory, it.audioFileName!!).delete() }
        return emptyList()
    }

    /** Applies [change], writes the result, and publishes it only once it is on disk. */
    @Synchronized
    fun mutate(change: (VoiceNoteQueue) -> VoiceNoteQueue): Boolean {
        val candidate = change(_queue.value)
        if (candidate == _queue.value) return true
        if (!write(candidate)) return false
        _queue.value = candidate
        sweepUnreferencedAudio()
        return true
    }

    /**
     * A note being transcribed is not deleted under the processor, as on iOS. Synchronised with
     * [mutate], so the processor cannot claim the note between the check and the removal.
     */
    @Synchronized
    fun delete(id: UUID): Boolean {
        val note = _queue.value.note(id) ?: return false
        if (note.state is VoiceNoteState.Transcribing) return false
        return mutate { it.remove(id) }
    }

    private fun write(queue: VoiceNoteQueue): Boolean = try {
        directory.mkdirs()
        val temporary = File(directory, "queue.json.tmp")
        temporary.writeText(encode(queue))
        if (!temporary.renameTo(queueFile)) throw IOException("rename failed")
        true
    } catch (e: IOException) {
        // Counts only: the queue holds what other people said.
        Timber.w("Voice note queue write failed: notes=%d, error=%s", queue.notes.size, e.javaClass.simpleName)
        false
    }

    /** Deletes every audio copy no note refers to: leftovers of a crash between copy and add. */
    private fun sweepUnreferencedAudio() {
        val referenced = _queue.value.notes.mapNotNullTo(HashSet()) { it.audioFileName }
        audioDirectory.listFiles()?.forEach { file ->
            if (file.name !in referenced) file.delete()
        }
    }

    companion object {

        internal fun encode(queue: VoiceNoteQueue): String {
            val array = JSONArray()
            for (note in queue.notes) {
                val state = note.state
                array.put(
                    JSONObject()
                        .put("id", note.id.toString())
                        .put("receivedAt", note.receivedAt)
                        .put("audioFileName", note.audioFileName ?: JSONObject.NULL)
                        .put("format", note.format.name)
                        .put("durationSeconds", note.durationSeconds ?: JSONObject.NULL)
                        .put("state", stateName(state))
                        .put("progress", (state as? VoiceNoteState.Transcribing)?.progress ?: 0.0)
                        .put("failure", (state as? VoiceNoteState.Failed)?.failure?.name ?: JSONObject.NULL)
                        .put("transcript", note.transcript ?: JSONObject.NULL),
                )
            }
            return array.toString()
        }

        /** A file that will not decode is an empty queue rather than a crash, as on iOS. */
        internal fun decode(json: String): VoiceNoteQueue = try {
            val array = JSONArray(json)
            VoiceNoteQueue().add(
                (0 until array.length()).mapNotNull { index ->
                    val item = array.getJSONObject(index)
                    val format = SharedAudioFormat.entries.firstOrNull { it.name == item.optString("format") }
                        ?: return@mapNotNull null
                    VoiceNote(
                        id = UUID.fromString(item.getString("id")),
                        receivedAt = item.getLong("receivedAt"),
                        audioFileName = item.optStringOrNull("audioFileName"),
                        format = format,
                        durationSeconds = if (item.isNull("durationSeconds")) null else item.getInt("durationSeconds"),
                        state = when (item.optString("state")) {
                            "transcribing" -> VoiceNoteState.Transcribing(item.optDouble("progress", 0.0))
                            "done" -> VoiceNoteState.Done
                            "failed" -> VoiceNoteState.Failed(VoiceNoteFailure.fromName(item.optStringOrNull("failure")))
                            else -> VoiceNoteState.Waiting
                        },
                        transcript = item.optStringOrNull("transcript"),
                    )
                },
            )
        } catch (e: JSONException) {
            VoiceNoteQueue()
        } catch (e: IllegalArgumentException) {
            VoiceNoteQueue()
        }

        private fun read(file: File): VoiceNoteQueue =
            if (file.exists()) decode(runCatching { file.readText() }.getOrDefault("")) else VoiceNoteQueue()

        private fun stateName(state: VoiceNoteState) = when (state) {
            VoiceNoteState.Waiting -> "waiting"
            is VoiceNoteState.Transcribing -> "transcribing"
            VoiceNoteState.Done -> "done"
            is VoiceNoteState.Failed -> "failed"
        }

        private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
    }
}
