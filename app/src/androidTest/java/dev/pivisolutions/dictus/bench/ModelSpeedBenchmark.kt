package dev.pivisolutions.dictus.bench

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.asr.ParakeetProvider
import dev.pivisolutions.dictus.core.stt.SttProvider
import dev.pivisolutions.dictus.model.AiProvider
import dev.pivisolutions.dictus.model.DeviceRam
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.WhisperProvider
import dev.pivisolutions.dictus.whisper.WhisperCpuConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

/**
 * Transcription speed of every catalog model present on the device, for the Speed bars (#180).
 *
 * For each model in the app's own models dir (absent ones are logged SKIP): load it (timed
 * apart), then per clip one warm-up run and N timed runs of the same French clip, about 10 s
 * and 45 s long. Only the transcribe call is timed. The providers are the ones dictation uses,
 * called directly, so DictationService's 120 s timeout does not apply; [timeoutS] bounds each
 * run instead.
 *
 * The clips are cut from the skill's synthesized fixture fr-long-varied (about 47 s of varied
 * French speech): its leading and trailing silence is dropped, then each clip is its first 10 s
 * or 45 s, speech throughout, so silence does not flatter the real-time factor. A clip ends at
 * the last pause of at least 200 ms before that length, as a dictation does: cut mid-word,
 * Tiny invented text past the cut and took as long on 10 s as on 45 s. CLIP and RESULT give the
 * clip's real length. `-e source repeat` alternates the two short fixtures (fr-bonjour-test,
 * fr-beau-paris) instead, 150 ms apart. That repetitive text made whisper Tiny 3.5x slower on
 * 45 s on the emulator, most likely whisper's temperature fallback re-decoding low-entropy text;
 * it is kept to show that difference, not to set the bars.
 *
 * Runs in the app's process (the test APK instruments dev.pivisolutions.dictus), so it reads
 * the models the app downloaded and uses the installed app's engines: START's build= and
 * version= name that app, benchBuild= the commit this test was compiled from. Install with
 * `adb install -r` and run with `am instrument`, never `connectedAndroidTest`, which uninstalls
 * the app and its models afterwards:
 *   adb shell am instrument -w -e class dev.pivisolutions.dictus.bench.ModelSpeedBenchmark \
 *     [-e models tiny,base] [-e runs 3] [-e clips 10,45] [-e timeoutS 900] [-e language fr] \
 *     [-e source varied|repeat] \
 *     dev.pivisolutions.dictus.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Logs one key=value line per event under the tag DictusBench (START, CLIP, SKIP, LOAD, RUN,
 * RESULT, FAIL, SUMMARY). Text lengths only, never the transcript.
 */
@RunWith(AndroidJUnit4::class)
class ModelSpeedBenchmark {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val args = InstrumentationRegistry.getArguments()

