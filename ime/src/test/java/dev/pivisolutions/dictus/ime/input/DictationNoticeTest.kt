package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.ime.input.DictationNotice.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationNoticeTest {

    private val scope = TestScope()
    private val notice = DictationNotice(scope)

    @Test
    fun `notice is hidden until a dictation inserts nothing`() {
        assertNull(notice.message.value)
    }

    @Test
    fun `notice stays three seconds, like the iOS toolbar message`() {
        notice.show(Message.NoWordsDetected)
        assertEquals(Message.NoWordsDetected, notice.message.value)

        scope.advanceTimeBy(DictationNotice.DISPLAY_DURATION_MS - 1)
        scope.runCurrent()
        assertEquals(Message.NoWordsDetected, notice.message.value)

        scope.advanceTimeBy(1)
        scope.runCurrent()
        assertNull(notice.message.value)
    }

    @Test
    fun `a second empty dictation restarts the three seconds`() {
        notice.show(Message.NoWordsDetected)
        scope.advanceTimeBy(2_000)
        notice.show(Message.NoWordsDetected)

        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertEquals(Message.NoWordsDetected, notice.message.value)
    }

    @Test
    fun `a failure replaces the no words notice and gets its own three seconds`() {
        notice.show(Message.NoWordsDetected)
        scope.advanceTimeBy(2_000)
        notice.show(Message.Failed)
        assertEquals(Message.Failed, notice.message.value)

        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertEquals(Message.Failed, notice.message.value)
    }

    @Test
    fun `clear hides the notice at once and cancels the timer`() {
        notice.show(Message.NoWordsDetected)
        notice.clear()
        assertNull(notice.message.value)

        notice.show(Message.NoWordsDetected)
        scope.advanceTimeBy(DictationNotice.DISPLAY_DURATION_MS + 1)
        scope.runCurrent()
        assertNull(notice.message.value)
    }
}
