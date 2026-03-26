package dev.pivisolutions.dictus.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for ModelManager.
 *
 * Uses Robolectric to provide a real Android context with an in-memory file system.
 * Tests exercise file presence/absence detection, canDelete guard, deleteModel,
 * storageUsedBytes, and getDownloadedModels without network access.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelManagerTest {

    private lateinit var modelManager: ModelManager
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        modelManager = ModelManager(RuntimeEnvironment.getApplication())
        modelsDir = File(RuntimeEnvironment.getApplication().filesDir, "models")
        modelsDir.mkdirs()
    }

    // --- getModelPath ---

    @Test
    fun `getModelPath returns null when file does not exist`() {
        assertNull(modelManager.getModelPath("tiny"))
    }

    @Test
    fun `getModelPath returns path when file exists with correct size`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(77_691_713))
        val path = modelManager.getModelPath("tiny")
        assertNotNull(path)
        assertTrue(path!!.endsWith("models/ggml-tiny.bin"))
    }

    @Test
    fun `getModelPath returns null when file has wrong size`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(100)) // partial/corrupt
        assertNull(modelManager.getModelPath("tiny"))
    }

    @Test
    fun `getModelPath returns null for unknown model key`() {
        assertNull(modelManager.getModelPath("nonexistent"))
    }

    // --- isDownloaded ---

    @Test
    fun `isDownloaded returns false when no file`() {
        assertFalse(modelManager.isDownloaded("tiny"))
    }

    @Test
    fun `isDownloaded returns true when file exists with correct size`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(77_691_713))
        assertTrue(modelManager.isDownloaded("tiny"))
    }

    // --- getDownloadedModels ---

    @Test
    fun `getDownloadedModels returns empty list when nothing downloaded`() {
        assertTrue(modelManager.getDownloadedModels().isEmpty())
    }

    @Test
    fun `getDownloadedModels returns key for downloaded model`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(77_691_713))
        val downloaded = modelManager.getDownloadedModels()
        assertEquals(listOf("tiny"), downloaded)
    }

    // --- canDelete ---

    @Test
    fun `canDelete returns false when only 1 model downloaded`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(77_691_713))
        assertFalse(modelManager.canDelete("tiny"))
    }

    @Test
    fun `canDelete returns true when 2 models downloaded`() {
        val tiny = File(modelsDir, "ggml-tiny.bin")
        tiny.writeBytes(ByteArray(77_691_713))
        val small = File(modelsDir, "ggml-small-q5_1.bin")
        small.writeBytes(ByteArray(190_031_232))
        assertTrue(modelManager.canDelete("tiny"))
        assertTrue(modelManager.canDelete("small-q5_1"))
    }

    @Test
    fun `canDelete returns false for non-downloaded model`() {
        val tiny = File(modelsDir, "ggml-tiny.bin")
        tiny.writeBytes(ByteArray(77_691_713))
        val small = File(modelsDir, "ggml-small-q5_1.bin")
        small.writeBytes(ByteArray(190_031_232))
        // "base" is not downloaded, so canDelete should be false even though 2 others are present
        assertFalse(modelManager.canDelete("base"))
    }

    // --- deleteModel ---

    @Test
    fun `deleteModel returns false when last model`() {
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(77_691_713))
        assertFalse(modelManager.deleteModel("tiny"))
        assertTrue("File should still exist", file.exists())
    }

    @Test
    fun `deleteModel returns true and removes file when multiple models exist`() {
        val tiny = File(modelsDir, "ggml-tiny.bin")
        tiny.writeBytes(ByteArray(77_691_713))
        val small = File(modelsDir, "ggml-small-q5_1.bin")
        small.writeBytes(ByteArray(190_031_232))
        assertTrue(modelManager.deleteModel("tiny"))
        assertFalse("File should be deleted", tiny.exists())
    }

    // --- storageUsedBytes ---

    @Test
    fun `storageUsedBytes returns 0 when no models downloaded`() {
        assertEquals(0L, modelManager.storageUsedBytes())
    }

    @Test
    fun `storageUsedBytes returns sum of actual file sizes`() {
        val tiny = File(modelsDir, "ggml-tiny.bin")
        tiny.writeBytes(ByteArray(100))
        val small = File(modelsDir, "ggml-small-q5_1.bin")
        small.writeBytes(ByteArray(200))
        assertEquals(300L, modelManager.storageUsedBytes())
    }

    // --- downloadUrl (backward compat) ---

    @Test
    fun `downloadUrl returns correct URL for tiny`() {
        assertEquals(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            modelManager.downloadUrl("tiny"),
        )
    }

    @Test
    fun `downloadUrl returns correct URL for small-q5_1`() {
        assertEquals(
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            modelManager.downloadUrl("small-q5_1"),
        )
    }

    @Test
    fun `downloadUrl returns null for unknown model`() {
        assertNull(modelManager.downloadUrl("nonexistent"))
    }

    // --- DEFAULT_MODEL_KEY ---

    @Test
    fun `defaultModelKey is tiny`() {
        assertEquals("tiny", ModelManager.DEFAULT_MODEL_KEY)
    }
}
