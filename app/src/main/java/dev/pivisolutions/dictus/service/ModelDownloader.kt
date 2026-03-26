package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Represents the state of an in-progress or completed model download.
 *
 * Sealed class ensures exhaustive handling in UI state machines:
 * - Progress: Download is in flight, emit current percentage (0–100)
 * - Complete: Download finished successfully, file written at [path]
 * - Error: Download failed with a human-readable [message]
 *
 * WHY sealed class (not enum): Each variant carries different data. The
 * sealed class hierarchy makes it impossible to forget to handle new states.
 */
sealed class DownloadProgress {
    /** Download is in flight. [percent] is 0–100. */
    data class Progress(val percent: Int) : DownloadProgress()

    /** Download completed successfully. [path] is the absolute path to the model file. */
    data class Complete(val path: String) : DownloadProgress()

    /** Download failed. [message] describes the failure (HTTP code, IO error, etc.). */
    data class Error(val message: String) : DownloadProgress()
}

/**
 * Downloads Whisper GGML models from HuggingFace.
 *
 * Provides two download APIs:
 * 1. [ensureModelAvailable] — blocking suspend function kept for [DictationService] backward compat
 * 2. [downloadWithProgress] — reactive Flow API for UI progress indicators (Phase 4)
 *
 * WHY OkHttp (not HttpURLConnection): OkHttp handles redirects (HuggingFace CDN uses 302),
 * connection pooling, and timeouts more robustly. It also supports in-flight call cancellation
 * which is required for Flow-based cancellation via awaitClose { call.cancel() }.
 *
 * WHY callbackFlow for [downloadWithProgress]: callbackFlow bridges the imperative OkHttp
 * streaming API to Kotlin's reactive Flow model. awaitClose registers the cleanup action
 * (call.cancel()) that runs when the collector cancels the Flow.
 *
 * @param modelManager Source of model metadata and file path resolution.
 * @param client OkHttpClient used for HTTP requests. Injectable for testability.
 */
