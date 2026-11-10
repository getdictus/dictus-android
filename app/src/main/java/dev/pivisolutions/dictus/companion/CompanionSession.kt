package dev.pivisolutions.dictus.companion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CompanionPhase { IDLE, PREPARING, RECORDING, TRANSCRIBING, INSERTING, COMPLETED, ERROR, CANCELLED }

data class CompanionStatus(
    val phase: CompanionPhase = CompanionPhase.IDLE,
    val transcript: String? = null,
    val message: String? = null,
) {
    val busy get() = phase in setOf(CompanionPhase.PREPARING, CompanionPhase.RECORDING,
        CompanionPhase.TRANSCRIBING, CompanionPhase.INSERTING)
}

/** Platform-free single-flight session. The result stays in memory until explicit dismissal. */
class CompanionSession(
    private val scope: CoroutineScope,
    private val begin: suspend () -> Unit,
    private val transcribe: suspend () -> String?,
    private val insert: (String) -> Boolean,
    private val abort: () -> Unit,
    private val release: () -> Unit,
) {
    private val mutableState = MutableStateFlow(CompanionStatus())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var inserted = false

    fun start(): Boolean {
        if (state.value.busy || state.value.transcript != null || operation?.isActive == true) return false
        val id = ++generation
        inserted = false
        mutableState.value = CompanionStatus(CompanionPhase.PREPARING)
        operation = scope.launch {
            try {
                begin()
                if (generation == id) mutableState.value = CompanionStatus(CompanionPhase.RECORDING)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == id) {
                    abort()
                    release()
                    mutableState.value = CompanionStatus(CompanionPhase.ERROR, message = "start")
                }
            }
        }
        return true
    }

    fun stop() {
        if (state.value.phase != CompanionPhase.RECORDING) return
        val id = generation
        mutableState.value = CompanionStatus(CompanionPhase.TRANSCRIBING)
        operation = scope.launch {
            try {
                val text = transcribe()
                if (generation != id) return@launch
                if (text.isNullOrBlank()) {
                    mutableState.value = CompanionStatus(CompanionPhase.ERROR, message = "recognition")
                } else {
                    mutableState.value = CompanionStatus(CompanionPhase.INSERTING, text)
                    inserted = insert(text)
                    mutableState.value = CompanionStatus(CompanionPhase.COMPLETED,
                        transcript = if (inserted) null else text,
                        message = if (inserted) "inserted" else "copy")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == id) {
                    // Keep a result if a platform insertion operation failed unexpectedly.
                    mutableState.value = state.value.copy(phase = CompanionPhase.ERROR, message = "recognition")
                }
            } finally {
                // Cancellation already released this generation. A late native completion must
                // never release the lease belonging to a newer dictation session.
                if (generation == id) release()
            }
        }
    }

    fun retryInsertion() {
        val result = state.value.transcript ?: return
        if (state.value.busy || inserted) return
        mutableState.value = CompanionStatus(CompanionPhase.INSERTING, result)
        inserted = runCatching { insert(result) }.getOrDefault(false)
        mutableState.value = CompanionStatus(CompanionPhase.COMPLETED,
            if (inserted) null else result, if (inserted) "inserted" else "copy")
    }

    fun cancel() {
        ++generation
        operation?.cancel()
        abort()
        release()
        mutableState.value = CompanionStatus(CompanionPhase.CANCELLED)
    }

    fun fail() {
        if (!state.value.busy) return
        ++generation
        operation?.cancel()
        abort()
        release()
        mutableState.value = CompanionStatus(CompanionPhase.ERROR, message = "capture")
    }

    fun dismiss() {
        if (state.value.busy) return
        release()
        mutableState.value = CompanionStatus()
    }
}
