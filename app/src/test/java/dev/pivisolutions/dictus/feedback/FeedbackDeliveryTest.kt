package dev.pivisolutions.dictus.feedback

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the form reads the server's answer and when it lets the user try again (#373). */
class FeedbackDeliveryTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `200 counts only with its ok body`() {
        assertEquals(FeedbackOutcome.Delivered, FeedbackOutcome.of(200, null, """{"ok":true}"""))
        assertEquals(
            FeedbackOutcome.Failed(FeedbackFailure.Unreachable),
            FeedbackOutcome.of(200, null, "<html>Wi-Fi login</html>"),
        )
    }

    @Test
    fun `each status maps to one failure`() {
        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.Unavailable), FeedbackOutcome.of(503, null, ""))
        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.Unavailable), FeedbackOutcome.of(404, null, ""))
        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.DeliveryFailed), FeedbackOutcome.of(502, null, ""))
        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.DeliveryFailed), FeedbackOutcome.of(500, null, ""))
        listOf(400, 413, 415, 405).forEach {
            assertEquals(FeedbackOutcome.Failed(FeedbackFailure.Rejected(it)), FeedbackOutcome.of(it, null, ""))
        }
    }

    @Test
    fun `Retry is offered for every failure but a rejection`() {
        assertTrue(FeedbackFailure.Unreachable.allowsRetry)
        assertTrue(FeedbackFailure.RateLimited(600).allowsRetry)
        assertTrue(FeedbackFailure.DeliveryFailed.allowsRetry)
        assertTrue(FeedbackFailure.Unavailable.allowsRetry)
        assertFalse(FeedbackFailure.Rejected(400).allowsRetry)
        assertNull(FeedbackFailure.Rejected(413).retryOpensAt(1_000))
    }

    @Test
    fun `a 429 holds Retry for Retry-After, or 60 s when it is missing, zero or a date`() {
        val at = 1_000_000L
        fun opensAfter(retryAfter: String?): Long {
            val failure = (FeedbackOutcome.of(429, retryAfter, "") as FeedbackOutcome.Failed).failure
            return failure.retryOpensAt(at)!! - at
        }

        assertEquals(600_000L, opensAfter("600"))
        assertEquals(45_000L, opensAfter(" 45 "))
        assertEquals(60_000L, opensAfter(null))
        assertEquals(60_000L, opensAfter("0"))
        assertEquals(60_000L, opensAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `the other retryable failures open Retry at once`() {
        listOf(FeedbackFailure.Unreachable, FeedbackFailure.DeliveryFailed, FeedbackFailure.Unavailable).forEach {
            assertEquals(5L, it.retryOpensAt(5L))
        }
    }

    @Test
    fun `the client posts JSON and reads the 429 header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "600").setBody("""{"ok":false}"""))
        server.start()
        val payload = FeedbackPayload.from(
            FeedbackDraft(name = "Zoé", message = "Hi"),
            FeedbackDeviceInfo.of("1.5.0", "105", "abc", "13", 33, "Google", "Pixel 4", "fr-FR"),
            null,
        )!!

        val outcome = FeedbackClient(server.url("/api/feedback").toString()).send(payload)

        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.RateLimited(600)), outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertTrue(request.body.readUtf8().contains(""""platform":"android""""))
    }

    @Test
    fun `no answer is unreachable`() = runTest {
        server.start()
        val url = server.url("/api/feedback").toString()
        server.shutdown()
        val payload = FeedbackPayload.from(
            FeedbackDraft(name = "Zoé", message = "Hi"),
            FeedbackDeviceInfo.of("1.5.0", "105", "abc", "13", 33, "Google", "Pixel 4", "fr-FR"),
            null,
        )!!

        assertEquals(FeedbackOutcome.Failed(FeedbackFailure.Unreachable), FeedbackClient(url).send(payload))
    }
}
