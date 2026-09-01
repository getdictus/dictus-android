package dev.pivisolutions.dictus.ime.input

import java.text.Normalizer

/**
 * Minimal editor surface needed by autocorrect.
 *
 * [attemptVerifiedReplacement] is deliberately not called atomic or compare-and-swap: an
 * InputConnection cannot provide either guarantee. An Android implementation must compare a fresh
 * pre-operation snapshot with [AutocorrectReplacement.expectedSnapshot], perform the replacement,
 * verify the post-operation snapshot, and compensate when verification fails. It must report
 * [AutocorrectReplacementOutcome.IndeterminateMutation] whenever it cannot prove either the
 * requested post-state or the unchanged pre-state (including failed compensation).
 */
interface AutocorrectEditor {
    fun snapshot(): AutocorrectEditorSnapshot?

    fun beginBatchEdit(): Boolean

    fun attemptVerifiedReplacement(request: AutocorrectReplacement): AutocorrectReplacementOutcome

    fun endBatchEdit(): Boolean

    /**
     * Replaces the token immediately before the cursor using only cursor-relative operations.
     *
     * WHY a second path at all: [attemptVerifiedReplacement] needs absolute offsets, which only
     * `getExtractedText` supplies, and that method is the host app's to implement. Apps that
     * return nothing from it — a note-taking app was the first found — got no autocorrect at all,
     * silently, while their suggestion bar worked fine.
     *
     * The operations used here are the ones AOSP LatinIME's own correction path uses:
     * `getTextBeforeCursor`, `deleteSurroundingText`, `commitText`. Every InputConnection
     * implements them, so this reaches editors the extracted-text path cannot.
     *
     * It verifies what this API allows — that the text before the cursor ends with [original]
     * beforehand and with [replacement] afterwards — which is less than the offset path proves
     * and is why it stays the fallback rather than the default.
     */
    fun attemptRelativeReplacement(
        original: String,
        replacement: String,
    ): AutocorrectReplacementOutcome = AutocorrectReplacementOutcome.FailedUnchanged
}

/**
 * A transient extracted-text snapshot. Selection offsets are relative to [text]. The two document
 * boundary flags let callers distinguish a complete edge from a truncated extraction window.
 */
data class AutocorrectEditorSnapshot(
    val text: String,
    val startOffset: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val textStartsAtDocumentStart: Boolean = startOffset == 0,
    val textEndsAtDocumentEnd: Boolean = false,
)

data class AutocorrectReplacement(
    val expectedSnapshot: AutocorrectEditorSnapshot,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val expectedText: String,
    val replacement: String,
)

sealed interface AutocorrectReplacementOutcome {
    /** The exact requested post-state was observed. */
    data object Applied : AutocorrectReplacementOutcome

    /** A stale precondition was observed and the editor is proven unchanged by this attempt. */
    data object RejectedUnchanged : AutocorrectReplacementOutcome

    /** The attempt failed, but the original state (possibly after compensation) is proven restored. */
    data object FailedUnchanged : AutocorrectReplacementOutcome

    /** The editor may have changed; neither the requested state nor the original state is proven. */
    data object IndeterminateMutation : AutocorrectReplacementOutcome
}

data class AutocorrectUndo(
    /** Exact spelling/code-point representation replaced in the editor. */
    val original: String,
    /** Exact correction inserted in the editor, without its trailing space. */
    val correction: String,
    /** Absolute collapsed selection after inserting the correction and its owned trailing space. */
    val correctedSelection: Int,
)

enum class AutocorrectBatchCleanup {
    SUCCEEDED,
    FAILED,
}

sealed interface AutocorrectTransactionResult {
    data class Applied(
        val undo: AutocorrectUndo,
        val batchCleanup: AutocorrectBatchCleanup = AutocorrectBatchCleanup.SUCCEEDED,
    ) : AutocorrectTransactionResult

    data class Restored(
        val batchCleanup: AutocorrectBatchCleanup = AutocorrectBatchCleanup.SUCCEEDED,
    ) : AutocorrectTransactionResult

    data class Rejected(val reason: AutocorrectRejection) : AutocorrectTransactionResult

