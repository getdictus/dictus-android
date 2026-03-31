package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.AiProvider
import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelCatalog.isDirectoryModel
import dev.pivisolutions.dictus.model.ModelInfo
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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
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
     * For Parakeet models (tar.bz2 archives), downloads to a temp file and
     * extracts the archive contents (model.onnx + tokens.txt) into models/{key}/.
     *
     * @param modelKey Model identifier (e.g., "tiny", "small-q5_1", "parakeet-ctc-110m-int8")
     * @return Absolute path to model file, or null if download failed
     */
    suspend fun ensureModelAvailable(modelKey: String): String? {
        // Check if already downloaded
        val existingPath = modelManager.getModelPath(modelKey)
        if (existingPath != null) {
            Timber.d("Model '%s' already available at %s", modelKey, existingPath)
            return existingPath
        }

        val info = ModelCatalog.findByKey(modelKey) ?: run {
            Timber.e("Unknown model key: %s", modelKey)
            return null
        }

        val url = modelManager.downloadUrl(modelKey) ?: run {
            Timber.e("No download URL for model: %s", modelKey)
            return null
        }
        val targetPath = modelManager.getTargetPath(modelKey) ?: return null

        Timber.d("Downloading model '%s' from %s to %s", modelKey, url, targetPath)

        return if (info.isDirectoryModel()) {
            // Parakeet: download archive to temp file, then extract
            val targetDir = File(targetPath).parentFile?.absolutePath ?: return null
            val archiveTempPath = "$targetPath.archive.tmp"
            val downloaded = downloadFile(url, archiveTempPath)
            if (downloaded == null) {
                Timber.e("Failed to download Parakeet archive for: %s", modelKey)
                return null
            }
            Timber.d("Extracting Parakeet archive for: %s", modelKey)
            val extracted = extractTarBz2(archiveTempPath, targetDir, info)
            File(archiveTempPath).delete()
            if (extracted) modelManager.getModelPath(modelKey) else null
        } else {
            downloadFile(url, targetPath)
        }
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

        // For Parakeet archives, download to a .archive.tmp file; for Whisper, use .tmp directly.
        val isArchive = info.isDirectoryModel()
        val tempFile = if (isArchive) File("$targetPath.archive.tmp") else File("$targetPath.tmp")

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

            if (isArchive) {
                // Parakeet: extract tar.bz2 archive, then delete the archive temp file
                val targetDir = File(targetPath).parent ?: run {
                    emit(DownloadProgress.Error("Cannot determine target directory for: $modelKey"))
                    tempFile.delete()
                    return@flow
                }
                val extracted = extractTarBz2(tempFile.absolutePath, targetDir, info)
                tempFile.delete()
                if (extracted) {
                    val finalPath = modelManager.getModelPath(modelKey)
                    if (finalPath != null) {
                        emit(DownloadProgress.Complete(finalPath))
                    } else {
                        emit(DownloadProgress.Error("Archive extracted but model file not found for: $modelKey"))
                    }
                } else {
                    emit(DownloadProgress.Error("Failed to extract archive for: $modelKey"))
                }
            } else {
                if (tempFile.renameTo(File(targetPath))) {
                    emit(DownloadProgress.Complete(targetPath))
                } else {
                    emit(DownloadProgress.Error("Failed to rename temp file"))
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            if (!call.isCanceled()) {
                emit(DownloadProgress.Error(e.message ?: "Download failed"))
            }
            tempFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extract a tar.bz2 archive and place the model files into [targetDir].
     *
     * Parakeet archives from sherpa-onnx GitHub Releases contain a directory like
     * `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8/` with `model.int8.onnx`
     * and `tokens.txt` inside. This method extracts those two files directly into
     * [targetDir] (i.e., `models/{modelInfo.key}/`), then cleans up the archive.
     *
     * Uses Apache Commons Compress for pure-Java BZip2 + TAR handling — no `tar`
     * binary required, works on all Android API levels.
     *
     * @param archivePath Absolute path to the downloaded .tar.bz2 file
     * @param targetDir   Absolute path to the destination directory (models/{key}/)
     * @param modelInfo   ModelInfo for the model being extracted (used to identify files)
     * @return true if model file and tokens.txt were successfully extracted
     */
    private fun extractTarBz2(archivePath: String, targetDir: String, modelInfo: ModelInfo): Boolean {
        val targetDirFile = File(targetDir).also { it.mkdirs() }

        return try {
            java.io.FileInputStream(archivePath).use { fis ->
                BZip2CompressorInputStream(fis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var modelExtracted = false
                        var tokensExtracted = false

                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            val entryName = File(entry.name).name // strip parent directory component
                            if (!entry.isDirectory && (entryName == modelInfo.fileName || entryName == "tokens.txt")) {
                                val outFile = File(targetDirFile, entryName)
                                FileOutputStream(outFile).use { out ->
                                    val buffer = ByteArray(8192)
                                    var n: Int
                                    while (tarIn.read(buffer).also { n = it } != -1) {
                                        out.write(buffer, 0, n)
                                    }
                                }
                                if (entryName == modelInfo.fileName) modelExtracted = true
                                if (entryName == "tokens.txt") tokensExtracted = true
                                Timber.d("Extracted: %s → %s", entryName, outFile.absolutePath)
                            }
                            entry = tarIn.nextEntry
                        }

                        if (!modelExtracted) Timber.e("Model file '%s' not found in archive", modelInfo.fileName)
                        if (!tokensExtracted) Timber.e("tokens.txt not found in archive for %s", modelInfo.key)
                        modelExtracted && tokensExtracted
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract tar.bz2 archive: %s", archivePath)
            false
        }
    }

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
