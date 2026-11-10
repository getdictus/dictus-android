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
        assertEquals(5, whisperModels.size)
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
        assertEquals("Parakeet 110M", model.displayName)
        assertTrue(model.isEnglishOnly)
        assertFalse(model.isTransducer)
    }

    @Test
    fun `catalog contains parakeet-tdt-0_6b-v3`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")
        assertNotNull(model)
        assertEquals(AiProvider.PARAKEET, model!!.provider)
        assertTrue(model.isTransducer)
        assertFalse(model.isEnglishOnly)
        assertEquals(6, model.minRamGb)
    }

    @Test
    fun `parakeet models have correct count`() {
        val parakeetModels = ModelCatalog.ALL.filter { it.provider == AiProvider.PARAKEET }
        assertEquals(2, parakeetModels.size)
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
    fun `all models have descriptions and bounded metric scores`() {
        ModelCatalog.ALL.forEach { model ->
            assertTrue("${model.key} should have a description", model.descriptionRes != 0)
            assertTrue("${model.key} precision should be in 0 to 1", model.precision in 0f..1f)
            assertTrue("${model.key} speed should be in 0 to 1", model.speed in 0f..1f)
        }
    }

    @Test
    fun `whisper speed scores preserve expected device ordering`() {
        val expectedFastestToSlowest = listOf(
            "tiny",
            "base",
            "small-q5_1",
            "small",
            "medium-q5_0",
        )
        val actualFastestToSlowest = ModelCatalog.ALL
            .filter { it.provider == AiProvider.WHISPER }
            .sortedByDescending { it.speed }
            .map { it.key }

        assertEquals(expectedFastestToSlowest, actualFastestToSlowest)
    }

    @Test
    fun `parakeet speed scores preserve expected device ordering`() {
        val parakeet110m = ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!
        val parakeet600m = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!

        assertTrue(parakeet110m.speed > parakeet600m.speed)
    }

    // --- Supported languages (issue #133) ---

    @Test
    fun `every catalog model declares unique well-formed BCP-47 language codes`() {
        ModelCatalog.ALL.forEach { model ->
            val codes = model.languages.codes
            assertTrue("${model.key} should declare languages", codes.isNotEmpty())
            assertEquals("${model.key} has duplicate codes", codes.size, codes.distinct().size)
            codes.forEach { code ->
                assertEquals(
                    "${model.key}: '$code' is not a BCP-47 language code",
                    code,
                    java.util.Locale.forLanguageTag(code).language,
                )
            }
        }
    }

    @Test
    fun `parakeet 110M declares English only`() {
        val model = ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!
        assertEquals(listOf("en"), model.languages.codes)
        assertEquals(LanguageCoverage.ENGLISH_ONLY, model.languages.coverage)
    }

    @Test
    fun `parakeet v3 declares the 25 European languages of the NVIDIA model card`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!
        val expected = setOf(
            "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it",
            "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk",
        )
        assertEquals(25, model.languages.codes.size)
        assertEquals(expected, model.languages.codes.toSet())
        assertEquals(LanguageCoverage.EUROPEAN, model.languages.coverage)
    }

    @Test
    fun `whisper models are multilingual with 99 languages and FR EN ES DE first`() {
        ModelCatalog.ALL.filter { it.provider == AiProvider.WHISPER }.forEach { model ->
            assertEquals(LanguageCoverage.MULTILINGUAL, model.languages.coverage)
            assertEquals(99, model.languages.codes.size)
            assertEquals(listOf("fr", "en", "es", "de"), model.languages.codes.take(4))
            assertEquals(listOf("fr", "en", "es", "de"), model.languages.highlights)
            assertFalse(model.isEnglishOnly)
        }
    }

    @Test
    fun `highlights and others split the codes without overlap`() {
        val parakeet = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!.languages
        assertEquals(listOf("fr", "en", "es", "de"), parakeet.highlights)
        assertEquals(21, parakeet.others.size)
        assertTrue(parakeet.others.none { it in parakeet.highlights })
        assertEquals(listOf("en"), ModelLanguages.ENGLISH.highlights)
        assertTrue(ModelLanguages.ENGLISH.others.isEmpty())
    }

    @Test
    fun `parakeet v3 display name says v3`() {
        val model = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!
        assertTrue(model.displayName.contains("v3"))
    }
}
