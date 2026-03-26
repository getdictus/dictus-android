package dev.pivisolutions.dictus.model

import android.content.Context
import java.io.File

/**
 * Manages Whisper GGML model files on internal storage.
 *
 * Models are downloaded from HuggingFace to context.filesDir/models/.
 * Phase 3 provides two models: tiny (for fast dev iteration) and small-q5_1
 * (for performance validation on Pixel 4).
 *
 * Phase 4 will extend this with download progress, deletion UI, and storage
 * indicators. The storage location and URL pattern are designed to be reused.
 *
 * WHY internal storage (filesDir): Models are large (77-190 MB) but must survive
 * app updates. Internal storage is app-private and not cleared by cache cleanup.
 */
class ModelManager(context: Context) {

    companion object {
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        const val DEFAULT_MODEL_KEY = "tiny"

        /**
         * Available models with file names and expected sizes.
         *
         * NOTE: CONTEXT.md mentions "small-q5_0" but HuggingFace only provides
         * pre-built q5_1 (190 MB). Using q5_1 per research recommendation.
         * Performance difference is negligible; q5_1 is slightly more accurate.
         */
        val MODELS = mapOf(
            "tiny" to ModelInfo("ggml-tiny.bin", 77_691_713L),
            "small-q5_1" to ModelInfo("ggml-small-q5_1.bin", 190_031_232L),
        )
    }

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    /**
     * Get the absolute path to a downloaded model, or null if not available.
     *
     * Checks both file existence and file size to detect corrupt/partial downloads.
     * A mismatch in size means the download was interrupted and the file is unusable.
     */
    fun getModelPath(modelKey: String): String? {
        val info = MODELS[modelKey] ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists() && file.length() == info.expectedSize) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Get the HuggingFace download URL for a model.
     */
    fun downloadUrl(modelKey: String): String? {
        val info = MODELS[modelKey] ?: return null
        return "$BASE_URL/${info.fileName}"
    }

    /**
     * Get the target file path for downloading a model (may not exist yet).
     */
    fun getTargetPath(modelKey: String): String? {
        val info = MODELS[modelKey] ?: return null
        return File(modelsDir, info.fileName).absolutePath
    }

    data class ModelInfo(val fileName: String, val expectedSize: Long)
}