    data class EditorFailure(
        val operation: AutocorrectEditorOperation,
        val batchCleanup: AutocorrectBatchCleanup? = null,
    ) : AutocorrectTransactionResult

    data class IndeterminateMutation(
        val operation: AutocorrectEditorOperation,
        val batchCleanup: AutocorrectBatchCleanup,
    ) : AutocorrectTransactionResult
}

enum class AutocorrectRejection {
    STALE_IDENTITY,
    CONTEXT_UNAVAILABLE,
    SELECTION_NOT_COLLAPSED,
    INVALID_TOKEN,
    SAME_CORRECTION,
    TOKEN_MISMATCH,
    EDITOR_PRECONDITION_CHANGED,
}

enum class AutocorrectEditorOperation {
    BEGIN_BATCH,
    VERIFIED_REPLACEMENT,
}

/** Coordinates fail-closed autocorrect and its immediate undo without issuing deletion calls. */
object AutocorrectEditorTransaction {

    /**
     * Selection marker for a correction applied without absolute offsets.
     *
     * The coordinator compares this against the selection reported by onUpdateSelection to keep an
     * undo alive; a value no real selection can take retires the undo on the first movement, which
     * is the safe direction when offsets could not be verified in the first place.
     */
    const val UNKNOWN_SELECTION = -1

