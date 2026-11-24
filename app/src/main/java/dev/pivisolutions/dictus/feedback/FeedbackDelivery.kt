package dev.pivisolutions.dictus.feedback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why a report did not arrive, at the level the form explains it (iOS `FeedbackDelivery.swift`). */
sealed interface FeedbackFailure {
    /** No answer from the server: offline, timeout, or a captive portal page. */
    data object Unreachable : FeedbackFailure

    /** `429`. The seconds come from `Retry-After` when the server sent a usable number. */
    data class RateLimited(val retryAfterSeconds: Int?) : FeedbackFailure

    /** `502` or another 5xx: the server could not hand the report to Telegram. */
    data object DeliveryFailed : FeedbackFailure

    /** `503`, or `404` before the route is deployed. */
    data object Unavailable : FeedbackFailure

    /** `400`, `413`, `415` or any other answer: sending it again unchanged would fail the same way. */
    data class Rejected(val status: Int) : FeedbackFailure

    val allowsRetry: Boolean get() = this !is Rejected

    /**
     * When Retry and Send may be tapped again, or null for never. A `429` holds them for the
     * server's `Retry-After`, or a minute when the header is missing, zero or a date.
     */
    fun retryOpensAt(failedAtMillis: Long): Long? = when (this) {
        is Rejected -> null
        is RateLimited -> failedAtMillis + 1000L * (retryAfterSeconds?.takeIf { it > 0 } ?: DEFAULT_RATE_LIMIT_WAIT_SECONDS)
        Unreachable, DeliveryFailed, Unavailable -> failedAtMillis
    }

    companion object {
        const val DEFAULT_RATE_LIMIT_WAIT_SECONDS = 60
    }
}

sealed interface FeedbackOutcome {
    data object Delivered : FeedbackOutcome
    data class Failed(val failure: FeedbackFailure) : FeedbackOutcome

    companion object {
        /**
         * Reads the server's answer per `docs/feedback-api.md`. A `200` counts only with its
         * `{"ok":true}` body: a Wi-Fi login page also answers `200`.
         */
        fun of(status: Int, retryAfter: String?, body: String): FeedbackOutcome = when (status) {
            200 -> if (OK_BODY.containsMatchIn(body)) Delivered else Failed(FeedbackFailure.Unreachable)
            429 -> Failed(FeedbackFailure.RateLimited(retryAfter?.trim()?.toIntOrNull()))
            404, 503 -> Failed(FeedbackFailure.Unavailable)
            in 500..599 -> Failed(FeedbackFailure.DeliveryFailed)
            else -> Failed(FeedbackFailure.Rejected(status))
        }

        private val OK_BODY = Regex(""""ok"\s*:\s*true""")
    }
}

/** Posts a report. No secret in the app: the route is public and rate limited on the server. */
class FeedbackClient(
    private val endpoint: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) {
    suspend fun send(payload: FeedbackPayload): FeedbackOutcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.body().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                FeedbackOutcome.of(response.code, response.header("Retry-After"), response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            FeedbackOutcome.Failed(FeedbackFailure.Unreachable)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
