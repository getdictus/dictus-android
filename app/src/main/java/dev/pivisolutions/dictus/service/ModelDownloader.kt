package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.model.ModelCatalog
import dev.pivisolutions.dictus.model.ModelCatalog.isDirectoryModel
import dev.pivisolutions.dictus.model.ModelInfo
import dev.pivisolutions.dictus.model.ModelIntegrity
import dev.pivisolutions.dictus.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * State of a model install, emitted in this order: [Progress]* → [Extracting]* (archives
 * only) → [Verifying] → [Complete], or [Error] at any point.
 *
 * WHY sealed class (not enum): Each variant carries different data. The
 * sealed class hierarchy makes it impossible to forget to handle new states.
 */
sealed class DownloadProgress {
    /** Download is in flight. [percent] is 0–100. */
    data class Progress(val percent: Int) : DownloadProgress()

    /** Archive downloaded, model files being extracted (Parakeet). [percent] is 0–100. */
    data class Extracting(val percent: Int) : DownloadProgress()

    /** Files written, size and SHA-256 being checked before they replace the model on disk. */
    data object Verifying : DownloadProgress()

    /** Install finished and verified. [path] is the absolute path to the main model file. */
    data class Complete(val path: String) : DownloadProgress()

    /** Install failed. [message] describes the failure (HTTP code, IO error, integrity). */
    data class Error(val message: String) : DownloadProgress()
}

/**
 * Downloads and installs Whisper GGML models (HuggingFace) and Parakeet models (sherpa-onnx
 * tar.bz2 archives).
 *
 * Every install takes the same path (#169): download to a temp file, extract an archive into
 * a staging directory, check every file against [ModelInfo.files] (exact size and SHA-256),
 * then rename into place. The engine never sees a partial or unverified file, and a failure
 * leaves nothing behind.
 *
 * Callers: [ModelInstallWorker] (the UI path, in the background) and
 * [ensureModelAvailable] (DictationService, first use of a missing model).
 *
 * WHY OkHttp (not HttpURLConnection): OkHttp handles redirects (HuggingFace CDN uses 302,
 * GitHub releases too), connection pooling, and timeouts more robustly.
 *
 * @param modelManager Source of model metadata and file path resolution.
 * @param client OkHttpClient used for HTTP requests. Injectable for testability.
 */
