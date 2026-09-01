package dev.pivisolutions.dictus.ime.input

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.text.Normalizer

/** A fail-closed [AutocorrectEditor] backed by an Android [InputConnection]. */
class InputConnectionAutocorrectEditor(
    private val inputConnection: InputConnection,
    private val contextLimit: Int = DEFAULT_CONTEXT_LIMIT,
) : AutocorrectEditor {
    init {
        require(contextLimit in 1..MAX_CONTEXT_LIMIT)
    }

    override fun snapshot(): AutocorrectEditorSnapshot? = try {
        readSnapshot()
    } catch (_: Exception) {
        null
    }

    override fun beginBatchEdit(): Boolean = inputConnection.beginBatchEdit()

    /**
     * Cursor-relative replacement for editors that expose no extracted text.
     *
     * Uses only `getTextBeforeCursor`, `deleteSurroundingText` and `commitText` — the three calls
     * AOSP LatinIME's own correction path relies on, and the ones every InputConnection
     * implements. It proves what those allow: the text before the cursor ends with [original]
     * beforehand and with [replacement] afterwards.
     *
     * The window returned by `getTextBeforeCursor` slides as the text changes, so both checks are
     * on the tail. Anything else is reported as indeterminate rather than guessed at.
     */
    override fun attemptRelativeReplacement(
        original: String,
        replacement: String,
    ): AutocorrectReplacementOutcome {
        if (original.isEmpty() || original.length > contextLimit || replacement.length > contextLimit) {
            return AutocorrectReplacementOutcome.RejectedUnchanged
        }
        if (!selectionIsCollapsed()) return AutocorrectReplacementOutcome.RejectedUnchanged
        val before = textBeforeCursor() ?: return AutocorrectReplacementOutcome.FailedUnchanged
        // The editor may hold a different normalization than the dictionary, so the tail is
        // located by comparing normalized forms while deleting the code units actually present.
        val deletable = matchingTailLength(before, original)
            ?: return AutocorrectReplacementOutcome.RejectedUnchanged

        val deleted = try {
            inputConnection.deleteSurroundingText(deletable, 0)
        } catch (_: Exception) {
            false
        }
        val committed = if (deleted) {
            try {
                inputConnection.commitText(replacement, 1)
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val after = textBeforeCursor()
        return when {
            after == null -> AutocorrectReplacementOutcome.IndeterminateMutation
            matchingTailLength(after, replacement) != null -> AutocorrectReplacementOutcome.Applied
            !deleted || !committed -> restoredOrIndeterminate(after, original)
            else -> restoredOrIndeterminate(after, original)
        }
    }

    private fun restoredOrIndeterminate(
        after: String,
        original: String,
    ): AutocorrectReplacementOutcome = if (matchingTailLength(after, original) != null) {
        AutocorrectReplacementOutcome.FailedUnchanged
    } else {
        AutocorrectReplacementOutcome.IndeterminateMutation
    }

    private fun selectionIsCollapsed(): Boolean = try {
        inputConnection.getSelectedText(0).isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    private fun textBeforeCursor(): String? = try {
        inputConnection.getTextBeforeCursor(contextLimit, 0)?.toString()
    } catch (_: Exception) {
        null
    }

    /**
     * The number of trailing code units of [text] whose normalized form equals [expected], or null.
     *
     * A composed `é` and a decomposed `e` + combining acute mean the same word in different code
     * unit counts, so the length to delete has to be measured against the editor's own bytes
     * rather than against the dictionary's.
     */
    private fun matchingTailLength(text: String, expected: String): Int? {
        val normalizedExpected = Normalizer.normalize(expected, Normalizer.Form.NFC)
        val shortest = normalizedExpected.length
        val longest = minOf(text.length, shortest * 2)
        for (length in shortest..longest) {
            val tail = text.takeLast(length)
            if (tail.length != length) return null
            if (Normalizer.normalize(tail, Normalizer.Form.NFC) == normalizedExpected) return length
        }
        return null
    }

    override fun endBatchEdit(): Boolean = inputConnection.endBatchEdit()

    override fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome {
        val before = snapshot() ?: return AutocorrectReplacementOutcome.IndeterminateMutation
        if (before != request.expectedSnapshot || !request.isValidFor(before)) {
            return AutocorrectReplacementOutcome.RejectedUnchanged
        }

        var composingAttempted = false
        var composingSucceeded = false
        var mutationCallFailed = false
        try {
            composingAttempted = true
            composingSucceeded = inputConnection.setComposingRegion(request.absoluteStart, request.absoluteEnd)
            if (composingSucceeded) {
                mutationCallFailed = try {
                    !inputConnection.commitText(request.replacement, 1)
                } catch (_: Exception) {
                    true
                }
            } else {
                mutationCallFailed = true
            }
        } catch (_: Exception) {
            mutationCallFailed = true
        }

        val expectedAfter = before.after(request)
        // Keep verification anchored to the pre-operation window. A replacement may grow the text
        // and make a generic before-cursor window slide, even when the exact mutation succeeded.
        val observed = readAnchoredSnapshot(expectedAfter)
        if (observed == expectedAfter) {
            if (mutationCallFailed && composingAttempted && !clearComposingState()) {
                return AutocorrectReplacementOutcome.IndeterminateMutation
            }
            return AutocorrectReplacementOutcome.Applied
        }
        if (readAnchoredSnapshot(before) == before) {
            if (composingAttempted && !clearComposingState()) {
                return AutocorrectReplacementOutcome.IndeterminateMutation
            }
            return AutocorrectReplacementOutcome.FailedUnchanged
        }

        // Roll back only the exact requested text mutation. Any other observed change may belong to
        // the application, so overwriting it would be unsafe.
        if (observed != null && observed.hasExactAttemptedText(expectedAfter)) {
            try {
                val replacementEnd = request.absoluteStart + request.replacement.length
                if (inputConnection.setComposingRegion(request.absoluteStart, replacementEnd)) {
                    try {
                        inputConnection.commitText(request.expectedText, 1)
                    } catch (_: Exception) {
                        // Verification below is authoritative even when the call throws.
                    }
                }
            } catch (_: Exception) {
                // Verification below decides whether compensation restored the original state.
            }
            val composingCleared = clearComposingState()
            return if (composingCleared && readAnchoredSnapshot(before) == before) {
                AutocorrectReplacementOutcome.FailedUnchanged
            } else {
                AutocorrectReplacementOutcome.IndeterminateMutation
            }
        }

        if (composingAttempted) clearComposingState()
        return AutocorrectReplacementOutcome.IndeterminateMutation
    }

    private fun readSnapshot(): AutocorrectEditorSnapshot? {
        val first = extract() ?: return null
        if (!first.isUsable()) return null
        val absoluteSelectionStart = first.startOffset.toLong() + first.selectionStart
        val absoluteSelectionEnd = first.startOffset.toLong() + first.selectionEnd
        if (
            absoluteSelectionStart !in 0..Int.MAX_VALUE.toLong() ||
            absoluteSelectionEnd !in absoluteSelectionStart..Int.MAX_VALUE.toLong()
        ) return null
        val selectionStart = absoluteSelectionStart.toInt()
        val selectionEnd = absoluteSelectionEnd.toInt()
        val selectedLength = selectionEnd - selectionStart
        if (selectedLength > contextLimit) return null

        val before = inputConnection.getTextBeforeCursor(contextLimit, 0)?.toString() ?: return null
        val selected = if (selectedLength == 0) {
            ""
        } else {
            inputConnection.getSelectedText(0)?.toString() ?: return null
        }
        val after = inputConnection.getTextAfterCursor(contextLimit, 0)?.toString() ?: return null
        if (before.length > contextLimit || after.length > contextLimit || selected.length != selectedLength) return null

        val second = extract() ?: return null
        if (!first.sameAs(second)) return null

        val localStart = selectionStart - before.length
        val localEnd = selectionEnd.toLong() + after.length
        if (localEnd > Int.MAX_VALUE) return null
        val extractedStart = first.startOffset
        val extractedEnd = extractedStart.toLong() + first.text.length
        if (localStart < extractedStart || localEnd > extractedEnd) return null
        val from = localStart - extractedStart
        val to = localEnd.toInt() - extractedStart
        val localText = before + selected + after
        if (first.text.substring(from, to) != localText) return null

        return AutocorrectEditorSnapshot(
            text = localText,
            startOffset = localStart,
            selectionStart = before.length,
            selectionEnd = before.length + selected.length,
            textStartsAtDocumentStart = before.length < contextLimit && localStart == 0,
            textEndsAtDocumentEnd = after.length < contextLimit && localEnd == extractedEnd,
        )
    }

    private fun readAnchoredSnapshot(template: AutocorrectEditorSnapshot): AutocorrectEditorSnapshot? {
        return try {
            val first = extract() ?: return null
            if (!first.isUsable()) return null
            val second = extract() ?: return null
            if (first != second) return null

            val absoluteSelectionStart = first.startOffset.toLong() + first.selectionStart
            val absoluteSelectionEnd = first.startOffset.toLong() + first.selectionEnd
            val requestedStart = template.startOffset.toLong()
            val requestedEnd = requestedStart + template.text.length
            val extractedEnd = first.startOffset.toLong() + first.text.length
            if (
                requestedStart < first.startOffset ||
                requestedEnd > extractedEnd ||
                absoluteSelectionStart !in requestedStart..requestedEnd ||
                absoluteSelectionEnd !in absoluteSelectionStart..requestedEnd
            ) return null

            val from = (requestedStart - first.startOffset).toInt()
            val to = (requestedEnd - first.startOffset).toInt()
            AutocorrectEditorSnapshot(
                text = first.text.substring(from, to),
                startOffset = template.startOffset,
                selectionStart = (absoluteSelectionStart - requestedStart).toInt(),
                selectionEnd = (absoluteSelectionEnd - requestedStart).toInt(),
                // Extraction edges are not document edges. Preserve only the boundary knowledge
                // established by the before/after probes in the original snapshot.
                textStartsAtDocumentStart = template.textStartsAtDocumentStart,
                textEndsAtDocumentEnd = template.textEndsAtDocumentEnd,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extract(): ExactExtraction? {
        val request = ExtractedTextRequest().apply {
            hintMaxChars = contextLimit * 3 + 1
            hintMaxLines = 1
        }
        val extracted = inputConnection.getExtractedText(request, 0) ?: return null
        val text = extracted.text?.toString() ?: return null
        return ExactExtraction(
            text = text,
            startOffset = extracted.startOffset,
            partialStartOffset = extracted.partialStartOffset,
            partialEndOffset = extracted.partialEndOffset,
            selectionStart = extracted.selectionStart,
            selectionEnd = extracted.selectionEnd,
        )
    }

    private fun ExactExtraction.isUsable(): Boolean =
        startOffset >= 0 &&
            partialStartOffset == -1 &&
            partialEndOffset == -1 &&
            startOffset <= Int.MAX_VALUE - text.length &&
            selectionStart in 0..text.length &&
            selectionEnd in selectionStart..text.length

    private fun AutocorrectReplacement.isValidFor(snapshot: AutocorrectEditorSnapshot): Boolean {
        val relativeStart = absoluteStart - snapshot.startOffset
        val relativeEnd = absoluteEnd - snapshot.startOffset
        return absoluteStart >= 0 &&
            absoluteEnd >= absoluteStart &&
            relativeStart >= 0 &&
            relativeEnd <= snapshot.text.length &&
            snapshot.text.substring(relativeStart, relativeEnd) == expectedText
    }

    private fun AutocorrectEditorSnapshot.after(request: AutocorrectReplacement): AutocorrectEditorSnapshot {
        val relativeStart = request.absoluteStart - startOffset
        val relativeEnd = request.absoluteEnd - startOffset
        val cursor = relativeStart + request.replacement.length
        return copy(
            text = text.replaceRange(relativeStart, relativeEnd, request.replacement),
            selectionStart = cursor,
            selectionEnd = cursor,
        )
    }

    private fun AutocorrectEditorSnapshot.hasExactAttemptedText(expected: AutocorrectEditorSnapshot): Boolean =
        text == expected.text &&
            startOffset == expected.startOffset &&
            textStartsAtDocumentStart == expected.textStartsAtDocumentStart &&
            textEndsAtDocumentEnd == expected.textEndsAtDocumentEnd

    private fun clearComposingState(): Boolean =
        try {
            inputConnection.finishComposingText()
        } catch (_: Exception) {
            false
        }

    private data class ExactExtraction(
        val text: String,
        val startOffset: Int,
        val partialStartOffset: Int,
        val partialEndOffset: Int,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) {
        fun sameAs(extracted: ExactExtraction): Boolean = this == extracted
    }

    private companion object {
        const val DEFAULT_CONTEXT_LIMIT = 256
        const val MAX_CONTEXT_LIMIT = 4096
    }
}
