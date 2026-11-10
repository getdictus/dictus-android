package dev.pivisolutions.dictus.companion

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.R

object CompanionLauncher {
    /** Capture while the selected field still owns focus, before opening recording controls. */
    fun captureAndLaunch(context: Context) {
        EntryPointAccessors.fromApplication(context.applicationContext,
            CompanionEntryPoint::class.java).companionCoordinator().captureTarget()
        launch(context)
    }

    fun showOverlay(context: Context) {
        runCatching { ContextCompat.startForegroundService(context,
            Intent(context, CompanionOverlayService::class.java)) }.onFailure {
            Toast.makeText(context, R.string.companion_overlay_error, Toast.LENGTH_LONG).show()
        }
    }

    fun hideOverlay(context: Context) {
        context.stopService(Intent(context, CompanionOverlayService::class.java))
    }

    fun launch(context: Context) {
        val coordinator = EntryPointAccessors.fromApplication(context.applicationContext,
            CompanionEntryPoint::class.java).companionCoordinator()
        coordinator.beginActivation()
        runCatching {
            context.startActivity(Intent(context, CompanionActivationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            if (!coordinator.state.value.busy && coordinator.state.value.transcript == null) coordinator.dismiss()
            Toast.makeText(context, R.string.companion_start_error, Toast.LENGTH_LONG).show()
        }
    }
}
