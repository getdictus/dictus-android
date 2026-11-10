package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import dev.pivisolutions.dictus.core.service.DictationSource
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stats.DictationTally
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.core.stt.SttTranscript
import dev.pivisolutions.dictus.debug.LanguageResolution
import dev.pivisolutions.dictus.debug.TranscriptionDebugEntry
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
import java.io.IOException

/** A take in a fixed language, started from the app, with the keyboard in French. */
internal fun fixed(language: String) = DictationContext(
    sttLanguage = language,
    transcriptionMode = language,
    keyboardLanguage = "fr",
    source = DictationSource.APP,
)

/** How DictationService maps a take to "no words", "failed", partial or whole text (#153, #179). */
class DictationTranscriberTest {

    private val halfSecond = FloatArray(RecordingDurationPolicy.MINIMUM_SAMPLE_COUNT)
    private val wakeLock = FakeWakeLock()
    private val lastTranscription = LastTranscriptionStore()
    // What reached the tally, as (text, audio seconds).
    private val tallied = mutableListOf<Pair<String, Int>>()
    private var tallyFails = false
    private val debugEntries = mutableListOf<TranscriptionDebugEntry>()
    private var debugLogFails = false

    private fun transcriber(
        engine: suspend (FloatArray, String) -> String = { _, _ -> "Bonjour" },
        modelPath: String? = "/models/tiny.bin",
        providerStarts: Boolean = true,
        // What a segmented engine (Whisper) has decoded when the timeout fires; null: plain engine.
        atTimeout: SttTranscript? = null,
        providerId: String = "fake",
    ) = DictationTranscriber(
        ensureModel = { modelPath },
        withProvider = { _, _, use ->
            if (providerStarts) use(FakeProvider(engine, atTimeout, providerId = providerId)) else null
        },
        wakeLock = wakeLock,
        lastTranscription = lastTranscription,
        recordTally = { text, audioSeconds ->
            if (tallyFails) throw IOException("disk full")
            tallied += text to audioSeconds
            DictationTally.Totals(words = 1, audioSeconds = audioSeconds)
        },
        recordDebugEntry = { entry ->
            if (debugLogFails) throw IOException("disk full")
            debugEntries += entry
        },
        now = { NOW_MS },
    )

    // --- Home's last transcription card (#245) ---

    @Test
    fun `a take with words fills Home's card with the text the keyboard inserts`() = runTest {
        // The service runs every dictation, keyboard ones included, through this transcriber.
        val outcome = transcriber(engine = { _, _ -> " Bonjour à tous" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Text("Bonjour à tous. "), outcome)
        assertEquals("Bonjour à tous. ", lastTranscription.text.value)
    }

    @Test
    fun `a partial take fills the card with its segments`() = runTest {
        val partial = SttTranscript(" Bonjour à tous", segmentCount = 1, complete = false)
        transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial).transcribe(halfSecond, "small", fixed("fr"))

        assertEquals("Bonjour à tous. ", lastTranscription.text.value)
    }

    @Test
    fun `no words or a failure leave the card as the start left it`() = runTest {
        transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", fixed("fr"))

        assertNull(lastTranscription.text.value)
    }

    // --- Dictation tally (#201) ---

    @Test
    fun `a take with words reaches the tally with its text and rounded audio seconds`() = runTest {
        // 2.5 s of audio rounds to 3, as iOS rounds audioDuration.
        transcriber(engine = { _, _ -> " Bonjour à tous" }).transcribe(FloatArray(40_000), "tiny", fixed("fr"))

        assertEquals(listOf("Bonjour à tous. " to 3), tallied)
    }

    @Test
    fun `a partial take reaches the tally, since its text is inserted`() = runTest {
        val partial = SttTranscript(" Bonjour à tous", segmentCount = 1, complete = false)
        transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial).transcribe(halfSecond, "small", fixed("fr"))