class ModelDownloader(
    private val modelManager: ModelManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES) // Large models (190MB) need time
        .followRedirects(true)
        .build()
) {

    companion object {
        /**
         * One install per model at a time in this process. The worker, a retry and
         * DictationService's first-use download all write the same temp and staging paths;
         * a second caller waits, then finds the model installed. Before #169 a second tap on
         * Download deleted the file an orphaned extraction was still writing.
         */
        private val installLocks = ConcurrentHashMap<String, Mutex>()
    }

    /**
     * Ensure a model is available on disk, installing it if needed.
     *
     * @param modelKey Model identifier (e.g., "tiny", "small-q5_1", "parakeet-ctc-110m-int8")
     * @return Absolute path to model file, or null if the install failed
     */
    suspend fun ensureModelAvailable(modelKey: String): String? =
        when (val result = downloadWithProgress(modelKey).last()) {
            is DownloadProgress.Complete -> result.path
            is DownloadProgress.Error -> {
                Timber.e("Model '%s' unavailable: %s", modelKey, result.message)
                null
            }
            else -> null
        }

    /**
     * Install a model and emit its progress as a [Flow].
     *
     * If the model is already installed, emits Progress(100) + Complete immediately.
     * If the model key is unknown, emits a single Error and completes.
     * Cancelling the collector stops the download or the extraction at the next buffer and
     * deletes the temp and staging files.
     *
     * @param modelKey Stable model key from [ModelCatalog] (e.g., "tiny", "base")
     */
    fun downloadWithProgress(modelKey: String): Flow<DownloadProgress> {
        val info = ModelCatalog.findByKey(modelKey)
            ?: return flowOf(DownloadProgress.Error("Unknown model key: $modelKey"))
        return install(info, ModelCatalog.downloadUrl(info))
    }

    /**
     * Install [info] from [url]. Internal so tests can pass a mock server URL and a manifest
     * that matches the bytes it serves.
     *
     * WHY catch() after the flow (not try/catch inside): it only sees upstream failures, so
     * an exception from the collector is never turned into an Error event, and coroutine
     * cancellation passes through.
     */
    internal fun install(info: ModelInfo, url: String): Flow<DownloadProgress> = flow {
        installLocks.getOrPut(info.key) { Mutex() }.withLock {
            val existingPath = modelManager.getModelPath(info.key)
            if (existingPath != null) {
                emit(DownloadProgress.Progress(100))
                emit(DownloadProgress.Complete(existingPath))
                return@withLock
            }
            val target = File(modelManager.getTargetPath(info.key) ?: throw IOException("No target path for ${info.key}"))
            if (info.isDirectoryModel()) installArchive(info, url, target) else installFile(info, url, target)
        }
    }.catch { e ->
        Timber.e(e, "Install failed for %s", info.key)
        emit(DownloadProgress.Error(e.message ?: "Install failed"))
    }.flowOn(Dispatchers.IO)

    /** Whisper: one file, downloaded to `<target>.tmp`, verified, renamed. */
    private suspend fun FlowCollector<DownloadProgress>.installFile(info: ModelInfo, url: String, target: File) {
        val temp = File("${target.path}.tmp")
        try {
            download(url, temp)
            emit(DownloadProgress.Verifying)
            verify(info) { ModelIntegrity.problemWithFile(temp, info.files.single()) }
            if (!temp.renameTo(target)) throw IOException("Failed to rename ${temp.name}")
            emit(DownloadProgress.Complete(target.absolutePath))
        } finally {
            temp.delete()
        }
    }

    /**
     * Parakeet: archive downloaded to `models/<key>.archive.tmp`, extracted into the staging
     * directory `models/<key>.part/`, verified, then the staging directory replaces
     * `models/<key>/`. Nothing is written inside `models/<key>/` before that rename.
     */
    private suspend fun FlowCollector<DownloadProgress>.installArchive(info: ModelInfo, url: String, target: File) {
        val staging = modelManager.getStagingDir(info.key) ?: throw IOException("No staging dir for ${info.key}")
        val archive = File(staging.parentFile, "${info.key}.archive.tmp")
        val modelDir = target.parentFile ?: throw IOException("No model dir for ${info.key}")
        try {
            download(url, archive)
            emit(DownloadProgress.Extracting(0))
            staging.deleteRecursively()
            staging.mkdirs()
            extractTarBz2(archive, staging, info)
            archive.delete()
            emit(DownloadProgress.Verifying)
            verify(info) { ModelIntegrity.problemWithDirectory(staging, info.files) }
            // Only reached when getModelPath said the model is not installed: whatever is in
            // modelDir is a broken earlier install, not files an engine has loaded.
            modelDir.deleteRecursively()
            if (!staging.renameTo(modelDir)) throw IOException("Failed to move ${staging.name} to ${modelDir.name}")
            emit(DownloadProgress.Complete(target.absolutePath))
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private fun verify(info: ModelInfo, check: () -> String?) {
        val start = System.currentTimeMillis()
        val problem = check()
        val elapsed = System.currentTimeMillis() - start
        if (problem != null) throw IOException("Integrity check failed for ${info.key}: $problem")
        Timber.d("Verification took %d ms for %s (%d files)", elapsed, info.key, info.files.size)
    }

    /**
     * Download [url] into [file], from byte 0.
     *
     * #131 adds resume here: keep the partial file and ask for the rest with `Range`. Callers
     * only rely on [file] holding the whole body when this returns.
     */
    private suspend fun FlowCollector<DownloadProgress>.download(url: String, file: File) {
        val start = System.currentTimeMillis()
        file.delete()
        file.parentFile?.mkdirs()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            var bytesRead = 0L
            var lastPercent = -1
            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val percent = ((bytesRead * 100) / totalBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadProgress.Progress(percent))
                            }
                        }
                    }
                }
            }
            if (totalBytes > 0 && bytesRead != totalBytes) {
                throw IOException("Download truncated: $bytesRead of $totalBytes bytes")
            }
        }
        Timber.d("Download took %d ms for %s (%d bytes)", System.currentTimeMillis() - start, file.name, file.length())
    }

    /**
     * Extract the files of [info] from a tar.bz2 archive into [dir].
     *
     * Parakeet archives from sherpa-onnx GitHub Releases hold a directory such as
     * `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/` with the model files and test WAVs; only the
     * files named in [ModelInfo.files] are written, by their base name.
     *
     * Progress is the share of the compressed archive consumed: the read position of the
     * underlying file, which BufferedInputStream advances 256 KB at a time. It moves at the
     * pace of the bzip2 decoder, which is where the time goes.
     *
     * Uses Apache Commons Compress for pure-Java BZip2 + TAR handling — no `tar`
     * binary required, works on all Android API levels.
     */
    private suspend fun FlowCollector<DownloadProgress>.extractTarBz2(archive: File, dir: File, info: ModelInfo) {
        val start = System.currentTimeMillis()
        val remaining = info.files.map { it.name }.toMutableSet()
        FileInputStream(archive).use { fileIn ->
            val archiveBytes = fileIn.channel.size()
            var lastPercent = 0
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(fileIn, 256 * 1024))).use { tarIn ->
                while (remaining.isNotEmpty()) {
                    val entry = tarIn.nextEntry ?: break
                    val name = File(entry.name).name
                    if (entry.isDirectory || name !in remaining) continue
                    val fileStart = System.currentTimeMillis()
                    BufferedOutputStream(FileOutputStream(File(dir, name)), 256 * 1024).use { out ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = tarIn.read(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            val percent = ((fileIn.channel.position() * 100) / archiveBytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadProgress.Extracting(percent))
                            }
                        }
                    }
                    remaining -= name
                    Timber.d("Extracted %s (%d bytes) in %d ms", name, entry.size, System.currentTimeMillis() - fileStart)
                }
            }
        }
        if (remaining.isNotEmpty()) throw IOException("Missing from archive: ${remaining.sorted()}")
        Timber.d("Extraction took %d ms for %s", System.currentTimeMillis() - start, info.key)
    }
}
