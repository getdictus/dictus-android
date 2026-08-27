package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.stt.SttProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class SttProviderSlotTest {

    @Test
    fun `provider is released after idle timeout`() = runTest {
        val provider = FakeWhisperProvider()
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 1_000L)

        slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { provider }
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(0, provider.releaseCount)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, provider.releaseCount)
        assertEquals(dev.pivisolutions.dictus.core.service.SttEngineState.Cold, slot.state.value)
    }

    @Test
    fun `provider activity resets idle timeout`() = runTest {
        val provider = FakeWhisperProvider()
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 1_000L)

        slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { provider }
        advanceTimeBy(750L)
        slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { provider }
        advanceTimeBy(750L)
        runCurrent()
        assertEquals(0, provider.releaseCount)

        advanceTimeBy(250L)
        runCurrent()
        assertEquals(1, provider.releaseCount)
    }

    @Test
    fun `idle release waits until provider use completes`() = runTest {
        val provider = FakeWhisperProvider()
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 1_000L)
        val enteredUse = CompletableDeferred<Unit>()
        val finishUse = CompletableDeferred<Unit>()

        val use = async {
            slot.withProvider("tiny", "/models/tiny.bin", FakeWhisperProvider::class, { provider }) {
                enteredUse.complete(Unit)
                finishUse.await()
            }
        }
        enteredUse.await()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, provider.releaseCount)

        finishUse.complete(Unit)
        use.await()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, provider.releaseCount)
    }

    @Test
    fun `same ready provider and model are reused`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val created = mutableListOf<FakeWhisperProvider>()
        val factory = {
            FakeWhisperProvider().also(created::add)
        }

        val first = slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class, factory)
        val second = slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class, factory)

        assertSame(first, second)
        assertEquals(1, created.size)
        assertEquals(1, created.single().initializeCount)
        assertEquals(0, created.single().releaseCount)
    }

    @Test
    fun `same provider class with a different model releases and reinitializes`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val created = mutableListOf<FakeWhisperProvider>()
        val factory = {
            FakeWhisperProvider().also(created::add)
        }

        val tiny = slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class, factory)
        val small = slot.getOrInitialize("small-q5_1", "/models/small-q5_1.bin", FakeWhisperProvider::class, factory)

        assertNotSame(tiny, small)
        assertEquals(2, created.size)
        assertEquals(1, created[0].releaseCount)
        assertEquals(listOf("/models/tiny.bin"), created[0].initializedPaths)
        assertEquals(listOf("/models/small-q5_1.bin"), created[1].initializedPaths)
    }

    @Test
    fun `different provider class releases and reinitializes`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val whisper = FakeWhisperProvider()
        val parakeet = FakeParakeetProvider()

        slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { whisper }
        val switched = slot.getOrInitialize(
            "parakeet-ctc-110m-int8",
            "/models/parakeet/model.int8.onnx",
            FakeParakeetProvider::class,
        ) { parakeet }

        assertSame(parakeet, switched)
        assertEquals(1, whisper.releaseCount)
        assertEquals(1, parakeet.initializeCount)
    }

    @Test
    fun `failed initialization releases partial provider and leaves slot empty`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val failed = FakeWhisperProvider(initializeResult = false)

        val result = slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { failed }

        assertNull(result)
        assertEquals(1, failed.releaseCount)
        val replacement = FakeWhisperProvider()
        assertSame(
            replacement,
            slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { replacement },
        )
    }

    @Test
    fun `throwing initialization releases partial provider`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val failed = FakeWhisperProvider(initializeFailure = IllegalStateException("native load failed"))
        var threwExpectedFailure = false

        try {
            slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) { failed }
        } catch (_: IllegalStateException) {
            threwExpectedFailure = true
        }

        assertTrue(threwExpectedFailure)
        assertEquals(1, failed.releaseCount)
    }

    @Test
    fun `concurrent requests for the same model initialize only once`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val created = mutableListOf<FakeWhisperProvider>()

        val providers = List(20) {
            async {
                slot.getOrInitialize("tiny", "/models/tiny.bin", FakeWhisperProvider::class) {
                    FakeWhisperProvider(initializeDelayMs = 10).also(created::add)
                }
            }
        }.awaitAll()

        assertEquals(1, created.size)
        providers.forEach { assertSame(created.single(), it) }
        assertEquals(
            dev.pivisolutions.dictus.core.service.SttEngineState.Ready("tiny"),
            slot.state.value,
        )
    }

    @Test
    fun `model switch waits until current provider use completes`() = runTest {
        val slot = SttProviderSlot(backgroundScope, idleTimeoutMs = 60_000L)
        val tiny = FakeWhisperProvider()
        val small = FakeWhisperProvider()
        val enteredUse = CompletableDeferred<Unit>()
        val finishUse = CompletableDeferred<Unit>()

        val firstUse = async {
            slot.withProvider("tiny", "/models/tiny.bin", FakeWhisperProvider::class, { tiny }) {
                enteredUse.complete(Unit)
                finishUse.await()
                assertEquals(0, tiny.releaseCount)
            }
        }
        enteredUse.await()
        val switch = async {
            slot.withProvider(
                "small-q5_1",
                "/models/small-q5_1.bin",
                FakeWhisperProvider::class,
                { small },
            ) { }
        }

        yield()
        assertEquals(0, tiny.releaseCount)
        finishUse.complete(Unit)
        firstUse.await()
        switch.await()

        assertEquals(1, tiny.releaseCount)
        assertEquals(1, small.initializeCount)
    }
}

private suspend fun <T : SttProvider> SttProviderSlot.getOrInitialize(
    modelKey: String,
    modelPath: String,
    providerClass: KClass<T>,
    factory: () -> T,
): SttProvider? = withProvider(modelKey, modelPath, providerClass, factory) { it }

private open class FakeSttProvider(
    private val id: String,
    private val initializeResult: Boolean = true,
    private val initializeFailure: Throwable? = null,
    private val initializeDelayMs: Long = 0,
) : SttProvider {
    override val providerId: String = id
    override val displayName: String = id
    override val supportedLanguages: List<String> = emptyList()
    final override var isReady: Boolean = false
        private set

    var initializeCount = 0
        private set
    var releaseCount = 0
        private set
    val initializedPaths = mutableListOf<String>()

    override suspend fun initialize(modelPath: String): Boolean {
        initializeCount++
        initializedPaths += modelPath
        if (initializeDelayMs > 0) delay(initializeDelayMs)
        initializeFailure?.let { throw it }
        isReady = initializeResult
        return initializeResult
    }

    override suspend fun transcribe(samples: FloatArray, language: String): String = ""

    override suspend fun release() {
        releaseCount++
        isReady = false
    }
}

private class FakeWhisperProvider(
    initializeResult: Boolean = true,
    initializeFailure: Throwable? = null,
    initializeDelayMs: Long = 0,
) : FakeSttProvider("whisper", initializeResult, initializeFailure, initializeDelayMs)

private class FakeParakeetProvider : FakeSttProvider("parakeet")
