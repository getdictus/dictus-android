package dev.pivisolutions.dictus.feedback

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The body `POST /api/feedback` gets (#373), against getdictus/dictus-website `docs/feedback-api.md`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeedbackReportTest {

    private val device = FeedbackDeviceInfo.of(
        appVersion = "1.5.0",
        build = "105",
        gitRevision = "db8a816",
        androidRelease = "13",
        sdkInt = 33,
        manufacturer = "Google",
        model = "Pixel 4",
        languageTag = "fr-FR",
    )

    private val draft = FeedbackDraft(name = "Zoé", email = "zoe@example.org", message = "The bubble stays.")

    private fun body(payload: FeedbackPayload) = JSONObject(String(payload.body(), Charsets.UTF_8))

    @Test
    fun `the body carries the Android keys of the contract and nothing else`() {
        val json = body(FeedbackPayload.from(draft.copy(attachesLog = true), device, "line 1\nline 2\n")!!)

        assertEquals(
            setOf(
                "platform", "type", "name", "email", "message", "appVersion", "build", "gitRevision",
                "androidVersion", "deviceModel", "locale", "log",
            ),
            json.keys().asSequence().toSet(),
        )
        assertEquals("android", json.getString("platform"))
        assertEquals("bug", json.getString("type"))
        assertEquals("13 (API 33)", json.getString("androidVersion"))
        assertEquals("Google Pixel 4", json.getString("deviceModel"))
        assertEquals("fr-FR", json.getString("locale"))
        assertEquals("line 1\nline 2\n", json.getString("log"))
    }

    @Test
    fun `keys are sorted and slashes are not escaped`() {
        val raw = String(FeedbackPayload.from(draft.copy(message = "a/b \"q\" \\ é\ttab"), device, null)!!.body(), Charsets.UTF_8)

        val keys = Regex(""""([A-Za-z]+)":""").findAll(raw).map { it.groupValues[1] }.toList()
        assertEquals(keys.sorted(), keys)
        assertTrue(raw.contains(""""message":"a/b \"q\" \\ é\ttab""""))
    }

    @Test
    fun `an empty email and an unattached log are left out`() {
        val json = body(FeedbackPayload.from(draft.copy(email = "  "), device, "log text")!!)

        assertFalse(json.has("email"))
        assertFalse(json.has("log"))
        assertFalse(json.has("iosVersion"))
    }

    @Test
    fun `an empty log is left out even when attached`() {
        assertNull(FeedbackPayload.from(draft.copy(attachesLog = true), device, "")!!.log)
    }

    @Test
    fun `the log keeps its newest lines under 1,5 MB, cut at a line boundary`() {
        val line = "2026-11-19T10:00:00.000+01:00 INFO [dictation] é event\n"
        val log = line.repeat(FeedbackLimits.LOG_BYTES / line.toByteArray().size + 100) + "newest\n"

        val sent = FeedbackPayload.from(draft.copy(attachesLog = true), device, log)!!.log!!

        assertTrue(sent.toByteArray(Charsets.UTF_8).size <= FeedbackLimits.LOG_BYTES)
        assertTrue(sent.startsWith("2026-11-19T10"))
        assertTrue(sent.endsWith("newest\n"))
    }

    @Test
    fun `a body over 2 MiB after escaping drops more old log lines`() {
        // Each quote doubles on the wire, so a log under its own cap overflows the body cap.
        val line = "\"".repeat(99) + "\n"
        val log = line.repeat(FeedbackLimits.LOG_BYTES / line.length)

        val bytes = FeedbackPayload.from(draft.copy(attachesLog = true), device, log)!!.body()

        assertTrue(bytes.size <= FeedbackLimits.BODY_BYTES)
        assertTrue(JSONObject(String(bytes, Charsets.UTF_8)).getString("log").startsWith("\""))
    }

    @Test
    fun `the snapshot keeps the export header when the log is over the cap`() {
        val header = "Dictus Debug Log\n---\n"
        val lines = "x".repeat(99).plus("\n").repeat(20_000)

        val snapshot = feedbackLogSnapshot(header, lines)

        assertTrue(snapshot.startsWith(header))
        assertTrue(snapshot.toByteArray().size <= FeedbackLimits.LOG_BYTES)
        assertEquals(snapshot, FeedbackPayload.from(draft.copy(attachesLog = true), device, snapshot)!!.log)
    }

    @Test
    fun `deviceModel replaces characters outside the server set and is cut at 64`() {
        assertEquals("Xiaomi Redmi Note 13 Pro+ 5G_", FeedbackDeviceInfo.deviceModel("Xiaomi", "Redmi Note 13 Pro+ 5G™"))
        assertEquals("Google Pixel_4 _", FeedbackDeviceInfo.deviceModel("Google", "Pixel/4 😀"))
        assertEquals(64, FeedbackDeviceInfo.deviceModel("M".repeat(40), "N".repeat(40)).length)
        assertEquals("unknown", FeedbackDeviceInfo.deviceModel("", ""))
    }

    @Test
    fun `locale drops the extensions the server refuses`() {
        assertEquals("fr-FR", FeedbackDeviceInfo.locale("fr-FR"))
        assertEquals("fr-FR", FeedbackDeviceInfo.locale("fr-FR-u-mu-celsius"))
        assertEquals("zh-Hans-CN", FeedbackDeviceInfo.locale("zh-Hans-CN"))
        assertEquals("und", FeedbackDeviceInfo.locale("und"))
        assertEquals("und", FeedbackDeviceInfo.locale(""))
    }

    @Test
    fun `version fields keep only the server charset`() {
        val odd = FeedbackDeviceInfo.of("1.5.0-dev/x", "105", "abc123+dirty", "15 Beta/2", 35, "G", "P", "en-US")

        assertEquals("1.5.0-devx", odd.appVersion)
        assertEquals("abc123+dirty", odd.gitRevision)
        assertEquals("15 Beta2 (API 35)", odd.androidVersion)
    }

    @Test
    fun `name is required and capped at 100 characters, emoji counted once`() {
        assertEquals(setOf(FeedbackDraftProblem.NAME_MISSING), draft.copy(name = " \u0007 ").problems)
        assertTrue(draft.copy(name = "😀".repeat(100)).problems.isEmpty())
        assertEquals(setOf(FeedbackDraftProblem.NAME_TOO_LONG), draft.copy(name = "a".repeat(101)).problems)
    }

    @Test
    fun `email is optional, x@y_z shaped and at most 254`() {
        assertTrue(draft.copy(email = "").problems.isEmpty())
        assertEquals(setOf(FeedbackDraftProblem.EMAIL_INVALID), draft.copy(email = "zoe@example").problems)
        assertEquals(
            setOf(FeedbackDraftProblem.EMAIL_TOO_LONG),
            draft.copy(email = "a".repeat(250) + "@b.co").problems,
        )
    }

    @Test
    fun `message is required, at most 4000, and keeps its line breaks`() {
        assertEquals(setOf(FeedbackDraftProblem.MESSAGE_MISSING), draft.copy(message = "\n \t").problems)
        assertEquals(1, draft.copy(message = "a".repeat(4001)).messageOverflow)
        assertEquals("a\tb\nc", draft.copy(message = "a\tb\u0000\nc").cleanMessage)
        assertNull(FeedbackPayload.from(draft.copy(message = "a".repeat(4001)), device, null))
    }

    @Test
    fun `the log box is off by default for every type`() {
        FeedbackKind.entries.forEach { assertFalse(FeedbackDraft(kind = it).attachesLog) }
    }

    @Test
    fun `Send asks for the log only for a Bug whose box is off`() {
        assertTrue(draft.copy(kind = FeedbackKind.BUG, attachesLog = false).asksForLogBeforeSending)
        assertFalse(draft.copy(kind = FeedbackKind.BUG, attachesLog = true).asksForLogBeforeSending)
        assertFalse(draft.copy(kind = FeedbackKind.IDEA, attachesLog = false).asksForLogBeforeSending)
        assertFalse(draft.copy(kind = FeedbackKind.OTHER, attachesLog = false).asksForLogBeforeSending)
    }

    @Test
    fun `share text names the report and the device, then the message`() {
        val text = FeedbackPayload.from(draft, device, null)!!.shareText

        assertEquals(
            "Dictus feedback: bug\nName: Zoé\nEmail: zoe@example.org\n" +
                "Dictus 1.5.0 (105) rev db8a816 | Android 13 (API 33) | Google Pixel 4 | fr-FR\n\nThe bubble stays.",
            text,
        )
    }
}
