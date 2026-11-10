package dev.pivisolutions.dictus.ime.ui

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import dev.pivisolutions.dictus.core.theme.DictusTheme
import dev.pivisolutions.dictus.ime.input.BackspaceRepeatPolicy
import dev.pivisolutions.dictus.ime.model.KeyDefinition
import dev.pivisolutions.dictus.ime.model.KeyType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Regression tests for #212: the delete key-repeat must stop however the gesture ends.
 *
 * The key is hosted in a plain ComposeView rather than a compose test rule, so the
 * pointer-input coroutine runs on the production AndroidUiDispatcher. Touch events are
 * injected as MotionEvents and the main looper only runs when the test idles it, which
 * lets a test deliver the release before the press handler has resumed.
 *
 * AndroidUiDispatcher has no Delay of its own, so the repeat's delay() runs on the
 * coroutines default executor in real time: waits below sleep, then idle the looper.
 */
@RunWith(RobolectricTestRunner::class)
class KeyButtonDeleteRepeatTest {
    private val showKey = mutableStateOf(true)
    private var deletes = 0
    private var keyBounds = Rect.Zero
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var composeView: ComposeView
    private var downTime = 0L

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            composeView = ComposeView(activity).apply {
                setContent {
                    DictusTheme {
                        if (showKey.value) {
                            KeyButton(
                                key = KeyDefinition(label = "⌫", type = KeyType.DELETE),
                                isShifted = false,
                                onPress = { deletes++ },
                                modifier = Modifier.onGloballyPositioned { keyBounds = it.boundsInRoot() },
                            )
                        }
                    }
                }
            }
            activity.setContentView(composeView)
        }
        idle(100L)
        check(!keyBounds.isEmpty) { "Delete key was not laid out" }
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun `quick tap deletes once and never repeats`() {
        // The release reaches the key before the press handler resumes, as when the
        // main thread is busy and both events are already queued.
        touch(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)
        runFor(REPEAT_WINDOW_MS)

        assertEquals(1, deletes)
    }

    @Test
    fun `releasing a held delete stops the repeat`() {
        holdUntilRepeating()

        touch(MotionEvent.ACTION_UP)

        assertNoMoreDeletes()
    }

    @Test
    fun `sliding off a held delete stops the repeat`() {
        holdUntilRepeating()

        touch(MotionEvent.ACTION_MOVE, x = keyBounds.right + keyBounds.width)

        assertNoMoreDeletes()
        touch(MotionEvent.ACTION_UP, x = keyBounds.right + keyBounds.width)
    }

    @Test
    fun `pointer cancel right after press never starts the repeat`() {
        // A system gesture can take the pointer away right after the press.
        touch(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)
        runFor(REPEAT_WINDOW_MS)

        assertEquals(1, deletes)
    }

    @Test
    fun `pointer cancel on a held delete stops the repeat`() {
        holdUntilRepeating()

        touch(MotionEvent.ACTION_CANCEL)

        assertNoMoreDeletes()
    }

    @Test
    fun `disposing a held delete stops the repeat`() {
        holdUntilRepeating()

        showKey.value = false

        assertNoMoreDeletes()
    }

    @Test
    fun `delete repeats again on the next hold after a slide-off`() {
        holdUntilRepeating()
        touch(MotionEvent.ACTION_MOVE, x = keyBounds.right + keyBounds.width)
        touch(MotionEvent.ACTION_UP, x = keyBounds.right + keyBounds.width)
        runFor(REPEAT_WINDOW_MS)

        holdUntilRepeating()
        touch(MotionEvent.ACTION_UP)
    }

    private fun holdUntilRepeating() {
        val before = deletes
        touch(MotionEvent.ACTION_DOWN)
        val deadline = System.nanoTime() / 1_000_000 + HOLD_TIMEOUT_MS
        while (deletes <= before + 1 && System.nanoTime() / 1_000_000 < deadline) runFor(FRAME_MS)
        assertTrue("Delete repeat must run while held", deletes > before + 1)
    }

    private fun assertNoMoreDeletes() {
        idle(0L)
        val stoppedAt = deletes
        runFor(REPEAT_WINDOW_MS)
        assertEquals(stoppedAt, deletes)
    }

    /** Lets [ms] of real time pass while running the main looper. */
    private fun runFor(ms: Long) {
        val end = System.nanoTime() / 1_000_000 + ms
        while (System.nanoTime() / 1_000_000 < end) {
            Thread.sleep(FRAME_MS)
            idle(FRAME_MS)
        }
    }

    /** Dispatches [actions] for one pointer within a single main-thread turn. */
    private fun touch(vararg actions: Int, x: Float = keyBounds.center.x) {
        val y = keyBounds.center.y
        actions.forEach { action ->
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downTime = now
            val event = MotionEvent.obtain(downTime, now, action, x, y, 0)
            composeView.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun idle(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private companion object {
        // Long enough for a stuck repeat to start and delete several times.
        const val REPEAT_WINDOW_MS =
            BackspaceRepeatPolicy.INITIAL_DELAY_MS + BackspaceRepeatPolicy.REPEAT_INTERVAL_MS * 5
        const val HOLD_TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
