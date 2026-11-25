package dev.pivisolutions.dictus.core.voicenotes

import java.util.UUID

/** Why a note was not transcribed. Port of iOS `VoiceNoteFailure` (#204). */
enum class VoiceNoteFailure {
    UNSUPPORTED_FORMAT,
    UNREADABLE,
    TOO_LONG,
    NO_SPEECH,
    TRANSCRIPTION_FAILED,
    ;

    /** Only an engine failure may succeed on a second try; the audio is kept for it. */
    val isRetryable: Boolean get() = this == TRANSCRIPTION_FAILED

    companion object {
        /** An unknown name, from a later version, reads as the retryable failure, as on iOS. */
        fun fromName(name: String?): VoiceNoteFailure =
            entries.firstOrNull { it.name == name } ?: TRANSCRIPTION_FAILED
    }
}

/** Where a note is in the queue. Port of iOS `VoiceNoteState`. */
sealed interface VoiceNoteState {
    data object Waiting : VoiceNoteState
    data class Transcribing(val progress: Double) : VoiceNoteState
    data object Done : VoiceNoteState
    data class Failed(val failure: VoiceNoteFailure) : VoiceNoteState

    val isFinished: Boolean get() = this is Done || this is Failed
}

/**
 * One shared audio file and what became of it. Port of iOS `VoiceNote`, without the Smart Mode
 * summary (#627), which Android has no engine for.
 *
 * @param audioFileName the copy in the app's storage, dropped once the note no longer needs it.
 */
data class VoiceNote(
    val id: UUID = UUID.randomUUID(),
    val receivedAt: Long,
    val audioFileName: String?,
    val format: SharedAudioFormat,
    val durationSeconds: Int? = null,
    val state: VoiceNoteState = VoiceNoteState.Waiting,
    val transcript: String? = null,
)

/**
 * The notes, newest first, and every move between states. Port of iOS `VoiceNoteQueue`: a value,
 * so each rule is a pure function a test can hold it to; `VoiceNoteQueueStore` persists it.
 */
data class VoiceNoteQueue(val notes: List<VoiceNote> = emptyList()) {

    fun note(id: UUID): VoiceNote? = notes.firstOrNull { it.id == id }

    /** The oldest waiting note: notes are transcribed in the order they arrived. */
    val next: VoiceNote? get() = notes.lastOrNull { it.state == VoiceNoteState.Waiting }

    val hasPendingWork: Boolean get() = notes.any { !it.state.isFinished }

    fun add(newNotes: List<VoiceNote>): VoiceNoteQueue {
        val known = notes.mapTo(HashSet()) { it.id }
        return copy(notes = sorted(notes + newNotes.filter { it.id !in known }))
    }

    fun update(id: UUID, change: (VoiceNote) -> VoiceNote): VoiceNoteQueue =
        copy(notes = notes.map { if (it.id == id) change(it) else it })

    fun remove(id: UUID): VoiceNoteQueue = copy(notes = notes.filter { it.id != id })

    /** A note left transcribing by a process that died goes back to waiting. */
    fun recoverInterrupted(): VoiceNoteQueue = copy(
        notes = notes.map { if (it.state is VoiceNoteState.Transcribing) it.copy(state = VoiceNoteState.Waiting) else it },
    )

    /** The transcript is in: the audio is no longer needed. */
    fun complete(id: UUID, transcript: String): VoiceNoteQueue =
        update(id) { it.copy(state = VoiceNoteState.Done, transcript = transcript, audioFileName = null) }.trimFinished()

    /** A failure keeps the audio only when a retry could use it. */
    fun fail(id: UUID, failure: VoiceNoteFailure): VoiceNoteQueue = update(id) {
        it.copy(
            state = VoiceNoteState.Failed(failure),
            audioFileName = if (failure.isRetryable) it.audioFileName else null,
        )
    }.trimFinished()

    /** Back to waiting, when the failure allows it and the audio is still there. */
    fun retry(id: UUID): VoiceNoteQueue = update(id) {
        val state = it.state
        if (state is VoiceNoteState.Failed && state.failure.isRetryable && it.audioFileName != null) {
            it.copy(state = VoiceNoteState.Waiting)
        } else {
            it
        }
    }

    /** Keeps the [MAX_FINISHED] newest finished notes; pending ones are never dropped. */
    internal fun trimFinished(): VoiceNoteQueue {
        val dropped = notes.filter { it.state.isFinished }.drop(MAX_FINISHED).mapTo(HashSet()) { it.id }
        return if (dropped.isEmpty()) this else copy(notes = notes.filter { it.id !in dropped })
    }

    companion object {
        /** iOS keeps the same number of results when History does not take them. */
        const val MAX_FINISHED = 20

        private fun sorted(notes: List<VoiceNote>) = notes.sortedByDescending { it.receivedAt }
    }
}