        assertEquals(listOf("Bonjour à tous. " to 1), tallied)
    }

    @Test
    fun `no words or a failure leave the tally untouched`() = runTest {
        transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(engine = { _, _ -> throw IllegalStateException("boom") }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertTrue(tallied.isEmpty())
    }

    @Test
    fun `a tally that cannot be written does not fail the dictation`() = runTest {
        tallyFails = true

        val outcome = transcriber(engine = { _, _ -> " Bonjour" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcome)
    }

    // --- Transcription debug log (#295) ---

    @Test
    fun `every take the engine returned is logged with the language that reached the engine`() = runTest {
        // (stored mode, keyboard, code handed to the engine) → iOS transcriptionMode.
        val modes = listOf(
            Triple(TranscriptionLanguage.FOLLOW, "de", "de") to "followKeyboard",
            Triple("auto", "de", "auto") to "autoDetect",
            Triple("fr", "de", "fr") to "explicit(fr)",
        )
        // provider → (iOS sttEngine, whether the engine honours the code).
        val engines = mapOf("whisper" to ("WK" to true), "parakeet" to ("PK" to false))
        for ((providerId, engine) in engines) {
            for ((mode, description) in modes) {
                debugEntries.clear()
                val (stored, keyboard, sttLanguage) = mode
                var heard: String? = null
                transcriber(engine = { _, language -> heard = language; " Hallo" }, providerId = providerId)
                    .transcribe(halfSecond, "tiny", DictationContext(sttLanguage, stored, keyboard, DictationSource.KEYBOARD))

                val case = "$providerId $stored"
                assertEquals(case, sttLanguage, heard)
                val entry = debugEntries.single()
                assertEquals(case, "<KBD>", entry.writer)
                assertEquals(case, engine.first, entry.metrics.sttEngine)
                assertEquals(
                    case,
                    LanguageResolution(description, keyboard, sttLanguage, sttLanguageIsEffective = engine.second),
                    entry.metrics.languageResolution,
                )
            }
        }
    }

    @Test
    fun `the logged entry is the iOS polish-off event for the engine's own text`() = runTest {
        transcriber(engine = { _, _ -> " Bonjour à tous" }).transcribe(halfSecond, "small-q5_1", fixed("fr"))

        val entry = debugEntries.single()
        assertEquals(" Bonjour à tous", entry.raw)
        assertEquals("<APP>", entry.writer)
        assertEquals(NOW_MS, entry.timestampMs)
        val metrics = entry.metrics
        assertEquals("skippedToggleOff", metrics.outcome)
        assertEquals("none", metrics.engine)
        assertEquals("small-q5_1", metrics.sttModelID)
        assertNull(metrics.detectedLanguage)
        assertEquals(15, metrics.rawCharCount)
        assertEquals(15, metrics.polishedCharCount)
        val timings = checkNotNull(metrics.timings)
        assertEquals(timings.preprocessMs + timings.engineMs + timings.postprocessMs, metrics.latencyMs)
    }

    @Test
    fun `a take without words is logged, a failure is not`() = runTest {
        transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "tiny", fixed("fr"))
        assertEquals(" ...", debugEntries.single().raw)

        debugEntries.clear()
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(providerStarts = false).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(engine = { _, _ -> throw IllegalStateException("boom") }).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "small", fixed("fr"))
        assertTrue(debugEntries.isEmpty())
    }

    @Test
    fun `a debug log that cannot be written does not fail the dictation`() = runTest {
        debugLogFails = true

        val outcome = transcriber(engine = { _, _ -> " Bonjour" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcome)
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
        val outcome = transcriber(engine = { _, _ -> "" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `blank engine text is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " \n\t " }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `a Whisper music tag alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " [Musique]." }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `an ellipsis alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " ..." }).transcribe(halfSecond, "small-q5_1", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `a Parakeet result made of tags is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> "(silence) [BLANK_AUDIO]" })
            .transcribe(halfSecond, "parakeet-tdt-0.6b-v3", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    @Test
    fun `an asterisk marker alone is no words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " *Bruit de chute*" }).transcribe(halfSecond, "small-q5_1", fixed("fr"))

        assertEquals(TranscriptionOutcome.NoWordsDetected, outcome)
    }

    // --- Failed ---

    @Test
    fun `missing model is failed`() = runTest {
        val outcome = transcriber(modelPath = null).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `provider that cannot start is failed`() = runTest {
        val outcome = transcriber(providerStarts = false).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout is failed`() = runTest {
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout before any segment is failed`() = runTest {
        // Whisper returns null when the abort left no segment.
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = null)
            .transcribe(halfSecond, "small", fixed("fr"))

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test
    fun `timeout after some segments is partial with their joined text`() = runTest {
        val partial = SttTranscript(" Bonjour à tous. Voici la suite", segmentCount = 2, complete = false)
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial)
            .transcribe(halfSecond, "small", fixed("fr"))

        assertEquals(TranscriptionOutcome.Partial("Bonjour à tous. Voici la suite. "), outcome)
    }

    @Test
    fun `partial segments without a word are failed, not no words`() = runTest {
        val partial = SttTranscript(" [Musique]", segmentCount = 1, complete = false)
        val outcome = transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial)
            .transcribe(halfSecond, "small", fixed("fr"))

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
            recordTally = { _, _ -> null },
            recordDebugEntry = {},
        ).transcribe(threeMinutes, "small", fixed("fr"))

        assertEquals(324_000L, seenTimeoutMs)
    }

    @Test
    fun `engine exception is failed`() = runTest {
        val outcome = transcriber(engine = { _, _ -> throw IllegalStateException("native crash") })
            .transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Failed, outcome)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not swallowed as a failure`() = runTest {
        transcriber(engine = { _, _ -> throw CancellationException("left the screen") })
            .transcribe(halfSecond, "tiny", fixed("fr"))
    }

    // --- Text ---

    @Test
    fun `engine text is post-processed and returned`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " Bonjour " }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Text("Bonjour. "), outcome)
    }

    @Test
    fun `tags are dropped from text with words`() = runTest {
        val outcome = transcriber(engine = { _, _ -> " Bonjour [Musique] ça va" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(TranscriptionOutcome.Text("Bonjour ça va. "), outcome)
    }

    @Test
    fun `the engine gets the samples and language it was given`() = runTest {
        var seen: Pair<Int, String>? = null
        transcriber(engine = { samples, language -> seen = samples.size to language; "Ok" })
            .transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(halfSecond.size to "fr", seen)
    }

    // --- Wake lock (#225) ---

    // A half-second take gets the 120 s floor, plus the 60 s an aborted Whisper decode gets to
    // return its segments.
    private val heldThenReleased = listOf("acquire(180000)", "release")

    @Test
    fun `the wake lock is held while the engine runs, capped at the timeout plus the abort grace`() = runTest {
        var heldDuringEngine = false
        transcriber(engine = { _, _ -> heldDuringEngine = wakeLock.held; "Ok" }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertTrue("the engine ran without the wake lock", heldDuringEngine)
        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released after an engine exception`() = runTest {
        transcriber(engine = { _, _ -> throw IllegalStateException("native crash") }).transcribe(halfSecond, "tiny", fixed("fr"))

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released when the transcription is cancelled`() = runTest {
        try {
            transcriber(engine = { _, _ -> throw CancellationException("left the screen") })
                .transcribe(halfSecond, "tiny", fixed("fr"))
        } catch (expected: CancellationException) {
        }

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released when the caller gives up mid-decode`() = runTest {
        // The service scope is cancelled (service destroyed) while the engine runs.
        val result = withTimeoutOrNull(1_000L) {
            transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "tiny", fixed("fr"))
        }

        assertNull(result)
        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released at a timeout with no segment`() = runTest {
        transcriber(engine = { _, _ -> awaitCancellation() }).transcribe(halfSecond, "small", fixed("fr"))

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `the wake lock is released at a timeout with some segments`() = runTest {
        val partial = SttTranscript(" Bonjour", segmentCount = 1, complete = false)
        transcriber(engine = { _, _ -> awaitCancellation() }, atTimeout = partial).transcribe(halfSecond, "small", fixed("fr"))

        assertEquals(heldThenReleased, wakeLock.events)
    }

    @Test
    fun `no wake lock when the model is missing or the provider cannot start`() = runTest {
        transcriber(modelPath = null).transcribe(halfSecond, "tiny", fixed("fr"))
        transcriber(providerStarts = false).transcribe(halfSecond, "tiny", fixed("fr"))

        assertFalse(wakeLock.held)
        assertEquals(emptyList<String>(), wakeLock.events)
    }

    private class FakeProvider(
        private val engine: suspend (FloatArray, String) -> String,
        private val atTimeout: SttTranscript?,
        override val providerId: String = "fake",
        private val onTimeoutMs: (Long) -> Unit = {},
    ) : SttProvider {
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

    private companion object {
        const val NOW_MS = 1_793_000_000_000L
    }
}
