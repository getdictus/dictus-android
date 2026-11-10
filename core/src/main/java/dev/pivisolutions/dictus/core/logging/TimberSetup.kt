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
 * - FileLoggingTree: always-on, writes every log entry to dictus.log on disk,
 *   tagged with its [LogCategory] and calling class
 *
 * WHY FileLoggingTree in all builds: Debug logs need to survive app restarts
 * and are exportable from Settings, which is useful for diagnosing issues on
 * real devices where Logcat is not accessible.
 *
 * @param isDebug Whether this is a debug build (plants DebugTree for Logcat output).
 * @param filesDir The application's internal files directory where dictus.log is written.
 */
object TimberSetup {

    private const val PRIVACY_MIGRATION_MARKER = ".privacy-log-v1"

    private var logFile: File? = null
    private var previousLogFile: File? = null

    fun init(isDebug: Boolean, filesDir: File) {
        val file = File(filesDir, "dictus.log")
        logFile = null
        previousLogFile = null
        val purge = purgeLegacySensitiveLogOnce(filesDir, file)
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        if (purge != Purge.FAILED) {
            logFile = file
            previousLogFile = FileLoggingTree.previousFileFor(file)
            Timber.plant(FileLoggingTree(file))
        }
        if (purge == Purge.DONE_NOW) {
            // WHY (#112): a log that starts at an app launch could be the true first entry or the
            // point where the purge erased everything before it. Saying so removes the guess.
            Timber.i("Privacy migration v1 purged the previous log; nothing before this line survives")
        }
    }

    private enum class Purge { ALREADY_DONE, DONE_NOW, FAILED }

    /** Existing releases may have persisted dictated or typed text in this file. */
    private fun purgeLegacySensitiveLogOnce(filesDir: File, file: File): Purge {
        val marker = File(filesDir, PRIVACY_MIGRATION_MARKER)
        if (marker.exists()) return Purge.ALREADY_DONE

        val previous = FileLoggingTree.previousFileFor(file)
        if ((file.exists() && !file.delete()) || (previous.exists() && !previous.delete())) {
            return Purge.FAILED
        }
        return if (marker.createNewFile() || marker.exists()) Purge.DONE_NOW else Purge.FAILED
    }

    /**
     * Return the current log file, or null if TimberSetup has not been initialised.
     *
     * Used by LogExporter to locate the file to include in the ZIP.
     */
    fun getLogFile(): File? = logFile

    /** The file the last rotation moved the log to, or null if TimberSetup has not been initialised. */
    fun getPreviousLogFile(): File? = previousLogFile
}
