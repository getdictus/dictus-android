package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome

/**
 * What the suggestion bar says about the last dictation: the undo offer after an insertion
 * (#132) or the "No words detected" notice after an empty one (#153).
 *
 * WHY one owner: both describe the field as the dictation left it, so anything that changes
 * the field (a keystroke, a caret move, a suggestion, a new dictation, a new field) must withdraw
 * both. A single [withdraw] keeps a call site from clearing one and leaving the other, which
 * would let the notice hide the suggestions while the user types.
 */
class DictationFeedback(
    val undo: DictationUndo,
    val noWordsNotice: NoWordsNotice,
) {

    fun withdraw() {
        undo.clear()
        noWordsNotice.clear()
    }

    /**
     * Applies a finished dictation: [insert] receives the text, an empty dictation shows the
     * notice and inserts nothing, a failure does neither.
     */
    fun onOutcome(outcome: TranscriptionOutcome, insert: (String) -> Unit) {
        when (outcome) {
            is TranscriptionOutcome.Text -> insert(outcome.text)
            TranscriptionOutcome.NoWordsDetected -> noWordsNotice.show()
            TranscriptionOutcome.Failed -> Unit
        }
    }
}
