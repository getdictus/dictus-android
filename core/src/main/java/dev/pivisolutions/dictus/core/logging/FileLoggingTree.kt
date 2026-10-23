package dev.pivisolutions.dictus.core.logging

import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Timber tree that appends log entries to a file on disk.
 *
 * WHY file logging: The in-memory Logcat buffer is too small to debug
 * issues that occur during long recording sessions or after app restart.
 * Writing to a file lets the user export the full session log via the
 * "Export debug logs" action in Settings.
 *
 * WHY synchronized: Multiple coroutines on different threads can trigger
 * Timber log calls concurrently. synchronized(logFile) prevents interleaved
 * writes from corrupting line boundaries in the log file.
 *
 * Line format (#112), close to the iOS export:
 * `2026-10-23T00:12:03.456+02:00 INFO    [IME] DictusImeService d=7f3a01: Mic tap ...`
 * — zoned timestamp, level, [LogCategory], calling class, and the [DictationTrace] id while a
 * dictation is in flight.
 *
 * Size cap (#112): once [logFile] passes [maxBytes], it replaces [previousFile] and a new current
 * file starts. At most two files exist, so the log never takes more than about twice the cap.
 *
 * @param logFile The file to append log entries to. Created on first write.
 * @param previousFile Where the full file goes on rotation; replaced each time.
 * @param maxBytes Size past which [logFile] rotates.
 */
class FileLoggingTree(
    private val logFile: File,
    private val now: () -> Long = System::currentTimeMillis,
    timeZone: TimeZone = TimeZone.getDefault(),
    private val previousFile: File = previousFileFor(logFile),
    private val maxBytes: Long = MAX_BYTES,
) : Timber.Tree() {

    private val dateFormat = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).apply {
        this.timeZone = timeZone
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Plain Timber.Tree gets no tag unless the caller set one: the stack-derived tag lives in
        // DebugTree only. That is why every line used to read [D/null] (#112).
        val caller = callerClassName()
        val category = LogCategory.fromTag(tag) ?: LogCategory.forClass(caller)
        val source = tag?.takeIf { LogCategory.fromTag(it) == null } ?: simpleName(caller)
        synchronized(logFile) {
            val line = formatLine(
                timestamp = dateFormat.format(Date(now())),
                priority = priority,
                category = category,
                source = source,
                dictationId = DictationTrace.currentId(),
                message = message,
            )
            logFile.appendText(line)
            t?.let { logFile.appendText(it.stackTraceToString() + "\n") }
            if (logFile.length() > maxBytes) rotate()
        }
    }

    /**
     * Current becomes previous, current starts empty. Runs under the writer's lock, so no line
     * lands between the size check and the rename. If the rename fails the file is truncated:
     * losing history is acceptable, growing without bound is not.
     */
    private fun rotate() {
        previousFile.delete()
        if (!logFile.renameTo(previousFile)) {
            logFile.writeText("")
        }
    }

    /** First frame outside Timber and this tree: the class that called `Timber.x(...)`. */
    private fun callerClassName(): String =
        Throwable().stackTrace
            .firstOrNull { frame -> frame.className !in IGNORED_FRAMES && !frame.className.startsWith(TIMBER_PREFIX) }
            ?.className
            ?: "unknown"

    companion object {
        const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

        /**
         * 2 MB per file, so 4 MB at most on disk.
         *
         * A line averages ~95 bytes (measured on an emulator export: 572 lines, 54 KB), so one
         * file holds ~22,000 lines. A dictation writes ~20 lines and an hour of typing a few
         * hundred, so even a heavy day (~5,000 lines) leaves several days across the two files:
         * enough to still hold the event a field report is about. The text compresses ~10x, so the
         * shared ZIP stays well under 1 MB for any mail or chat app, and 4 MB is negligible storage.
         */
        const val MAX_BYTES: Long = 2L * 1024 * 1024

        /** `dictus.log` -> `dictus.previous.log`, in the same directory. */
        fun previousFileFor(logFile: File): File =
            File(logFile.parentFile, "${logFile.nameWithoutExtension}.previous.${logFile.extension}")

        private const val TIMBER_PREFIX = "timber.log.Timber"
        private val IGNORED_FRAMES = setOf(FileLoggingTree::class.java.name)
        private val ANONYMOUS_CLASS = Regex("\\$.*$")

        /** Outer class of a nested, anonymous or lambda class, without the `Kt` of a file facade. */
        fun simpleName(className: String): String =
            className.substringAfterLast('.').replace(ANONYMOUS_CLASS, "").removeSuffix("Kt")
                .ifEmpty { "unknown" }

        /** Pure line formatter, so the format is testable without a clock or a stack. */
        fun formatLine(
            timestamp: String,
            priority: Int,
            category: LogCategory,
            source: String,
            dictationId: String?,
            message: String,
        ): String {
            val correlation = dictationId?.let { " d=$it" } ?: ""
            return "$timestamp ${levelName(priority).padEnd(LEVEL_WIDTH)} [${category.name}] " +
                "$source$correlation: $message\n"
        }

        private const val LEVEL_WIDTH = 7

        private fun levelName(priority: Int): String = when (priority) {
            2 -> "VERBOSE"
            3 -> "DEBUG"
            4 -> "INFO"
            5 -> "WARN"
            6 -> "ERROR"
            7 -> "ASSERT"
            else -> "?"
        }
    }
}
