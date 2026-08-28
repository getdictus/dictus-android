package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.service.SttEngineState
import dev.pivisolutions.dictus.core.stt.SttProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * Owns the single loaded STT provider and the exact model that initialized it.
 *
 * Provider type alone is not a sufficient cache key: two Whisper catalog entries
 * use the same provider class but load different native model files. Acquisition,
 * use, replacement, and release share one mutex so native contexts cannot be freed
 * while a transcription is still using them. Each completed use renews an idle
 * lease; expiry releases native memory without racing an active use.
 */
internal class SttProviderSlot(
    private val scope: CoroutineScope,
    private val idleTimeoutMs: Long,
) {
    private val mutex = Mutex()
    private var provider: SttProvider? = null
    private var modelKey: String? = null
    private var idleReleaseJob: Job? = null

    private val _state = MutableStateFlow<SttEngineState>(SttEngineState.Cold)
    val state: StateFlow<SttEngineState> = _state.asStateFlow()

    suspend fun <T> withProvider(
        requestedModelKey: String,
        modelPath: String,
        requiredProviderClass: KClass<out SttProvider>,
        createProvider: () -> SttProvider,
        useProvider: suspend (SttProvider) -> T,
    ): T? {
        return mutex.withLock {
            // Serialize lease changes with provider ownership. If expiry and a new
            // request arrive together, whichever owns the lock defines the order.
            idleReleaseJob?.cancel()
            val current = provider
            val canReuse = current?.isReady == true &&
                current::class == requiredProviderClass &&
                modelKey == requestedModelKey

            val readyProvider = if (canReuse) {
                current
            } else {
                if (current != null) {
                    provider = null
                    modelKey = null
                    _state.value = SttEngineState.Cold
                    withContext(NonCancellable) { current.release() }
                    currentCoroutineContext().ensureActive()
                }

                _state.value = SttEngineState.Loading(requestedModelKey)
                val replacement = createProvider()
                val initialized = try {
                    replacement.initialize(modelPath)
                } catch (failure: Throwable) {
                    withContext(NonCancellable) { runCatching { replacement.release() } }
                    _state.value = SttEngineState.Failed(requestedModelKey)
                    throw failure
                }
                if (!initialized) {
                    withContext(NonCancellable) { replacement.release() }
                    _state.value = SttEngineState.Failed(requestedModelKey)
                    return null
                }

                provider = replacement
                modelKey = requestedModelKey
                _state.value = SttEngineState.Ready(requestedModelKey)
                replacement
            }

            try {
                useProvider(readyProvider)
            } finally {
                if (provider === readyProvider) scheduleIdleReleaseLocked()
            }
        }
    }

    suspend fun release() {
        mutex.withLock {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            val current = provider
            provider = null
            modelKey = null
            _state.value = SttEngineState.Cold
            withContext(NonCancellable) { current?.release() }
        }
    }

    /** Publish "no model on disk yet" without retaining a provider. Not a failure. */
    suspend fun markModelMissing(requestedModelKey: String) {
        mutex.withLock {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            val current = provider
            provider = null
            modelKey = null
            withContext(NonCancellable) { current?.release() }
            _state.value = SttEngineState.ModelMissing(requestedModelKey)
        }
    }

    /** Publish an actionable failure when prewarm cannot start, without retaining a provider. */
    suspend fun markFailed(requestedModelKey: String) {
        mutex.withLock {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            val current = provider
            provider = null
            modelKey = null
            withContext(NonCancellable) { current?.release() }
            _state.value = SttEngineState.Failed(requestedModelKey)
        }
    }

    private fun scheduleIdleReleaseLocked() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(idleTimeoutMs)
            mutex.withLock {
                val current = provider
                provider = null
                modelKey = null
                _state.value = SttEngineState.Cold
                withContext(NonCancellable) { current?.release() }
            }
        }
    }
}
