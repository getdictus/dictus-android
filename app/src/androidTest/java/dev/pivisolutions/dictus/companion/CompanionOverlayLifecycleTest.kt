package dev.pivisolutions.dictus.companion

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.companion.insertion.InsertionHarnessActivity
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.service.DictationServiceEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WindowManager overlay behavior; app-op changes are confined to test setup/teardown. */
@RunWith(AndroidJUnit4::class)
class CompanionOverlayLifecycleTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private val automation get() = instrument.uiAutomation

    @Test fun nonFocusableControlDragsPersistsPositionRotatesAndHides() {
        val dataStore = EntryPointAccessors.fromApplication(context.applicationContext,
            DictationServiceEntryPoint::class.java).dataStore()
        val oldPrefs = runBlocking { dataStore.data.first() }
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val oldMode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            context.applicationInfo.uid, context.packageName)
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var fixture: ActivityScenario<InsertionHarnessActivity>? = null
        try {
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            runBlocking { dataStore.edit {
                it[PreferenceKeys.COMPANION_ENABLED] = true
                it[PreferenceKeys.COMPANION_FLOATING] = true
                it[PreferenceKeys.COMPANION_DOCK_RIGHT] = true
                it[PreferenceKeys.COMPANION_Y] = .4f
            } }
            fixture = ActivityScenario.launch(Intent(context, InsertionHarnessActivity::class.java))
            instrument.runOnMainSync { CompanionLauncher.showOverlay(context) }
            await("floating control") { overlayBounds() != null }
            fixture.onActivity { assertTrue("Overlay must not steal input focus", it.primary.hasFocus()) }
            val before = requireNotNull(overlayBounds())
            val down = SystemClock.uptimeMillis()
            val destinationY = (before.centerY() - 150).coerceAtLeast(before.height())
            touch(down, down, MotionEvent.ACTION_DOWN, before.centerX().toFloat(), before.centerY().toFloat())
            touch(down, down + 100, MotionEvent.ACTION_MOVE, before.centerX().toFloat(), destinationY.toFloat())
            touch(down, down + 200, MotionEvent.ACTION_UP, before.centerX().toFloat(), destinationY.toFloat())
            await("position persistence") {
                runBlocking { dataStore.data.first()[PreferenceKeys.COMPANION_Y] != .4f }
            }
            await("moved window bounds from $before, prefs=" + runBlocking {
                dataStore.data.first()[PreferenceKeys.COMPANION_Y]
            }) { overlayBounds()?.top?.let { it < before.top } == true }
            val moved = requireNotNull(overlayBounds())
            instrument.runOnMainSync { CompanionLauncher.hideOverlay(context) }
            await("hidden control") { overlayBounds() == null }
            instrument.runOnMainSync { CompanionLauncher.showOverlay(context) }
            await("restored control") { overlayBounds() != null }
            assertEquals(moved.top, requireNotNull(overlayBounds()).top)
            fixture.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("landscape display") { context.resources.displayMetrics.widthPixels > context.resources.displayMetrics.heightPixels }
            await("in-bounds rotated overlay") {
                overlayBounds()?.let { bounds ->
                    bounds.left >= 0 && bounds.top >= 0 && bounds.right <= context.resources.displayMetrics.widthPixels &&
                        bounds.bottom <= context.resources.displayMetrics.heightPixels
                } == true
            }
        } finally {
            instrument.runOnMainSync { CompanionLauncher.hideOverlay(context) }
            fixture?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            fixture?.close()
            runBlocking { dataStore.updateData { oldPrefs } }
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW ${when (oldMode) {
                AppOpsManager.MODE_ALLOWED -> "allow"
                AppOpsManager.MODE_IGNORED -> "ignore"
                AppOpsManager.MODE_ERRORED -> "deny"
                else -> "default"
            }}")
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun overlayBounds(): Rect? {
        val windows = automation.windows
        try {
            for (window in windows) {
                val root = window.root ?: continue
                try {
                    if (!root.refresh()) continue
                    if (root.packageName == context.packageName &&
                        root.contentDescription == context.getString(R.string.companion_tap_start)) {
                        return Rect().also { root.getBoundsInScreen(it) }
                    }
                } finally { root.recycle() }
            }
        } finally { windows.forEach { it.recycle() } }
        return null
    }

    private fun touch(down: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for $label")
    }
}
