package dev.pivisolutions.dictus.feedback

/**
 * The in-app feedback form's data (#373, iOS `FeedbackReport.swift`): what the user writes,
 * what the device adds, and the JSON body `POST /api/feedback` accepts
 * (getdictus/dictus-website `docs/feedback-api.md`).
 */
enum class FeedbackKind(val wire: String) { BUG("bug"), IDEA("idea"), OTHER("other") }

/** Why the form cannot be sent yet. Each one matches a server-side `400`. */
enum class FeedbackDraftProblem { NAME_MISSING, NAME_TOO_LONG, EMAIL_INVALID, EMAIL_TOO_LONG, MESSAGE_MISSING, MESSAGE_TOO_LONG }

/** The server's caps. Lengths are Unicode scalars, except the email (UTF-16 units, JavaScript `length`). */
object FeedbackLimits {
    const val NAME = 100
    const val EMAIL = 254
    const val MESSAGE = 4000
    const val LOG_BYTES = 1_572_864
    const val BODY_BYTES = 2_097_152
}

/** What the user typed, kept as typed; the cleaned values the server sees are derived on demand. */
data class FeedbackDraft(
    val kind: FeedbackKind = FeedbackKind.BUG,
    val name: String = "",
    val email: String = "",
    val message: String = "",
    // Off for every kind until the user turns it on: the log leaves the device only by choice.
    val attachesLog: Boolean = false,
) {
    val cleanName: String get() = trimmed(name.withoutControls(keepLineBreaks = false))
    val cleanEmail: String get() = trimmed(email.withoutControls(keepLineBreaks = false))
    val cleanMessage: String get() = trimmed(message.withoutControls(keepLineBreaks = true))

    val problems: Set<FeedbackDraftProblem>
        get() = buildSet {
            val name = cleanName
            if (name.isEmpty()) add(FeedbackDraftProblem.NAME_MISSING)
            if (name.scalarCount() > FeedbackLimits.NAME) add(FeedbackDraftProblem.NAME_TOO_LONG)
            val email = cleanEmail
            if (email.length > FeedbackLimits.EMAIL) {
                add(FeedbackDraftProblem.EMAIL_TOO_LONG)
            } else if (email.isNotEmpty() && !EMAIL_SHAPE.matches(email)) {
                add(FeedbackDraftProblem.EMAIL_INVALID)
            }
            val message = cleanMessage
            if (message.isEmpty()) add(FeedbackDraftProblem.MESSAGE_MISSING)
            if (message.scalarCount() > FeedbackLimits.MESSAGE) add(FeedbackDraftProblem.MESSAGE_TOO_LONG)
        }

    /** How many characters the message is over the cap, for the form to say so. */
    val messageOverflow: Int get() = maxOf(0, cleanMessage.scalarCount() - FeedbackLimits.MESSAGE)

    /**
     * Pierre's decision (2026-11-19, iOS #981): a bug report is rarely useful without the log,
     * yet the box stays off by default, so Send asks once for a Bug whose box is still off.
     */
    val asksForLogBeforeSending: Boolean get() = kind == FeedbackKind.BUG && !attachesLog

    private companion object {
        val EMAIL_SHAPE = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

        // JavaScript's `trim()` also strips U+FEFF.
        fun trimmed(text: String): String = text.trim { it.isWhitespace() || it == '\uFEFF' }
    }
}

/**
 * What the app says about itself in every report, shown on the form before Send. Each value is
 * cut down to the character set the server accepts, so an odd build name or device cannot turn
 * a report into a `400`.
 */
data class FeedbackDeviceInfo(
    val appVersion: String,
    val build: String,
    val gitRevision: String,
    val androidVersion: String,
    val deviceModel: String,
    val locale: String,
) {
    companion object {
        fun of(
            appVersion: String,
            build: String,
            gitRevision: String,
            androidRelease: String,
            sdkInt: Int,
            manufacturer: String,
            model: String,
            languageTag: String,
        ) = FeedbackDeviceInfo(
            appVersion = restricted(appVersion, "._+-", 32),
            build = restricted(build, "._-", 32),
            gitRevision = restricted(gitRevision, "._+-", 64),
            androidVersion = restricted("$androidRelease (API $sdkInt)", " .()_+-", 64),
            deviceModel = deviceModel(manufacturer, model),
            locale = locale(languageTag),
        )

        /**
         * `MANUFACTURER MODEL` with every character outside the server's set replaced by `_`
         * and cut at 64, as the contract asks: `Redmi Note 13 Pro+ 5G™` → `Redmi Note 13 Pro+ 5G_`.
         */
        fun deviceModel(manufacturer: String, model: String): String {
            val raw = "$manufacturer $model".trim()
            val replaced = buildString {
                raw.codePoints().forEach { cp ->
                    append(if (cp < 0x80 && (Character.isLetterOrDigit(cp) || " ,._()+-".indexOf(cp.toChar()) >= 0)) cp.toChar() else '_')
                }
            }
            return replaced.take(64).ifBlank { UNKNOWN }
        }

        /**
         * `Locale.toLanguageTag()`, without the extensions the server's pattern refuses
         * (Android 14 adds `-u-mu-celsius` and the like). Anything else unusable becomes `und`.
         */
        fun locale(languageTag: String): String {
            val base = languageTag.split('-').takeWhile { it.length != 1 }.joinToString("-")
            return if (base.length in 2..64 && LOCALE_SHAPE.matches(base)) base else "und"
        }

        private fun restricted(value: String, punctuation: String, max: Int): String {
            val kept = value.filter { it.code < 0x80 && (it.isLetterOrDigit() || punctuation.indexOf(it) >= 0) }
            return kept.take(max).trim().ifEmpty { UNKNOWN }
        }

        private const val UNKNOWN = "unknown"
        private val LOCALE_SHAPE = Regex("""[A-Za-z]{2,3}(?:[_-][A-Za-z0-9]{2,8})*""")
    }
}

