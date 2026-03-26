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
    fun `catalog has exactly 4 entries`() {
        assertEquals(4, ModelCatalog.ALL.size)
    }

    @Test
    fun `all model keys are unique`() {
        val keys = ModelCatalog.ALL.map { it.key }
        assertEquals("Duplicate keys found", keys.size, keys.distinct().size)
    }

    @Test
    fun `all models have WHISPER provider`() {
        ModelCatalog.ALL.forEach { model ->
            assertEquals(
                "Model '${model.key}' should have WHISPER provider",
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
        assertEquals(142_000_000L, base.expectedSizeBytes)
    }

    @Test
    fun `third model is small with correct size`() {
        val small = ModelCatalog.ALL[2]
        assertEquals("small", small.key)
        assertEquals(466_000_000L, small.expectedSizeBytes)
    }

    @Test
    fun `fourth model is small-q5_1 with correct size`() {
        val smallQ5 = ModelCatalog.ALL[3]
        assertEquals("small-q5_1", smallQ5.key)
        assertEquals(190_031_232L, smallQ5.expectedSizeBytes)
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
}
