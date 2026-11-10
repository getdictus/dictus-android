package dev.pivisolutions.dictus.companion

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AppOpsManager
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** The specialUse foreground service exists only while the user enables the visible control.
 * It never opens the microphone. Recording is owned by the existing microphone service. */
@AndroidEntryPoint
class CompanionOverlayService : Service() {
    @Inject lateinit var coordinator: CompanionCoordinator
    @Inject lateinit var dataStore: DataStore<Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windows get() = getSystemService(WindowManager::class.java)
    private var button: ImageButton? = null
    private var right = true
    private var yFraction = .3f
    private var attached = false
    private var enabled = false
    private var processingAnimation: ObjectAnimator? = null
    private var lastSafeArea: android.graphics.Rect? = null
    private val overlayPermission = AppOpsManager.OnOpChangedListener { _, packageName ->
        if (packageName == this.packageName && !Settings.canDrawOverlays(this)) {
            scope.launch { stopSelf() }
        }
    }
    private val params by lazy {
        WindowManager.LayoutParams(dp(56), dp(56), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                detach()
                coordinator.cancelActive()
            } else if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) attach()
        }
    }

    override fun onCreate() {
        super.onCreate()
        coordinator.retain()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.companion_title),
            NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, 42, notification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.companion_overlay_error, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        getSystemService(AppOpsManager::class.java).startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, overlayPermission)
        scope.launch {
            dataStore.data.collect { prefs ->
                enabled = prefs[PreferenceKeys.COMPANION_ENABLED] == true &&
                    prefs[PreferenceKeys.COMPANION_FLOATING] == true
                right = prefs[PreferenceKeys.COMPANION_DOCK_RIGHT] ?: true
                yFraction = (prefs[PreferenceKeys.COMPANION_Y] ?: .3f).coerceIn(0f, 1f)
                if (!enabled || !Settings.canDrawOverlays(this@CompanionOverlayService)) {
                    stopSelf()
                } else {
                    attach()
                    reposition()
                }
            }
        }
        scope.launch {
            coordinator.state.collect { status ->
                paint(status)
                manager.notify(42, notification())
            }
        }
    }

    private var receiverRegistered = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun attach() {
        if (attached || !enabled || !Settings.canDrawOverlays(this) ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val control = button ?: ImageButton(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(14), dp(14), dp(14), dp(14))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnClickListener {
                when {
                    coordinator.state.value.phase == CompanionPhase.RECORDING -> coordinator.stop()
                    coordinator.state.value.busy -> Unit
                    coordinator.state.value.transcript != null -> openResult()
                    else -> {
                        coordinator.captureTarget()
                        CompanionLauncher.launch(this@CompanionOverlayService)
                    }
                }
            }
            setOnLongClickListener { openResult(); true }
            setOnApplyWindowInsetsListener { _, insets ->
                // Moving this window can dispatch unchanged insets. Repositioning on every
                // callback would snap an in-progress drag back to its saved location.
                val area = safeArea()
                if (lastSafeArea != area) {
                    lastSafeArea = android.graphics.Rect(area)
                    reposition()
                }
                insets
            }
            installDrag(this)
        }.also { button = it }
        try {
            reposition()
            windows.addView(control, params)
            attached = true
            paint(coordinator.state.value)
        } catch (_: RuntimeException) { stopSelf() }
    }

    private fun openResult() {
        startActivity(Intent(this, CompanionActivationActivity::class.java)
            .putExtra(CompanionActivationActivity.EXTRA_CONTROLS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun installDrag(control: ImageButton) {
        var startX = 0f
        var startY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false
        var downTime = 0L
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        control.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY
                    initialX = params.x; initialY = params.y; moved = false; downTime = event.eventTime
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        val area = safeArea()
                        params.x = (initialX + dx.toInt()).coerceIn(area.left, area.right)
                        params.y = (initialY + dy.toInt()).coerceIn(area.top, area.bottom)
                        update()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val area = safeArea()
                        right = params.x > (area.left + area.right) / 2
                        yFraction = (params.y - area.top).toFloat() / (area.bottom - area.top).coerceAtLeast(1)
                        reposition()
                        scope.launch { dataStore.edit {
                            it[PreferenceKeys.COMPANION_DOCK_RIGHT] = right
                            it[PreferenceKeys.COMPANION_Y] = yFraction
                        } }
                    } else if (event.eventTime - downTime >= ViewConfiguration.getLongPressTimeout()) {
                        control.performLongClick()
                    } else control.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { reposition(); true }
                else -> false
            }
        }
    }

    private fun safeArea(): android.graphics.Rect {
        val width: Int
        val height: Int
        var left = dp(8); var top = dp(32); var rightInset = dp(8); var bottom = dp(32)
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            width = metrics.bounds.width(); height = metrics.bounds.height()
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.systemGestures())
            left += insets.left; top = maxOf(top, insets.top + dp(8))
            rightInset += insets.right; bottom = maxOf(bottom, insets.bottom + dp(8))
        } else {
            val metrics = resources.displayMetrics
            width = metrics.widthPixels; height = metrics.heightPixels
            @Suppress("DEPRECATION")
            button?.rootWindowInsets?.let { insets ->
                left = maxOf(left, insets.stableInsetLeft + dp(8), insets.displayCutout?.safeInsetLeft ?: 0)
                top = maxOf(top, insets.stableInsetTop + dp(8), insets.displayCutout?.safeInsetTop ?: 0)
                rightInset = maxOf(rightInset, insets.stableInsetRight + dp(8), insets.displayCutout?.safeInsetRight ?: 0)
                bottom = maxOf(bottom, insets.stableInsetBottom + dp(8), insets.displayCutout?.safeInsetBottom ?: 0)
            }
        }
        return android.graphics.Rect(left, top, maxOf(left, width - rightInset - dp(56)),
            maxOf(top, height - bottom - dp(56)))
    }

    private fun reposition() {
        val area = safeArea()
        params.x = if (right) area.right else area.left
        params.y = area.top + ((area.bottom - area.top) * yFraction).toInt()
        update()
    }

    private fun update() {
        if (attached) try { windows.updateViewLayout(button, params) }
        catch (_: RuntimeException) { stopSelf() }
    }

    private fun paint(status: CompanionStatus) {
        processingAnimation?.cancel()
        processingAnimation = null
        button?.rotation = 0f
        val description = when (status.phase) {
            CompanionPhase.RECORDING -> R.string.companion_recording
            CompanionPhase.PREPARING, CompanionPhase.TRANSCRIBING, CompanionPhase.INSERTING -> R.string.companion_processing
            CompanionPhase.ERROR -> R.string.companion_error
            else -> if (status.transcript != null) R.string.companion_result else R.string.companion_tap_start
        }
        button?.apply {
            contentDescription = getString(description)
            setImageResource(when {
                status.phase == CompanionPhase.RECORDING -> R.drawable.ic_stop
                status.busy -> android.R.drawable.ic_popup_sync
                status.transcript != null -> android.R.drawable.ic_menu_edit
                else -> R.drawable.ic_mic
            })
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when (status.phase) {
                    CompanionPhase.RECORDING, CompanionPhase.ERROR -> Color.rgb(239, 68, 68)
                    CompanionPhase.COMPLETED -> Color.rgb(34, 150, 90)
                    else -> Color.rgb(61, 126, 255)
                })
            }
            if (status.busy && status.phase != CompanionPhase.RECORDING && ValueAnimator.areAnimatorsEnabled()) {
                processingAnimation = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f).apply {
                    duration = 1200
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        }
    }

    private fun notification(): android.app.Notification {
        val result = PendingIntent.getActivity(this, 42,
            Intent(this, CompanionActivationActivity::class.java).putExtra(CompanionActivationActivity.EXTRA_CONTROLS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic).setContentTitle(getString(R.string.companion_title))
            .setContentText(getString(if (coordinator.state.value.transcript != null)
                R.string.companion_result else R.string.companion_overlay_notification))
            .setContentIntent(result).setOngoing(true).build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reposition()
    }

    private fun detach() {
        processingAnimation?.cancel()
        processingAnimation = null
        if (attached) runCatching { windows.removeViewImmediate(button) }
        attached = false
    }

    override fun onDestroy() {
        processingAnimation?.cancel()
        detach()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        getSystemService(AppOpsManager::class.java).stopWatchingMode(overlayPermission)
        scope.cancel()
        coordinator.releaseClient()
        super.onDestroy()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    companion object { private const val CHANNEL = "dictus_companion_overlay" }
}
