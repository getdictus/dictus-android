package dev.pivisolutions.dictus.companion.insertion

import android.content.Context
import androidx.annotation.MainThread

/** Opaque, single-use capability. Never contains editor text or an accessibility node. */
class InsertionTarget internal constructor()

enum class InsertionFailure {
    UNAVAILABLE, INVALID_TARGET, TARGET_CHANGED, SENSITIVE, UNSUPPORTED,
    TEXT_CHANGED, SELECTION_CHANGED, INVALID_SELECTION, EMPTY_TRANSCRIPT,
    TEXT_TOO_LONG, ACTION_FAILED, VERIFICATION_FAILED, CURSOR_FAILED,
}

sealed interface InsertionResult {
    data object Inserted : InsertionResult
    data class Rejected(val reason: InsertionFailure) : InsertionResult
    /** Text may already have been written. Never automatically retry this result. */
    data class Partial(val reason: InsertionFailure) : InsertionResult
}

/**
 * Explicit capture/insert only; never observes editor text in the background or writes clipboard.
 * The coordinator owns session IDs. Every insertion attempt consumes its target, including failures.
 * All calls belong on the main thread. Clear on cancellation, release when the owner is disposed.
 */
@MainThread
class TextInsertionManager internal constructor(
    private val access: EditorAccess,
    private val reportCaptureFailure: (InsertionFailure) -> Unit = {},
) {
    constructor(context: Context) : this(ServiceEditorAccess(context.applicationContext.packageName), { reason ->
        if (dev.pivisolutions.dictus.BuildConfig.DEBUG) {
            // An enum code only: never include editor identity/content or dictated text.
            android.util.Log.d("DictusCompanion", "Capture unavailable: ${reason.name}")
        }
    })

    private var captured: Pair<InsertionTarget, EditorSnapshot>? = null
    private var released = false

    fun capture(): InsertionTarget? {
        clear()
        if (released) return captureFailed(InsertionFailure.UNAVAILABLE)
        return try {
            access.withEditor { editor ->
                if (editor == null) return@withEditor captureFailed(InsertionFailure.UNAVAILABLE)
                editor.rejection()?.let { return@withEditor captureFailed(it) }
                val state = editor.snapshot() ?: return@withEditor captureFailed(InsertionFailure.UNSUPPORTED)
                if (state.text.length > MAX_EDITOR_TEXT_LENGTH) return@withEditor captureFailed(InsertionFailure.TEXT_TOO_LONG)
                if (!validSelection(state.text, state.start, state.end)) return@withEditor captureFailed(InsertionFailure.INVALID_SELECTION)
                InsertionTarget().also {
                    captured = it to state
                    access.arm(state.identity)
                }
            }
        } catch (_: RuntimeException) {
            clear()
            captureFailed(InsertionFailure.UNAVAILABLE)
        }
    }

    private fun captureFailed(reason: InsertionFailure): InsertionTarget? {
        reportCaptureFailure(reason)
        return null
    }

    fun insert(target: InsertionTarget, transcript: String): InsertionResult {
        val saved = captured
        captured = null
        if (released || saved?.first !== target) {
            clear()
            return rejected(InsertionFailure.INVALID_TARGET)
        }
        if (transcript.isBlank()) { clear(); return rejected(InsertionFailure.EMPTY_TRANSCRIPT) }
        var attemptedWrite = false
        return try {
            if (!access.ready()) return rejected(InsertionFailure.TARGET_CHANGED)
            access.withEditor { editor ->
                if (editor == null) return@withEditor rejected(InsertionFailure.UNAVAILABLE)
                editor.rejection()?.let { return@withEditor rejected(it) }
                val current = editor.snapshot() ?: return@withEditor rejected(InsertionFailure.UNSUPPORTED)
                val previous = saved.second
                if (current.identity != previous.identity) return@withEditor rejected(InsertionFailure.TARGET_CHANGED)
                if (current.text != previous.text) return@withEditor rejected(InsertionFailure.TEXT_CHANGED)
                if (current.start != previous.start || current.end != previous.end) {
                    return@withEditor rejected(InsertionFailure.SELECTION_CHANGED)
                }
                if (!validSelection(current.text, current.start, current.end) || !validUtf16(transcript)) {
                    return@withEditor rejected(InsertionFailure.INVALID_SELECTION)
                }
                val start = minOf(current.start, current.end)
                val end = maxOf(current.start, current.end)
                val mergedLength = current.text.length.toLong() - (end - start) + transcript.length
                val maximum = editor.maximumLength()
                if (mergedLength > MAX_EDITOR_TEXT_LENGTH || (maximum >= 0 && mergedLength > maximum)) {
                    return@withEditor rejected(InsertionFailure.TEXT_TOO_LONG)
                }
                val merged = current.text.substring(0, start) + transcript + current.text.substring(end)
                val cursor = start + transcript.length
                attemptedWrite = true
                // Even a false/throwing action can have mutated an uncooperative editor.
                if (!editor.setText(merged)) return@withEditor InsertionResult.Partial(InsertionFailure.ACTION_FAILED)
                verify(previous.identity, merged)?.let { return@withEditor it }
                // Reacquire for cursor placement too; never act on a cached pre-write node.
                access.withEditor selection@{ fresh ->
                    if (fresh == null || fresh.rejection() != null) {
                        return@selection InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
                    }
                    val updated = fresh.snapshot()
                    if (updated?.identity != previous.identity || updated.text != merged) {
                        return@selection InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
                    }
                    if (updated.start == cursor && updated.end == cursor) return@selection InsertionResult.Inserted
                    if (!fresh.setSelection(cursor)) return@selection InsertionResult.Partial(InsertionFailure.CURSOR_FAILED)
                    access.withEditor final@{ last ->
                        if (last == null || last.rejection() != null) return@final InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
                        val result = last.snapshot()
                        if (result?.identity == previous.identity && result.text == merged && result.start == cursor && result.end == cursor) {
                            InsertionResult.Inserted
                        } else InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
                    }
                }
            }
        } catch (_: RuntimeException) {
            if (attemptedWrite) InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
            else rejected(InsertionFailure.UNAVAILABLE)
        } finally {
            access.disarm()
        }
    }

    /** Call immediately before launching CompanionActivationActivity, after capture. */
    fun beginActivation() { if (!released && captured != null) access.beginActivation() }

    /** Call when activation is finishing; the original editor must regain focus before insert. */
    fun endActivation() { if (!released && captured != null) access.endActivation() }

    fun clear() {
        captured = null
        access.disarm()
    }

    fun release() {
        clear()
        released = true
    }

    private fun verify(identity: EditorIdentity, text: String): InsertionResult.Partial? =
        access.withEditor { editor ->
            val state = if (editor != null && editor.rejection() == null) editor.snapshot() else null
            if (state?.identity == identity && state.text == text) null
            else InsertionResult.Partial(InsertionFailure.VERIFICATION_FAILED)
        }

    private fun rejected(reason: InsertionFailure) = InsertionResult.Rejected(reason)
}

