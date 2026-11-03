package dev.pivisolutions.dictus.debug

import dev.pivisolutions.dictus.core.preferences.TranscriptionLanguage
import org.json.JSONObject
import java.time.Instant

/**
 * One finished dictation in the transcription debug log (#295).
 *
 * Port of iOS `PolishDebugEntry` (dictus-ios `DictusCore/Sources/DictusCore/Polish/PolishEventStore.swift:11-70`)
 * and its `PolishMetrics` (`PolishMetrics.swift`), under the iOS JSON keys, so the exports of both
 * platforms share one schema. Android has no polish: every entry is the event iOS writes with the
 * polish toggle off (`skippedToggleOff`, `PolishService.swift:1057-1075`), and the keys only the polish
 * or the smart modes fill are written as null (see [toJson]).
 *
 * @param raw the speech engine's own output, before any cleanup (iOS `raw`).
 * @param writer `<KBD>` or `<APP>`: the surface that started the dictation (iOS `writer`).
 */
data class TranscriptionDebugEntry(
    val id: String,
    val timestampMs: Long,
    val raw: String,
    val writer: String?,
    val metrics: TranscriptionDebugMetrics,
) {

    /** One JSONL line of the log; the iOS-only keys are null. */
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("timestamp", isoTimestamp(timestampMs))
        .put("raw", raw)
        .put("vocabularyCorrected", JSONObject.NULL)
        .put("polished", JSONObject.NULL)
        .put("writer", writer ?: JSONObject.NULL)
        .put("metrics", metrics.toJson())

    companion object {
        /** Seconds, UTC, as iOS `JSONEncoder.dateEncodingStrategy = .iso8601` writes them. */
        fun isoTimestamp(epochMs: Long): String = Instant.ofEpochSecond(Math.floorDiv(epochMs, 1_000L)).toString()

        /** Null for a line that does not decode, which the reader skips, as iOS `decodeAll` does. */
        fun fromJson(json: JSONObject): TranscriptionDebugEntry? = runCatching {
            TranscriptionDebugEntry(
                id = json.getString("id"),
                timestampMs = Instant.parse(json.getString("timestamp")).toEpochMilli(),
                raw = json.getString("raw"),
                writer = json.stringOrNull("writer"),
                metrics = TranscriptionDebugMetrics.fromJson(json.getJSONObject("metrics")),
            )
        }.getOrNull()
    }
}

/**
 * iOS `PolishMetrics`, the fields Android fills.
 *
 * @param engine the polish engine: always [NO_POLISH_ENGINE] on Android.
 * @param latencyMs the whole transcription, model load included: [timings] summed. On iOS this is the
 *   polish's time, 0 on a `skippedToggleOff` event; the issue asks for the transcription's here.
 * @param sttEngine iOS `SpeechEngine` codes by engine family: `WK` for Whisper (whisper.cpp here,
 *   WhisperKit on iOS), `PK` for Parakeet.
 */
