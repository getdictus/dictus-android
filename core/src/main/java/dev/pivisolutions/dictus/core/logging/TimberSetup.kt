package dev.pivisolutions.dictus.core.logging

import timber.log.Timber
import java.io.File

/**
 * Centralized Timber logging initialization.
 *
 * Timber is Jake Wharton's logging library for Android. It wraps
 * Android's Log class and adds automatic tag detection (uses the
 * calling class name as the log tag). The "tree" pattern lets you
 * plant different logging backends:
 * - DebugTree: logs to Logcat with auto-tags (debug builds only)
 * - FileLoggingTree: always-on, writes every log entry to dictus.log on disk
 *
 * WHY FileLoggingTree in all builds: Debug logs need to survive app restarts
 * and are exportable from Settings, which is useful for diagnosing issues on
 * real devices where Logcat is not accessible.
 *
 * @param isDebug Whether this is a debug build (plants DebugTree for Logcat output).
 * @param filesDir The application's internal files directory where dictus.log is written.
 */
object TimberSetup {

    private var logFile: File? = null

    fun init(isDebug: Boolean, filesDir: File) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        val file = File(filesDir, "dictus.log")
        logFile = file
        Timber.plant(FileLoggingTree(file))
    }

    /**
     * Return the current log file, or null if TimberSetup has not been initialised.
     *
     * Used by LogExporter to locate the file to include in the ZIP.
     */
    fun getLogFile(): File? = logFile
}
