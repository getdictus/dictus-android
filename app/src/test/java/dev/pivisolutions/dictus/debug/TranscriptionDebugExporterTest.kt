package dev.pivisolutions.dictus.debug

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * « Export JSON… » (#295) parses with the keys of iOS `PolishDebugExport`
 * (dictus-ios `DictusApp/Polish/PolishDebugExporter.swift:17-167`), so both platforms' exports compare.
 */
@RunWith(RobolectricTestRunner::class)
class TranscriptionDebugExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val nowMs = 1_793_000_000_000L

    private val device = TranscriptionDebugExporter.Device("flame", "Google Pixel 4", "Android", "13", "1.2.0", "42")
    private val settings = TranscriptionDebugExporter.Settings(
        keyboardLanguage = "de",
        transcriptionLanguageMode = "followKeyboard",
        activeModelID = "tiny",
        activeModelEngine = "WK",
    )
    private val entry = TranscriptionDebugEntry(
        id = "6F9619FF-8B86-D011-B42D-00C04FC964FF",
        timestampMs = 1_792_999_990_000L,
        raw = " Guten Tag",
        writer = "<KBD>",
        metrics = TranscriptionDebugMetrics(
            detectedLanguage = null,
            languageResolution = LanguageResolution("followKeyboard", "de", "de", sttLanguageIsEffective = true),
            rawCharCount = 10,
            polishedCharCount = 10,
            latencyMs = 830,
            sttEngine = "WK",
            sttModelID = "tiny",
            timings = Timings(preprocessMs = 30, engineMs = 790, postprocessMs = 10),
        ),
    )

    @Test
    fun `the written file parses with every iOS key, at every level`() {
        val file = TranscriptionDebugExporter.write(
            TranscriptionDebugExporter.makeExport(listOf(entry), device, settings, nowMs),
            folder.root,
            nowMs,
        )
        val export = JSONObject(file.readText())

        assertEquals(EXPORT_KEYS, export.keySet())
        assertEquals(DEVICE_KEYS, export.getJSONObject("device").keySet())
        assertEquals(SETTINGS_KEYS, export.getJSONObject("settings").keySet())
        assertEquals(TranscriptionDebugMetrics.OUTCOMES.toSet(), export.getJSONObject("outcomes").keySet())
        val event = export.getJSONArray("events").getJSONObject(0)
        assertEquals(EVENT_KEYS, event.keySet())
        assertEquals("2026-10-26T07:33:10Z", event.getString("timestamp"))
        assertEquals("<KBD>", event.getString("writer"))
        assertEquals("followKeyboard", event.getString("transcriptionMode"))
        assertEquals("de", event.getString("keyboardLanguage"))
        assertEquals("de", event.getString("sttLanguageCode"))
        assertTrue(event.getBoolean("sttLanguageIsEffective"))
        assertEquals("skippedToggleOff", event.getString("outcome"))
        assertEquals(" Guten Tag", event.getString("raw"))
        assertTrue(event.isNull("polished"))
        assertEquals(790, event.getInt("engineMs"))
        assertEquals(1, export.getJSONObject("outcomes").getInt("skippedToggleOff"))
        assertEquals(0, export.getJSONObject("outcomes").getInt("success"))
        assertEquals(false, export.getJSONObject("settings").getBoolean("polishEnabled"))
    }

    @Test
    fun `the file is named as on iOS and its keys are sorted`() {
        val file = TranscriptionDebugExporter.write(
            TranscriptionDebugExporter.makeExport(listOf(entry), device, settings, nowMs),
            folder.root,
            nowMs,
        )

        assertTrue(file.name, Regex("""dictus-polish-debug-\d{8}-\d{6}\.json""").matches(file.name))
        val topLevelKeys = Regex("""^ {2}"(\w+)":""", RegexOption.MULTILINE).findAll(file.readText())
            .map { it.groupValues[1] }.toList()
        assertEquals(EXPORT_KEYS.sorted(), topLevelKeys)
    }

    @Test
    fun `a new export replaces the previous one in the directory`() {
        val export = TranscriptionDebugExporter.makeExport(emptyList(), device, settings, nowMs)
        TranscriptionDebugExporter.write(export, folder.root, nowMs)
        val latest = TranscriptionDebugExporter.write(export, folder.root, nowMs + 60_000)

        assertEquals(listOf(latest.name), folder.root.list()!!.toList())
    }

    private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

    private companion object {
        // PolishDebugExport (:17-48).
        val EXPORT_KEYS = setOf(
            "exportedAt", "device", "settings", "outcomes", "failureReasons", "guardrailChecks",
            "smartModesSkippedForLength", "failureReasonsByWriter", "events",
        )

        // DeviceInfo (:50-59).
        val DEVICE_KEYS = setOf("hardware", "modelClass", "systemName", "systemVersion", "appVersion", "buildNumber")

        // SettingsInfo (:61-79).
        val SETTINGS_KEYS = setOf(
            "polishEnabled", "keyboardLanguage", "transcriptionLanguageMode", "activeModelID",
            "activeModelEngine", "appleFMAvailable", "appleFMState",
        )

        // Event (:81-167).
        val EVENT_KEYS = setOf(
            "id", "timestamp", "writer", "engine", "mode", "targetLanguage", "detectedLanguage",
            "transcriptionMode", "keyboardLanguage", "sttLanguageCode", "sttLanguageIsEffective",
            "languageMix", "targetSource", "outcome", "failureReason", "guardrailCheck",
            "smartModeLengthSkip", "latencyMs", "preprocessMs", "engineMs", "postprocessMs",
            "rawCharCount", "engineRawCharCount", "polishedCharCount", "raw", "vocabularyCorrected",
            "polished", "sttEngine", "sttModelID",
        )
    }
}
