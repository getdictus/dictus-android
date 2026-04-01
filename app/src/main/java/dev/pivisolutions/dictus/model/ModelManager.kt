package dev.pivisolutions.dictus.model

import android.content.Context
import dev.pivisolutions.dictus.model.ModelCatalog.isDirectoryModel
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
 * @param description     Short human-readable description shown on model card.
 * @param precision       Relative accuracy score 0.0–1.0 for the Precision bar.
 * @param speed           Relative speed score 0.0–1.0 for the Vitesse bar.
 * @param provider        Download source and inference engine.
 * @param isDeprecated    When true, hide from new downloads but keep if installed.
 */
data class ModelInfo(
    val key: String,
    val fileName: String,
    val displayName: String,
    val expectedSizeBytes: Long,
    val qualityLabel: String,
    val description: String = "",
    val precision: Float = 0f,
    val speed: Float = 0f,
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
    val isEnglishOnly: Boolean = false,
    val isTransducer: Boolean = false,
    val minRamGb: Int = 0,
)

/**
 * Static catalog of all supported ASR models (Whisper GGML and Parakeet ONNX).
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
    private const val SHERPA_ONNX_RELEASES_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    /**
     * All available models in selection order (fastest → most accurate).
     *
     * Whisper models (5 entries):
     *   tiny       — 77 MB  — fastest, good for short commands
     *   base       — 148 MB — balanced speed and accuracy
     *   small      — 488 MB — high accuracy, slower
     *   small-q5_1 — 190 MB — quantised small, best accuracy/size trade-off
     *   medium-q5_0 — 539 MB — highest Whisper accuracy, quantised
     *
     * Phase 9 Parakeet models (2 entries, sherpa-onnx):
     *   parakeet-ctc-110m-int8 — 126 MB — fast quantised, English-only (CTC)
     *   parakeet-tdt-0.6b-v3  — 640 MB — 25 languages, requires 8+ GB RAM (transducer)
     *
     * WHY corrected sizes: The exact byte sizes were confirmed via UAT debug session
     * (Whisper) or from official sherpa-onnx release metadata (Parakeet).
     * expectedSizeBytes is used for the 95% minimum-size threshold check in getModelPath()
     * which tolerates minor CDN mirror variations while still rejecting partial downloads.
     */
    val ALL = listOf(
        ModelInfo(
            key = "tiny",
            fileName = "ggml-tiny.bin",
            displayName = "Tiny",
            expectedSizeBytes = 77_691_713L,
            qualityLabel = "Rapide",
            description = "Rapide et leger",
            precision = 0.4f,
            speed = 1.0f,
        ),
        ModelInfo(
            key = "base",
            fileName = "ggml-base.bin",
            displayName = "Base",
            expectedSizeBytes = 147_951_465L,
            qualityLabel = "Equilibre",
            description = "Precis et equilibre",
            precision = 0.6f,
            speed = 0.7f,
        ),
        ModelInfo(
            key = "small",
            fileName = "ggml-small.bin",
            displayName = "Small",
            expectedSizeBytes = 487_601_967L,
            qualityLabel = "Precis",
            description = "Haute precision",
            precision = 0.9f,
            speed = 0.35f,
        ),
        ModelInfo(
            key = "small-q5_1",
            fileName = "ggml-small-q5_1.bin",
            displayName = "Small Q5",
            expectedSizeBytes = 190_085_487L,
            qualityLabel = "Precis",
            description = "Meilleur compromis",
            precision = 0.85f,
            speed = 0.55f,
        ),
        ModelInfo(
            key = "medium-q5_0",
            fileName = "ggml-medium-q5_0.bin",
            displayName = "Medium",
            expectedSizeBytes = 539_212_467L,
            qualityLabel = "Precis",
            description = "Meilleure precision",
            precision = 0.9f,
            speed = 0.3f,
        ),
        // --- Parakeet models (sherpa-onnx) ---
        // Downloaded as tar.bz2 archives, stored in models/{key}/ directory on device.
        ModelInfo(
            key = "parakeet-ctc-110m-int8",
            fileName = "model.int8.onnx",
            displayName = "Parakeet 110M",
            expectedSizeBytes = 131_628_000L,
            qualityLabel = "Rapide",
            description = "Anglais uniquement - Rapide et precis",
            precision = 0.75f,
            speed = 0.85f,
            provider = AiProvider.PARAKEET,
            isEnglishOnly = true,
        ),
        ModelInfo(
            key = "parakeet-tdt-0.6b-v3",
            fileName = "encoder.int8.onnx",
            displayName = "Parakeet 0.6B",
            expectedSizeBytes = 622_000_000L,
            qualityLabel = "Premium",
            description = "25 langues - Detection automatique",
            precision = 0.95f,
            speed = 0.40f,
            provider = AiProvider.PARAKEET,
            isTransducer = true,
            minRamGb = 8,
        ),
    )

    /** Find a model descriptor by its stable key, or null if not in catalog. */
    fun findByKey(key: String): ModelInfo? = ALL.find { it.key == key }

    /**
     * Returns true if a model uses a directory-based storage layout.
     *
     * Parakeet models are stored in a subdirectory (models/{key}/{fileName}) because
     * they require companion files (tokens.txt) alongside the ONNX model file.
     * Whisper models use a flat layout (models/{fileName}).
     */
    fun ModelInfo.isDirectoryModel(): Boolean = provider == AiProvider.PARAKEET

    /**
     * Build the download URL for a model based on its provider.
     *
     * WHY when expression: Exhaustive switch ensures a compile-time error if a
     * new provider is added without implementing its URL scheme.
     *
     * Parakeet models are distributed as tar.bz2 archives from the sherpa-onnx
     * GitHub Releases page (asr-models tag). Each archive extracts to a directory
     * containing model.onnx (or model.int8.onnx) + tokens.txt.
     */
    fun downloadUrl(info: ModelInfo): String = when (info.provider) {
        AiProvider.WHISPER -> "$WHISPER_BASE_URL/${info.fileName}"
        AiProvider.PARAKEET -> when (info.key) {
            "parakeet-ctc-110m-int8" ->
                "$SHERPA_ONNX_RELEASES_URL/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2"
            "parakeet-tdt-0.6b-v3" ->
                "$SHERPA_ONNX_RELEASES_URL/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"
            else -> error("Unknown Parakeet model key: ${info.key}")
        }
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
     * Checks both file existence and a minimum-size threshold to detect
     * corrupt/partial downloads. Using >= 95% of expectedSizeBytes (instead of
     * exact equality) tolerates minor size variations across HuggingFace CDN
     * mirrors while still rejecting clearly partial downloads.
     *
     * For Parakeet models (directory-based), the path points to the ONNX model file
     * inside models/{key}/{fileName} — e.g. models/parakeet-ctc-110m-int8/model.int8.onnx.
     * For Whisper models (flat), the path points to models/{fileName}.
     */
    fun getModelPath(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        val file = if (info.isDirectoryModel()) {
            File(File(modelsDir, info.key), info.fileName)
        } else {
            File(modelsDir, info.fileName)
        }
        return if (file.exists() && file.length() >= info.expectedSizeBytes * 95 / 100) {
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
     * For Parakeet models, returns the path to the ONNX file inside the model subdirectory.
     * The parent directory is created by ModelDownloader during extraction.
     */
    fun getTargetPath(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        return if (info.isDirectoryModel()) {
            File(File(modelsDir, info.key), info.fileName).absolutePath
        } else {
            File(modelsDir, info.fileName).absolutePath
        }
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
     * Delete a model from disk.
     *
     * Returns false and leaves files untouched if canDelete is false (e.g.,
     * it is the last downloaded model). Returns true and deletes files when
     * deletion is allowed.
     *
     * For Parakeet models (directory-based), deletes the entire model subdirectory
     * (models/{key}/) including model.onnx and tokens.txt.
     * For Whisper models (flat), deletes the single model file.
     */
    fun deleteModel(modelKey: String): Boolean {
        if (!canDelete(modelKey)) return false
        val info = ModelCatalog.findByKey(modelKey) ?: return false
        return if (info.isDirectoryModel()) {
            File(modelsDir, info.key).deleteRecursively()
        } else {
            File(modelsDir, info.fileName).delete()
        }
    }

    /**
     * Return the total bytes used by all downloaded model files on disk.
     *
     * Uses actual file lengths rather than expectedSizeBytes so partially
     * downloaded files are accounted for correctly during active downloads.
     * For Parakeet models (directory-based), sums all files in the model subdirectory.
     */
    fun storageUsedBytes(): Long =
        ModelCatalog.ALL.sumOf { info ->
            if (info.isDirectoryModel()) {
                val dir = File(modelsDir, info.key)
                if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
            } else {
                val file = File(modelsDir, info.fileName)
                if (file.exists()) file.length() else 0L
            }
        }

    /**
     * Return the actual disk size of a single model, or 0 if not present.
     *
     * For Parakeet models (directory-based), returns the size of the ONNX model file
     * (tokens.txt is small and not included in the size check).
     * For Whisper models (flat), returns the single file size.
     */
    fun modelFileSize(modelKey: String): Long {
        val info = ModelCatalog.findByKey(modelKey) ?: return 0L
        val file = if (info.isDirectoryModel()) {
            File(File(modelsDir, info.key), info.fileName)
        } else {
            File(modelsDir, info.fileName)
        }
        return if (file.exists()) file.length() else 0L
    }
}