    fun apply(
        editor: AutocorrectEditor,
        original: String,
        correction: String,
        identityEligible: Boolean,
    ): AutocorrectTransactionResult {
        val normalizedOriginal = original.nfc()
        val normalizedCorrection = correction.nfc()
        validateRequest(normalizedOriginal, normalizedCorrection, identityEligible)?.let { return it }
        val snapshot = readSnapshot(editor)
            ?: return applyRelative(editor, normalizedOriginal, normalizedCorrection)
        val match = matchTokenAtCursor(snapshot, normalizedOriginal) ?: return snapshotValidationFailure(snapshot, normalizedOriginal)

        val insertedCorrection = normalizedCorrection
        val correctedSelection = match.absoluteStart.toLong() + insertedCorrection.length + 1
        if (correctedSelection > Int.MAX_VALUE) {
            return rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE)
        }
        return replace(
            editor,
            AutocorrectReplacement(
                expectedSnapshot = snapshot,
                absoluteStart = match.absoluteStart,
                absoluteEnd = match.absoluteEnd,
                expectedText = match.editorText,
                replacement = "$insertedCorrection ",
            ),
        ) { cleanup ->
            AutocorrectTransactionResult.Applied(
                AutocorrectUndo(match.editorText, insertedCorrection, correctedSelection.toInt()),
                cleanup,
            )
        }
    }

    /**
     * Applies a correction through the cursor-relative path, for editors with no extracted text.
     *
     * The undo it hands back carries no absolute selection, so the caller must not use one: an
     * editor that cannot report offsets cannot have them checked either.
     */
    private fun applyRelative(
        editor: AutocorrectEditor,
        original: String,
        correction: String,
    ): AutocorrectTransactionResult {
        val outcome = withBatch(editor) { editor.attemptRelativeReplacement(original, "$correction ") }
        return when (outcome.result) {
            AutocorrectReplacementOutcome.Applied -> AutocorrectTransactionResult.Applied(
                AutocorrectUndo(original, correction, UNKNOWN_SELECTION),
                outcome.cleanup,
            )
            AutocorrectReplacementOutcome.RejectedUnchanged ->
                rejected(AutocorrectRejection.EDITOR_PRECONDITION_CHANGED)
            AutocorrectReplacementOutcome.FailedUnchanged, null ->
                AutocorrectTransactionResult.EditorFailure(
                    AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                    outcome.cleanup,
                )
            AutocorrectReplacementOutcome.IndeterminateMutation ->
                AutocorrectTransactionResult.IndeterminateMutation(
                    AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                    outcome.cleanup,
                )
        }
    }

    private data class BatchOutcome(
        val result: AutocorrectReplacementOutcome?,
        val cleanup: AutocorrectBatchCleanup,
    )

    private fun withBatch(
        editor: AutocorrectEditor,
        body: () -> AutocorrectReplacementOutcome,
    ): BatchOutcome {
        val began = try {
            editor.beginBatchEdit()
        } catch (_: Exception) {
            false
        }
        val result = if (began) {
            try {
                body()
            } catch (_: Exception) {
                AutocorrectReplacementOutcome.IndeterminateMutation
            }
        } else {
            null
        }
        val cleanup = try {
            if (editor.endBatchEdit()) AutocorrectBatchCleanup.SUCCEEDED else AutocorrectBatchCleanup.FAILED
        } catch (_: Exception) {
            AutocorrectBatchCleanup.FAILED
        }
        return BatchOutcome(result, cleanup)
    }

    fun undo(
        editor: AutocorrectEditor,
        undo: AutocorrectUndo,
        identityEligible: Boolean,
    ): AutocorrectTransactionResult {
        val normalizedOriginal = undo.original.nfc()
        val normalizedCorrection = undo.correction.nfc()
        validateRequest(normalizedOriginal, normalizedCorrection, identityEligible)?.let { return it }
        val snapshot = readSnapshot(editor)
            ?: return undoRelative(editor, normalizedCorrection, normalizedOriginal)
        val match = matchCorrectionAndSpaceAtCursor(snapshot, normalizedCorrection)
            ?: return snapshotValidationFailure(snapshot, normalizedCorrection, trailingSpace = true)

        return replace(
            editor,
            AutocorrectReplacement(
                expectedSnapshot = snapshot,
                absoluteStart = match.absoluteStart,
                absoluteEnd = match.absoluteEnd,
                expectedText = match.editorText,
                replacement = undo.original,
            ),
        ) { cleanup -> AutocorrectTransactionResult.Restored(cleanup) }
    }

    /** Reverses a relative correction, restoring the original token and dropping its space. */
    private fun undoRelative(
        editor: AutocorrectEditor,
        correction: String,
        original: String,
    ): AutocorrectTransactionResult {
        val outcome = withBatch(editor) { editor.attemptRelativeReplacement("$correction ", original) }
        return when (outcome.result) {
            AutocorrectReplacementOutcome.Applied ->
                AutocorrectTransactionResult.Restored(outcome.cleanup)
            AutocorrectReplacementOutcome.RejectedUnchanged ->
                rejected(AutocorrectRejection.EDITOR_PRECONDITION_CHANGED)
            AutocorrectReplacementOutcome.FailedUnchanged, null ->
                AutocorrectTransactionResult.EditorFailure(
                    AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                    outcome.cleanup,
                )
            AutocorrectReplacementOutcome.IndeterminateMutation ->
                AutocorrectTransactionResult.IndeterminateMutation(
                    AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                    outcome.cleanup,
                )
        }
    }

    private fun validateRequest(
        original: String,
        correction: String,
        identityEligible: Boolean,
    ): AutocorrectTransactionResult.Rejected? = when {
        !identityEligible -> rejected(AutocorrectRejection.STALE_IDENTITY)
        !original.isLetterToken() || !correction.isLetterToken() -> rejected(AutocorrectRejection.INVALID_TOKEN)
        original == correction -> rejected(AutocorrectRejection.SAME_CORRECTION)
        else -> null
    }

    private data class Match(val editorText: String, val absoluteStart: Int, val absoluteEnd: Int)

    private fun matchTokenAtCursor(snapshot: AutocorrectEditorSnapshot, expectedNfc: String): Match? {
        if (!snapshot.hasValidCollapsedSelection()) return null
        val cursor = snapshot.selectionStart
        if (!snapshot.hasProvenRightTokenBoundary(cursor)) return null
        var start = cursor
        while (start > 0) {
            val codePoint = snapshot.text.codePointBefore(start)
            if (!codePoint.isTokenCodePoint()) break
            start -= Character.charCount(codePoint)
        }
        if (!snapshot.hasProvenLeftTokenBoundary(start)) return null
        val editorToken = snapshot.text.substring(start, cursor)
        if (!editorToken.isLetterToken() || editorToken.nfc() != expectedNfc) return null
        return snapshot.match(editorToken, start, cursor)
    }

    private fun matchCorrectionAndSpaceAtCursor(
        snapshot: AutocorrectEditorSnapshot,
        expectedCorrectionNfc: String,
    ): Match? {
        if (!snapshot.hasValidCollapsedSelection()) return null
        val cursor = snapshot.selectionStart
        if (cursor == 0 || snapshot.text[cursor - 1] != ' ' || !snapshot.hasProvenRightTokenBoundary(cursor)) return null
        val tokenEnd = cursor - 1
        var tokenStart = tokenEnd
        while (tokenStart > 0) {
            val codePoint = snapshot.text.codePointBefore(tokenStart)
            if (!codePoint.isTokenCodePoint()) break
            tokenStart -= Character.charCount(codePoint)
        }
        if (!snapshot.hasProvenLeftTokenBoundary(tokenStart)) return null
        val token = snapshot.text.substring(tokenStart, tokenEnd)
        if (!token.isLetterToken() || token.nfc() != expectedCorrectionNfc) return null
        return snapshot.match(snapshot.text.substring(tokenStart, cursor), tokenStart, cursor)
    }

    private fun snapshotValidationFailure(
        snapshot: AutocorrectEditorSnapshot,
        expectedNfc: String,
        trailingSpace: Boolean = false,
    ): AutocorrectTransactionResult.Rejected {
        if (!snapshot.hasValidCollapsedSelection()) {
            return if (snapshot.selectionStart != snapshot.selectionEnd) {
                rejected(AutocorrectRejection.SELECTION_NOT_COLLAPSED)
            } else {
                rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE)
            }
        }
        val cursor = snapshot.selectionStart
        if (cursor < snapshot.text.length && snapshot.text.codePointAt(cursor).isTokenCodePoint()) {
            return rejected(AutocorrectRejection.TOKEN_MISMATCH)
        }
        val tokenEnd = if (trailingSpace && cursor > 0 && snapshot.text[cursor - 1] == ' ') cursor - 1 else cursor
        var start = tokenEnd
        while (start > 0 && snapshot.text.codePointBefore(start).isTokenCodePoint()) {
            start -= Character.charCount(snapshot.text.codePointBefore(start))
        }
        if (!snapshot.hasProvenLeftTokenBoundary(start) || !snapshot.hasProvenRightTokenBoundary(cursor)) {
            return rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE)
        }
        val candidate = snapshot.text.substring(start, tokenEnd)
        return if (candidate.nfc() == expectedNfc && !trailingSpace) {
            rejected(AutocorrectRejection.CONTEXT_UNAVAILABLE)
        } else {
            rejected(AutocorrectRejection.TOKEN_MISMATCH)
        }
    }

    private fun replace(
        editor: AutocorrectEditor,
        request: AutocorrectReplacement,
        appliedResult: (AutocorrectBatchCleanup) -> AutocorrectTransactionResult,
    ): AutocorrectTransactionResult {
        var beginSucceeded: Boolean
        var outcome: AutocorrectReplacementOutcome? = null
        beginSucceeded = try {
            editor.beginBatchEdit()
        } catch (_: Exception) {
            false
        }
        if (beginSucceeded) {
            outcome = try {
                editor.attemptVerifiedReplacement(request)
            } catch (_: Exception) {
                AutocorrectReplacementOutcome.IndeterminateMutation
            }
        }

        // InputConnection implementations are inconsistent about begin's Boolean/exception
        // semantics. Once begin was invoked, end is always attempted exactly once.
        val cleanup = try {
            if (editor.endBatchEdit()) AutocorrectBatchCleanup.SUCCEEDED else AutocorrectBatchCleanup.FAILED
        } catch (_: Exception) {
            AutocorrectBatchCleanup.FAILED
        }

        if (!beginSucceeded) {
            return AutocorrectTransactionResult.EditorFailure(
                AutocorrectEditorOperation.BEGIN_BATCH,
                cleanup,
            )
        }
        return when (outcome) {
            AutocorrectReplacementOutcome.Applied -> appliedResult(cleanup)
            AutocorrectReplacementOutcome.RejectedUnchanged ->
                rejected(AutocorrectRejection.EDITOR_PRECONDITION_CHANGED)
            AutocorrectReplacementOutcome.FailedUnchanged ->
                AutocorrectTransactionResult.EditorFailure(AutocorrectEditorOperation.VERIFIED_REPLACEMENT, cleanup)
            AutocorrectReplacementOutcome.IndeterminateMutation, null ->
                AutocorrectTransactionResult.IndeterminateMutation(
                    AutocorrectEditorOperation.VERIFIED_REPLACEMENT,
                    cleanup,
                )
        }
    }

    private fun AutocorrectEditorSnapshot.hasValidCollapsedSelection(): Boolean =
        selectionStart == selectionEnd &&
            selectionStart in 0..text.length &&
            startOffset >= 0 &&
            startOffset <= Int.MAX_VALUE - selectionStart

    private fun AutocorrectEditorSnapshot.hasProvenLeftTokenBoundary(start: Int): Boolean = when {
        start > 0 -> !text.codePointBefore(start).isTokenCodePoint()
        else -> startOffset == 0 && textStartsAtDocumentStart
    }

    private fun AutocorrectEditorSnapshot.hasProvenRightTokenBoundary(cursor: Int): Boolean = when {
        cursor < text.length -> !text.codePointAt(cursor).isTokenCodePoint()
        else -> textEndsAtDocumentEnd
    }

    private fun AutocorrectEditorSnapshot.match(editorText: String, start: Int, end: Int): Match? {
        val absoluteStart = startOffset.toLong() + start
        val absoluteEnd = startOffset.toLong() + end
        if (absoluteStart !in 0..Int.MAX_VALUE.toLong() || absoluteEnd !in 0..Int.MAX_VALUE.toLong()) return null
        return Match(editorText, absoluteStart.toInt(), absoluteEnd.toInt())
    }

    private fun readSnapshot(editor: AutocorrectEditor): AutocorrectEditorSnapshot? = try {
        editor.snapshot()
    } catch (_: Exception) {
        null
    }

    private fun String.nfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

    /**
     * Whether a token is a shape autocorrect may replace.
     *
     * Letters and combining marks, plus an apostrophe or hyphen **between** them: never leading,
     * never trailing, never doubled. French cannot be autocorrected without them — `c'est`,
     * `j'ai`, `aujourd'hui`, `peut-être`, `rendez-vous` are ordinary words, and refusing the shape
     * refused every correction whose input or candidate contained one, silently.
     *
     * The interior rule is what keeps the relaxation safe: `'` alone, `-word` and `word-` are
     * still rejected, so a stray separator can never be mistaken for a word to replace.
     */
    private fun String.isLetterToken(): Boolean {
        if (isEmpty()) return false
        var sawLetter = false
        var pendingSeparator = false
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            when {
                Character.isLetter(codePoint) -> {
                    sawLetter = true
                    pendingSeparator = false
                }
                codePoint.isCombiningMark() -> if (!sawLetter || pendingSeparator) return false
                codePoint.isTokenSeparator() -> {
                    if (!sawLetter || pendingSeparator) return false
                    pendingSeparator = true
                }
                else -> return false
            }
            index += Character.charCount(codePoint)
        }
        return sawLetter && !pendingSeparator
    }

    /**
     * Apostrophes and hyphens that join two halves of one word.
     *
     * Both apostrophe shapes are accepted because the editor holds whatever the host app or an
     * earlier keyboard produced, while the dictionary is normalized to the straight one.
     */
    private fun Int.isTokenSeparator(): Boolean =
        this == '\''.code || this == '\u2019'.code || this == '-'.code

    /**
     * Whether a code point belongs to the token under the cursor, for boundary scanning.
     *
     * This must agree with how the service isolates the current word, which splits only on spaces
     * and newlines. Stopping the scan at an apostrophe made the editor see `ordinateur` where the
     * service had offered `l'ordinateur`, and the mismatch rejected the correction.
     */
    private fun Int.isTokenCodePoint(): Boolean =
        Character.isLetter(this) || isCombiningMark() || isTokenSeparator()

    private fun Int.isCombiningMark(): Boolean = when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(), -> true
        else -> false
    }

    private fun rejected(reason: AutocorrectRejection) = AutocorrectTransactionResult.Rejected(reason)
}
