package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads Whisper GGML models from HuggingFace.
 *
 * Phase 3: Simple blocking download with Timber progress logging.
 * Phase 4 will add progress callbacks for UI indicators.
 *
 * WHY OkHttp (not HttpURLConnection): OkHttp handles redirects (HuggingFace
 * CDN uses 302 redirects), connection pooling, and timeouts more robustly.
 * Phase 4's model manager will also use OkHttp for download progress.
 */
class ModelDownloader(private val modelManager: ModelManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES) // Large models (190MB) need time
        .followRedirects(true)
        .build()

    /**
     * Ensure a model is available on disk. Downloads if not present.
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
