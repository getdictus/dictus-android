package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelFile
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelManager
import dev.pivisolutions.dictus.model.ModelSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Unit tests for ModelDownloader installs: download, extraction, verification, rename (#169).
 *
 * Uses OkHttp MockWebServer to simulate HTTP responses without a real network.
 * Uses Robolectric for the Android Context needed by ModelManager.
 *
 * WHY install(info): the catalog's sources and manifests point at the real files. Tests
 * serve their own bytes and pass a ModelInfo whose source is the mock server and whose
 * manifest describes those bytes.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var modelManager: ModelManager
    private lateinit var downloader: ModelDownloader
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val context = RuntimeEnvironment.getApplication()
        modelManager = ModelManager(context)
        modelsDir = File(context.filesDir, "models")

        val testClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false) // Don't follow redirects in tests
            .build()

        downloader = ModelDownloader(modelManager, testClient)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun manifestOf(name: String, bytes: ByteArray) = ModelFile(
        name,
        bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )

    private fun serve(bytes: ByteArray) {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(bytes))
                .addHeader("Content-Length", bytes.size.toString()),
        )
    }

    private val filesSource get() = ModelSource.Files(mockServer.url("/models").toString())

    /** Tiny, served by the mock server, with a manifest describing [bytes]. */
    private fun whisperInfo(bytes: ByteArray): ModelInfo =
        ModelCatalog.findByKey("tiny")!!.copy(files = listOf(manifestOf("ggml-tiny.bin", bytes)), source = filesSource)

    /** Parakeet 110M, its archive served by the mock server, with a manifest describing [files]. */
    private fun archiveInfo(files: Map<String, ByteArray>): ModelInfo =
        ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!.copy(
            files = files.map { (name, bytes) -> manifestOf(name, bytes) },
            source = ModelSource.Archive(mockServer.url("/model.tar.bz2").toString()),
        )

    /** Parakeet 0.6B v3 files with random content, in the catalog's order. */
    private fun parakeetV3Files(seed: Int): Map<String, ByteArray> {
        val random = Random(seed)
        return mapOf(
            "encoder.int8.onnx" to random.nextBytes(200_000),
            "decoder.int8.onnx" to random.nextBytes(20_000),
            "joiner.int8.onnx" to random.nextBytes(10_000),
            "tokens.txt" to "a 0\nb 1\n".toByteArray(),
        )
    }

    /** Parakeet 0.6B v3, served file by file by the mock server, with a manifest describing [files]. */
    private fun parakeetV3Info(files: Map<String, ByteArray>): ModelInfo =
        ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!
            .copy(files = files.map { (name, bytes) -> manifestOf(name, bytes) }, source = filesSource)

    /** Serve each of [files] at /models/<name>; [respond] can replace the response of a file. */
    private fun serveFiles(
        files: Map<String, ByteArray>,
        respond: (name: String, ok: MockResponse) -> MockResponse = { _, ok -> ok },
    ) {
        mockServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.path!!.removePrefix("/models/")
                val bytes = files[name] ?: return MockResponse().setResponseCode(404)
                return respond(name, MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
            }
        }
    }

    private fun tarBz2(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(out)).use { tar ->
            entries.forEach { (name, bytes) ->
                tar.putArchiveEntry(TarArchiveEntry("sherpa-onnx-test/$name").apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `downloadWithProgress unknown key emits Error with message`() = runTest {
        val events = downloader.downloadWithProgress("unknown").toList()

        assertEquals(1, events.size)
        val error = events[0] as DownloadProgress.Error
        assertTrue("Message should contain key", error.message.contains("unknown"))
        assertTrue("Message should mention 'Unknown model key'", error.message.contains("Unknown model key"))
    }

    @Test
    fun `HTTP 404 emits Error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val events = downloader.install(whisperInfo(ByteArray(1))).toList()

        val last = events.last()
        assertTrue("Expected Error, got $last", last is DownloadProgress.Error)
        assertTrue((last as DownloadProgress.Error).message.contains("404"))
    }

    @Test
    fun `whisper install downloads, verifies, then completes at the model path`() = runTest {
        val bytes = Random(1).nextBytes(8192)
        serve(bytes)

        val events = downloader.install(whisperInfo(bytes)).toList()

        val progress = events.filterIsInstance<DownloadProgress.Progress>().map { it.percent }
        assertTrue("Should report download progress", progress.isNotEmpty())
        assertTrue(progress.all { it in 0..100 })
        assertEquals(DownloadProgress.Verifying, events[events.size - 2])
        val complete = events.last() as DownloadProgress.Complete
        assertEquals(File(modelsDir, "ggml-tiny.bin").absolutePath, complete.path)
        assertTrue(File(complete.path).readBytes().contentEquals(bytes))
        assertFalse(File(modelsDir, "ggml-tiny.bin.tmp").exists())
    }

    @Test
    fun `whisper install with a corrupted body fails and leaves no file`() = runTest {
        val expected = Random(2).nextBytes(4096)
        serve(expected.copyOf().also { it[0] = (it[0] + 1).toByte() })

        val events = downloader.install(whisperInfo(expected)).toList()

        val error = events.last() as DownloadProgress.Error
        assertTrue(error.message, error.message.contains("Integrity check failed"))
        assertFalse(File(modelsDir, "ggml-tiny.bin").exists())
        assertFalse(File(modelsDir, "ggml-tiny.bin.tmp").exists())
    }

    @Test
    fun `whisper install resumes a part left by a network error, then verifies the whole file`() = runTest {
        val bytes = Random(4).nextBytes(8192)
        val etag = "\"tiny-v1\""
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setHeader("ETag", etag).setBody(Buffer().write(bytes))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val failed = downloader.install(whisperInfo(bytes)).toList()

        assertTrue("Expected Error, got ${failed.last()}", failed.last() is DownloadProgress.Error)
        val part = File(modelsDir, "ggml-tiny.bin.tmp.part")
        val offset = part.length().toInt()
        assertTrue("Part kept after the network error", offset in 1 until bytes.size)

        mockServer.takeRequest()
        mockServer.enqueue(
            MockResponse().setResponseCode(206).setHeader("ETag", etag)
                .setHeader("Content-Range", "bytes $offset-${bytes.size - 1}/${bytes.size}")
                .setBody(Buffer().write(bytes.copyOfRange(offset, bytes.size))),
        )

        val events = downloader.install(whisperInfo(bytes)).toList()

        assertEquals("bytes=$offset-", mockServer.takeRequest().getHeader("Range"))
        val complete = events.last() as DownloadProgress.Complete
        assertTrue(File(complete.path).readBytes().contentEquals(bytes))
        assertFalse(part.exists())
    }

    @Test
    fun `parakeet install extracts with progress, verifies, then moves the files into place`() = runTest {
        val model = Random(3).nextBytes(300_000)
        val tokens = "a 0\nb 1\n".toByteArray()
        val info = archiveInfo(mapOf("model.int8.onnx" to model, "tokens.txt" to tokens))
        // A file the model does not need, like the archives' test WAVs: must not be extracted
        serve(tarBz2(mapOf("tokens.txt" to tokens, "test.wav" to ByteArray(100), "model.int8.onnx" to model)))

        val events = downloader.install(info).toList()

        val extracting = events.filterIsInstance<DownloadProgress.Extracting>().map { it.percent }
        assertEquals(0, extracting.first())
        assertEquals("Extraction progress only goes up", extracting.sorted(), extracting)
        assertTrue("Extraction ends near the end of the archive", extracting.last() >= 90)
        assertEquals(DownloadProgress.Verifying, events[events.size - 2])
        val complete = events.last() as DownloadProgress.Complete
        val dir = File(modelsDir, info.key)
        assertEquals(File(dir, "model.int8.onnx").absolutePath, complete.path)
        assertEquals(setOf("model.int8.onnx", "tokens.txt"), dir.list()!!.toSet())
        assertFalse("Staging dir is gone", File(modelsDir, "${info.key}.part").exists())
        assertFalse("Archive is gone", File(modelsDir, "${info.key}.archive.tmp").exists())
    }

    @Test
    fun `parakeet archive missing a file fails and leaves no model dir`() = runTest {
        val model = Random(4).nextBytes(1000)
        val tokens = "a 0\n".toByteArray()
        val info = archiveInfo(mapOf("model.int8.onnx" to model, "tokens.txt" to tokens))
        serve(tarBz2(mapOf("model.int8.onnx" to model)))

        val events = downloader.install(info).toList()

        val error = events.last() as DownloadProgress.Error
        assertTrue(error.message, error.message.contains("tokens.txt"))
        assertFalse(File(modelsDir, info.key).exists())
        assertFalse(File(modelsDir, "${info.key}.part").exists())
        assertNull(modelManager.getModelPath(info.key))
    }

    @Test
    fun `parakeet v3 install downloads each file, verifies, then moves them into place without extracting`() = runTest {
        val files = parakeetV3Files(5)
        val info = parakeetV3Info(files)
        serveFiles(files)

        val events = downloader.install(info).toList()

        assertEquals(files.keys.map { "/models/$it" }, List(files.size) { mockServer.takeRequest().path })
        assertTrue(events.none { it is DownloadProgress.Extracting })
        val progress = events.filterIsInstance<DownloadProgress.Progress>().map { it.percent }
        assertEquals("Progress covers all the files and only goes up", progress.sorted(), progress)
        assertEquals(100, progress.last())
        assertEquals(DownloadProgress.Verifying, events[events.size - 2])
        val complete = events.last() as DownloadProgress.Complete
        val dir = File(modelsDir, info.key)
        assertEquals(File(dir, "encoder.int8.onnx").absolutePath, complete.path)
        files.forEach { (name, bytes) -> assertTrue(name, File(dir, name).readBytes().contentEquals(bytes)) }
        assertEquals(files.keys, dir.list()!!.toSet())
        assertEquals("No temp file left", setOf(info.key), modelsDir.list()!!.filter { it.startsWith(info.key) }.toSet())
    }

    @Test
    fun `parakeet v3 retry after a network error does not download a finished file again`() = runTest {
        val files = parakeetV3Files(6)
        val info = parakeetV3Info(files)
        serveFiles(files) { name, ok ->
            if (name == "joiner.int8.onnx") ok.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) else ok
        }

        val failed = downloader.install(info).toList()

        assertTrue("Expected Error, got ${failed.last()}", failed.last() is DownloadProgress.Error)
        assertNull(modelManager.getModelPath(info.key))
        serveFiles(files)
        val before = mockServer.requestCount

        val events = downloader.install(info).toList()

        assertTrue("Expected Complete, got ${events.last()}", events.last() is DownloadProgress.Complete)
        val retried = List(mockServer.requestCount) { mockServer.takeRequest().path }.drop(before)
        assertEquals(listOf("/models/joiner.int8.onnx", "/models/tokens.txt"), retried)
    }

    @Test
    fun `parakeet v3 corrupted file fails the install and leaves no model dir`() = runTest {
        val files = parakeetV3Files(7)
        val info = parakeetV3Info(files)
        serveFiles(files.mapValues { (name, bytes) -> if (name == "decoder.int8.onnx") bytes.reversedArray() else bytes })

        val events = downloader.install(info).toList()

        val error = events.last() as DownloadProgress.Error
        assertTrue(error.message, error.message.contains("decoder.int8.onnx"))
        assertFalse(File(modelsDir, info.key).exists())
        assertEquals("Nothing left behind", emptyList<String>(), modelsDir.list()!!.filter { it.startsWith(info.key) })
    }

    @Test
    fun `an installed parakeet v3 from the archive completes without a request`() = runTest {
        // Same directory and file names as the archive installs before #325: an update keeps it
        val info = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!
        val dir = File(modelsDir, info.key).apply { mkdirs() }
        info.files.forEach { java.io.RandomAccessFile(File(dir, it.name), "rw").use { f -> f.setLength(it.sizeBytes) } }

        val events = downloader.downloadWithProgress(info.key).toList()

        assertEquals(DownloadProgress.Complete(File(dir, "encoder.int8.onnx").absolutePath), events.last())
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `an installed model completes without a request`() = runTest {
        val tiny = ModelCatalog.findByKey("tiny")!!
        modelsDir.mkdirs()
        java.io.RandomAccessFile(File(modelsDir, "ggml-tiny.bin"), "rw").use { it.setLength(tiny.expectedSizeBytes) }

        val events = downloader.downloadWithProgress("tiny").toList()

        assertEquals(DownloadProgress.Complete(File(modelsDir, "ggml-tiny.bin").absolutePath), events.last())
        assertEquals(0, mockServer.requestCount)
    }
}