/** The JSON body of `POST /api/feedback`, built only from a draft with no problem. */
data class FeedbackPayload(
    val type: String,
    val name: String,
    val email: String?,
    val message: String,
    val device: FeedbackDeviceInfo,
    val log: String?,
) {
    /**
     * The request body, at most [FeedbackLimits.BODY_BYTES]. Keys are sorted, absent values are
     * left out. JSON escaping can push a log under its own cap over the wire cap, so the oldest
     * lines go first until it fits.
     */
    fun body(): ByteArray {
        var payload = this
        while (true) {
            val bytes = payload.json().toByteArray(Charsets.UTF_8)
            val log = payload.log
            if (bytes.size <= FeedbackLimits.BODY_BYTES || log == null) return bytes
            val target = log.toByteArray(Charsets.UTF_8).size - (bytes.size - FeedbackLimits.BODY_BYTES) - 1
            val shorter = if (target > 0) newestLines(log, target) else ""
            payload = payload.copy(log = shorter.ifEmpty { null })
        }
    }

    private fun json(): String {
        val fields = sortedMapOf(
            "platform" to "android",
            "type" to type,
            "name" to name,
            "email" to email,
            "message" to message,
            "appVersion" to device.appVersion,
            "build" to device.build,
            "gitRevision" to device.gitRevision,
            "androidVersion" to device.androidVersion,
            "deviceModel" to device.deviceModel,
            "locale" to device.locale,
            "log" to log,
        )
        return fields.entries
            .filter { it.value != null }
            .joinToString(",", "{", "}") { (key, value) -> "${jsonString(key)}:${jsonString(value!!)}" }
    }

    /** The report as plain text, for the share sheet when sending fails; the log travels as a file. */
    val shareText: String
        get() = buildList {
            add("Dictus feedback: $type")
            add("Name: $name")
            email?.let { add("Email: $it") }
            add(
                "Dictus ${device.appVersion} (${device.build}) rev ${device.gitRevision} | " +
                    "Android ${device.androidVersion} | ${device.deviceModel} | ${device.locale}",
            )
            add("")
            add(message)
        }.joinToString("\n")

    companion object {
        fun from(draft: FeedbackDraft, device: FeedbackDeviceInfo, log: String?): FeedbackPayload? {
            if (draft.problems.isNotEmpty()) return null
            val attached = if (draft.attachesLog) log?.let { newestLines(it, FeedbackLimits.LOG_BYTES) } else null
            return FeedbackPayload(
                type = draft.kind.wire,
                name = draft.cleanName,
                email = draft.cleanEmail.ifEmpty { null },
                message = draft.cleanMessage,
                device = device,
                log = attached?.ifEmpty { null },
            )
        }

        /**
         * The end of [text] within [maxBytes] of UTF-8, starting at a line boundary so the first
         * line the reader sees is whole.
         */
        fun newestLines(text: String, maxBytes: Int): String {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return text
            var start = bytes.size - maxBytes
            // Off a UTF-8 continuation byte, then past the next line break.
            while (start < bytes.size && (bytes[start].toInt() and 0xC0) == 0x80) start++
            val newline = (start until bytes.size).firstOrNull { bytes[it] == '\n'.code.toByte() }
            val from = if (newline == null) start else newline + 1
            return String(bytes, from, bytes.size - from, Charsets.UTF_8)
        }

        private fun jsonString(value: String): String = buildString(value.length + 2) {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}

/**
 * The log the form attaches: the export header, then the newest lines that fit under
 * [FeedbackLimits.LOG_BYTES] with it. Cut here rather than in the payload, so « See what will be
 * sent » shows exactly what leaves, and a long log never loses its header.
 */
fun feedbackLogSnapshot(header: String, lines: String): String {
    val room = FeedbackLimits.LOG_BYTES - header.toByteArray(Charsets.UTF_8).size
    return header + if (room > 0) FeedbackPayload.newestLines(lines, room) else ""
}

private fun String.scalarCount(): Int = codePointCount(0, length)

/** Drops C0, DEL and C1 controls, which the server refuses; a message keeps tab, LF and CR. */
private fun String.withoutControls(keepLineBreaks: Boolean): String = filter { c ->
    val isControl = c < ' ' || c in '\u007F'..'\u009F'
    !isControl || (keepLineBreaks && (c == '\t' || c == '\n' || c == '\r'))
}
