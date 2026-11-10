package dev.pivisolutions.dictus.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors

/** Only immutable, app-created notification PendingIntents can reach this receiver. */
class CompanionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = EntryPointAccessors.fromApplication(context.applicationContext,
            CompanionEntryPoint::class.java).companionCoordinator()
        when (intent.action) {
            ACTION_STOP -> coordinator.stop()
            ACTION_CANCEL -> coordinator.cancel()
        }
    }

    companion object {
        const val ACTION_STOP = "dev.pivisolutions.dictus.companion.STOP"
        const val ACTION_CANCEL = "dev.pivisolutions.dictus.companion.CANCEL"
    }
}
