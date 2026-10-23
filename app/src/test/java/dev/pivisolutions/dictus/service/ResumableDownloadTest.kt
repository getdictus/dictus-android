package dev.pivisolutions.dictus.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resume, restart and stall rules of [ResumableDownload] (#131), against a local MockWebServer.
 */
class ResumableDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dest: File
    private lateinit var part: File
    private lateinit var meta: File
    private val body = ByteArray(10_000) { (it % 251).toByte() }
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val download = ResumableDownload(client, stallTimeoutMs = 300)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dest = File(tmp.root, "model.bin.tmp")
        part = File("${dest.path}.part")
        meta = File("${part.path}.meta")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url() = server.url("/model.bin").toString()

    private fun full(etag: String = ETAG) =
        MockResponse().setResponseCode(200).setHeader("ETag", etag).setBody(Buffer().write(body))

    private fun partial(from: Int, etag: String = ETAG, total: Int = body.size) =
        MockResponse().setResponseCode(206)
            .setHeader("ETag", etag)
            .setHeader("Content-Range", "bytes $from-${total - 1}/$total")
            .setBody(Buffer().write(body.copyOfRange(from, body.size)))

    /** A part of [length] bytes left by an interrupted download of the file tagged [etag]. */
    private fun leavePart(length: Int, etag: String = ETAG) {
        part.writeBytes(body.copyOfRange(0, length))
        ResumableDownload.PartMeta(etag, body.size.toLong()).write(meta)
    }

    @Test
    fun `fresh download writes dest and leaves no part`() = runTest {
        server.enqueue(full())
        val progress = mutableListOf<Int>()

        download.fetch(url(), dest) { progress += it }

        assertArrayEquals(body, dest.readBytes())
        assertFalse(part.exists())
        assertFalse(meta.exists())
        assertEquals(100, progress.last())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `resume sends Range and If-Range and appends the 206 body`() = runTest {
        leavePart(4_000)
        server.enqueue(partial(4_000))
        val progress = mutableListOf<Int>()

        download.fetch(url(), dest) { progress += it }

        val request = server.takeRequest()
        assertEquals("bytes=4000-", request.getHeader("Range"))
        assertEquals(ETAG, request.getHeader("If-Range"))
        assertArrayEquals(body, dest.readBytes())
        assertTrue("Progress starts from the part, not from 0", progress.first() >= 40)
        assertFalse(part.exists())
    }

    @Test
    fun `200 to a range request restarts from byte 0`() = runTest {
        leavePart(4_000)
        server.enqueue(full())

        download.fetch(url(), dest) {}

        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `If-Range mismatch answered with 200 replaces the part with the new file`() = runTest {
        leavePart(4_000, etag = "\"old\"")
        val newBody = ByteArray(6_000) { 7 }
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", ETAG).setBody(Buffer().write(newBody)))

        download.fetch(url(), dest) {}

        assertEquals("\"old\"", server.takeRequest().getHeader("If-Range"))
        assertArrayEquals(newBody, dest.readBytes())
    }

    @Test
    fun `206 with another validator is not appended, the download restarts`() = runTest {
        // The HuggingFace CDN answers 206 even when If-Range does not match.
        leavePart(4_000, etag = "\"old\"")
        server.enqueue(partial(4_000, etag = ETAG))
        server.enqueue(full())

        download.fetch(url(), dest) {}

        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertNull("Second request starts from 0", server.takeRequest().getHeader("Range"))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `206 starting at another offset restarts from byte 0`() = runTest {
        leavePart(4_000)
        server.enqueue(partial(3_000))
        server.enqueue(full())

        download.fetch(url(), dest) {}

        assertEquals(2, server.requestCount)
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `416 on a complete part finishes without downloading again`() = runTest {
        leavePart(body.size)
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${body.size}"))

        download.fetch(url(), dest) {}

        assertEquals(1, server.requestCount)
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `416 on a part of the wrong size discards it and restarts`() = runTest {
        leavePart(4_000)
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(full())

        download.fetch(url(), dest) {}

        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `part without sidecar is not resumed`() = runTest {
        part.writeBytes(body.copyOfRange(0, 4_000))
        server.enqueue(full())

        download.fetch(url(), dest) {}

        assertNull(server.takeRequest().getHeader("Range"))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `stall throws DownloadStalledException and keeps the part for a resume`() = runTest {
        // 4000 bytes, then the server stops sending for far longer than the stall timeout.
        server.enqueue(full().throttleBody(4_000, 1, TimeUnit.SECONDS))

        try {
            download.fetch(url(), dest) {}
            fail("Expected a stall")
        } catch (e: DownloadStalledException) {
            // expected
        }

        assertFalse(dest.exists())
        assertEquals(4_000L, part.length())
        assertEquals(ResumableDownload.PartMeta(ETAG, body.size.toLong()), ResumableDownload.PartMeta.read(meta))

        server.enqueue(partial(4_000))
        download.fetch(url(), dest) {}

        assertArrayEquals(body, dest.readBytes())
    }

    @Test
    fun `connection dropped mid-body keeps the part`() = runTest {
        server.enqueue(full().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        try {
            download.fetch(url(), dest) {}
            fail("Expected an IOException")
        } catch (e: IOException) {
            // expected
        }

        assertFalse(dest.exists())
        assertTrue(meta.exists())
        assertTrue(part.length() < body.size)
    }

    @Test
    fun `HTTP error keeps the part`() = runTest {
        leavePart(4_000)
        server.enqueue(MockResponse().setResponseCode(503))

        try {
            download.fetch(url(), dest) {}
            fail("Expected an IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 503", e.message)
        }

        assertEquals(4_000L, part.length())
        assertTrue(meta.exists())
    }

    @Test
    fun `cancellation deletes the part`() = runTest {
        server.enqueue(full())

        try {
            download.fetch(url(), dest) { percent -> if (percent >= 10) throw CancellationException("user cancel") }
            fail("Expected a cancellation")
        } catch (e: CancellationException) {
            // expected
        }

        assertFalse(part.exists())
        assertFalse(meta.exists())
        assertFalse(dest.exists())
    }

    private companion object {
        const val ETAG = "\"abc123\""
    }
}
