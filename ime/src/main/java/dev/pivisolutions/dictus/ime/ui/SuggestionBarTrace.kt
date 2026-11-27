package dev.pivisolutions.dictus.ime.ui

import dev.pivisolutions.dictus.ime.input.AutocorrectUndoOffer

/**
 * What the service last decided for the suggestion row: how many candidates the engine returned
 * for the latest request ([computed], null when nothing was asked) and, when the row has nothing
 * to show, why ([reason]). Counts and fixed reasons only, never a word (privacy).
 */
data class SuggestionTrace(val computed: Int?, val reason: String?) {
    companion object {
        val Initial = SuggestionTrace(computed = null, reason = "no word")
    }
}

/**
 * One dictus.log line per change of the keyboard toolbar's centre slot or of its suggestions
 * (#374): before it, the log showed autocorrect working while nothing told whether the bar got
 * the candidates, showed them, or was taken by another occupant.
 */
object SuggestionBarTrace {

    fun line(
        slot: ToolbarCentreSlot,
        trace: SuggestionTrace,
        mode: SuggestionPresentationMode,
        currentWord: String,
        suggestions: List<String>,
        undoOffer: AutocorrectUndoOffer?,
    ): String {
        val shown = if (slot == ToolbarCentreSlot.Suggestions) {
            suggestionSlots(currentWord, suggestions, mode, undoOffer).count { it.isNotEmpty() }
        } else {
            0
        }
        val reason = when {
            slot != ToolbarCentreSlot.Suggestions && slot != ToolbarCentreSlot.Empty ->
                "hidden by ${slot.traceName}"
            shown > 0 -> null
            else -> trace.reason ?: "no candidates"
        }
        return "Suggestion bar: slot=${slot.traceName} mode=$mode computed=${trace.computed ?: "-"} " +
            "received=${suggestions.size} shown=$shown" + (reason?.let { " reason=$it" } ?: "")
    }

    private val ToolbarCentreSlot.traceName: String
        get() = when (this) {
            is ToolbarCentreSlot.Error -> "Error"
            ToolbarCentreSlot.AppDictationInProgress -> "AppDictationInProgress"
            ToolbarCentreSlot.DictationUndo -> "DictationUndo"
            ToolbarCentreSlot.Suggestions -> "Suggestions"
            ToolbarCentreSlot.Paste -> "Paste"
            ToolbarCentreSlot.Empty -> "Empty"
        }
}
