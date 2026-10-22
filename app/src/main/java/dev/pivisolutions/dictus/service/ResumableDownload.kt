package dev.pivisolutions.dictus.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** No byte arrived for [seconds] seconds: the network is gone or the server stopped sending. */
class DownloadStalledException(seconds: Long) :
    IOException("Download stalled: no data received for $seconds s")

/**
 * Downloads a URL into a file and resumes after an interruption (#131).
 *
 * The bytes go to `<dest>.part`. Its sidecar `<dest>.part.meta` holds the server validator
 * (ETag, else Last-Modified) and the total size. The next call for the same [dest] asks for
 * the rest with `Range: bytes=<partLen>-` and `If-Range: <validator>`:
 * - 206 with the expected start, total and validator: the bytes are appended.
 * - 200: the server ignored the range or the file changed. The part restarts from byte 0.
 * - 416: the part already holds the whole file if its length equals the saved total;
 *   otherwise it is discarded and the download restarts from byte 0.
 * `.part` becomes [dest] only once its length equals the total the server announced.
 *
 * WHY check the validator of a 206 ourselves: the HuggingFace CDN answers 206 even when
 * If-Range does not match (checked 2026-10-23), so If-Range alone does not stop stale bytes
 * from being appended to a part of an older file.
 *
 * A network error or a stall keeps the part for the next call. Cancelling the coroutine is a
 * deliberate stop: the part and its sidecar are deleted.
 *
 * @param stallTimeoutMs Time without a single byte after which the download fails with
 *   [DownloadStalledException]. Applied as the call's read timeout, so the client's own
 *   (longer) read timeout cannot hide a stall.
 */
class ResumableDownload(
    client: OkHttpClient,
    private val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
) {
    private val client = client.newBuilder()
        .readTimeout(stallTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Download [url] into [dest]. Returns once [dest] holds the whole body; throws otherwise.
     *
     * @param onProgress Called with the share of the file on disk, 0–100, each time it changes.
     */
    suspend fun fetch(url: String, dest: File, onProgress: suspend (Int) -> Unit) {
        val part = File("${dest.path}.part")
        val meta = File("${part.path}.meta")
        part.parentFile?.mkdirs()
        try {
            // A second pass only happens when the part turned out unusable (416 or a 206
            // that does not continue it); that pass starts from byte 0 and cannot loop.
            if (!fetchOnce(url, part, meta, onProgress)) {
                discard(part, meta)
                check(fetchOnce(url, part, meta, onProgress)) { "Download restarted twice" }
            }
        } catch (e: CancellationException) {
            discard(part, meta)
            throw e
        } catch (e: SocketTimeoutException) {
            // A read timeout ends the HTTP/2 stream, not its connection: without this, Retry
            // reuses the dead pooled connection and stalls again (seen on the emulator).
            client.connectionPool.evictAll()
            Timber.w("Download %s: stalled, part kept at %d bytes", part.name, part.length())
            throw DownloadStalledException(stallTimeoutMs / 1000)
        }
        dest.delete()
        if (!part.renameTo(dest)) throw IOException("Failed to rename ${part.name}")
        meta.delete()
    }

    /** One request. Returns false when the part cannot be continued and must be discarded. */
    private suspend fun fetchOnce(
        url: String,
        part: File,
        meta: File,
        onProgress: suspend (Int) -> Unit,
    ): Boolean {
        val saved = PartMeta.read(meta)
        val offset = if (saved != null && part.exists()) part.length() else 0L
        if (offset == 0L) discard(part, meta)

        val request = Request.Builder().url(url).apply {
            if (offset > 0) {
                header("Range", "bytes=$offset-")
                header("If-Range", saved!!.validator)
            }
        }.build()
        val start = System.currentTimeMillis()
        client.newCall(request).execute().use { response ->
            Timber.i("Download %s: range offset=%d status=%d", part.name, offset, response.code)
            val append: Boolean
            val total: Long
            when (response.code) {
                206 -> {
                    val range = ContentRange.parse(response.header("Content-Range"))
                    val validator = response.validator()
                    if (saved == null || range == null || range.start != offset || range.total != saved.total ||
                        (validator != null && validator != saved.validator)
                    ) {
                        Timber.w("Download %s: 206 does not continue the part, restarting", part.name)
                        return false
                    }
                    append = true
                    total = range.total
                }
                200 -> {
                    append = false
                    total = response.body?.contentLength() ?: -1L
                    val validator = response.validator()
                    // Without a validator a later resume could not tell a changed file apart.
                    if (validator != null && total > 0) PartMeta(validator, total).write(meta) else meta.delete()
                }
                416 -> {
                    if (saved != null && offset == saved.total) {
                        Timber.i("Download %s: part already complete (%d bytes)", part.name, offset)
                        return true
                    }
                    return false
                }
                else -> throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            var written = if (append) offset else 0L
            var lastPercent = -1
            FileOutputStream(part, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            Timber.i(
                "Download %s: status=%d received=%d bytes, part=%d of %d bytes in %d ms",
                part.name, response.code, written - (if (append) offset else 0L), written, total,
                System.currentTimeMillis() - start,
            )
            if (total > 0 && written != total) {
                // A short body is a valid prefix: keep it for the next call. A longer one is not.
                if (written > total) discard(part, meta)
                throw IOException("Download truncated: $written of $total bytes")
            }
        }
        return true
    }

    private fun discard(part: File, meta: File) {
        part.delete()
        meta.delete()
    }

    private fun Response.validator(): String? =
        header("ETag")?.takeUnless { it.startsWith("W/") } ?: header("Last-Modified")

    /** Validator and total size of the file a `.part` belongs to. */
    internal data class PartMeta(val validator: String, val total: Long) {
        fun write(file: File) = file.writeText("$total\n$validator\n")

        companion object {
            fun read(file: File): PartMeta? {
                val lines = runCatching { file.readLines() }.getOrNull() ?: return null
                val total = lines.getOrNull(0)?.toLongOrNull() ?: return null
                val validator = lines.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
                return PartMeta(validator, total)
            }
        }
    }

    /** `Content-Range: bytes <start>-<end>/<total>`; a `*` total is treated as unknown. */
    internal data class ContentRange(val start: Long, val total: Long) {
        companion object {
            private val PATTERN = Regex("""bytes (\d+)-\d+/(\d+)""")

            fun parse(header: String?): ContentRange? {
                val match = header?.let { PATTERN.matchEntire(it.trim()) } ?: return null
                return ContentRange(match.groupValues[1].toLong(), match.groupValues[2].toLong())
            }
        }
    }

    companion object {
        /**
         * 30 s: the stall detector iOS used before #449 (dictus-ios). Long enough for a
         * Wi-Fi to cellular handover or a slow CDN start, short enough that a user on the
         * download screen sees an error and a Retry instead of a frozen bar.
         */
        const val DEFAULT_STALL_TIMEOUT_MS = 30_000L
    }
}
