package dev.pivisolutions.dictus.ime.input

import dev.pivisolutions.dictus.core.service.TranscriptionOutcome

/**
 * What the suggestion bar says about the last dictation: the undo offer after an insertion
 * (#132), or the notice after a dictation that inserted nothing (#153, #182).
 *
 * WHY one owner: both describe the field as the dictation left it, so anything that changes
 * the field (a keystroke, a caret move, a suggestion, a new dictation, a new field) must withdraw
 * both. A single [withdraw] keeps a call site from clearing one and leaving the other, which
 * would let the notice hide the suggestions while the user types.
 */
class DictationFeedback(
    val undo: DictationUndo,
    val notice: DictationNotice,
) {

    fun withdraw() {
        undo.clear()
        notice.clear()
    }

    /**
     * Applies a finished dictation: [insert] receives the text; an empty or failed dictation
     * inserts nothing and shows its notice.
     */
    fun onOutcome(outcome: TranscriptionOutcome, insert: (String) -> Unit) {
        when (outcome) {
            is TranscriptionOutcome.Transcribed -> insert(outcome.text)
            TranscriptionOutcome.NoWordsDetected -> notice.show(DictationNotice.Message.NoWordsDetected)
            TranscriptionOutcome.Failed -> notice.show(DictationNotice.Message.Failed)
        }
    }
}
