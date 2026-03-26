package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/**
 * Unit tests for ModelDownloader.downloadWithProgress().
 *
 * Uses OkHttp MockWebServer to simulate HTTP responses without a real network.
 * Uses Robolectric for the Android Context needed by ModelManager.
 *
 * WHY downloadWithProgress(key, urlOverride): The production method resolves the URL
 * from the catalog. The internal overload allows tests to inject a mock server URL
 * without wrapping ModelManager or changing catalog constants.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var modelManager: ModelManager
    private lateinit var downloader: ModelDownloader

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val context = RuntimeEnvironment.getApplication()
        modelManager = ModelManager(context)

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

    @Test
    fun `downloadWithProgress unknown key emits Error with message`() = runTest {
        val events = downloader.downloadWithProgress("unknown").toList()

        assertEquals(1, events.size)
        val error = events[0] as DownloadProgress.Error
        assertTrue("Message should contain key", error.message.contains("unknown"))
        assertTrue("Message should mention 'Unknown model key'",
            error.message.contains("Unknown model key"))
    }

    @Test
    fun `downloadWithProgress HTTP 404 emits Error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val events = downloader.downloadWithProgress(
            modelKey = "tiny",
            urlOverride = mockServer.url("/tiny.bin").toString()
        ).toList()

        assertTrue("Should have at least one event", events.isNotEmpty())
        val last = events.last()
        assertTrue("Expected Error, got $last", last is DownloadProgress.Error)
        assertTrue("Error message should contain 404",
            (last as DownloadProgress.Error).message.contains("404"))
    }

    @Test
    fun `downloadWithProgress success emits Progress then Complete`() = runTest {
        val bytes = ByteArray(1024) { it.toByte() }
        val buffer = Buffer().write(bytes)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(buffer)
                .addHeader("Content-Length", bytes.size.toString())
        )

        val events = downloader.downloadWithProgress(
            modelKey = "tiny",
            urlOverride = mockServer.url("/tiny.bin").toString()
        ).toList()

        assertTrue("Should have at least 2 events", events.size >= 2)

        val progressEvents = events.filterIsInstance<DownloadProgress.Progress>()
        val completeEvents = events.filterIsInstance<DownloadProgress.Complete>()

        assertTrue("Should have at least one progress event", progressEvents.isNotEmpty())
        assertEquals("Should have exactly one Complete event", 1, completeEvents.size)
    }

    @Test
    fun `downloadWithProgress Progress percent stays in 0 to 100 range`() = runTest {
        val bytes = ByteArray(8192) { it.toByte() }
        val buffer = Buffer().write(bytes)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(buffer)
                .addHeader("Content-Length", bytes.size.toString())
        )

        val events = downloader.downloadWithProgress(
            modelKey = "tiny",
            urlOverride = mockServer.url("/tiny.bin").toString()
        ).toList()

        val percentValues = events
            .filterIsInstance<DownloadProgress.Progress>()
            .map { it.percent }

        percentValues.forEach { percent ->
            assertTrue("Percent $percent should be in 0..100", percent in 0..100)
        }
    }

    @Test
    fun `downloadWithProgress Complete emits a non-empty path`() = runTest {
        val bytes = ByteArray(512) { it.toByte() }
        val buffer = Buffer().write(bytes)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(buffer)
                .addHeader("Content-Length", bytes.size.toString())
        )

        val events = downloader.downloadWithProgress(
            modelKey = "tiny",
            urlOverride = mockServer.url("/tiny.bin").toString()
        ).toList()

        val completeEvent = events.filterIsInstance<DownloadProgress.Complete>().firstOrNull()
        assertTrue("Should have a Complete event", completeEvent != null)
        assertTrue("Complete path should not be empty", completeEvent!!.path.isNotEmpty())
    }
}
