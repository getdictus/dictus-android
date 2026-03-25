package dev.pivisolutions.dictus.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelManagerTest {

    private lateinit var modelManager: ModelManager
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        modelManager = ModelManager(RuntimeEnvironment.getApplication())
        modelsDir = File(RuntimeEnvironment.getApplication().filesDir, "models")
    }

    @Test
    fun `getModelPath returns null when file does not exist`() {
        assertNull(modelManager.getModelPath("tiny"))
    }

    @Test
    fun `getModelPath returns path when file exists with correct size`() {
        modelsDir.mkdirs()
        val file = File(modelsDir, "ggml-tiny.bin")
        // Write expected number of bytes
        file.writeBytes(ByteArray(77_691_456))
        val path = modelManager.getModelPath("tiny")
        assertNotNull(path)
        assertTrue(path!!.endsWith("models/ggml-tiny.bin"))
    }

    @Test
    fun `getModelPath returns null when file has wrong size`() {
        modelsDir.mkdirs()
        val file = File(modelsDir, "ggml-tiny.bin")
        file.writeBytes(ByteArray(100)) // partial/corrupt
        assertNull(modelManager.getModelPath("tiny"))
    }

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

    @Test
    fun `getModelPath returns null for unknown model`() {
        assertNull(modelManager.getModelPath("nonexistent"))
    }

    @Test
    fun `defaultModelKey is tiny`() {
        assertEquals("tiny", ModelManager.DEFAULT_MODEL_KEY)
    }

    @Test
    fun `modelKeys contains tiny and small-q5_1`() {
        val keys = ModelManager.MODELS.keys
        assertTrue(keys.contains("tiny"))
        assertTrue(keys.contains("small-q5_1"))
    }
}
