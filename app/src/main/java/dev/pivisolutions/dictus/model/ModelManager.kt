package dev.pivisolutions.dictus.model

import android.content.Context
import java.io.File

/**
 * Provider abstraction for future multi-engine support.
 *
 * Currently only WHISPER is implemented. PARAKEET is reserved for a future
 * on-device ASR engine with smaller model sizes. The enum ensures that adding
 * a new provider requires updating the download URL switch exhaustively.
 */
enum class AiProvider { WHISPER, PARAKEET }

/**
 * Full descriptor for a downloadable ASR model.
 *
 * WHY a separate data class from the old ModelInfo: The Phase 3 ModelInfo only
 * stored fileName and expectedSize. Phase 4 adds display metadata (displayName,
 * qualityLabel), provider routing, and deprecation signalling. The old nested
 * class was removed; all consumers use ModelCatalog to look up models.
 *
 * @param key             Stable string identifier used in preferences and URLs.
 * @param fileName        File name on disk and in the HuggingFace repo.
 * @param displayName     Human-readable name shown in the model list UI.
 * @param expectedSizeBytes Expected byte size used to detect complete downloads.
 * @param qualityLabel    Short quality descriptor shown as a badge (e.g. "Rapide").
 * @param provider        Download source and inference engine.
 * @param isDeprecated    When true, hide from new downloads but keep if installed.
 */
data class ModelInfo(
    val key: String,
    val fileName: String,
    val displayName: String,
    val expectedSizeBytes: Long,
    val qualityLabel: String,
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
)

/**
 * Static catalog of all supported Whisper GGML models.
 *
 * Acts as the single source of truth for model metadata. All download URLs,
 * file names, and size checks reference this catalog rather than being
 * scattered across callers.
 *
 * WHY an object (not companion): ModelCatalog is shared across ModelManager
 * instances and tests that don't have an Android context. Keeping it at the
 * file level avoids coupling catalog logic to Android Context.
 */
object ModelCatalog {
    const val DEFAULT_KEY = "tiny"
    private const val WHISPER_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    /**
     * All available models in selection order (fastest → most accurate).
     *
     * Phase 4 exposes 4 models:
     *   tiny      — 77 MB  — fastest, good for short commands
     *   base      — 142 MB — balanced speed and accuracy
     *   small     — 466 MB — high accuracy, slower
     *   small-q5_1 — 190 MB — quantised small, best accuracy/size trade-off
     */
    val ALL = listOf(
        ModelInfo("tiny", "ggml-tiny.bin", "Tiny", 77_691_713L, "Rapide"),
        ModelInfo("base", "ggml-base.bin", "Base", 142_000_000L, "Equilibre"),
        ModelInfo("small", "ggml-small.bin", "Small", 466_000_000L, "Precis"),
        ModelInfo("small-q5_1", "ggml-small-q5_1.bin", "Small Q5", 190_031_232L, "Precis"),
    )

    /** Find a model descriptor by its stable key, or null if not in catalog. */
    fun findByKey(key: String): ModelInfo? = ALL.find { it.key == key }

    /**
     * Build the download URL for a model based on its provider.
     *
     * WHY when expression: Exhaustive switch ensures a compile-time error if a
     * new provider is added without implementing its URL scheme.
     */
    fun downloadUrl(info: ModelInfo): String = when (info.provider) {
        AiProvider.WHISPER -> "$WHISPER_BASE_URL/${info.fileName}"
        AiProvider.PARAKEET -> error("Parakeet downloads not yet supported")
    }
}

/**
 * Manages Whisper GGML model files on internal storage.
 *
 * Models are downloaded from HuggingFace to context.filesDir/models/.
 * Phase 4 extends the Phase 3 implementation with:
 *   - 4-model catalog (tiny, base, small, small-q5_1)
 *   - Provider abstraction via AiProvider enum
 *   - canDelete guard (prevent deleting the last remaining model)
 *   - deleteModel to remove a downloaded model from disk
 *   - storageUsedBytes to report total disk usage
 *   - getDownloadedModels to list keys of models present on disk
 *
 * WHY internal storage (filesDir): Models are large (77-466 MB) but must survive
 * app updates. Internal storage is app-private and not cleared by cache cleanup.
 * They are NOT cached content (externalCacheDir) — they are permanent data.
 */
class ModelManager(context: Context) {

    companion object {
        /** Stable default model key. Used by DictationService to select the model. */
        const val DEFAULT_MODEL_KEY = ModelCatalog.DEFAULT_KEY
    }

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * Get the absolute path to a downloaded model, or null if not available.
     *
     * Checks both file existence and file size to detect corrupt/partial downloads.
     * A size mismatch means the download was interrupted; the file is unusable.
     */
    fun getModelPath(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists() && file.length() == info.expectedSizeBytes) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Get the HuggingFace download URL for a model.
     *
     * Returns null for unknown model keys. Delegates URL construction to
     * ModelCatalog so the URL scheme stays centralized.
     */
    fun downloadUrl(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        return ModelCatalog.downloadUrl(info)
    }

    /**
     * Get the target file path for downloading a model (may not exist yet).
     *
     * Used by ModelDownloader to determine the write destination.
     */
    fun getTargetPath(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        return File(modelsDir, info.fileName).absolutePath
    }

    /**
     * Return true if the model file exists on disk with the correct size.
     */
    fun isDownloaded(modelKey: String): Boolean = getModelPath(modelKey) != null

    /**
     * Return keys for all models that are currently downloaded on disk.
     *
     * Uses getModelPath (not just file existence) so partial/corrupt downloads
     * are excluded from the list.
     */
    fun getDownloadedModels(): List<String> =
        ModelCatalog.ALL.filter { isDownloaded(it.key) }.map { it.key }

    /**
     * Return true if the given model can safely be deleted.
     *
     * A model can be deleted only when at least one other model is downloaded.
     * This prevents the user from ending up with no usable model, which would
     * break dictation entirely.
     *
     * @param modelKey The key of the model the user wants to delete.
     */
    fun canDelete(modelKey: String): Boolean {
        val downloaded = getDownloadedModels()
        return downloaded.size > 1 && modelKey in downloaded
    }

    /**
     * Delete a model file from disk.
     *
     * Returns false and leaves the file untouched if canDelete is false (e.g.,
     * it is the last downloaded model). Returns true and deletes the file when
     * deletion is allowed.
     */
    fun deleteModel(modelKey: String): Boolean {
        if (!canDelete(modelKey)) return false
        val info = ModelCatalog.findByKey(modelKey) ?: return false
        return File(modelsDir, info.fileName).delete()
    }

    /**
     * Return the total bytes used by all downloaded model files on disk.
     *
     * Uses actual file lengths rather than expectedSizeBytes so partially
     * downloaded files are accounted for correctly during active downloads.
     */
    fun storageUsedBytes(): Long =
        ModelCatalog.ALL.sumOf { info ->
            val file = File(modelsDir, info.fileName)
            if (file.exists()) file.length() else 0L
        }

    /**
     * Return the actual disk size of a single model file, or 0 if not present.
     */
    fun modelFileSize(modelKey: String): Long {
        val info = ModelCatalog.findByKey(modelKey) ?: return 0L
        val file = File(modelsDir, info.fileName)
        return if (file.exists()) file.length() else 0L
    }
}
