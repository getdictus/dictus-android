package dev.pivisolutions.dictus.service

import androidx.test.core.app.ApplicationProvider
import dev.pivisolutions.dictus.core.logging.DictationTrace
import dev.pivisolutions.dictus.core.logging.LogCategory
import dev.pivisolutions.dictus.core.logging.PrivacySafeLog
import dev.pivisolutions.dictus.core.logging.TimberSetup
import dev.pivisolutions.dictus.core.service.TranscriptionOutcome
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.ui.settings.LogExporter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * A known transcript goes through the logged pipeline (engine call, post-processing, insertion)
 * and the export (both files, after a rotation), and only its lengths come out the other side (#112).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DictationLogPrivacyTest {

    private val context: android.app.Application = ApplicationProvider.getApplicationContext()
    private val transcript = "Zephyrine quokka marmelade"
    private val transcriptWords = transcript.split(' ')

    @Before
    fun setUp() {
        Timber.uprootAll()
        File(context.filesDir, "dictus.log").delete()
        TimberSetup.init(isDebug = false, filesDir = context.filesDir)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        DictationTrace.end()
    }

    @Test
    fun `exported log of a full dictation holds the transcript length and none of its words`() = runTest {
        val id = DictationTrace.begin(source = "keyboard")
        DictationTrace.onRecordingRequested(fallbackSource = "app")
        val transcriber = DictationTranscriber(
            ensureModel = { "/models/tiny.bin" },
            withProvider = { _, _, use -> use(EchoProvider(transcript)) },
            wakeLock = FakeWakeLock(),
        )

        val outcome = transcriber.transcribe(FloatArray(16_000), "tiny", "fr")
        DictationTrace.end()
        // Rotate by hand (the real cap is 2 MB) so the engine lines sit in the previous file.
        val logFile = TimberSetup.getLogFile()!!
        assertTrue(logFile.renameTo(TimberSetup.getPreviousLogFile()!!))
        val inserted = (outcome as TranscriptionOutcome.Text).text
        Timber.tag(LogCategory.INSERT.name).i(
            PrivacySafeLog.dictationInserted(inserted, true, DictationTrace.lastId(), DictationTrace.elapsedMs()),
        )

        val exported = export()

        transcriptWords.forEach { word ->
            assertFalse("exported log leaks a transcript word", exported.contains(word, ignoreCase = true))
        }
        assertTrue(exported, exported.contains("rawLength=${transcript.length}"))
        assertTrue(exported, exported.contains("[INSERT] DictationLogPrivacyTest: Dictation inserted: d=$id, length=${inserted.length}"))
        assertTrue(exported, exported.contains("[STT] DictationTranscriber d=$id: Transcription finished: engine=echo"))
    }

    private fun export(): String {
        val zip = LogExporter.createZip(
            context,
            TimberSetup.getLogFile()!!,
            LogExporter.DictationSettings(activeModel = "tiny", language = "fr"),
        )!!
        return ZipFile(zip).use { archive ->
            archive.entries().toList().joinToString("") { entry ->
                archive.getInputStream(entry).bufferedReader().readText()
            }
        }
    }

    private class EchoProvider(private val text: String) : SttProvider {
        override val providerId = "echo"
        override val displayName = "Echo"
        override val supportedLanguages = listOf("fr")
        override val isReady = true
        override suspend fun initialize(modelPath: String) = true
        override suspend fun transcribe(samples: FloatArray, language: String) = text
        override suspend fun release() = Unit
    }
}
