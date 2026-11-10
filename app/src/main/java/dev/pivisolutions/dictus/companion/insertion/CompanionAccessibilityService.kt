package dev.pivisolutions.dictus.companion.insertion

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.Spanned
import android.text.NoCopySpan
import android.text.method.SingleLineTransformationMethod
import android.text.style.UnderlineSpan
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * OS enablement is the opt-in; connection alone never captures text or performs an action.
 * Armed events inspect metadata only, never event source/text. Focus/window transitions invalidate
 * captures except a coordinator-bracketed, exactly identified activation activity round trip.
 */
class CompanionAccessibilityService : AccessibilityService() {
    internal val transition = ActivationTransition(
        dev.pivisolutions.dictus.companion.CompanionActivationActivity::class.java.name,
    )
    internal var generation = 0L
        private set
    internal var connection = 0L
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        connection = ++nextConnection
        connected = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (transition.target == null) return
        try {
            when (event?.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    // Resolve window and focus from the same root. Separate acquisitions can
                    // straddle the activity handoff and invent an unrelated focus transition.
                    val (activePackage, activeWindow, focused) = ServiceEditorAccess(packageName)
                        .withActiveWindow { pkg, window, editor ->
                            // Dialog buttons may hold input focus during activation. They are
                            // not editor targets; the exact named-window bracket still applies.
                            Triple(pkg, window, (editor as? AndroidEditorHandle)
                                ?.takeIf { it.editable }?.identity)
                        }
                    if (!transition.accept(
                        packageName, event.packageName?.toString(), event.className?.toString(),
                        event.windowId, event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED,
                        activePackage, activeWindow, focused,
                    )) {
                        generation++
                        if (dev.pivisolutions.dictus.BuildConfig.DEBUG) {
                            android.util.Log.d("DictusCompanion", "Target unavailable: ${transition.lastRejection}")
                        }
                    }
                }
            }
        } catch (_: RuntimeException) {
            transition.clear()
            generation++
        }
    }

    override fun onInterrupt() { disconnect() }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        transition.clear()
        generation++
        if (connected === this) connected = null
    }

    companion object {
        @Volatile internal var connected: CompanionAccessibilityService? = null
            private set
        private var nextConnection = 0L
    }
}

internal class ServiceEditorAccess(private val ownPackage: String) : EditorAccess {
    override fun arm(identity: EditorIdentity) {
        CompanionAccessibilityService.connected?.takeIf { it.connection == identity.connection }?.transition?.arm(identity)
    }
    override fun disarm() { CompanionAccessibilityService.connected?.transition?.clear() }
    override fun beginActivation() { CompanionAccessibilityService.connected?.transition?.begin() }
    override fun endActivation() { CompanionAccessibilityService.connected?.transition?.end() }
    override fun ready(): Boolean = CompanionAccessibilityService.connected?.transition?.ready == true
    override fun <T> withEditor(block: (EditorHandle?) -> T): T =
        withActiveWindow { _, _, editor -> block(editor) }

    internal fun <T> withActiveWindow(block: (String?, Int, EditorHandle?) -> T): T {
        val service = CompanionAccessibilityService.connected ?: return block(null, -1, null)
        val root = service.rootInActiveWindow ?: return block(null, -1, null)
        var focus: AccessibilityNodeInfo? = null
        try {
            if (!root.refresh()) return block(null, -1, null)
            fun absent(): T = block(root.packageName?.toString(), root.windowId, null)
            focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val node = focus ?: return absent()
            if (!node.refresh()) return absent()
            if (root.windowId != node.windowId || root.packageName != node.packageName) return absent()
            val path = pathToRoot(node, root) ?: return absent()
            val identity = EditorIdentity(
                service.connection, service.generation, node.packageName?.toString() ?: return absent(),
                node.windowId, path, node.viewIdResourceName,
                if (Build.VERSION.SDK_INT >= 33) node.uniqueId else null,
                node.className?.toString() ?: return absent(),
                node.hashCode(),
            )
            return block(root.packageName?.toString(), root.windowId, AndroidEditorHandle(node, identity, ownPackage))
        } finally {
            if (focus !== root) focus?.dispose()
            root.dispose()
        }
    }

