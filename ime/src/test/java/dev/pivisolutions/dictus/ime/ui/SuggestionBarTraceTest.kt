package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Assert.assertEquals
import org.junit.Test

/** The suggestion bar's dictus.log line: counts and reasons, never a word. */
class SuggestionBarTraceTest {

    private val completion = SuggestionPresentationMode.COMPLETION

    @Test
    fun `a filled bar gives the computed and shown counts, without the words`() {
        val line = SuggestionBarTrace.line(
            slot = ToolbarCentreSlot.Suggestions,
            trace = SuggestionTrace(computed = 3, reason = null),
            mode = completion,
            currentWord = "Bonj",
            suggestions = listOf("Bonjour", "Bonjours", "Bonsoir"),
            undoOffer = null,
        )

        assertEquals(
            "Suggestion bar: slot=Suggestions mode=COMPLETION computed=3 received=3 shown=3",
            line,
        )
    }

    @Test
    fun `an empty bar says why`() {
        val line = SuggestionBarTrace.line(
            slot = ToolbarCentreSlot.Empty,
            trace = SuggestionTrace(computed = null, reason = "editor not eligible"),
            mode = completion,
            currentWord = "",
            suggestions = emptyList(),
            undoOffer = null,
        )

        assertEquals(
            "Suggestion bar: slot=Empty mode=COMPLETION computed=- received=0 shown=0 reason=editor not eligible",
            line,
        )
    }

    @Test
    fun `a bar taken by another occupant says which`() {
        val line = SuggestionBarTrace.line(
            slot = ToolbarCentreSlot.Error(DictationNotice.Message.Failed),
            trace = SuggestionTrace(computed = 2, reason = null),
            mode = completion,
            currentWord = "Bonj",
            suggestions = listOf("Bonjour", "Bonsoir"),
            undoOffer = null,
        )

        assertEquals(
            "Suggestion bar: slot=Error mode=COMPLETION computed=2 received=2 shown=0 reason=hidden by Error",
            line,
        )
    }
}