    private val modelFilter: Set<String>? = args.getString("models")
        ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
    private val runs = args.getString("runs")?.toIntOrNull()?.coerceAtLeast(1) ?: 3
    private val clipSeconds = args.getString("clips")
        ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it > 0 }
        ?.takeIf { it.isNotEmpty() } ?: listOf(10, 45)
    private val timeoutS = args.getString("timeoutS")?.toLongOrNull()?.coerceAtLeast(1) ?: 900L
    private val language = args.getString("language") ?: "fr"
    private val source = args.getString("source") ?: "varied"

    @Test
    fun benchmarkCatalogModels() = runBlocking {
        val startedMs = SystemClock.elapsedRealtime()
        log(
            "START", "device" to Build.MODEL, "sdk" to Build.VERSION.SDK_INT,
            "abi" to Build.SUPPORTED_ABIS.firstOrNull(),
            "ramGb" to DeviceRam.marketedGb(DeviceRam.totalMemBytes(context)),
            "whisperThreads" to WhisperCpuConfig.preferredThreadCount,
            "build" to installedBuild(), "version" to installedVersion(),
            "benchBuild" to BuildConfig.GIT_SHA + if (BuildConfig.GIT_DIRTY) "-dirty" else "",
            "runs" to runs, "clips" to clipSeconds.joinToString(","), "timeoutS" to timeoutS,
            "language" to language, "source" to source, "models" to (modelFilter?.joinToString(",") ?: "all"),
        )

        val fixtures = when (source) {
            "varied" -> listOf("fr-long-varied.wav")
            "repeat" -> listOf("fr-bonjour-test.wav", "fr-beau-paris.wav")
            else -> error("unknown source '$source', expected varied or repeat")
        }
        val phrases = fixtures.map { trimSilence(readWav(it)) }
        val clips = clipSeconds.map { seconds ->
            val clip = buildClip("${seconds}s", seconds * 1_000L, phrases)
            log(
                "CLIP", "clip" to clip.name, "audioMs" to samplesToMs(clip.samples.size),
                "speechMs" to samplesToMs(clip.speechSamples), "source" to fixtures.joinToString("+"),
            )
            clip
        }

        val manager = ModelManager(context)
        var measured = 0
        var failed = 0
        var skipped = 0
        for (info in ModelCatalog.ALL) {
            if (modelFilter != null && info.key !in modelFilter) continue
            val path = manager.getModelPath(info.key)
            if (path == null) {
                log("SKIP", "model" to info.key, "engine" to engineOf(info), "reason" to "absent")
                skipped++
                continue
            }
            if (benchmarkModel(info, path, clips)) measured++ else failed++
        }
        modelFilter?.filter { ModelCatalog.findByKey(it) == null }?.forEach {
            log("SKIP", "model" to it, "reason" to "unknown_key")
            skipped++
        }

        log(
            "SUMMARY", "measured" to measured, "failed" to failed, "skipped" to skipped,
            "totalMs" to SystemClock.elapsedRealtime() - startedMs,
        )
        assumeTrue("no catalog model on the device", measured + failed > 0)
        assertTrue("every model present failed, see the FAIL lines", measured > 0)
    }

    /** Load, then time every clip. False after the first failure, logged as FAIL. */
    private suspend fun benchmarkModel(info: ModelInfo, path: String, clips: List<Clip>): Boolean {
        val engine = engineOf(info)
        val tags = arrayOf<Pair<String, Any?>>("model" to info.key, "engine" to engine)
        var stage = "load"
        var clipName: String? = null
        val provider: SttProvider = when (info.provider) {
            AiProvider.WHISPER -> WhisperProvider()
            AiProvider.PARAKEET -> ParakeetProvider()
        }
        try {
            val loadStart = SystemClock.elapsedRealtime()
            val loaded = provider.initialize(path)
            val loadMs = SystemClock.elapsedRealtime() - loadStart
            if (!loaded) {
                log("FAIL", *tags, "stage" to stage, "reason" to "init_returned_false", "loadMs" to loadMs)
                return false
            }
            log("LOAD", *tags, "loadMs" to loadMs)

            stage = "transcribe"
            for (clip in clips) {
                clipName = clip.name
                val audioMs = samplesToMs(clip.samples.size)
                val clipTags = arrayOf<Pair<String, Any?>>(*tags, "clip" to clip.name, "audioMs" to audioMs)
                val timings = mutableListOf<Long>()
                var textLen = 0
                for (run in 0..runs) {
                    val runStart = SystemClock.elapsedRealtime()
                    val text = withTimeoutOrNull(timeoutS * 1_000) { provider.transcribe(clip.samples, language) }
                    val ms = SystemClock.elapsedRealtime() - runStart
                    if (text == null) {
                        // A Whisper decode cannot be interrupted: release() below waits for it.
                        log("FAIL", *clipTags, "stage" to stage, "run" to run, "reason" to "timeout", "ms" to ms)
                        return false
                    }
                    textLen = text.length
                    log("RUN", *clipTags, "run" to run, "warmup" to (run == 0), "ms" to ms, "textLen" to textLen)
                    if (run > 0) timings += ms
                }
                val median = median(timings)
                log(
                    "RESULT", *clipTags, "loadMs" to loadMs, "runsMs" to timings.joinToString(","),
                    "medianMs" to median, "rtf" to "%.3f".format(Locale.US, median / audioMs),
                    "xRealtime" to "%.2f".format(Locale.US, audioMs / median), "textLen" to textLen,
                )
            }
            return true
        } catch (e: OutOfMemoryError) {
            log("FAIL", *tags, "stage" to stage, "clip" to clipName, "reason" to "OutOfMemoryError")
            return false
        } catch (e: Exception) {
            log("FAIL", *tags, "stage" to stage, "clip" to clipName, "reason" to e.javaClass.simpleName)
            return false
        } finally {
            try {
                provider.release()
            } catch (e: Exception) {
                log("FAIL", *tags, "stage" to "release", "reason" to e.javaClass.simpleName)
            }
            // Free the Java side of one model before the next one loads.
            Runtime.getRuntime().gc()
        }
    }

    private class Clip(val name: String, val samples: FloatArray, val speechSamples: Int)

    /**
     * The installed app's commit. Read by reflection: BuildConfig's fields are compile-time
     * constants, so a direct read is inlined and gives the commit the test APK was built from.
     */
    private fun installedBuild(): String = try {
        val config = Class.forName("dev.pivisolutions.dictus.BuildConfig")
        val sha = config.getField("GIT_SHA").get(null) as String
        sha + if (config.getField("GIT_DIRTY").getBoolean(null)) "-dirty" else ""
    } catch (e: ReflectiveOperationException) {
        "unknown"
    }

    private fun installedVersion(): String? =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName

    private fun engineOf(info: ModelInfo) = info.provider.name.lowercase(Locale.US)

    private fun median(values: List<Long>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun samplesToMs(samples: Int): Long = samples * 1_000L / SAMPLE_RATE

    /**
     * The phrases in turn, 150 ms apart, up to [targetMs], then cut 100 ms into the last pause
     * of at least 200 ms that starts in the final 5 s. With no such pause it stays [targetMs].
     */
    private fun buildClip(name: String, targetMs: Long, phrases: List<FloatArray>): Clip {
        val target = (targetMs * SAMPLE_RATE / 1_000).toInt()
        val gap = GAP_MS * SAMPLE_RATE / 1_000
        val out = FloatArray(target)
        val copied = mutableListOf<IntRange>()
        var at = 0
        var i = 0
        while (at < target) {
            val phrase = phrases[i++ % phrases.size]
            val n = minOf(phrase.size, target - at)
            phrase.copyInto(out, at, 0, n)
            copied += at until at + n
            at += n + gap
        }

        val frame = SAMPLE_RATE / 100
        val frames = target / frame
        var cut = target
        var quiet = 0
        for (f in frames - 1 downTo maxOf(0, frames - 500)) {
            quiet = if (isLoud(out, f * frame, frame)) 0 else quiet + 1
            // f is the first frame of a quiet run at least PAUSE_FRAMES long.
            if (quiet >= PAUSE_FRAMES && (f == 0 || isLoud(out, (f - 1) * frame, frame))) {
                cut = (f + 10) * frame
                break
            }
        }
        val speech = copied.sumOf { range -> (minOf(range.last + 1, cut) - range.first).coerceAtLeast(0) }
        return Clip(name, out.copyOf(cut), speech)
    }

    private fun isLoud(samples: FloatArray, from: Int, length: Int): Boolean {
        var sum = 0.0
        for (j in from until from + length) sum += samples[j] * samples[j]
        return sqrt(sum / length) > SILENCE_RMS
    }

    /** Drop the fixture's leading and trailing silence, keeping 50 ms around the voice. */
    private fun trimSilence(samples: FloatArray): FloatArray {
        val frame = SAMPLE_RATE / 100
        val loud = (0 until samples.size / frame).filter { f -> isLoud(samples, f * frame, frame) }
        check(loud.isNotEmpty()) { "fixture has no voice" }
        val margin = SAMPLE_RATE / 20
        val from = (loud.first() * frame - margin).coerceAtLeast(0)
        val to = ((loud.last() + 1) * frame + margin).coerceAtMost(samples.size)
        return samples.copyOfRange(from, to)
    }

    /** A 16 kHz mono 16-bit PCM WAV from the test APK's assets, to floats in [-1, 1]. */
    private fun readWav(asset: String): FloatArray {
        val bytes = testAssets.open(asset).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Walk the RIFF chunks to "data" rather than assume a 44-byte header.
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buffer.getInt(pos + 4)
            if (id == "fmt ") {
                check(buffer.getShort(pos + 10).toInt() == 1) { "$asset is not mono" }
                check(buffer.getInt(pos + 12) == SAMPLE_RATE) { "$asset is not 16 kHz" }
                check(buffer.getShort(pos + 22).toInt() == 16) { "$asset is not 16-bit" }
            }
            if (id == "data") {
                val pcm = ByteBuffer.wrap(bytes, pos + 8, minOf(size, bytes.size - pos - 8))
                    .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                return FloatArray(pcm.remaining()) { pcm.get(it) / 32_768f }
            }
            pos += 8 + size + (size and 1)
        }
        error("$asset has no data chunk")
    }

    private fun log(event: String, vararg fields: Pair<String, Any?>) {
        val line = fields.joinToString(" ", prefix = "$event ") { (key, value) ->
            "$key=${value.toString().replace(' ', '_')}"
        }
        Log.i(TAG, line)
    }

    private companion object {
        const val TAG = "DictusBench"
        const val SAMPLE_RATE = 16_000
        const val GAP_MS = 150
        // 10 ms frames: a pause a clip may end in is at least 200 ms long.
        const val PAUSE_FRAMES = 20
        const val SILENCE_RMS = 0.01
    }
}
