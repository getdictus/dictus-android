package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.DictationNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Priority of the toolbar's centre slot, as iOS `ToolbarCentreSlot.resolve` (#139). */
class ToolbarCentreSlotTest {

    private val notices = listOf(null) + DictationNotice.Message.entries

    @Test
    fun `every combination resolves by the iOS order, error then undo then suggestions`() {
        for (notice in notices) {
            for (undo in listOf(false, true)) {
                for (suggestions in listOf(false, true)) {
                    val expected = when {
                        notice != null -> ToolbarCentreSlot.Error(notice)
                        undo -> ToolbarCentreSlot.DictationUndo
                        suggestions -> ToolbarCentreSlot.Suggestions
                        else -> ToolbarCentreSlot.Empty
                    }
                    assertEquals(
                        "notice=$notice undo=$undo suggestions=$suggestions",
                        expected,
                        ToolbarCentreSlot.resolve(
                            notice = notice,
                            offersDictationUndo = undo,
                            hasSuggestions = suggestions,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `an error hides the undo and the suggestions`() {
        assertEquals(
            ToolbarCentreSlot.Error(DictationNotice.Message.Failed),
            ToolbarCentreSlot.resolve(DictationNotice.Message.Failed, offersDictationUndo = true, hasSuggestions = true),
        )
    }

    @Test
    fun `undo hides the suggestions`() {
        assertEquals(
            ToolbarCentreSlot.DictationUndo,
            ToolbarCentreSlot.resolve(notice = null, offersDictationUndo = true, hasSuggestions = true),
        )
    }

    @Test
    fun `the menu yields to the error, the undo and the suggestions, and only to them`() {
        DictationNotice.Message.entries.forEach {
            assertTrue(ToolbarCentreSlot.Error(it).evictsHamburger)
        }
        assertTrue(ToolbarCentreSlot.DictationUndo.evictsHamburger)
        assertTrue(ToolbarCentreSlot.Suggestions.evictsHamburger)
        assertFalse(ToolbarCentreSlot.Empty.evictsHamburger)
    }

    @Test
    fun `the menu shows only on an empty bar, for every combination`() {
        for (notice in notices) {
            for (undo in listOf(false, true)) {
                for (suggestions in listOf(false, true)) {
                    val slot = ToolbarCentreSlot.resolve(notice, undo, suggestions)
                    assertEquals(
                        "notice=$notice undo=$undo suggestions=$suggestions",
                        notice == null && !undo && !suggestions,
                        !slot.evictsHamburger,
                    )
                }
            }
        }
    }

    @Test
    fun `a typed word without any suggestion leaves the bar empty, as iOS`() {
        assertFalse(ToolbarCentreSlot.hasSuggestions(emptyList()))
        assertTrue(ToolbarCentreSlot.hasSuggestions(listOf("bonjour")))
    }
}
