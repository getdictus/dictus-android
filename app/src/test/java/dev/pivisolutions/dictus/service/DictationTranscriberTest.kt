package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.stt.SttTranscript
import dev.pivisolutions.dictus.recording.LastTranscriptionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How DictationService maps a take to "no words", "failed", partial or whole text (#153, #179). */
class DictationTranscriberTest {

    private val halfSecond = FloatArray(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT)
    private val wakeLock = FakeWakeLock()
    private val lastTranscription = LastTranscriptionStore()

    private fun transcriber(
        engine: suspend (FloatArray, String) -> String = { _, _ -> "Bonjour" },
        modelPath: String? = "/models/tiny.bin",
        providerStarts: Boolean = true,
        // What a segmented engine (Whisper) has decoded when the timeout fires; null: plain engine.
        atTimeout: SttTranscript? = null,
    ) = DictationTranscriber(
        ensureModel = { modelPath },
        withProvider = { _, _, use -> if (providerStarts) use(FakeProvider(engine, atTimeout)) else null },
        wakeLock = wakeLock,
        lastTranscription = lastTranscription,
    )

    // --- Home's last transcription card (#245) ---

    @Test
    fun `a take with words fills Home's card with the text the keyboard inserts`() = runTest {
        // The service runs every dictation, keyboard ones included, through this transcriber.
        val outcome = transcriber(engine = { _, _ -> " Bonjour à tous" }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Text("Bonjour à tous. "), outcome)
        assertEquals("Bonjour à tous. ", lastTranscription.text.value)
    }

    @Test
    fun `a partial take fills the card with its segments`() = runTest {
        val partial = SttTranscript(" Bonjour à tous", segmentCount = 1, complete = false)
        transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial).transcribe(halfSecond, "small", "fr")

        assertEquals("Bonjour à tous. ", lastTranscription.text.value)
    }

    @Test
    fun `no words or a failure leave the card as the start left it`() = runTest {
        transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "tiny", "fr")
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", "fr")

        assertNull(lastTranscription.text.value)
    }

    // --- No words ---

    @Test
    fun `no audio is no words`() {
        assertEquals(TranscriptionOutcome.NoWordsDetected, transcriber().rejectBeforeEngine(FloatArray(0)))
    }

    @Test
    fun `a take under half a second is no words`() {
        val short = FloatArray(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT - 1)

        assertEquals(TranscriptionOutcome.NoWordsDetected, transcriber().rejectBeforeEngine(short))
    }

    @Test
    fun `a take of half a second goes to the engine`() {
        assertNull(transcriber().rejectBeforeEngine(halfSecond))
    }

    @Test
    fun `empty engine text is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> "" }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `blank engine text is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " \n\t " }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `a Whisper music tag alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " [Musique]." }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `an ellipsis alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "small-q5_1", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `a Parakeet result made of tags is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> "(silence) [BLANK_AUDIO]" })
            .transcribe(halfSecond, "parakeet-tdt-0.6b-v3", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `an asterisk marker alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " *Bruit de chute*" }).transcribe(halfSecond, "small-q5_1", "fr")

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    // --- Failed ---

    @Test
    fun `missing model is failed`() = runTest {
        val outcome = transcriber(modelPath = null).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `provider that cannot start is failed`() = runTest {
        val outcome = transcriber(providerStarts = false).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout is failed`() = runTest {
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout before any segment is failed`() = runTest {
        // Whisper returns null when the abort left no segment.
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = null)
            .transcribe(halfSecond, "small", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout after some segments is partial with their joined text`() = runTest {
        val partial = SttTranscript(" Bonjour à tous. Voici la suite", segmentCount = 2, complete = false)
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial)
            .transcribe(halfSecond, "small", "fr")

        assertEquals(TranscriptionOutcome.Partial("Bonjour à tous. Voici la suite. "), outcome)
    }

    @Test
    fun `partial segments without a word are failed, not no words`() = runTest {
        val partial = SttTranscript(" [Musique]", segmentCount = 1, complete = false)
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial)
            .transcribe(halfSecond, "small", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `the engine gets the model's proportional timeout`() = runTest {
        var seenTimeoutMs: Long? = null
        val threeMinutes = FloatArray(180 * RecordingDurationPolicy.SAMPLE_RATE_HZ)
        DictationTranscriber(
            ensureModel = { "/models/small.bin" },
            withProvider = { _, _, use ->
                use(FakeProvider({ _, _ -> "Ok" }, null) { timeoutMs -> seenTimeoutMs = timeoutMs })
            },
            wakeLock = wakeLock,
            lastTranscription = lastTranscription,
        ).transcribe(threeMinutes, "small", "fr")

        assertEquals(324_000L, seenTimeoutMs)
    }

    @Test
    fun `engine exception is failed`() = runTest {
        val outcome = transcriber(engine = { _, _ -> throw IllegalStateException("native crash") })
            .transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not swallowed as a failure`() = runTest {
        transcriber(engine = { _, _ -> throw CancellationException("left the screen") })
            .transcribe(halfSecond, "tiny", "fr")
    }

    // --- Text ---

    @Test
    fun `engine text is post-processed and returned`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " Bonjour " }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcome)
    }

    @Test
    fun `tags are dropped from text with words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " Bonjour [Musique] ça va" }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(TranscriptionOutcome.Text("Bonjour ça va. "), outcome)
    }

    @Test
    fun `the engine gets the samples and language it was given`() = runTest {
        var seen: Pair<Int, String>? = null
        transcriber(engine = { samples, language -> seen = samples.size to language; "Ok" })
            .transcribe(halfSecond, "tiny", "fr")

        assertEquals(halfSecond.size to "fr", seen)
    }

    // --- Wake lock (#225) ---

    // A half-second take gets the 120 s floor, plus the 60 s an aborted Whisper decode gets to
    // return its segments.
    private val heldThenReleased = listOf("acquire(180000)", "release")

    @Test
    fun `the wake lock is held while the engine runs, capped at the timeout plus the abort grace`() = runTest {
        var heldDuringEngine = false
        transcriber(engine = { _, _ -> heldDuringEngine = wakeLock.held; "Ok" }).transcribe(halfSecond, "tiny", "fr")

        assertTrue("the engine ran without the wake lock", heldDuringEngine)
        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released after an engine exception`() = runTest {
        transcriber(engine = { _, _ -> throw IllegalStateException("native crash") }).transcribe(halfSecond, "tiny", "fr")

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released when the transcription is cancelled`() = runTest {
        try {
            transcriber(engine = { _, _ -> throw CancellationException("left the screen") })
                .transcribe(halfSecond, "tiny", "fr")
        } catch (expected: CancellationException) {
        }

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released when the caller gives up mid-decode`() = runTest {
        // The service scope is cancelled (service destroyed) while the engine runs.
        val result = withTimeoutOrNull(1_000L) {
            transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "tiny", "fr")
        }

        assertNull(result)
        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released at a timeout with no segment`() = runTest {
        transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "small", "fr")

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released at a timeout with some segments`() = runTest {
        val partial = SttTranscript(" Bonjour", segmentCount = 1, complete = false)
        transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial).transcribe(halfSecond, "small", "fr")

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `no wake lock when the model is missing or the provider cannot start`() = runTest {
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", "fr")
        transcriber(providerStarts = false).transcribe(halfSecond, "tiny", "fr")

        assertFalse(wakeLock.held)
        assertEquals(emptyList<String>(), wakeLock.events)
    }

    private class FakeProvider(
        private val engine: suspend (FloatArray, String) -> String,
        private val atTimeout: SttTranscript?,
        private val onTimeoutMs: (Long) -> Unit = {},
    ) : SttProvider {
        override val providerId = "fake"
        override val displayName = "Fake"
        override val supportedLanguages = listOf("fr")
        override val isReady = true
        override suspend fun initialize(modelPath: String) = true
        override suspend fun transcribe(samples: FloatArray, language: String) = engine(samples, language)
        override suspend fun transcribe(samples: FloatArray, language: String, timeoutMs: Long): SttTranscript? {
            onTimeoutMs(timeoutMs)
            // Virtual-time timeout: the default's elapsedRealtime clock needs a device.
            return withTimeoutOrNull(timeoutMs) { engine(samples, language) }
                ?.let { text -> SttTranscript(text, segmentCount = null, complete = true) }
                ?: atTimeout
        }
        override suspend fun release() = Unit
    }
}
