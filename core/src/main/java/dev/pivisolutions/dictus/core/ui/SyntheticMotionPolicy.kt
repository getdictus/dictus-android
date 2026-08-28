package dev.pivisolutions.dictus.core.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Gates decorative synthetic waveform motion using Android's accessibility and power signals.
 * Live microphone energy remains visible because it is functional recording feedback.
 */
object SyntheticMotionPolicy {
    fun isEnabled(animatorDurationScale: Float, isPowerSaveMode: Boolean): Boolean =
        animatorDurationScale > 0f && !isPowerSaveMode

    fun current(context: Context): Boolean {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return isEnabled(durationScale, powerManager.isPowerSaveMode)
    }

    /** Emits immediately and whenever animator scale or battery-saver state changes. */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val applicationContext = context.applicationContext
        fun publish() {
            trySend(current(applicationContext))
        }

        val animationObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = publish()
        }
        applicationContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animationObserver,
        )

        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = publish()
        }
        ContextCompat.registerReceiver(
            applicationContext,
            powerReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        publish()
        awaitClose {
            applicationContext.contentResolver.unregisterContentObserver(animationObserver)
            applicationContext.unregisterReceiver(powerReceiver)
        }
    }.distinctUntilChanged()
}

/** Collects the shared motion policy and updates active composables without process recreation. */
@Composable
fun rememberSyntheticMotionEnabled(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val policy = remember(context) { SyntheticMotionPolicy.observe(context) }
    return policy.collectAsState(initial = SyntheticMotionPolicy.current(context))
}
