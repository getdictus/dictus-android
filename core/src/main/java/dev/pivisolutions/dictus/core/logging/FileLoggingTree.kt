package dev.pivisolutions.dictus.core.logging

import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * @param logFile The file to append log entries to. Created on first write.
 */
class FileLoggingTree(private val logFile: File) : Timber.Tree() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        synchronized(logFile) {
            val timestamp = dateFormat.format(Date())
            val level = when (priority) {
                2 -> "V"
                3 -> "D"
                4 -> "I"
                5 -> "W"
                6 -> "E"
                7 -> "A"
                else -> "?"
            }
            logFile.appendText("$timestamp [$level/$tag] $message\n")
            t?.let { logFile.appendText(it.stackTraceToString() + "\n") }
        }
    }
}
