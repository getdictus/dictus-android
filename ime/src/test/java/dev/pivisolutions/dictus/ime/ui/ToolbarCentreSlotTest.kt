package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer
import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Priority of the toolbar's centre slot, as iOS `ToolbarCentreSlot.resolve` (#139). */
class ToolbarCentreSlotTest {

    private val notices = listOf(null) + DictationNotice.Message.entries
    private val flags = listOf(false, true)

    private fun forEveryCombination(check: (DictationNotice.Message?, Boolean, Boolean, Boolean) -> Unit) {
        for (notice in notices) for (app in flags) for (undo in flags) for (suggestions in flags) {
            check(notice, app, undo, suggestions)
        }
    }

    @Test
    fun `every combination resolves by the iOS order, error then app dictation then undo then suggestions`() {
        forEveryCombination { notice, app, undo, suggestions ->
            val expected = when {
                notice != null -> ToolbarCentreSlot.Error(notice)
                app -> ToolbarCentreSlot.AppDictationInProgress
                undo -> ToolbarCentreSlot.DictationUndo
                suggestions -> ToolbarCentreSlot.Suggestions
                else -> ToolbarCentreSlot.Empty
            }
            assertEquals(
                "notice=$notice app=$app undo=$undo suggestions=$suggestions",
                expected,
                ToolbarCentreSlot.resolve(
                    notice = notice,
                    appDictationInProgress = app,
                    offersDictationUndo = undo,
                    hasSuggestions = suggestions,
                ),
            )
        }
    }

    @Test
    fun `an error hides the app dictation notice, the undo and the suggestions`() {
        assertEquals(
            ToolbarCentreSlot.Error(DictationNotice.Message.Failed),
            ToolbarCentreSlot.resolve(
                DictationNotice.Message.Failed,
                appDictationInProgress = true,
                offersDictationUndo = true,
                hasSuggestions = true,
            ),
        )
    }

    // D2 (iOS dev): the #248 notice takes iOS recoveredTranscription's rung, below the error.
    @Test
    fun `the app dictation notice hides the undo and the suggestions`() {
        assertEquals(
            ToolbarCentreSlot.AppDictationInProgress,
            ToolbarCentreSlot.resolve(
                notice = null,
                appDictationInProgress = true,
                offersDictationUndo = true,
                hasSuggestions = true,
            ),
        )
    }

    @Test
    fun `undo hides the suggestions`() {
        assertEquals(
            ToolbarCentreSlot.DictationUndo,
            ToolbarCentreSlot.resolve(
                notice = null,
                appDictationInProgress = false,
                offersDictationUndo = true,
                hasSuggestions = true,
            ),
        )
    }

    @Test
    fun `the menu yields to every occupant but the empty slot`() {
        DictationNotice.Message.entries.forEach {
            assertTrue(ToolbarCentreSlot.Error(it).evictsHamburger)
        }
        assertTrue(ToolbarCentreSlot.AppDictationInProgress.evictsHamburger)
        assertTrue(ToolbarCentreSlot.DictationUndo.evictsHamburger)
        assertTrue(ToolbarCentreSlot.Suggestions.evictsHamburger)
        assertFalse(ToolbarCentreSlot.Empty.evictsHamburger)
    }

    @Test
    fun `the menu shows only on an empty bar, for every combination`() {
        forEveryCombination { notice, app, undo, suggestions ->
            val slot = ToolbarCentreSlot.resolve(notice, app, undo, suggestions)
            assertEquals(
                "notice=$notice app=$app undo=$undo suggestions=$suggestions",
                notice == null && !app && !undo && !suggestions,
                !slot.evictsHamburger,
            )
        }
    }

    @Test
    fun `a typed word without any suggestion leaves the bar empty, as iOS`() {
        assertFalse(ToolbarCentreSlot.hasSuggestions(emptyList()))
        assertTrue(ToolbarCentreSlot.hasSuggestions(listOf("bonjour")))
    }

    // #276: the undo chip sits in the suggestion row, so it alone fills the slot, suggestions off
    // included (decided by Pierre: it is the only way left to revert a correction).
    @Test
    fun `the autocorrect undo chip alone counts as a suggestion`() {
        assertTrue(ToolbarCentreSlot.hasSuggestions(emptyList(), AutocorrectUndoOffer("helo", 1)))
    }
}
