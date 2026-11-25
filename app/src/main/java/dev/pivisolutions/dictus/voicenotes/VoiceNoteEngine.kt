package dev.pivisolutions.dictus.voicenotes

/**
 * The dictation engine as the voice note queue sees it (#204). An interface so the processor's
 * rules can be tested without a service or a model.
 */
interface VoiceNoteEngine {

    /** Suspends while a dictation runs: a voice note never starts a chunk under one, as on iOS. */
    suspend fun awaitNoDictation()

    /** The chunk's spoken text, empty for silence, or null when the engine could not transcribe it. */
    suspend fun transcribeChunk(samples: FloatArray): String?
}
