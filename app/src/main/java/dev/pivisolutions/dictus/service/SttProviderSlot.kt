package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.stt.SttProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
 * while a transcription is still using them.
 */
internal class SttProviderSlot {
    private val mutex = Mutex()
    private var provider: SttProvider? = null
    private var modelKey: String? = null

    suspend fun <T> withProvider(
        requestedModelKey: String,
        modelPath: String,
        requiredProviderClass: KClass<out SttProvider>,
        createProvider: () -> SttProvider,
        useProvider: suspend (SttProvider) -> T,
    ): T? = mutex.withLock {
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
                withContext(NonCancellable) { current.release() }
                currentCoroutineContext().ensureActive()
            }

            val replacement = createProvider()
            val initialized = try {
                replacement.initialize(modelPath)
            } catch (failure: Throwable) {
                withContext(NonCancellable) { runCatching { replacement.release() } }
                throw failure
            }
            if (!initialized) {
                withContext(NonCancellable) { replacement.release() }
                return null
            }

            provider = replacement
            modelKey = requestedModelKey
            replacement
        }

        useProvider(readyProvider)
    }

    suspend fun release() = mutex.withLock {
        val current = provider
        provider = null
        modelKey = null
        withContext(NonCancellable) { current?.release() }
    }
}
