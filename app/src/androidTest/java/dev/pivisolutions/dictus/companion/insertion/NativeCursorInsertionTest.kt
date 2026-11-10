package dev.pivisolutions.dictus.companion.insertion

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests of the real ACTION_SET_TEXT / ACTION_SET_SELECTION implementation. No enabled
 * CompanionAccessibilityService is needed. This covers native insertion, not external-app service
 * discovery, activation transitions, microphone eligibility, or foreground-service ownership.
 */
@RunWith(AndroidJUnit4::class)
class NativeCursorInsertionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<InsertionHarnessActivity>
    private lateinit var access: AutomationEditorAccess
    private lateinit var manager: TextInsertionManager
    private var originalAutomationFlags: Int? = null

    @Before fun launchFixture() {
        val automation = instrumentation.uiAutomation
        originalAutomationFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        access = AutomationEditorAccess(automation, instrumentation.targetContext.packageName)
        manager = TextInsertionManager(access)
        scenario = ActivityScenario.launch(Intent(instrumentation.targetContext, InsertionHarnessActivity::class.java))
        configure("prefix suffix", 7, 7)
    }

    @After fun closeFixture() {
        try {
            if (::manager.isInitialized) manager.release()
            if (::scenario.isInitialized) scenario.close()
        } finally {
            originalAutomationFlags?.let { original ->
                val automation = instrumentation.uiAutomation
                automation.serviceInfo = automation.serviceInfo.apply { flags = original }
            }
        }
    }

    @Test fun insertsAtCursorAndPreservesBothSides() {
        val target = capture()
        assertEquals(InsertionResult.Inserted, manager.insert(target, "spoken "))
        assertEditor("prefix spoken suffix", 14)
        assertEquals(1, access.textActions)
        assertEquals("Middle insertion must issue real ACTION_SET_SELECTION", 1, access.selectionActions)
        assertTrue("Post-write verification must reacquire nodes", access.acquisitions >= 4)
    }

    @Test fun replacesSelectedUnicodeRangeAndRestoresUtf16Cursor() {
        val before = "left \uD83D\uDE00 café right"
        val start = before.indexOf("\uD83D\uDE00")
        val end = before.indexOf(" right")
        val transcript = "\uD83E\uDDEA e\u0301"
        configure(before, start, end)
        assertEquals(InsertionResult.Inserted, manager.insert(capture(), transcript))
        assertEditor("left $transcript right", start + transcript.length)
        assertEquals(1, access.textActions)
        assertEquals(1, access.selectionActions)
    }

    @Test fun imeComposingUnderlinePreservesExistingTextDuringNativeInsertion() {
        scenario.onActivity { activity ->
            val composing = android.text.SpannableString("draft ").apply {
                setSpan(android.text.style.UnderlineSpan(), 0, length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val connection = requireNotNull(activity.primary.onCreateInputConnection(
                android.view.inputmethod.EditorInfo()))
            assertTrue(connection.setComposingText(composing, 1))
            assertTrue(activity.primary.text.getSpans(0, activity.primary.length(),
                android.text.style.UnderlineSpan::class.java).any {
                activity.primary.text.getSpanFlags(it) and android.text.Spanned.SPAN_COMPOSING != 0
            })
        }
        instrumentation.waitForIdleSync()
        assertEditor("prefix draft suffix", 13)
        assertEquals(InsertionResult.Inserted, manager.insert(capture(), "spoken "))
        assertEditor("prefix draft spoken suffix", 20)
        assertEquals(1, access.textActions)
    }

    @Test fun committedUnderlineRemainsUnsupportedWithoutTextMutation() {
        scenario.onActivity { activity ->
            activity.primary.text.setSpan(android.text.style.UnderlineSpan(), 0, 6,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        instrumentation.waitForIdleSync()
        assertNull(manager.capture())
        assertEditor("prefix suffix", 7)
        assertNoActions()
    }

    @Test fun rejectsSelectionInsideSurrogatePairWithoutMutation() {
        configure("a\uD83D\uDE00b", 2, 2)
        assertNull(manager.capture())
        assertEditor("a\uD83D\uDE00b", 2)
        assertNoActions()
    }

    @Test fun malformedUnicodeTranscriptRejectsWithoutMutation() {
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_SELECTION),
            manager.insert(capture(), "broken \uD83D"))
        assertEditor("prefix suffix", 7)
        assertNoActions()
    }

    @Test fun passwordAndNumericEditorsRejectBeforeSnapshotOrAction() {
        for (kind in listOf(InsertionHarnessActivity.Kind.PASSWORD, InsertionHarnessActivity.Kind.NUMBER)) {
            configure("12345", 2, 2, kind)
            access.resetCounts()
            assertNull("Unsafe $kind capture", manager.capture())
            assertEquals("Sensitive editor must reject before snapshot", 0, access.snapshots)
            assertNoActions()
            assertEditor("12345", 2)
        }
    }

    @Test fun richTextRejectsWithoutMutation() {
        configure("styled text", 3, 3, InsertionHarnessActivity.Kind.RICH)
        access.resetCounts()
        assertNull("Parcelable StyleSpan must survive the accessibility snapshot", manager.capture())
        assertTrue(access.snapshots > 0)
        assertNoActions()
        assertEditor("styled text", 3)
        scenario.onActivity { activity ->
            assertTrue((activity.primary.text as android.text.Spanned)
                .getSpans(0, activity.primary.length(), android.text.style.StyleSpan::class.java).isNotEmpty())
        }
        configure("styled text", 3, 3)
        val target = capture()
        configure("styled text", 3, 3, InsertionHarnessActivity.Kind.RICH)
        access.resetCounts()
        assertEquals(InsertionResult.Rejected(InsertionFailure.UNSUPPORTED), manager.insert(target, "stale"))
        assertEditor("styled text", 3)
        assertNoActions()
    }

    @Test fun becomingProtectedAfterCaptureRejectsBeforeSnapshotOrAction() {
        for (kind in listOf(InsertionHarnessActivity.Kind.PASSWORD, InsertionHarnessActivity.Kind.NUMBER)) {
            configure("12345", 2, 2)
            val target = capture()
            configure("12345", 2, 2, kind)
            access.resetCounts()
            assertEquals(InsertionResult.Rejected(InsertionFailure.SENSITIVE), manager.insert(target, "secret"))
            assertEquals(0, access.snapshots)
            assertNoActions()
            assertEditor("12345", 2)
        }
    }

    @Test fun changedTextRejectsAndConsumesTarget() {
        val target = capture()
        configure("user changed this", 7, 7)
        assertEquals(InsertionResult.Rejected(InsertionFailure.TEXT_CHANGED), manager.insert(target, "stale"))
        assertEquals(InsertionResult.Rejected(InsertionFailure.INVALID_TARGET), manager.insert(target, "retry"))
        assertEditor("user changed this", 7)
        assertNoActions()
    }

    @Test fun movedSelectionRejectsWithoutMutation() {
        val target = capture()
        configure("prefix suffix", 1, 1)
        assertEquals(InsertionResult.Rejected(InsertionFailure.SELECTION_CHANGED), manager.insert(target, "stale"))
        assertEditor("prefix suffix", 1)
        assertNoActions()
    }

    @Test fun differentFocusedFieldRejectsWithoutMutatingEitherEditor() {
        val target = capture()
        scenario.onActivity { it.secondary.requestFocus(); it.secondary.setSelection(3) }
        awaitFocus(InsertionHarnessActivity.SECONDARY)
        assertEquals(InsertionResult.Rejected(InsertionFailure.TARGET_CHANGED), manager.insert(target, "stale"))
        scenario.onActivity {
            assertEquals("prefix suffix", it.primary.text.toString())
            assertEquals("other field", it.secondary.text.toString())
            assertEquals(3, it.secondary.selectionStart)
            assertEquals(3, it.secondary.selectionEnd)
        }
        assertNoActions()
    }

    private fun capture(): InsertionTarget {
        val result = manager.capture()
        assertNotNull("Expected a supported focused native EditText: ${access.diagnostic()}", result)
        return requireNotNull(result)
    }

    private fun configure(text: String, start: Int, end: Int, kind: InsertionHarnessActivity.Kind = InsertionHarnessActivity.Kind.PLAIN) {
        scenario.onActivity { it.configure(text, start, end, kind) }
        awaitFocus(InsertionHarnessActivity.PRIMARY)
    }

    private fun awaitFocus(label: String) {
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            if (access.focusLabel() == label) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Fixture must be visible on an unlocked device with $label focused")
    }

    private fun assertEditor(text: String, cursor: Int) {
        scenario.onActivity {
            assertEquals(text, it.primary.text.toString())
            assertEquals(cursor, it.primary.selectionStart)
            assertEquals(cursor, it.primary.selectionEnd)
        }
    }

    private fun assertNoActions() {
        assertEquals(0, access.textActions)
        assertEquals(0, access.selectionActions)
    }
}

/**
 * Blocking UiAutomation IPC runs on the instrumentation thread so the target app's main thread can
 * execute performAction. Only acquisition is supplied by this test: every operation delegates to
 * the production AndroidEditorHandle. Identity uses the live window, structural path and node IDs;
 * connection/generation stay fixed because this fixture deliberately does not test service events.
 */
private class AutomationEditorAccess(
    private val automation: UiAutomation,
    private val ownPackage: String,
) : EditorAccess {
    var acquisitions = 0
        private set
    var snapshots = 0
        private set
    var textActions = 0
        private set
    var selectionActions = 0
        private set

    fun resetCounts() { acquisitions = 0; snapshots = 0; textActions = 0; selectionActions = 0 }

    fun focusLabel(): String? = withFocusedNode { node, _ -> node?.contentDescription?.toString() }

    fun diagnostic(): String = withFocusedNode { node, path ->
        if (node == null) "no node" else {
            val identity = EditorIdentity(1L, 0L, node.packageName.toString(), node.windowId, path,
                node.viewIdResourceName, if (android.os.Build.VERSION.SDK_INT >= 33) node.uniqueId else null,
                node.className.toString())
            val handle = AndroidEditorHandle(node, identity, ownPackage)
            "class=${node.className}, rejection=${handle.rejection()}, selection=${node.textSelectionStart}:${node.textSelectionEnd}, spans=" +
                ((node.text as? android.text.Spanned)?.getSpans(0, node.text.length, Any::class.java)?.map { it.javaClass.name })
        }
    }

    override fun <T> withEditor(block: (EditorHandle?) -> T): T = withFocusedNode { node, path ->
        if (node == null) return@withFocusedNode block(null)
        acquisitions++
        val identity = EditorIdentity(
            connection = 1L, generation = 0L,
            packageName = node.packageName.toString(), windowId = node.windowId,
            path = path, viewId = node.viewIdResourceName,
            uniqueId = if (android.os.Build.VERSION.SDK_INT >= 33) node.uniqueId else null,
            className = node.className.toString(),
        )
        val delegate = AndroidEditorHandle(node, identity, ownPackage)
        block(object : EditorHandle by delegate {
            override fun snapshot(): EditorSnapshot? { snapshots++; return delegate.snapshot() }
            override fun setText(text: String): Boolean { textActions++; return delegate.setText(text) }
            override fun setSelection(cursor: Int): Boolean { selectionActions++; return delegate.setSelection(cursor) }
        })
    }

    private fun <T> withFocusedNode(block: (AccessibilityNodeInfo?, List<Int>) -> T): T {
        val root = automation.rootInActiveWindow ?: return block(null, emptyList())
        var found: Located? = null
        try {
            if (!root.refresh() || root.packageName?.toString() != ownPackage) return block(null, emptyList())
            found = findFocused(root, emptyList())
            val node = found?.node ?: return block(null, emptyList())
            if (!node.refresh()) return block(null, emptyList())
            return block(node, requireNotNull(found).path)
        } finally {
            if (found?.node !== root) found?.node?.recycleNode()
            root.recycleNode()
        }
    }

    private data class Located(val node: AccessibilityNodeInfo, val path: List<Int>)

    private fun findFocused(node: AccessibilityNodeInfo, path: List<Int>): Located? {
        if (!node.refresh()) return null
        if (node.isFocused && node.isEditable) return Located(node, path)
        if (path.size >= 64) return null
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = try { findFocused(child, path + index) } catch (failure: Throwable) {
                child.recycleNode()
                throw failure
            }
            if (result?.node !== child) child.recycleNode()
            if (result != null) return result
        }
        return null
    }
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleNode() = recycle()
