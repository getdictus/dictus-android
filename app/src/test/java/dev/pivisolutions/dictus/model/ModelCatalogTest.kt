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

    /** URLs of a model downloaded file by file, in the order of its manifest. */
    private fun urlsOf(key: String): List<String> {
        val info = ModelCatalog.findByKey(key)!!
        val source = info.source as ModelSource.Files
        return info.files.map { source.urlOf(it) }
    }

    @Test
    fun `whisper models download their file from the pinned whisper_cpp revision`() {
        assertEquals(
            listOf("https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny.bin"),
            urlsOf("tiny"),
        )
        assertEquals(
            listOf("https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin"),
            urlsOf("small-q5_1"),
        )
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
    fun `parakeet 0_6b v3 downloads each file from the pinned HuggingFace revision`() {
        // The sherpa-onnx archive was republished on 2026-11-07 with other files (#325)
        val base = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/" +
            "resolve/2bda32ec70b097a55adaa07d9a7173915b43cc78"
        assertEquals(
            listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt").map { "$base/$it" },
            urlsOf("parakeet-tdt-0.6b-v3"),
        )
    }

    @Test
    fun `parakeet 0_6b v3 manifest is the files of the pinned revision`() {
        // Sizes and LFS oids from the HuggingFace API at 2bda32ec; tokens.txt hashed (#325)
        assertEquals(
            listOf(
                ModelFile("encoder.int8.onnx", 652_184_281L, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
                ModelFile("decoder.int8.onnx", 11_845_275L, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
                ModelFile("joiner.int8.onnx", 6_355_277L, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
                ModelFile("tokens.txt", 93_939L, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
            ),
            ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!.files,
        )
    }

    @Test
    fun `parakeet 110m still installs from its sherpa-onnx release archive`() {
        assertEquals(
            ModelSource.Archive(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2",
            ),
            ModelCatalog.findByKey("parakeet-ctc-110m-int8")!!.source,
        )
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
