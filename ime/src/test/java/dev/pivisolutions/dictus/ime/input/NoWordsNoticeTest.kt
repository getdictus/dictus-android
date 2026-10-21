package dev.pivisolutions.dictus.ime.input

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoWordsNoticeTest {

    private val scope = TestScope()
    private val notice = NoWordsNotice(scope)

    @Test
    fun `notice is hidden until a dictation produces no words`() {
        assertFalse(notice.isVisible.value)
    }

    @Test
    fun `notice stays three seconds, like the iOS toolbar message`() {
        notice.show()
        assertTrue(notice.isVisible.value)

        scope.advanceTimeBy(NoWordsNotice.DISPLAY_DURATION_MS - 1)
        scope.runCurrent()
        assertTrue(notice.isVisible.value)

        scope.advanceTimeBy(1)
        scope.runCurrent()
        assertFalse(notice.isVisible.value)
    }

    @Test
    fun `a second empty dictation restarts the three seconds`() {
        notice.show()
        scope.advanceTimeBy(2_000)
        notice.show()

        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertTrue(notice.isVisible.value)
    }

    @Test
    fun `clear hides the notice at once and cancels the timer`() {
        notice.show()
        notice.clear()
        assertFalse(notice.isVisible.value)

        notice.show()
        scope.advanceTimeBy(NoWordsNotice.DISPLAY_DURATION_MS + 1)
        scope.runCurrent()
        assertFalse(notice.isVisible.value)
    }
}
