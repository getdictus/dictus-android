package dev.pivisolutions.dictus.ime.input

/** Opaque identity captured by the UI with the prediction it displays. */
data class NextWordPredictionToken(
    val sessionId: Long,
    val requestId: Long,
    val languageIdentity: String,
    val contextIdentity: NextWordContextIdentity,
)

data class NextWordPredictionPublication(
    val token: NextWordPredictionToken,
    val suggestions: List<String>,
)

enum class NextWordPredictionInsertResult {
    APPLIED,
    REJECTED_UNCHANGED,
    FAILED_UNCHANGED,
    INDETERMINATE,
}

/**
 * Fail-closed verified insertion. The expected snapshot is checked before the mutation and passed to
 * the InputConnection adapter, which checks it again immediately before mutation and verifies the
 * exact post-state. A successful InputConnection Boolean is never treated as proof.
 */
object NextWordPredictionEditorTransaction {
    fun insert(
        editor: AutocorrectEditor,
        expectedSnapshot: AutocorrectEditorSnapshot,
        prediction: String,
    ): NextWordPredictionInsertResult {
        if (!prediction.isValidPrediction()) return NextWordPredictionInsertResult.REJECTED_UNCHANGED
        val cursor = expectedSnapshot.selectionStart
        if (
            cursor != expectedSnapshot.selectionEnd || cursor !in 0..expectedSnapshot.text.length ||
            expectedSnapshot.startOffset < 0 ||
            expectedSnapshot.startOffset > Int.MAX_VALUE - cursor
        ) return NextWordPredictionInsertResult.REJECTED_UNCHANGED

        val fresh = try {
            editor.snapshot()
        } catch (_: Exception) {
            null
        }
        if (fresh != expectedSnapshot) return NextWordPredictionInsertResult.REJECTED_UNCHANGED

        val absoluteCursor = expectedSnapshot.startOffset + cursor
        val request = AutocorrectReplacement(
            expectedSnapshot = expectedSnapshot,
            absoluteStart = absoluteCursor,
            absoluteEnd = absoluteCursor,
            expectedText = "",
            replacement = "$prediction ",
        )
        val began = try {
            editor.beginBatchEdit()
        } catch (_: Exception) {
            false
        }
        val outcome = if (began) {
            try {
                editor.attemptVerifiedReplacement(request)
            } catch (_: Exception) {
                AutocorrectReplacementOutcome.IndeterminateMutation
            }
        } else {
            null
        }
        // InputConnection begin semantics are inconsistent; once attempted, always balance it.
        try {
            editor.endBatchEdit()
        } catch (_: Exception) {
            // Post-verification remains authoritative; cleanup failure does not invent success.
        }
        return when {
            !began -> NextWordPredictionInsertResult.FAILED_UNCHANGED
            outcome == AutocorrectReplacementOutcome.Applied -> NextWordPredictionInsertResult.APPLIED
            outcome == AutocorrectReplacementOutcome.RejectedUnchanged ->
                NextWordPredictionInsertResult.REJECTED_UNCHANGED
            outcome == AutocorrectReplacementOutcome.FailedUnchanged ->
                NextWordPredictionInsertResult.FAILED_UNCHANGED
            else -> NextWordPredictionInsertResult.INDETERMINATE
        }
    }
}

/**
 * Pure production coordinator for request/publication/tap orchestration.
 *
 * It owns session, language, policy, exact snapshot/context and request identity. Every invalidating
 * event drops both pending and published state synchronously. A tap carries the token captured by
 * the rendered UI, so even a delayed callback cannot be reinterpreted as a tap on newer content.
 */
class NextWordPredictionCoordinator {
    private var sessionId = 0L
    private var sessionActive = false
    private var editorEligible = false
    private var suggestionsEnabled = true
    private var hasNgram = false
    private var languageIdentity: String? = null
    private var pending: NextWordPredictionToken? = null
    private var publication: NextWordPredictionPublication? = null

    val currentPublication: NextWordPredictionPublication?
        get() = publication

    val latestToken: NextWordPredictionToken?
        get() = pending

    fun startSession(eligible: Boolean) {
        sessionId++
        sessionActive = true
        editorEligible = eligible
        invalidate()
    }