    /** A bounded structural identity also distinguishes editors sharing a resource ID. */
    private fun pathToRoot(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): List<Int>? {
        val path = mutableListOf<Int>()
        var current = node
        try {
            repeat(64) {
                if (current == root) return path.reversed()
                val parent = current.parent ?: return null
                var found = -1
                try {
                    if (parent.childCount > 256) return null
                    for (index in 0 until parent.childCount) {
                        val child = parent.getChild(index) ?: continue
                        try {
                            if (child == current) { found = index; break }
                        } finally { child.dispose() }
                    }
                    if (found < 0) return null
                    path.add(found)
                    if (current !== node) current.dispose()
                    current = parent
                } finally {
                    if (current !== parent) parent.dispose()
                }
            }
            return null
        } finally {
            if (current !== node) current.dispose()
        }
    }
}

internal class AndroidEditorHandle(
    private val node: AccessibilityNodeInfo,
    internal val identity: EditorIdentity,
    private val ownPackage: String,
) : EditorHandle {
    internal val editable: Boolean get() = node.isEditable
    override fun rejection(): InsertionFailure? {
        // These checks precede *every* node.text access, including post-write verification.
        if (node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) ||
            sensitiveInputType(node.inputType)) return InsertionFailure.SENSITIVE
        ancestorRejection()?.let { return it }
        if (identity.packageName == "com.android.systemui" ||
            !node.isFocused || !node.isEditable || !node.isEnabled || !node.isVisibleToUser ||
            node.windowId != identity.windowId || node.packageName?.toString() != identity.packageName) {
            return InsertionFailure.UNSUPPORTED
        }
        // Whole-value SET_TEXT is unsafe for unknown rich/virtual editors. Support native plain
        // EditText only, and require both actions so middle insertion can restore the cursor.
        if (identity.className != "android.widget.EditText" ||
            node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } ||
            node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_SELECTION }) {
            return InsertionFailure.UNSUPPORTED
        }
        return null
    }

    /** WebView can expose a virtual HTML editor with the native EditText class name. */
    private fun ancestorRejection(): InsertionFailure? {
        var parent = node.parent
        repeat(64) {
            val current = parent ?: return null
            try {
                if (current.isPassword || (Build.VERSION.SDK_INT >= 34 && current.isAccessibilityDataSensitive)) {
                    return InsertionFailure.SENSITIVE
                }
                if (current.className?.toString()?.contains("WebView", ignoreCase = true) == true) {
                    return InsertionFailure.UNSUPPORTED
                }
                parent = current.parent
            } finally { current.dispose() }
        }
        parent?.dispose()
        return InsertionFailure.UNSUPPORTED
    }

    override fun snapshot(): EditorSnapshot? {
        if (rejection() != null) return null
        // Android may expose the hint as text for an empty field. It is not user content.
        val text = if (node.isShowingHintText) "" else node.text ?: ""
        if (text.length > MAX_EDITOR_TEXT_LENGTH) return null
        if (!plainEditorText(text)) return null
        return EditorSnapshot(identity, text.toString(), node.textSelectionStart, node.textSelectionEnd)
    }

    override fun maximumLength(): Int = node.maxTextLength

    override fun setText(text: String): Boolean = node.performAction(
        AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
    )

    override fun setSelection(cursor: Int): Boolean = node.performAction(
        AccessibilityNodeInfo.ACTION_SET_SELECTION,
        Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        },
    )
}

internal fun plainEditorText(text: CharSequence): Boolean = text !is Spanned ||
    text.getSpans(0, text.length, Any::class.java).all { span ->
        // Native Editable instances carry non-copying selection/change/key listeners.
        // These are editing machinery; document styles, links and other copyable spans are not.
        span is NoCopySpan || span is SingleLineTransformationMethod ||
            // This platform metadata type is hidden from the public SDK. Inspect only its
            // runtime type name; do not invoke hidden APIs or accept arbitrary annotations.
            span.javaClass.name == "android.text.style.SpellCheckSpan" ||
            // DynamicLayout's internal observer is not a document style and does not
            // implement NoCopySpan on all Android versions. Do not allow arbitrary watchers.
            span.javaClass.name == "android.text.DynamicLayout\$ChangeWatcher" ||
            // Samsung's IME underlines its composing region in an ordinary plain EditText.
            // A committed/document underline remains unsupported, as do other style spans.
            (span is UnderlineSpan && text.getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0)
    }

internal fun sensitiveInputType(input: Int): Boolean {
    val type = input and InputType.TYPE_MASK_CLASS
    val variation = input and InputType.TYPE_MASK_VARIATION
    return (type == InputType.TYPE_CLASS_TEXT && variation in setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )) || type == InputType.TYPE_CLASS_NUMBER
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.dispose() { recycle() }
