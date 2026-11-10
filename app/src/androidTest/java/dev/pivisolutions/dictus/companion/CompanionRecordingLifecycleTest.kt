package dev.pivisolutions.dictus.companion

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.companion.insertion.InsertionHarnessActivity
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationState
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.DictationService
import dev.pivisolutions.dictus.service.DictationServiceEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual microphone/FGS acquisition on an unlocked test device, with explicit permission.
 * Requires a downloaded Tiny model fixture; captures only until RECORDING is acknowledged,
 * then cancels. This does not establish microphone quality, transcription latency or speech WER. */
@RunWith(AndroidJUnit4::class)
class CompanionRecordingLifecycleTest {
    private val tileComponent get() = ComponentName(
        InstrumentationRegistry.getInstrumentation().targetContext, CompanionTileService::class.java,
    ).flattenToString()
    private var lastTileState = "tile not found"
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(*
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.RECORD_AUDIO))

    @Test fun visibleActivationAcquiresMicrophoneCancelsAndKeepsDefaultKeyboard() {
        exerciseRecording(tile = false)
    }

    @Test fun quickSettingsStartsVisibleCaptureAndSecondClickStopsIt() {
        exerciseRecording(tile = true)
    }

    @Test fun notificationStopEndsCaptureThroughItsActualPendingIntent() {
        exerciseRecording(tile = false, notificationCancel = false)
    }

    @Test fun notificationCancelDiscardsCaptureThroughItsActualPendingIntent() {
        exerciseRecording(tile = false, notificationCancel = true)
    }

    private fun exerciseRecording(tile: Boolean, notificationCancel: Boolean? = null) {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        assumeTrue("Seed the downloaded Tiny model before this opt-in capture test",
            ModelManager(context).getModelPath("tiny") != null)
        val automation = instrument.uiAutomation
        val originalAutomationFlags = automation.serviceInfo.flags
        val originalTiles = if (tile) shell("settings get secure sysui_qs_tiles") else null
        if (tile) automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val dataStore = EntryPointAccessors.fromApplication(context.applicationContext,
            DictationServiceEntryPoint::class.java).dataStore()
        val previous = runBlocking { dataStore.data.first() }
        val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val coordinator = EntryPointAccessors.fromApplication(context.applicationContext,
            CompanionEntryPoint::class.java).companionCoordinator()
        var controller: DictationService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                controller = (binder as DictationService.LocalBinder).getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) { controller = null }
        }
        var bound = false
        var fixture: ActivityScenario<InsertionHarnessActivity>? = null
        var activation: ActivityScenario<CompanionActivationActivity>? = null
        try {
            runBlocking { dataStore.edit {
                it[PreferenceKeys.COMPANION_ENABLED] = true
                it[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION] = false
                it[PreferenceKeys.ACTIVE_MODEL] = "tiny"
            } }
            fixture = ActivityScenario.launch(Intent(context, InsertionHarnessActivity::class.java))
            bound = context.bindService(Intent(context, DictationService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            await("shared service binding") { controller != null }
            if (tile) {
                shell("cmd statusbar add-tile $tileComponent")
                showTilePage(context.getString(dev.pivisolutions.dictus.R.string.companion_tile_label))
                shell("cmd statusbar click-tile $tileComponent")
            } else {
                activation = ActivityScenario.launch(Intent(context, CompanionActivationActivity::class.java))
            }
            await("recording acknowledgment") { coordinator.state.value.phase == CompanionPhase.RECORDING }
            assertTrue(controller!!.state.value is DictationState.Recording)
            val audio = context.getSystemService(AudioManager::class.java)
            await("active microphone") { audio.activeRecordingConfigurations.isNotEmpty() }
            instrument.runOnMainSync {
                controller!!.startRecording() // A simultaneous legacy IME caller must be refused.
                controller!!.cancelRecording()
            }
            assertEquals(CompanionPhase.RECORDING, coordinator.state.value.phase)
            if (notificationCancel != null) {
                val notifications = context.getSystemService(NotificationManager::class.java)
                val stopTitle = context.getString(dev.pivisolutions.dictus.R.string.companion_stop)
                val cancelTitle = context.getString(dev.pivisolutions.dictus.R.string.companion_cancel)
                await("recording notification Stop and Cancel actions") {
                    val actions = notifications.activeNotifications
                        .firstOrNull { it.id == DictationService.NOTIFICATION_ID }?.notification?.actions
                    actions?.any { it.title.toString() == stopTitle } == true &&
                        actions.any { it.title.toString() == cancelTitle }
                }
                val recordingNotification = notifications.activeNotifications
                    .first { it.id == DictationService.NOTIFICATION_ID }.notification
                assertEquals(context.getString(dev.pivisolutions.dictus.R.string.companion_recording),
                    recordingNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
                val requestedTitle = if (notificationCancel) cancelTitle else stopTitle
                recordingNotification.actions.first { it.title.toString() == requestedTitle }.actionIntent.send()
            } else if (tile) {
                await("recording activation returned to the original window") {
                    var focused = false
                    fixture?.onActivity { focused = it.hasWindowFocus() }
                    focused
                }
                showTilePage(context.getString(dev.pivisolutions.dictus.R.string.companion_tile_label))
                shell("cmd statusbar click-tile $tileComponent")
            } else instrument.runOnMainSync { coordinator.cancel() }
            await("microphone release") { audio.activeRecordingConfigurations.isEmpty() }
            if (tile || notificationCancel == false) await("stop completion") { !coordinator.state.value.busy }
            else await("cancel completion") { coordinator.state.value.phase == CompanionPhase.CANCELLED }
            assertEquals(DictationState.Idle, controller!!.state.value)
            assertEquals(ime, Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        } finally {
            instrument.runOnMainSync { coordinator.cancel() }
            activation?.close()
            fixture?.close()
            if (bound) context.unbindService(connection)
            if (tile) {
                shell("cmd statusbar collapse")
                if (originalTiles?.contains(".companion.CompanionTileService") != true)
                    shell("cmd statusbar remove-tile $tileComponent")
            }
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalAutomationFlags }
            runBlocking { dataStore.updateData { previous } }
        }
    }

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        }

    private fun shadeTileReady(text: String): Boolean {
        val windows = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        try {
            for (window in windows) {
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != "com.android.systemui" || !root.refresh()) continue
                    val matches = root.findAccessibilityNodeInfosByText(text)
                    try {
                        for (match in matches) {
                            var node: android.view.accessibility.AccessibilityNodeInfo? =
                                android.view.accessibility.AccessibilityNodeInfo.obtain(match)
                            repeat(8) {
                                val current = node ?: return@repeat
                                try {
                                    lastTileState = "${current.className}: enabled=${current.isEnabled}, " +
                                        "clickable=${current.isClickable}, state=${current.stateDescription}, " +
                                        "description=${current.contentDescription}"
                                    if (current.isClickable && current.isEnabled) return true
                                    node = current.parent
                                } finally { current.recycle() }
                            }
                            node?.recycle()
                        }
                    } finally { matches.forEach { it.recycle() } }
                } finally { root.recycle() }
            }
        } finally { windows.forEach { it.recycle() } }
        return false
    }

    private fun quickSettingsPager(): android.view.accessibility.AccessibilityNodeInfo? {
        val windows = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        try {
            for (window in windows) {
                val root = window.root ?: continue
                try {
                    if (root.packageName?.toString() != "com.android.systemui") continue
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/qs_pager")
                    try {
                        nodes.firstOrNull()?.let {
                            return android.view.accessibility.AccessibilityNodeInfo.obtain(it)
                        }
                    } finally { nodes.forEach { it.recycle() } }
                } finally { root.recycle() }
            }
        } finally { windows.forEach { it.recycle() } }
        return null
    }

    private fun showTilePage(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var expanded = false
        // SystemUI can consume an expansion while the previous tile launch is still
        // collapsing its shade. Retry only when no expanded pager/tile is exposed.
        repeat(3) {
            if (!expanded) {
                automation.waitForIdle(200, 5_000)
                shell("cmd statusbar expand-settings")
                val deadline = SystemClock.uptimeMillis() + 5_000
                do {
                    expanded = shadeTileReady(label) ||
                        (quickSettingsPager()?.let { it.recycle(); true } ?: false)
                    if (expanded) break
                    SystemClock.sleep(25)
                } while (SystemClock.uptimeMillis() < deadline)
            }
        }
        assertTrue("Expanded Quick Settings pager unavailable; $lastTileState", expanded)
        automation.waitForIdle(100, 5_000)
        // Reopening the shade preserves its last page. Start the search at page one,
        // rather than repeatedly asking the last page to scroll farther forward.
        repeat(5) {
            val pager = quickSettingsPager() ?: return@repeat
            try {
                if (pager.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
                    automation.waitForIdle(100, 5_000)
            } finally { pager.recycle() }
        }
        repeat(5) {
            if (shadeTileReady(label)) return
            val pager = quickSettingsPager() ?: return@repeat
            try {
                automation.executeAndWaitForEvent({
                    assertTrue("Quick Settings pager must support semantic next-page navigation",
                        pager.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                }, { event -> event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED }, 5_000)
                automation.waitForIdle(100, 5_000)
            } finally { pager.recycle() }
        }
        fail("Could not find enabled tile on Quick Settings pages; $lastTileState")
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for $label; $lastTileState")
    }
}
