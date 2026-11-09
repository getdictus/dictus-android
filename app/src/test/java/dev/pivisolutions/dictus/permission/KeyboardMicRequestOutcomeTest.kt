package dev.pivisolutions.dictus.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardMicRequestOutcomeTest {
    private val flags = listOf(false, true)

    @Test
    fun `a grant wins whatever the rationale says`() {
        for (before in flags) for (after in flags) {
            assertEquals(KeyboardMicRequestOutcome.GRANTED, KeyboardMicRequestOutcome.of(true, before, after))
        }
    }

    @Test
    fun `no rationale before or after means Android showed no prompt, so settings open`() {
        assertEquals(KeyboardMicRequestOutcome.PROMPT_BLOCKED, KeyboardMicRequestOutcome.of(false, false, false))
    }

    @Test
    fun `a first refusal in the prompt stays in the app`() {
        assertEquals(KeyboardMicRequestOutcome.REFUSED_AT_PROMPT, KeyboardMicRequestOutcome.of(false, false, true))
    }

    @Test
    fun `the refusal that blocks the prompt stays in the app, the next request opens settings`() {
        assertEquals(KeyboardMicRequestOutcome.REFUSED_AT_PROMPT, KeyboardMicRequestOutcome.of(false, true, false))
        assertEquals(KeyboardMicRequestOutcome.PROMPT_BLOCKED, KeyboardMicRequestOutcome.of(false, false, false))
    }
}