data class TranscriptionDebugMetrics(
    val engine: String = NO_POLISH_ENGINE,
    val outcome: String = OUTCOME_SKIPPED_TOGGLE_OFF,
    val detectedLanguage: String?,
    val languageResolution: LanguageResolution?,
    val rawCharCount: Int,
    val polishedCharCount: Int,
    val latencyMs: Int,
    val sttEngine: String?,
    val sttModelID: String?,
    val timings: Timings?,
) {

    fun toJson(): JSONObject = JSONObject()
        .put("engine", engine)
        .put("mode", JSONObject.NULL)
        .put("targetLanguage", JSONObject.NULL)
        .put("detectedLanguage", detectedLanguage ?: JSONObject.NULL)
        .put("languageResolution", languageResolution?.toJson() ?: JSONObject.NULL)
        .put("rawCharCount", rawCharCount)
        .put("polishedCharCount", polishedCharCount)
        .put("latencyMs", latencyMs)
        .put("outcome", outcome)
        .put("sttEngine", sttEngine ?: JSONObject.NULL)
        .put("sttModelID", sttModelID ?: JSONObject.NULL)
        .put("timings", timings?.toJson() ?: JSONObject.NULL)
        .put("failureReason", JSONObject.NULL)
        .put("guardrailCheck", JSONObject.NULL)
        .put("smartModeLengthSkip", JSONObject.NULL)

    companion object {
        /** No polish engine runs on Android. */
        const val NO_POLISH_ENGINE = "none"

        /** iOS `PolishMetrics.Outcome.skippedToggleOff`: no polish was attempted (#747). */
        const val OUTCOME_SKIPPED_TOGGLE_OFF = "skippedToggleOff"

        /** iOS `SpeechEngine` code of an `SttProvider.providerId`: `WK` for Whisper, `PK` for Parakeet. */
        fun sttEngineCode(providerId: String): String = when (providerId) {
            "whisper" -> "WK"
            "parakeet" -> "PK"
            else -> providerId
        }

        /** iOS `PolishMetrics.Outcome`, every case, in iOS display order (`PolishDebugView.swift:381-385`). */
        val OUTCOMES = listOf(
            "success", "rejectedGuardrail", "skipped", "skippedShort", "skippedAutoMode",
            "cancelled", "engineFailed", "engineUnavailable", "exceededContextBudget",
            "unsupportedInputLanguage", "smartModeSkippedShortInput", OUTCOME_SKIPPED_TOGGLE_OFF,
        )

        fun fromJson(json: JSONObject) = TranscriptionDebugMetrics(
            engine = json.getString("engine"),
            outcome = json.getString("outcome"),
            detectedLanguage = json.stringOrNull("detectedLanguage"),
            languageResolution = json.optJSONObject("languageResolution")?.let(LanguageResolution::fromJson),
            rawCharCount = json.getInt("rawCharCount"),
            polishedCharCount = json.getInt("polishedCharCount"),
            latencyMs = json.getInt("latencyMs"),
            sttEngine = json.stringOrNull("sttEngine"),
            sttModelID = json.stringOrNull("sttModelID"),
            timings = json.optJSONObject("timings")?.let(Timings::fromJson),
        )
    }
}

/**
 * iOS `PolishMetrics.LanguageResolution` (`PolishMetrics.swift:157-197`) without `languageMix` and
 * `targetSource`, which describe the polish target.
 *
 * @param transcriptionMode `followKeyboard`, `autoDetect` or `explicit(<code>)`, iOS `telemetryDescription`.
 * @param sttLanguageCode the code handed to the engine, or `auto`.
 * @param sttLanguageIsEffective false for Parakeet, which ignores the code.
 */
data class LanguageResolution(
    val transcriptionMode: String,
    val keyboardLanguage: String,
    val sttLanguageCode: String,
    val sttLanguageIsEffective: Boolean,
) {

    fun toJson(): JSONObject = JSONObject()
        .put("transcriptionMode", transcriptionMode)
        .put("keyboardLanguage", keyboardLanguage)
        .put("sttLanguageCode", sttLanguageCode)
        .put("sttLanguageIsEffective", sttLanguageIsEffective)
        .put("languageMix", JSONObject.NULL)
        .put("targetSource", JSONObject.NULL)

    companion object {
        /** iOS `TranscriptionLanguageMode.telemetryDescription` of a resolved stored mode. */
        fun transcriptionModeDescription(mode: String): String = when (mode) {
            TranscriptionLanguage.FOLLOW -> "followKeyboard"
            TranscriptionLanguage.AUTO -> "autoDetect"
            else -> "explicit($mode)"
        }

        fun fromJson(json: JSONObject) = LanguageResolution(
            transcriptionMode = json.getString("transcriptionMode"),
            keyboardLanguage = json.getString("keyboardLanguage"),
            sttLanguageCode = json.getString("sttLanguageCode"),
            sttLanguageIsEffective = json.getBoolean("sttLanguageIsEffective"),
        )
    }
}

/**
 * iOS `PolishTimings` (`PolishMetrics.swift:13-26`), measured on the transcription.
 *
 * @param preprocessMs model download check and engine load, up to the engine call.
 * @param engineMs the engine call.
 * @param postprocessMs non-speech tags and punctuation cleanup.
 */
data class Timings(val preprocessMs: Int, val engineMs: Int, val postprocessMs: Int) {

    fun toJson(): JSONObject = JSONObject()
        .put("preprocessMs", preprocessMs)
        .put("engineMs", engineMs)
        .put("postprocessMs", postprocessMs)

    companion object {
        fun fromJson(json: JSONObject) = Timings(
            preprocessMs = json.getInt("preprocessMs"),
            engineMs = json.getInt("engineMs"),
            postprocessMs = json.getInt("postprocessMs"),
        )
    }
}

/** org.json returns "null" from optString for a JSON null. */
internal fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
