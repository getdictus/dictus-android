package dev.pivisolutions.dictus.debug

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * « Export JSON… » of the transcription debug screen (#295).
 *
 * Port of iOS `PolishDebugExporter` (`DictusApp/Polish/PolishDebugExporter.swift`): the same keys,
 * the same file name, one indented JSON object with sorted keys. Every key iOS can write is present;
 * those Android has no value for are null, where iOS leaves an absent optional out.
 */
object TranscriptionDebugExporter {

    /** iOS `PolishDebugExport.DeviceInfo` (`PolishDebugExporter.swift:50-59`). */
    data class Device(
        val hardware: String,
        val modelClass: String,
        val systemName: String,
        val systemVersion: String,
        val appVersion: String,
        val buildNumber: String,
    )

    /**
     * iOS `PolishDebugExport.SettingsInfo` (`PolishDebugExporter.swift:61-79`), the polish fields
     * fixed to "no polish".
     *
     * @param activeModelEngine `WK` or `PK`, as the events' `sttEngine`.
     */
    data class Settings(
        val keyboardLanguage: String,
        val transcriptionLanguageMode: String,
        val activeModelID: String?,
        val activeModelEngine: String?,
    )

    /** The export of [entries], the whole 7-day window (iOS `makeExport`, `PolishDebugExporter.swift:173-249`). */
    fun makeExport(entries: List<TranscriptionDebugEntry>, device: Device, settings: Settings, nowMs: Long): JSONObject {
        // Every iOS outcome, at 0 when absent (iOS `PolishEventTally.seededOutcomes`).
        val outcomes = JSONObject()
        TranscriptionDebugMetrics.OUTCOMES.forEach { outcomes.put(it, 0) }
        entries.forEach { outcomes.put(it.metrics.outcome, outcomes.optInt(it.metrics.outcome) + 1) }
        return JSONObject()
            .put("exportedAt", TranscriptionDebugEntry.isoTimestamp(nowMs))
            .put(
                "device",
                JSONObject()
                    .put("hardware", device.hardware)
                    .put("modelClass", device.modelClass)
                    .put("systemName", device.systemName)
                    .put("systemVersion", device.systemVersion)
                    .put("appVersion", device.appVersion)
                    .put("buildNumber", device.buildNumber),
            )
            .put(
                "settings",
                JSONObject()
                    .put("polishEnabled", false)
                    .put("keyboardLanguage", settings.keyboardLanguage)
                    .put("transcriptionLanguageMode", settings.transcriptionLanguageMode)
                    .put("activeModelID", settings.activeModelID ?: JSONObject.NULL)
                    .put("activeModelEngine", settings.activeModelEngine ?: JSONObject.NULL)
                    .put("appleFMAvailable", false)
                    .put("appleFMState", "android"),
            )
            .put("outcomes", outcomes)
            // Failures, guardrails and smart modes are polish events: none on Android.
            .put("failureReasons", JSONObject())
            .put("guardrailChecks", JSONObject())
            .put("smartModesSkippedForLength", JSONObject())
            .put("failureReasonsByWriter", JSONObject())
            .put("events", JSONArray(entries.map(::event)))
    }

    /** One entry flattened, as iOS `PolishDebugExport.Event` (`PolishDebugExporter.swift:81-167`). */
    private fun event(entry: TranscriptionDebugEntry): JSONObject {
        val metrics = entry.metrics
        val resolution = metrics.languageResolution
        return JSONObject()
            .put("id", entry.id)
            .put("timestamp", TranscriptionDebugEntry.isoTimestamp(entry.timestampMs))
            .put("writer", entry.writer ?: JSONObject.NULL)
            .put("engine", metrics.engine)
            .put("mode", JSONObject.NULL)
            .put("targetLanguage", JSONObject.NULL)
            .put("detectedLanguage", metrics.detectedLanguage ?: JSONObject.NULL)
            .put("transcriptionMode", resolution?.transcriptionMode ?: JSONObject.NULL)
            .put("keyboardLanguage", resolution?.keyboardLanguage ?: JSONObject.NULL)
            .put("sttLanguageCode", resolution?.sttLanguageCode ?: JSONObject.NULL)
            .put("sttLanguageIsEffective", resolution?.sttLanguageIsEffective ?: JSONObject.NULL)
            .put("languageMix", JSONObject.NULL)
            .put("targetSource", JSONObject.NULL)
            .put("outcome", metrics.outcome)
            .put("failureReason", JSONObject.NULL)
            .put("guardrailCheck", JSONObject.NULL)
            .put("smartModeLengthSkip", JSONObject.NULL)
            .put("latencyMs", metrics.latencyMs)
            .put("preprocessMs", metrics.timings?.preprocessMs ?: JSONObject.NULL)
            .put("engineMs", metrics.timings?.engineMs ?: JSONObject.NULL)
            .put("postprocessMs", metrics.timings?.postprocessMs ?: JSONObject.NULL)
            .put("rawCharCount", metrics.rawCharCount)
            // Only set when a custom-vocabulary pass rewrote `raw`, which Android does not run yet.
            .put("engineRawCharCount", JSONObject.NULL)
            .put("polishedCharCount", metrics.polishedCharCount)
            .put("raw", entry.raw)
            .put("vocabularyCorrected", JSONObject.NULL)
            .put("polished", JSONObject.NULL)
            .put("sttEngine", metrics.sttEngine ?: JSONObject.NULL)
            .put("sttModelID", metrics.sttModelID ?: JSONObject.NULL)
    }

    /**
     * Writes [export] to `dictus-polish-debug-yyyyMMdd-HHmmss.json` in [directory], indented with
     * sorted keys (iOS `writeToTempFile`, `PolishDebugExporter.swift:254-263`). Earlier exports in
     * [directory] are deleted, so the cache holds one at most.
     */
    fun write(export: JSONObject, directory: File, nowMs: Long): File {
        directory.listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".json") }
            ?.forEach { it.delete() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs))
        return File(directory, "$FILE_PREFIX$timestamp.json").apply {
            writeText((sortedKeys(export) as JSONObject).toString(2))
        }
    }

    /** org.json keeps insertion order: rebuild every object with its keys inserted sorted. */
    private fun sortedKeys(value: Any): Any = when (value) {
        is JSONObject -> JSONObject().also { sorted ->
            value.keys().asSequence().sorted().forEach { key -> sorted.put(key, sortedKeys(value.get(key))) }
        }
        is JSONArray -> JSONArray().also { sorted ->
            for (index in 0 until value.length()) sorted.put(sortedKeys(value.get(index)))
        }
        else -> value
    }

    private const val FILE_PREFIX = "dictus-polish-debug-"
}
