package dev.pivisolutions.dictus.companion.insertion

import android.app.Application
import android.widget.EditText
import android.text.InputType
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class CompanionAccessibilityServiceTest {
    private fun identity(packageName: String = "example", className: String = "android.widget.EditText") =
        EditorIdentity(1, 0, packageName, -1, emptyList(), "id", null, className)

    private fun node(): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().apply {
        packageName = "example"
        className = "android.widget.EditText"
        isEditable = true
        isEnabled = true
        isVisibleToUser = true
        isFocused = true
        text = "hello"
        setTextSelection(2, 2)
        addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT)
        addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
    }

    @Test fun `plain native editor snapshot is a string copy`() {
        val node = node()
        val handle = AndroidEditorHandle(node, identity(), "dictus")
        assertNull(handle.rejection())
        val snapshot = handle.snapshot()!!
        node.text = "changed"
        assertEquals("hello", snapshot.text)
        assertEquals(2, snapshot.start)
    }

    @Test fun `default native EditText is not implicitly sensitive on Android 14 plus`() {
        val view = EditText(RuntimeEnvironment.getApplication())
        assertFalse(view.isAccessibilityDataSensitive)
        assertFalse(view.createAccessibilityNodeInfo().isAccessibilityDataSensitive)
    }

    @Test fun `native editing spans are plain but document styles remain unsupported`() {
        val editable = EditText(RuntimeEnvironment.getApplication()).apply {
            setText("hello")
            setSelection(2)
        }
        assertTrue(plainEditorText(editable.text))
        val styled = SpannableString("hello").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        assertFalse(plainEditorText(styled))
    }

    @Test fun `spell checking is editing metadata rather than document formatting`() {
        val text = android.text.SpannableString("hello").apply {
            val annotation = Class.forName("android.text.style.SpellCheckSpan").getDeclaredConstructor().newInstance()
            setSpan(annotation, 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        assertTrue(plainEditorText(text))
    }

    @Test fun `IME composing underline is plain but committed underline and other styles are not`() {
        val text = android.text.SpannableString("hello")
        val underline = android.text.style.UnderlineSpan()
        text.setSpan(underline, 0, text.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or android.text.Spanned.SPAN_COMPOSING)
        assertTrue(plainEditorText(text))
        text.setSpan(underline, 0, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertFalse(plainEditorText(text))
        text.removeSpan(underline)
        text.setSpan(StyleSpan(Typeface.BOLD), 0, text.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or android.text.Spanned.SPAN_COMPOSING)
        assertFalse(plainEditorText(text))
    }

    @Test fun `native dynamic layout observer does not turn a plain editor into a rich document`() {
        val text = android.text.SpannableStringBuilder("hello")
        val layout = android.text.DynamicLayout.Builder.obtain(text, android.text.TextPaint(), 400).build()
        assertSame(text, layout.text)
        assertTrue(text.getSpans(0, text.length, Any::class.java).any {
            it.javaClass.name == "android.text.DynamicLayout\$ChangeWatcher"
        })
        assertTrue(plainEditorText(text))
    }

    @Test fun `empty field hint is never treated as entered text`() {
        val node = node().apply {
            text = "Type your message"
            isShowingHintText = true
            setTextSelection(0, 0)
        }
        assertEquals("", AndroidEditorHandle(node, identity(), "dictus").snapshot()!!.text)
    }

    @Test fun `adapter exposes max length and rejects oversized text snapshots`() {
        val node = node().apply { maxTextLength = 7 }
        val handle = AndroidEditorHandle(node, identity(), "dictus")
        assertEquals(7, handle.maximumLength())
        node.text = "x".repeat(MAX_EDITOR_TEXT_LENGTH + 1)
        assertNull(handle.snapshot())
    }

    @Test fun `all password variations and sensitive metadata reject`() {
        val inputs = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        inputs.forEach { input ->
            val node = node().apply { inputType = input }
            assertEquals(InsertionFailure.SENSITIVE, AndroidEditorHandle(node, identity(), "dictus").rejection())
            assertNull(AndroidEditorHandle(node, identity(), "dictus").snapshot())
        }
        assertFalse(sensitiveInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        val password = node().apply { isPassword = true }
        assertEquals(InsertionFailure.SENSITIVE, AndroidEditorHandle(password, identity(), "dictus").rejection())
        val sensitive = node().apply { isAccessibilityDataSensitive = true }
        assertEquals(InsertionFailure.SENSITIVE, AndroidEditorHandle(sensitive, identity(), "dictus").rejection())
    }

    @Test fun `ordinary numeric PIN and OTP without password flag never read text`() {
        listOf(InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED).forEach { type ->
            val node = CountingNode().apply { inputType = type; isPassword = false }
            val handle = AndroidEditorHandle(node, identity(), "dictus")
            assertEquals(InsertionFailure.SENSITIVE, handle.rejection())
            assertNull(handle.snapshot())
            assertEquals(0, node.textReads)
        }
    }

    @Test fun `virtual EditText inside WebView rejects before reading text`() {
        val parent = AccessibilityNodeInfo.obtain().apply { className = "android.webkit.WebView" }
        val node = CountingNode(parent).apply { className = "android.widget.EditText" }
        assertEquals(InsertionFailure.UNSUPPORTED, AndroidEditorHandle(node, identity(), "dictus").rejection())
        assertNull(AndroidEditorHandle(node, identity(), "dictus").snapshot())
        assertEquals(0, node.textReads)
    }

    private class CountingNode(private val ancestor: AccessibilityNodeInfo? = null) : AccessibilityNodeInfo() {
        var textReads = 0
        override fun getText(): CharSequence {
            textReads++
            error("Unsafe text read")
        }
        override fun getParent(): AccessibilityNodeInfo? = ancestor?.let { AccessibilityNodeInfo.obtain(it) }
    }

    @Test fun `rich spans and unknown editor classes reject`() {
        val rich = node().apply {
            text = SpannableString("hello").apply { setSpan(StyleSpan(Typeface.BOLD), 0, 3, 0) }
        }
        assertNull(AndroidEditorHandle(rich, identity(), "dictus").snapshot())
        assertEquals(InsertionFailure.UNSUPPORTED, AndroidEditorHandle(node(), identity(className = "android.webkit.WebView"), "dictus").rejection())
    }

    @Test fun `safe own app editor is allowed while system UI and unsafe editors reject`() {
        val own = node().apply { packageName = "dictus" }
        assertNull(AndroidEditorHandle(own, identity("dictus"), "dictus").rejection())
        listOf("com.android.systemui").forEach { packageName ->
            val node = node().apply { this.packageName = packageName }
            assertEquals(InsertionFailure.UNSUPPORTED, AndroidEditorHandle(node, identity(packageName), "dictus").rejection())
        }
        val mutations: List<(AccessibilityNodeInfo) -> Unit> = listOf(
            { it.isFocused = false }, { it.isEditable = false },
            { it.isVisibleToUser = false }, { it.isEnabled = false },
            { it.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT) },
            { it.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION) },
        )
        mutations.forEach { mutate ->
            val node = node().also(mutate)
            assertEquals(InsertionFailure.UNSUPPORTED, AndroidEditorHandle(node, identity(), "dictus").rejection())
        }
    }

    @Test fun `focus leave and return permanently advances capture generation`() {
        val service = Robolectric.buildService(CompanionAccessibilityService::class.java).create().get()
        val initial = service.generation
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED))
        assertEquals(initial, service.generation)
        listOf(AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED).forEach { type ->
            service.transition.arm(identity())
            service.onAccessibilityEvent(AccessibilityEvent.obtain(type))
        }
        assertEquals(initial + 3, service.generation)
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
        assertEquals(initial + 3, service.generation)
        service.onInterrupt()
        assertNull(CompanionAccessibilityService.connected)
        service.onDestroy()
    }

    @Test fun `connection registry clears on interruption and old destruction preserves new connection`() {
        val first = Robolectric.buildService(CompanionAccessibilityService::class.java).create().get()
        val second = Robolectric.buildService(CompanionAccessibilityService::class.java).create().get()
        val connect = CompanionAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply {
            isAccessible = true
        }
        connect.invoke(first)
        val firstConnection = first.connection
        assertSame(first, CompanionAccessibilityService.connected)
        first.onInterrupt()
        assertNull(CompanionAccessibilityService.connected)
        connect.invoke(second)
        assertTrue(second.connection > firstConnection)
        first.onDestroy()
        assertSame(second, CompanionAccessibilityService.connected)
        second.onDestroy()
        assertNull(CompanionAccessibilityService.connected)
    }
}
