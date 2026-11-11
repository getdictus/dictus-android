package dev.pivisolutions.dictus.model

import android.content.Context
import androidx.annotation.StringRes
import dev.pivisolutions.dictus.BuildConfig
import dev.pivisolutions.dictus.R
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
 * qualityLabelRes), provider routing, and deprecation signalling. The old nested
 * class was removed; all consumers use ModelCatalog to look up models.
 *
 * @param key             Stable string identifier used in preferences and URLs.
 * @param fileName        Main file on disk: the one the engine loads, and the one whose path
 *                        [ModelManager.getModelPath] returns.
 * @param displayName     Human-readable name shown in the model list UI.
 * @param files           Every file the installed model is made of, with its exact size and
 *                        SHA-256 (#169). The install is verified against it.
 * @param qualityLabelRes Short quality descriptor (e.g. "Fast"), as a string resource.
 * @param descriptionRes  Short description shown on the model card, as a string resource:
 *                        the catalog has no Context, the UI resolves it in its locale (#236).
 * @param precision       Relative accuracy score 0.0–1.0 for the Precision bar, from [ModelBars] (#180).
 * @param speed           Relative speed score 0.0–1.0 for the Vitesse bar, from [ModelBars] (#180).
 * @param provider        Inference engine.
 * @param source          Where the files are downloaded from.
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
    val source: ModelSource,
    @StringRes val qualityLabelRes: Int,
    @StringRes val descriptionRes: Int,
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
 * Where the files of a model are downloaded from.
 *
 * WHY a revision pinned in the URL: the hashes of [ModelInfo.files] describe one upstream
 * version. A moving URL breaks every new install the day upstream republishes, which is what
 * the sherpa-onnx Parakeet 0.6B v3 archive did on 2026-11-07 (#325).
 */
sealed interface ModelSource {
    /** Each file of [ModelInfo.files] is fetched on its own from `<baseUrl>/<name>`. */
    data class Files(val baseUrl: String) : ModelSource {
        fun urlOf(file: ModelFile): String = "$baseUrl/${file.name}"
    }

    /** One tar.bz2 archive holding the files of [ModelInfo.files], extracted after download. */
    data class Archive(val url: String) : ModelSource
}

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

    /** The lightest model: nothing lighter exists to suggest once it is the one running (#309). */
    const val LIGHTEST_KEY = "tiny"

    // Pinned revision: the SHA-256 below are the LFS object ids of the files at this commit, so a
    // later upstream change can never make a valid install fail its check.
    private const val WHISPER_BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1"
    private const val PARAKEET_V3_BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/2bda32ec70b097a55adaa07d9a7173915b43cc78"

    // Not pinned: the HuggingFace repo of this model holds no model files, so this release asset
    // is the only source. Unchanged since 2025-07-08; a republish would fail its check (#325).
    private const val PARAKEET_110M_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2"

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
     * Where the sizes and hashes come from (#169): Whisper and Parakeet 0.6B v3, the HuggingFace
     * API (size and LFS oid of each file at the pinned revision; the hash of the 0.6B v3
     * tokens.txt, which is not in LFS, measured on 2026-11-10, #325); Parakeet 110M, the members
     * of its release archive, measured on 2026-10-23.
     */
    val ALL = listOf(
        ModelInfo(
            key = "tiny",
            fileName = "ggml-tiny.bin",
            displayName = "Tiny",
            files = listOf(ModelFile("ggml-tiny.bin", 77_691_713L, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21")),
            source = ModelSource.Files(WHISPER_BASE_URL),
            qualityLabelRes = R.string.model_quality_fast,
            descriptionRes = R.string.model_description_tiny,
            precision = ModelBars.precision("tiny")!!,
            speed = ModelBars.speed("tiny"),
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "base",
            fileName = "ggml-base.bin",
            displayName = "Base",
            files = listOf(ModelFile("ggml-base.bin", 147_951_465L, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe")),
            source = ModelSource.Files(WHISPER_BASE_URL),
            qualityLabelRes = R.string.model_quality_balanced,
            descriptionRes = R.string.model_description_base,
            precision = ModelBars.precision("base")!!,
            speed = ModelBars.speed("base"),
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "small",
            fileName = "ggml-small.bin",
            displayName = "Small",
            files = listOf(ModelFile("ggml-small.bin", 487_601_967L, "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b")),
            source = ModelSource.Files(WHISPER_BASE_URL),
            qualityLabelRes = R.string.model_quality_accurate,
            descriptionRes = R.string.model_description_small,
            precision = ModelBars.precision("small")!!,
            speed = ModelBars.speed("small"),
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.8,
        ),
        ModelInfo(
            key = "small-q5_1",
            fileName = "ggml-small-q5_1.bin",
            displayName = "Small Q5",
            files = listOf(ModelFile("ggml-small-q5_1.bin", 190_085_487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb")),
            source = ModelSource.Files(WHISPER_BASE_URL),
            qualityLabelRes = R.string.model_quality_accurate,
            descriptionRes = R.string.model_description_small_q5,
            precision = ModelBars.precision("small-q5_1")!!,
            speed = ModelBars.speed("small-q5_1"),
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.0,
        ),
        ModelInfo(
            key = "medium-q5_0",
            fileName = "ggml-medium-q5_0.bin",
            displayName = "Medium",
            files = listOf(ModelFile("ggml-medium-q5_0.bin", 539_212_467L, "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f")),
            source = ModelSource.Files(WHISPER_BASE_URL),
            qualityLabelRes = R.string.model_quality_accurate,
            descriptionRes = R.string.model_description_medium,
            precision = ModelBars.precision("medium-q5_0")!!,
            speed = ModelBars.speed("medium-q5_0"),
            languages = ModelLanguages.WHISPER_MULTILINGUAL,
            timeoutFactor = 1.0,
        ),
        // --- Parakeet models (sherpa-onnx) ---
        // Stored in the models/{key}/ directory on device.
        ModelInfo(
            key = "parakeet-ctc-110m-int8",
            fileName = "model.int8.onnx",
            displayName = "Parakeet 110M",
            files = listOf(
                ModelFile("model.int8.onnx", 131_652_171L, "9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a"),
                ModelFile("tokens.txt", 9_953L, "450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10"),
            ),
            source = ModelSource.Archive(PARAKEET_110M_ARCHIVE_URL),
            qualityLabelRes = R.string.model_quality_fast,
            descriptionRes = R.string.model_description_parakeet_110m,
            precision = ModelBars.precision("parakeet-ctc-110m-int8")!!,
            speed = ModelBars.speed("parakeet-ctc-110m-int8"),
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
            source = ModelSource.Files(PARAKEET_V3_BASE_URL),
            qualityLabelRes = R.string.model_quality_premium,
            descriptionRes = R.string.model_description_parakeet_v3,
            // Hand-set, not rated from the measurement until the long-French drift is fixed and
            // re-measured (#330, #180): see ModelBars.PIXEL_4.
            precision = 0.95f,
            speed = ModelBars.speed("parakeet-tdt-0.6b-v3"),
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
     * Return the recommended model key for onboarding based on device RAM and CPU generation.
     *
     * >= 6 GB marketed RAM (Parakeet's minRamGb)     -> parakeet-tdt-0.6b-v3 (640 MB, 25 languages, best quality)
     * < 6 GB marketed RAM                            -> base (148 MB)
     * < 6 GB marketed RAM on a [SocClass.LEGACY] CPU -> tiny (77 MB)
     *
     * Follows iOS (ModelInfo.recommendedIdentifier), which keeps Small away from its old chips.
     * On a 6 GB Pixel 4, Parakeet answers a 6 s dictation in under 1 s where small-q5_1 takes
     * about 16 s, because whisper.cpp pads every take to a 30 s window (#145, #148): phones
     * below 6 GB are usually slower than that one, so they get a lighter Whisper (#309).
     *
     * WHY DeviceRam: the Models screen gates the same model with the same helper, so
     * onboarding never recommends a model the Models screen shows as unavailable.
     */
    fun recommendedModelKey(context: Context): String =
        recommendedModelKey(DeviceRam.totalMemBytes(context), SocClass.current())

    /** Pure variant of [recommendedModelKey] for a given totalMem reading and CPU generation. */
    fun recommendedModelKey(totalMemBytes: Long, socClass: SocClass): String {
        val parakeet = findByKey("parakeet-tdt-0.6b-v3")!!
        return when {
            DeviceRam.meetsRequirement(parakeet, totalMemBytes) -> parakeet.key
            socClass == SocClass.LEGACY -> LIGHTEST_KEY
            else -> "base"
        }
    }

    /**
     * Returns true if a model uses a directory-based storage layout.
     *
     * Parakeet models are stored in a subdirectory (models/{key}/{fileName}) because
     * they require companion files (tokens.txt) alongside the ONNX model file.
     * Whisper models use a flat layout (models/{fileName}).
     */
    fun ModelInfo.isDirectoryModel(): Boolean = provider == AiProvider.PARAKEET
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
     * Get the target file path for downloading a model (may not exist yet).
     *
     * Used by ModelDownloader to determine the write destination.
     * For Parakeet models, returns the path to the ONNX file inside the model subdirectory.
     * The parent directory is created by ModelDownloader when the install lands.
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
     * Directory the files of a directory model are gathered in before they are verified and
     * renamed to the model directory, so the engine never sees a half-written model (#169).
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
