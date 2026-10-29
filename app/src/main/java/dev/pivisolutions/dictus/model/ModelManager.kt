package dev.pivisolutions.dictus.model

import android.content.Context
import dev.pivisolutions.dictus.BuildConfig
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
 * @param fileName        Main file on disk: the one the engine loads, and the one whose path
 *                        [ModelManager.getModelPath] returns.
 * @param displayName     Human-readable name shown in the model list UI.
 * @param files           Every file the installed model is made of, with its exact size and
 *                        SHA-256 (#169). The install is verified against it.
 * @param qualityLabel    Short quality descriptor shown as a badge (e.g. "Rapide").
 * @param description     Short human-readable description shown on model card.
 * @param precision       Relative accuracy score 0.0–1.0 for the Precision bar.
 * @param speed           Relative speed score 0.0–1.0 for the Vitesse bar.
 * @param provider        Download source and inference engine.
 * @param isDeprecated    When true, hide from new downloads but keep if installed.
 * @param languages       Spoken languages the model transcribes (issue #133).
 * @param timeoutFactor   Seconds of transcription allowed per second of audio, above the
 *                        120 s floor (#179): see TranscriptionTimeout.
 */
data class ModelInfo(
    val key: String,
    val fileName: String,
    val displayName: String,
    val files: List<ModelFile>,
    val qualityLabel: String,
    val description: String = "",
    val precision: Float = 0f,
    val speed: Float = 0f,
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
    val isTransducer: Boolean = false,
    val minRamGb: Int = 0,
    val languages: ModelLanguages,
    val timeoutFactor: Double,
) {
    /** Bytes on disk once installed, shown as the model size. */
    val expectedSizeBytes: Long get() = files.sumOf { it.sizeBytes }

    /** True when the model only transcribes English (derived from [languages]). */
    val isEnglishOnly: Boolean get() = languages.coverage == LanguageCoverage.ENGLISH_ONLY
}

/**
 * One file of an installed model, as published upstream.
 *
 * WHY exact size and SHA-256, not a size threshold: a 95% size gate let a Parakeet encoder
 * truncated by 60 MB pass as installed, and never looked at the other files (#169). The size
 * is cheap enough to check on every read of the model list; the hash is checked once, when
 * the install finishes.
 */
data class ModelFile(val name: String, val sizeBytes: Long, val sha256: String)

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
    // Pinned revision: the SHA-256 below are the LFS object ids of the files at this commit, so a
    // later upstream change can never make a valid install fail its check.
    private const val WHISPER_BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1"
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
     *   parakeet-tdt-0.6b-v3  — 640 MB — 25 languages, requires 6+ GB RAM (transducer)
     *
     * Where the sizes and hashes come from (#169): Whisper, the HuggingFace API (size and LFS
     * oid of each file at the pinned revision); Parakeet, the members of the release archives,
     * measured on 2026-10-23. The 0.6B v3 encoder, decoder and joiner hashes match the LFS oids
     * of csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 on HuggingFace.
     */
    val ALL = listOf(
        ModelInfo(
            key = "tiny",
            fileName = "ggml-tiny.bin",
            displayName = "Tiny",
            files = listOf(ModelFile("ggml-tiny.bin", 77_691_713L, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21")),
            qualityLabel = "Rapide",
            description = "Rapide et léger",
            precision = 0.4f,
            speed = 1.0f,
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "base",
            fileName = "ggml-base.bin",
            displayName = "Base",
            files = listOf(ModelFile("ggml-base.bin", 147_951_465L, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe")),
            qualityLabel = "Équilibré",
            description = "Précis et équilibré",
            precision = 0.6f,
            speed = 0.7f,
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "small",
            fileName = "ggml-small.bin",
            displayName = "Small",
            files = listOf(ModelFile("ggml-small.bin", 487_601_967L, "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b")),
            qualityLabel = "Précis",
            description = "Haute précision",
            precision = 0.9f,
            speed = 0.35f,
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "small-q5_1",
            fileName = "ggml-small-q5_1.bin",
            displayName = "Small Q5",
            files = listOf(ModelFile("ggml-small-q5_1.bin", 190_085_487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb")),
            qualityLabel = "Précis",
            description = "Meilleur compromis",
            precision = 0.85f,
            speed = 0.55f,
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.0,
        ),
        ModelInfo(
            key = "medium-q5_0",
            fileName = "ggml-medium-q5_0.bin",
            displayName = "Medium",
            files = listOf(ModelFile("ggml-medium-q5_0.bin", 539_212_467L, "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f")),
            qualityLabel = "Précis",
            description = "Meilleure précision",
            precision = 0.9f,
            speed = 0.3f,
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.0,
        ),
        // --- Parakeet models (sherpa-onnx) ---
        // Downloaded as tar.bz2 archives, stored in models/{key}/ directory on device.
        ModelInfo(
            key = "parakeet-ctc-110m-int8",
            fileName = "model.int8.onnx",
            displayName = "Parakeet 110M",
            files = listOf(
                ModelFile("model.int8.onnx", 131_652_171L, "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a"),
                ModelFile("tokens.txt", 9_953L, "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10"),
            ),
            qualityLabel = "Rapide",
            description = "Rapide et précis",
            precision = 0.75f,
            speed = 0.85f,
            provider = AiProvider.PARAKEET,
            languages = ModelLanguages.ENGLISH,
            timeoutFactor = 0.6,
        ),
        ModelInfo(
            key = "parakeet-tdt-0.6b-v3",
            fileName = "encoder.int8.onnx",
            // "v3" is the multilingual release (v2 was English only): the name says it
            displayName = "Parakeet 0.6B v3",
            files = listOf(
                ModelFile("encoder.int8.onnx", 652_184_281L, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
                ModelFile("decoder.int8.onnx", 11_845_275L, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
                ModelFile("joiner.int8.onnx", 6_355_277L, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
                ModelFile("tokens.txt", 93_939L, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            ),
            qualityLabel = "Premium",
            description = "Détection automatique de la langue",
            precision = 0.95f,
            speed = 0.40f,
            provider = AiProvider.PARAKEET,
            isTransducer = true,
            minRamGb = BuildConfig.PARAKEET_MIN_RAM_GB,
            languages = ModelLanguages.PARAKEET_V3,
            timeoutFactor = 0.6,
        ),
    )

    /** Find a model descriptor by its stable key, or null if not in catalog. */
    fun findByKey(key: String): ModelInfo? = ALL.find { it.key == key }

    /**
     * Return the recommended model key for onboarding based on device RAM.
     *
     * >= 6 GB marketed RAM (Parakeet's minRamGb) -> parakeet-tdt-0.6b-v3 (640 MB, 25 languages, best quality)
     * < 6 GB marketed RAM                         -> small-q5_1 (190 MB, best accuracy/size trade-off)
     *
     * Same rule as iOS (ModelInfo.recommendedIdentifier, minus its A12/A13 exception). On a
     * 6 GB Pixel 4, Parakeet peaks at ~1.5 GB PSS and answers in under 1 s for a 6 s dictation,
     * where small-q5_1 takes about 30 s (#148).
     *
     * WHY DeviceRam: the Models screen gates the same model with the same helper, so
     * onboarding never recommends a model the Models screen shows as unavailable.
     */
    fun recommendedModelKey(context: Context): String =
        recommendedModelKey(DeviceRam.totalMemBytes(context))

    /** Pure variant of [recommendedModelKey] for a given totalMem reading. */
    fun recommendedModelKey(totalMemBytes: Long): String {
        val parakeet = findByKey("parakeet-tdt-0.6b-v3")!!
        return if (DeviceRam.meetsRequirement(parakeet, totalMemBytes)) parakeet.key else "small-q5_1"
    }

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
     * Every file of [ModelInfo.files] must exist with its exact size (#169): a truncated or
     * missing companion file (decoder, joiner, tokens) makes the whole model unavailable. The
     * SHA-256 is not read here, this runs on every model list refresh; ModelDownloader checks
     * it before an install lands in this directory.
     *
     * For Parakeet models (directory-based), the path points to the ONNX model file
     * inside models/{key}/{fileName} — e.g. models/parakeet-ctc-110m-int8/model.int8.onnx.
     * For Whisper models (flat), the path points to models/{fileName}.
     */
    fun getModelPath(modelKey: String): String? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        val dir = if (info.isDirectoryModel()) File(modelsDir, info.key) else modelsDir
        return if (ModelIntegrity.sizesMatch(dir, info.files)) File(dir, info.fileName).absolutePath else null
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
     * Directory an archive is extracted into before it is verified and renamed to the model
     * directory, so the engine never sees a half-written model (#169).
     */
    fun getStagingDir(modelKey: String): File? {
        val info = ModelCatalog.findByKey(modelKey) ?: return null
        return File(modelsDir, "${info.key}.part")
    }

    /**
     * Return true if every model file exists on disk with its exact size.
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
