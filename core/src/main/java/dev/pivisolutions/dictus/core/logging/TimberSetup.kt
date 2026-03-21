package dev.pivisolutions.dictus.core.logging

import timber.log.Timber

/**
 * Centralized Timber logging initialization.
 *
 * Timber is Jake Wharton's logging library for Android. It wraps
 * Android's Log class and adds automatic tag detection (uses the
 * calling class name as the log tag). The "tree" pattern lets you
 * plant different logging backends:
 * - DebugTree: logs to Logcat with auto-tags (debug builds only)
 * - In production, no tree is planted so all log calls are no-ops
 */
object TimberSetup {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
