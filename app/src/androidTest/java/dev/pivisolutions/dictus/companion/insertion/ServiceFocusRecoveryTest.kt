package dev.pivisolutions.dictus.companion.insertion

import android.app.UiAutomation
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.EntryPointAccessors
import dev.pivisolutions.dictus.companion.CompanionActivationActivity
import dev.pivisolutions.dictus.companion.CompanionEntryPoint
import dev.pivisolutions.dictus.companion.CompanionLauncher
import dev.pivisolutions.dictus.companion.CompanionPhase
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.service.DictationServiceEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Emulator/device integration: an actual enabled service receives the activation round trip.
 * Secure-setting changes exist only in this instrumentation APK and are restored after each test. */
@RunWith(AndroidJUnit4::class)
class ServiceFocusRecoveryTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(*
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.RECORD_AUDIO))
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val context = instrument.targetContext
    private lateinit var automation: UiAutomation
    private var oldServices: String? = null
    private var oldEnabled = 0
    private var fixture: ActivityScenario<InsertionHarnessActivity>? = null
    private var activation: ActivityScenario<CompanionActivationActivity>? = null
    private lateinit var manager: TextInsertionManager
    private val events = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val preserveDeviceAccessibility = InstrumentationRegistry.getArguments()
        .getString("preserveDeviceAccessibility") == "true"

    @Before fun setUp() {
        automation = instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType in setOf(8, 32, 4194304)) {
                events += "${event.eventType}:${event.packageName}:${event.className}:${event.windowId}"
            }
        }
        oldServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        oldEnabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (!preserveDeviceAccessibility) secureSettings {
            val component = ComponentName(context, CompanionAccessibilityService::class.java).flattenToString()
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                listOfNotNull(oldServices?.takeIf { it.isNotBlank() }, component).joinToString(":"))
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }
        await("accessibility service connection") { CompanionAccessibilityService.connected != null }
        // Initialize the production coordinator before a separate manager captures this test field.
        instrument.runOnMainSync {
            EntryPointAccessors.fromApplication(context.applicationContext,
                CompanionEntryPoint::class.java).companionCoordinator()
        }
        fixture = ActivityScenario.launch(Intent(context, InsertionHarnessActivity::class.java))
        instrument.waitForIdleSync()
        manager = TextInsertionManager(context)
        await("native fixture focus") {
            CompanionAccessibilityService.connected?.rootInActiveWindow?.let { root ->
                try { root.packageName?.toString() == context.packageName &&
                    root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)?.let { focus ->
                        try { focus.contentDescription == InsertionHarnessActivity.PRIMARY } finally { focus.recycle() }
                    } == true
                } finally { root.recycle() }
            } == true
        }
        automation.waitForIdle(200, 5_000)
    }

    @After fun tearDown() {
        if (::automation.isInitialized) automation.setOnAccessibilityEventListener(null)
        if (::manager.isInitialized) manager.release()
        activation?.close()
        fixture?.close()
        if (::automation.isInitialized && !preserveDeviceAccessibility) secureSettings {
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, oldServices)
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, oldEnabled)
        }
    }

    @Test fun actualActivationReturnsToOriginalFieldAndInserts() {
        val target = requireNotNull(manager.capture()) { "Expected native target capture" }
        manager.beginActivation()
        activation = ActivityScenario.launch(Intent(context, CompanionActivationActivity::class.java)
            .putExtra(CompanionActivationActivity.EXTRA_CONTROLS, true))
        instrument.waitForIdleSync()
        assertNotNull("Activation must retain target metadata", CompanionAccessibilityService.connected?.transition?.target)
        manager.endActivation()
        // ActivityScenario.close() introduces an unrelated test-package EmptyActivity. Production
        // correctly rejects that focus switch. Finish as the real activation activity does.
        activation?.onActivity { it.finish() }
        await("original focus recovery") { CompanionAccessibilityService.connected?.transition?.ready == true }
        assertEquals(InsertionResult.Inserted, manager.insert(target, "spoken "))
        fixture?.onActivity {
            assertEquals("prefix spoken suffix", it.primary.text.toString())
            assertEquals(14, it.primary.selectionStart)
        }
    }

    @Test fun unrelatedFocusPermanentlyInvalidatesTarget() {
        val target = requireNotNull(manager.capture())
        fixture?.onActivity { it.secondary.requestFocus() }
        await("focus invalidation") { CompanionAccessibilityService.connected?.transition?.target == null }
        fixture?.onActivity { it.primary.requestFocus() }
        instrument.waitForIdleSync()
        assertTrue(manager.insert(target, "stale") is InsertionResult.Rejected)
        fixture?.onActivity { assertEquals("prefix suffix", it.primary.text.toString()) }
    }

    @Test fun externalApplicationRetainsKeyboardAndPreservesSelectedText() {
        val externalPackage = instrument.context.packageName
        assertNotEquals("Fixture must be outside Dictus", context.packageName, externalPackage)
        assertNotEquals("Fixture must have its own application UID", context.applicationInfo.uid,
            context.packageManager.getApplicationInfo(externalPackage, 0).uid)
        val originalIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        context.startActivity(Intent().setComponent(ComponentName(
            externalPackage, ExternalEditorActivity::class.java.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        try {
            await("external editor and visible regular keyboard") {
                ServiceEditorAccess(context.packageName).withEditor { handle ->
                    (handle as? AndroidEditorHandle)?.identity?.packageName == externalPackage
                } && CompanionAccessibilityService.connected?.windows?.let { windows ->
                    try { windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
                    finally { windows.forEach { it.recycle() } }
                } == true
            }
            automation.waitForIdle(200, 5_000)
            var captured: InsertionTarget? = null
            instrument.runOnMainSync {
                captured = manager.capture()
                manager.beginActivation()
            }
            val target = requireNotNull(captured) { "Expected external native editor capture" }
            activation = ActivityScenario.launch(Intent(context, CompanionActivationActivity::class.java)
                .putExtra(CompanionActivationActivity.EXTRA_CONTROLS, true))
            instrument.waitForIdleSync()
            instrument.runOnMainSync { manager.endActivation() }
            activation?.onActivity { it.finish() }
            await("external editor focus recovery") { CompanionAccessibilityService.connected?.transition?.ready == true }
            var insertion: InsertionResult? = null
            // Production calls this MainThread API on the main thread. Running it on the
            // runner thread allows accessibility callbacks to race its write/verification.
            instrument.runOnMainSync { insertion = manager.insert(target, "spoken") }
            assertEquals("Test event metadata=$events", InsertionResult.Inserted, insertion)
            ServiceEditorAccess(context.packageName).withEditor { handle ->
                val result = requireNotNull(handle?.snapshot())
                assertEquals(externalPackage, result.identity.packageName)
                assertEquals("prefix spoken suffix", result.text)
                assertEquals(13, result.start)
                assertEquals(13, result.end)
            }
            assertEquals(originalIme, Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        } finally {
            // A focus-driven IME can reopen between Back events. Explicitly finish this
            // test-only activity so it cannot steal focus in a later test/session.
            context.startActivity(Intent().setComponent(ComponentName(
                externalPackage, ExternalEditorActivity::class.java.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ExternalEditorActivity.EXTRA_FINISH, true))
            await("external test activity finished") {
                automation.rootInActiveWindow?.let { root ->
                    try { root.packageName?.toString() == context.packageName }
                    finally { root.recycle() }
                } == true
            }
        }
    }

    @Test fun realMicrophoneStartupPreservesTheArmedTargetWithNotificationsEnabled() {
        assumeTrue("Seed Tiny before this capture integration test", ModelManager(context).getModelPath("tiny") != null)
        val dataStore = EntryPointAccessors.fromApplication(context.applicationContext,
            DictationServiceEntryPoint::class.java).dataStore()
        val previous = runBlocking { dataStore.data.first() }
        val coordinator = EntryPointAccessors.fromApplication(context.applicationContext,
            CompanionEntryPoint::class.java).companionCoordinator()
        val originalIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        try {
            runBlocking { dataStore.edit {
                it[PreferenceKeys.COMPANION_ENABLED] = true
                it[PreferenceKeys.COMPANION_AUTOMATIC_INSERTION] = true
                it[PreferenceKeys.ACTIVE_MODEL] = "tiny"
            } }
            await("armed production insertion target") {
                instrument.runOnMainSync { coordinator.captureTarget() }
                CompanionAccessibilityService.connected?.transition?.target != null
            }
            val originalTarget = CompanionAccessibilityService.connected!!.transition.target
            instrument.runOnMainSync { CompanionLauncher.launch(context) }
            await("real microphone capture") { coordinator.state.value.phase == CompanionPhase.RECORDING }
            await("actual recording activation returned to its original field") {
                CompanionAccessibilityService.connected?.transition?.ready == true
            }
            assertEquals(originalTarget, CompanionAccessibilityService.connected!!.transition.target)
            fixture?.onActivity {
                assertTrue(it.primary.hasFocus())
                assertEquals("prefix suffix", it.primary.text.toString())
                assertEquals(7, it.primary.selectionStart)
            }
            assertEquals(originalIme, Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            instrument.runOnMainSync { coordinator.cancel() }
            assertNull("Cancellation must release target metadata", CompanionAccessibilityService.connected!!.transition.target)
        } finally {
            instrument.runOnMainSync { coordinator.cancel() }
            runBlocking { dataStore.updateData { previous } }
        }
    }

    private fun secureSettings(action: () -> Unit) {
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try { action() } finally { automation.dropShellPermissionIdentity() }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        val service = CompanionAccessibilityService.connected
        val root = service?.rootInActiveWindow
        val active = try { "${root?.packageName}:${root?.windowId}" } finally { root?.recycle() }
        fail("Timed out waiting for $description; target=${service?.transition?.target}, " +
            "generation=${service?.generation}, rejection=${service?.transition?.lastRejection}, active=$active; test event metadata=$events")
    }
}
