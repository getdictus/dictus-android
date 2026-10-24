package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.stt.SttTranscript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How DictationService maps a take to "no words", "failed", partial or whole text (#153, #179). */
class DictationTranscriberTest {

    private val halfSecond = FloatArray(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT)

    private fun transcriber(
        engine: suspend (FloatArray, String) -> String = { _, _ -> "Bonjour" },
        modelPath: String? = "/models/tiny.bin",
        providerStarts: Boolean = true,
        // What a segmented engine (Whisper) has decoded when the timeout fires; null: plain engine.
        atTimeout: SttTranscript? = null,
    ) = DictationTranscriber(
        ensureModel = { modelPath },
        withProvider = { _, _, use -> if (providerStarts) use(FakeProvider(engine, atTimeout)) else null },
    )

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
            return super.transcribe(samples, language, timeoutMs) ?: atTimeout
        }
        override suspend fun release() = Unit
    }
}
