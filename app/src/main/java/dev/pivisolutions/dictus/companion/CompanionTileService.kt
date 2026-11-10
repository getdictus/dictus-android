package dev.pivisolutions.dictus.companion

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CompanionTileService : TileService() {
    private var scope: CoroutineScope? = null
    private val coordinator get() = EntryPointAccessors.fromApplication(applicationContext,
        CompanionEntryPoint::class.java).companionCoordinator()

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { owner ->
            owner.launch {
                coordinator.state.collect { status ->
                    qsTile?.apply {
                        label = getString(R.string.companion_tile_label)
                        state = if (isLocked) Tile.STATE_UNAVAILABLE else if (status.busy)
                            Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        subtitle = getString(when (status.phase) {
                            CompanionPhase.RECORDING -> R.string.companion_recording
                            CompanionPhase.PREPARING, CompanionPhase.TRANSCRIBING,
                            CompanionPhase.INSERTING -> R.string.companion_processing
                            else -> R.string.companion_tap_start
                        })
                        updateTile()
                    }
                }
            }
        }
    }

    @Suppress("StartActivityAndCollapseDeprecated") // PendingIntent overload exists only on API 34+.
    override fun onClick() {
        super.onClick()
        if (isLocked) return
        if (coordinator.state.value.phase == CompanionPhase.RECORDING) {
            coordinator.stop()
            return
        }
        if (coordinator.state.value.busy) return
        coordinator.captureTarget()
        coordinator.beginActivation()
        val intent = Intent(this, CompanionActivationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 11, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure {
            if (coordinator.state.value.transcript == null) coordinator.dismiss()
            Toast.makeText(this, R.string.companion_start_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }
}