    fun finishSession() {
        sessionId++
        sessionActive = false
        editorEligible = false
        invalidate()
    }

    fun configure(suggestionsEnabled: Boolean, languageIdentity: String?, hasNgram: Boolean) {
        val identityChanged = this.languageIdentity != languageIdentity
        this.suggestionsEnabled = suggestionsEnabled
        this.languageIdentity = languageIdentity
        this.hasNgram = hasNgram
        if (identityChanged || !isAllowed()) invalidate()
    }

    /** Space/autocorrect is the sole automatic trigger. An uncertain mutation never chains. */
    fun afterSpace(
        result: AutocorrectSpaceResult,
        editor: AutocorrectEditor,
        requestPredictions: (List<String>) -> Long?,
    ): NextWordPredictionToken? = if (result == AutocorrectSpaceResult.INDETERMINATE) {
        invalidate()
        null
    } else {
        request(editor, requestPredictions)
    }

    fun request(
        editor: AutocorrectEditor,
        requestPredictions: (List<String>) -> Long?,
    ): NextWordPredictionToken? {
        invalidate()
        if (!isAllowed()) return null
        val context = NextWordContextExtractor.extract(safeSnapshot(editor)) ?: return null
        val requestId = requestPredictions(context.words) ?: return null
        return NextWordPredictionToken(
            sessionId,
            requestId,
            languageIdentity ?: return null,
            context.identity,
        ).also { pending = it }
    }

    fun publish(
        editor: AutocorrectEditor,
        requestId: Long,
        input: String,
        suggestions: List<String>,
    ): NextWordPredictionPublication? {
        val token = pending ?: return null
        if (
            !isAllowed() || token.sessionId != sessionId || token.requestId != requestId ||
            token.languageIdentity != languageIdentity ||
            token.contextIdentity.words.joinToString(" ") != input
        ) return null
        if (NextWordContextExtractor.extract(safeSnapshot(editor))?.identity != token.contextIdentity) {
            invalidate()
            return null
        }
        val safeSuggestions = suggestions.filter(String::isValidPrediction).distinct().take(3)
        return NextWordPredictionPublication(token, safeSuggestions).also { publication = it }
    }

    /** Invalidates on cursor/selection/snapshot movement, including identical words elsewhere. */
    fun editorChanged(editor: AutocorrectEditor) {
        val expected = pending?.contextIdentity ?: return
        val observed = NextWordContextExtractor.extract(safeSnapshot(editor))?.identity
        if (observed != expected) invalidate()
    }

    fun otherInput() = invalidate()

    /** Inserts and requests a chain only after an exact verified post-state was observed. */
    fun selectAndChain(
        editor: AutocorrectEditor,
        token: NextWordPredictionToken,
        prediction: String,
        requestPredictions: (List<String>) -> Long?,
    ): NextWordPredictionInsertResult {
        val shown = publication
        if (
            shown == null || shown.token != token || token.sessionId != sessionId ||
            token.languageIdentity != languageIdentity ||
            prediction !in shown.suggestions || !isAllowed()
        ) return NextWordPredictionInsertResult.REJECTED_UNCHANGED

        // Consume first: synchronous editor callbacks and duplicate taps can only invalidate.
        invalidate()
        val result = NextWordPredictionEditorTransaction.insert(
            editor,
            token.contextIdentity.snapshot,
            prediction,
        )
        if (result == NextWordPredictionInsertResult.APPLIED) {
            request(editor, requestPredictions)
        }
        return result
    }

    fun invalidate() {
        pending = null
        publication = null
    }

    private fun isAllowed(): Boolean =
        sessionActive && editorEligible && suggestionsEnabled && hasNgram && languageIdentity != null

    private fun safeSnapshot(editor: AutocorrectEditor): AutocorrectEditorSnapshot? = try {
        editor.snapshot()
    } catch (_: Exception) {
        null
    }
}

private const val MAX_PREDICTION_LENGTH = 64

private fun String.isValidPrediction(): Boolean =
    isNotBlank() && length <= MAX_PREDICTION_LENGTH && none { it.isWhitespace() || it.isISOControl() }