open class ModelDownloader(
    private val modelManager: ModelManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES) // Large models (190MB) need time
        .followRedirects(true)
        .build()
) {

    /**
     * Ensure a model is available on disk. Downloads if not present.
     *
     * Kept for backward compatibility with DictationService. Uses the blocking
     * OkHttp API rather than the Flow API to keep the call site simple.
     *
     * @param modelKey Model identifier (e.g., "tiny", "small-q5_1")
     * @return Absolute path to model file, or null if download failed
     */
    suspend fun ensureModelAvailable(modelKey: String): String? {
        // Check if already downloaded
        val existingPath = modelManager.getModelPath(modelKey)
        if (existingPath != null) {
            Timber.d("Model '%s' already available at %s", modelKey, existingPath)
            return existingPath
        }

        // Download from HuggingFace
        val url = modelManager.downloadUrl(modelKey) ?: run {
            Timber.e("Unknown model key: %s", modelKey)
            return null
        }
        val targetPath = modelManager.getTargetPath(modelKey) ?: return null

        Timber.d("Downloading model '%s' from %s to %s", modelKey, url, targetPath)
        return downloadFile(url, targetPath)
    }

    /**
     * Download a model and emit progress events as a [Flow].
     *
     * Emits [DownloadProgress.Progress] events as bytes arrive, then a single
     * [DownloadProgress.Complete] or [DownloadProgress.Error] terminal event.
     *
     * If the model is already downloaded, emits Progress(100) + Complete immediately.
     * If the model key is unknown, emits a single Error and completes.
     *
     * Flow cancellation cancels the underlying OkHttp call via [awaitClose].
     *
     * @param modelKey Stable model key from [ModelCatalog] (e.g., "tiny", "base")
     */
    open fun downloadWithProgress(modelKey: String): Flow<DownloadProgress> {
        val url = modelManager.downloadUrl(modelKey)
        return downloadWithProgress(modelKey, urlOverride = url)
    }

    /**
     * Internal overload that allows injecting a custom URL for testing.
     *
     * In production, [downloadWithProgress(modelKey)] resolves the URL from [ModelManager].
     * Tests inject a mock server URL via [urlOverride] without changing catalog constants.
     *
     * WHY flow {} + flowOn(Dispatchers.IO): Using a plain flow builder with flowOn is simpler
     * and more test-friendly than callbackFlow + launch(Dispatchers.IO). Coroutine cancellation
     * is propagated automatically. OkHttp call cancellation is handled via a Job.invokeOnCompletion
     * hook registered on the collector's coroutine context.
     *
     * @param modelKey Stable model key used to locate the target file path on disk
     * @param urlOverride When non-null, this URL is used instead of the catalog URL
     */
    internal fun downloadWithProgress(
        modelKey: String,
        urlOverride: String?,
    ): Flow<DownloadProgress> = flow {
        // Validate model key
        val info = ModelCatalog.findByKey(modelKey)
        if (info == null) {
            emit(DownloadProgress.Error("Unknown model key: $modelKey"))
            return@flow
        }

        // Resolve URL — fail fast if neither catalog nor override provides one
        val url = urlOverride ?: run {
            emit(DownloadProgress.Error("No download URL for: $modelKey"))
            return@flow
        }

        // Check if already downloaded (only when using catalog URL)
        if (urlOverride == null) {
            val existingPath = modelManager.getModelPath(modelKey)
            if (existingPath != null) {
                emit(DownloadProgress.Progress(100))
                emit(DownloadProgress.Complete(existingPath))
                return@flow
            }
        }

        val targetPath = modelManager.getTargetPath(modelKey) ?: run {
            emit(DownloadProgress.Error("Cannot resolve target path for: $modelKey"))
            return@flow
        }
        val tempFile = File("$targetPath.tmp")

        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)

        // Register cancellation: when the collector's coroutine is cancelled,
        // cancel the OkHttp call so the blocking input.read() throws and unblocks the IO.
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        job?.invokeOnCompletion {
            call.cancel()
            tempFile.delete()
        }

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress.Error("HTTP ${response.code}"))
                return@flow
            }
            val body = response.body ?: run {
                emit(DownloadProgress.Error("Empty response body"))
                return@flow
            }
            val totalBytes = body.contentLength()
            var bytesRead = 0L

            tempFile.parentFile?.mkdirs()
            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (currentCoroutineContext().isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val percent = ((bytesRead * 100) / totalBytes).toInt()
                            emit(DownloadProgress.Progress(percent))
                        }
                    }
                }
            }

            if (!currentCoroutineContext().isActive) return@flow

            if (tempFile.renameTo(File(targetPath))) {
                emit(DownloadProgress.Complete(targetPath))
            } else {
                emit(DownloadProgress.Error("Failed to rename temp file"))
                tempFile.delete()
            }
        } catch (e: Exception) {
            if (!call.isCanceled()) {
                emit(DownloadProgress.Error(e.message ?: "Download failed"))
            }
            tempFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download a file from URL to target path.
     * Runs on IO dispatcher to avoid blocking the caller's thread.
     */
    private suspend fun downloadFile(url: String, targetPath: String): String? =
        withContext(Dispatchers.IO) {
            val targetFile = File(targetPath)
            val tempFile = File("$targetPath.tmp")

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Timber.e("Download failed: HTTP %d for %s", response.code, url)
                    return@withContext null
                }

                val body = response.body ?: run {
                    Timber.e("Download returned empty body for %s", url)
                    return@withContext null
                }

                val totalBytes = body.contentLength()
                Timber.d("Download started: %d bytes expected", totalBytes)

                // Write to temp file first, rename on success (atomic-ish)
                tempFile.parentFile?.mkdirs()
                FileOutputStream(tempFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var lastLogPercent = 0

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read

                            // Log progress every 10%
                            if (totalBytes > 0) {
                                val percent = ((bytesRead * 100) / totalBytes).toInt()
                                if (percent >= lastLogPercent + 10) {
                                    lastLogPercent = percent
                                    Timber.d("Download progress: %d%% (%d / %d bytes)", percent, bytesRead, totalBytes)
                                }
                            }
                        }
                    }
                }

                // Rename temp to final
                if (tempFile.renameTo(targetFile)) {
                    Timber.d("Model downloaded successfully to %s (%d bytes)", targetPath, targetFile.length())
                    return@withContext targetPath
                } else {
                    Timber.e("Failed to rename temp file to %s", targetPath)
                    tempFile.delete()
                    return@withContext null
                }
            } catch (e: Exception) {
                Timber.e(e, "Download failed for %s", url)
                tempFile.delete()
                return@withContext null
            }
        }
}
