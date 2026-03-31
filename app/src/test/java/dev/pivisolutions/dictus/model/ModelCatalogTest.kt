package dev.pivisolutions.dictus.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ModelCatalog — the static model registry.
 *
 * These tests verify the catalog contract without needing Android context
 * or file system access (pure Kotlin logic).
 */
class ModelCatalogTest {

    @Test
    fun `catalog has 7 total models`() {
        assertEquals(7, ModelCatalog.ALL.size)
    }

    @Test
    fun `all model keys are unique`() {
        val keys = ModelCatalog.ALL.map { it.key }
        assertEquals("Duplicate keys found", keys.size, keys.distinct().size)
    }

    @Test
    fun `whisper models all have WHISPER provider`() {
        val whisperModels = ModelCatalog.ALL.filter { it.provider == AiProvider.WHISPER }
        assertEquals(4, whisperModels.size)
        whisperModels.forEach { model ->
            assertEquals(
                "Whisper model '${model.key}' should have WHISPER provider",
                AiProvider.WHISPER,
                model.provider,
            )
        }
    }

    @Test
    fun `DEFAULT_KEY is tiny`() {
        assertEquals("tiny", ModelCatalog.DEFAULT_KEY)
    }

    @Test
    fun `first model is tiny with correct size`() {
        val tiny = ModelCatalog.ALL[0]
        assertEquals("tiny", tiny.key)
        assertEquals(77_691_713L, tiny.expectedSizeBytes)
    }

    @Test
    fun `second model is base with correct size`() {
        val base = ModelCatalog.ALL[1]
        assertEquals("base", base.key)
        assertEquals(147_951_465L, base.expectedSizeBytes)
    }

    @Test
    fun `third model is small with correct size`() {
        val small = ModelCatalog.ALL[2]
        assertEquals("small", small.key)
        assertEquals(487_601_967L, small.expectedSizeBytes)
    }

    @Test
    fun `fourth model is small-q5_1 with correct size`() {
        val smallQ5 = ModelCatalog.ALL[3]
        assertEquals("small-q5_1", smallQ5.key)
        assertEquals(190_085_487L, smallQ5.expectedSizeBytes)
    }

    @Test
    fun `findByKey returns correct ModelInfo for tiny`() {
        val info = ModelCatalog.findByKey("tiny")
        assertNotNull(info)
        assertEquals("ggml-tiny.bin", info!!.fileName)
    }

    @Test
    fun `findByKey returns null for unknown key`() {
        assertNull(ModelCatalog.findByKey("nonexistent"))
    }

    @Test
    fun `downloadUrl returns HuggingFace URL containing fileName`() {
        val info = ModelCatalog.findByKey("tiny")!!
        val url = ModelCatalog.downloadUrl(info)
        assertTrue("URL should contain fileName", url.contains("ggml-tiny.bin"))
        assertTrue("URL should be HuggingFace", url.contains("huggingface.co"))
    }

    @Test
    fun `downloadUrl for small-q5_1 contains correct filename`() {
        val info = ModelCatalog.findByKey("small-q5_1")!!
        val url = ModelCatalog.downloadUrl(info)
        assertTrue(url.contains("ggml-small-q5_1.bin"))
    }

    // --- Parakeet catalog tests ---

    @Test
    fun `catalog contains parakeet-ctc-110m-int8`() {
        val model = ModelCatalog.findByKey("parakeet-ctc-110m-int8")
        assertNotNull(model)
        assertEquals(AiProvider.PARAKEET, model!!.provider)
        assertEquals("Parakeet 110M INT8", model.displayName)
        assertTrue(model.description.contains("Anglais"))
    }

    @Test
    fun `catalog contains parakeet-ctc-110m-fp16`() {
        val model = ModelCatalog.findByKey("parakeet-ctc-110m-fp16")
        assertNotNull(model)
        assertEquals(AiProvider.PARAKEET, model!!.provider)
    }

    @Test
    fun `catalog contains parakeet-tdt-0_6b-v2`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v2")
        assertNotNull(model)
        assertEquals(AiProvider.PARAKEET, model!!.provider)
        assertTrue(model.description.contains("8+ GB RAM"))
    }

    @Test
    fun `all parakeet models have english-only description`() {
        val parakeetModels = ModelCatalog.ALL.filter { it.provider == AiProvider.PARAKEET }
        assertEquals(3, parakeetModels.size)
        parakeetModels.forEach { model ->
            assertTrue(
                "Parakeet model '${model.key}' should mention Anglais in description",
                model.description.contains("Anglais"),
            )
        }
    }

    @Test
    fun `downloadUrl returns valid URL for parakeet models`() {
        val model = ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!
        val url = ModelCatalog.downloadUrl(model)
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("sherpa-onnx") || url.contains("parakeet"))
    }

    @Test
    fun `downloadUrl returns sherpa-onnx releases URL for all parakeet models`() {
        ModelCatalog.ALL.filter { it.provider == AiProvider.PARAKEET }.forEach { model ->
            val url = ModelCatalog.downloadUrl(model)
            assertTrue(
                "Parakeet model '${model.key}' URL should point to sherpa-onnx releases",
                url.contains("github.com/k2-fsa/sherpa-onnx"),
            )
            assertTrue(
                "Parakeet model '${model.key}' URL should be a tar.bz2 archive",
                url.endsWith(".tar.bz2"),
            )
        }
    }

    @Test
    fun `parakeet models have non-zero precision and speed scores`() {
        ModelCatalog.ALL.filter { it.provider == AiProvider.PARAKEET }.forEach { model ->
            assertTrue("${model.key} should have precision > 0", model.precision > 0f)
            assertTrue("${model.key} should have speed > 0", model.speed > 0f)
        }
    }
}