internal data class EditorIdentity(
    val connection: Long,
    val generation: Long,
    val packageName: String,
    val windowId: Int,
    val path: List<Int>,
    val viewId: String?,
    val uniqueId: String?,
    val className: String,
    val nodeHash: Int = 0,
)

/** Only immutable metadata and a String copy live past a single node acquisition. */
internal data class EditorSnapshot(val identity: EditorIdentity, val text: String, val start: Int, val end: Int)

internal interface EditorAccess {
    fun <T> withEditor(block: (EditorHandle?) -> T): T
    fun arm(identity: EditorIdentity) {}
    fun disarm() {}
    fun beginActivation() {}
    fun endActivation() {}
    fun ready(): Boolean = true
}

internal interface EditorHandle {
    /** Must be checked before snapshot, which is the only operation allowed to read text. */
    fun rejection(): InsertionFailure?
    fun snapshot(): EditorSnapshot?
    fun maximumLength(): Int = -1
    fun setText(text: String): Boolean
    fun setSelection(cursor: Int): Boolean
}

internal const val MAX_EDITOR_TEXT_LENGTH = 16_384

internal fun validSelection(text: String, start: Int, end: Int): Boolean =
    validUtf16(text) && listOf(start, end).all { index ->
        index in 0..text.length && !(index > 0 && index < text.length &&
            text[index - 1].isHighSurrogate() && text[index].isLowSurrogate())
    }

private fun validUtf16(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val char = text[index++]
        if (char.isHighSurrogate()) {
            if (index == text.length || !text[index++].isLowSurrogate()) return false
        } else if (char.isLowSurrogate()) return false
    }
    return true
}
