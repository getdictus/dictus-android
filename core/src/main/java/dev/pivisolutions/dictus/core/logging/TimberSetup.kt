package dev.pivisolutions.dictus.core.logging

import timber.log.Timber

// Stub logging setup for Task 1 build verification. Full implementation in Task 2.
object TimberSetup {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
