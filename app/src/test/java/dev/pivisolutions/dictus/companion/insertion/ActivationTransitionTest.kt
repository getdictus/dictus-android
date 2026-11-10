package dev.pivisolutions.dictus.companion.insertion

import org.junit.Assert.*
import org.junit.Test

class ActivationTransitionTest {
    private val original = EditorIdentity(1, 0, "editor", 3, listOf(0), "field", "node", "android.widget.EditText")
    private val transition = ActivationTransition("dictus.companion.CompanionActivationActivity").apply { arm(original) }
    private fun event(
        pkg: String? = "editor", className: String? = "EditorActivity", window: Int = 3,
        state: Boolean = true, focusEvent: Boolean = false,
        activePkg: String? = pkg, activeWindow: Int = window, focused: EditorIdentity? = original,
    ) = transition.accept("dictus", pkg, className, window, state, focusEvent, activePkg, activeWindow, focused)

    private fun activation() = event("dictus", "dictus.companion.CompanionActivationActivity", 9, focused = null)

    @Test fun `application id suffix does not change the known activation class`() {
        transition.begin()
        assertTrue(transition.accept("dictus.test", "dictus.test", "dictus.companion.CompanionActivationActivity",
            9, true, false, "dictus.test", 9, null))
        transition.end()
        assertTrue(event())
        assertTrue(transition.ready)
    }

    @Test fun `intentional activity round trip restores original target only after end`() {
        transition.begin()
        assertFalse(transition.ready)
        assertTrue(activation())
        assertTrue(event("dictus", null, 9, state = false, focused = null))
        transition.end()
        assertTrue(event())
        assertTrue(transition.ready)
        assertEquals(original, transition.target)
    }

    @Test fun `window list event preceding named activation needs exact confirmation`() {
        transition.begin()
        assertTrue(event(null, null, -1, state = false, activePkg = "dictus", activeWindow = 9, focused = null))
        assertFalse(transition.ready)
        assertTrue(activation())
        transition.end()
        assertTrue(event())
        assertTrue(transition.ready)
    }

    @Test fun `unconfirmed own window cannot grant insertion`() {
        transition.begin()
        assertTrue(event("dictus", null, 9, state = false, focused = null))
        transition.end()
        assertFalse(event())
        assertNull(transition.target)
    }

    @Test fun `third party app permanently invalidates even if original returns`() {
        transition.begin()
        assertTrue(activation())
        assertFalse(event("unrelated", "OtherActivity", 20, focused = original.copy(packageName = "unrelated", windowId = 20)))
        transition.end()
        assertFalse(event())
        assertFalse(transition.ready)
    }

    @Test fun `different field in original app permanently invalidates`() {
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertFalse(event(focusEvent = true, state = false, focused = original.copy(path = listOf(1))))
        assertFalse(event())
    }

    @Test fun `different own activity or own editor is never suppressed`() {
        transition.begin()
        assertFalse(event("dictus", "dictus.SettingsActivity", 9, focused = null))
        transition.arm(original)
        transition.begin()
        assertFalse(event("dictus", "android.widget.EditText", 9, state = false, focusEvent = true,
            focused = original.copy(packageName = "dictus", windowId = 9)))
    }

    @Test fun `activation without bracket QS and original return before end reject`() {
        assertFalse(activation())
        transition.arm(original)
        transition.begin()
        assertFalse(event("com.android.systemui", "QS", 4, focused = null))
        transition.arm(original)
        transition.begin()
        assertTrue(activation())
        assertFalse(event())
    }

    @Test fun `normal original window refresh stays valid but focus to another field does not`() {
        assertTrue(event())
        assertTrue(transition.ready)
        assertFalse(event(state = false, focusEvent = true, focused = original.copy(uniqueId = "other")))
    }

    @Test fun `keyboard window list notifications retain exact original focus`() {
        assertTrue(event("keyboard", null, 55, state = false,
            activePkg = "editor", activeWindow = 3))
        assertTrue(transition.ready)
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertTrue(event("keyboard", null, 55, state = false,
            activePkg = "editor", activeWindow = 3))
        assertTrue(transition.ready)
    }

    @Test fun `keyboard input focus event is not a window list notification`() {
        assertFalse(event("keyboard", null, 55, state = false, focusEvent = true,
            activePkg = "editor", activeWindow = 3))
    }

    @Test fun `different own window cannot reuse activation exception`() {
        transition.begin()
        assertTrue(activation())
        assertFalse(event("dictus", null, 12, state = false, focused = null))
    }

    @Test fun `clear and rearm reset activation permission`() {
        transition.begin()
        assertTrue(activation())
        transition.clear()
        assertFalse(transition.ready)
        transition.arm(original)
        assertTrue(transition.ready)
        assertFalse(activation())
    }

    @Test fun `finished activation waits for original window editor focus without granting insertion`() {
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertTrue(event(null, null, 3, state = false, activePkg = "editor", focused = null))
        assertFalse(transition.ready)
        assertEquals(original, transition.target)
        assertTrue(event(focused = null))
        assertFalse(transition.ready)
        assertTrue(event(state = false, focusEvent = true))
        assertTrue(transition.ready)
    }

    @Test fun `returning window without focus still requires explicit activation finish`() {
        transition.begin()
        assertTrue(activation())
        assertFalse(event(focused = null))
        assertNull(transition.target)
    }

    @Test fun `focus on a non editor control cannot use returning window exception`() {
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertFalse(event(state = false, focusEvent = true, focused = null))
        assertNull(transition.target)
    }

    @Test fun `different window or unresolved active window cannot use return exception`() {
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertFalse(event(window = 7, focused = null))
        transition.arm(original)
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertFalse(event(null, null, -1, state = false, activePkg = null, activeWindow = -1, focused = null))
    }

    @Test fun `known activation removal may wait without an active root but cannot authorize insertion`() {
        transition.begin()
        assertTrue(activation())
        transition.end()
        assertTrue(event(null, null, 9, state = false, activePkg = null, activeWindow = -1, focused = null))
        assertFalse(transition.ready)
        assertEquals(original, transition.target)
        assertTrue(event())
        assertTrue(transition.ready)
    }

    @Test fun `missing root cannot conceal another app or focus event`() {
        transition.begin()
        assertTrue(activation())
        assertFalse(event("unrelated", null, 9, state = false, activePkg = null, activeWindow = -1, focused = null))
        transition.arm(original)
        transition.begin()
        assertTrue(activation())
        assertFalse(event(null, null, 9, state = false, focusEvent = true,
            activePkg = null, activeWindow = -1, focused = null))
    }
}
