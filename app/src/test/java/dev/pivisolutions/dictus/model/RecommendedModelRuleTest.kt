package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The onboarding recommendation by RAM and CPU generation (#148, #309). */
class RecommendedModelRuleTest {

    private fun gib(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    @Test
    fun `below 6 GB a current CPU gets Base`() {
        assertEquals("base", ModelCatalog.recommendedModelKey(gib(1.8), SocClass.CURRENT))
        assertEquals("base", ModelCatalog.recommendedModelKey(gib(3.6), SocClass.CURRENT))
        assertEquals("base", ModelCatalog.recommendedModelKey(gib(4.6), SocClass.CURRENT))
        assertEquals("base", ModelCatalog.recommendedModelKey(gib(5.0), SocClass.CURRENT))
    }

    @Test
    fun `below 6 GB a legacy CPU gets Tiny`() {
        assertEquals("tiny", ModelCatalog.recommendedModelKey(gib(1.8), SocClass.LEGACY))
        assertEquals("tiny", ModelCatalog.recommendedModelKey(gib(3.6), SocClass.LEGACY))
        assertEquals("tiny", ModelCatalog.recommendedModelKey(gib(5.0), SocClass.LEGACY))
    }

    @Test
    fun `from 6 GB every CPU gets Parakeet`() {
        val parakeet = "parakeet-tdt-0.6b-v3"
        assertEquals(parakeet, ModelCatalog.recommendedModelKey(gib(5.34), SocClass.CURRENT)) // Pixel 4, 6 GB
        assertEquals(parakeet, ModelCatalog.recommendedModelKey(gib(5.34), SocClass.LEGACY))
        assertEquals(parakeet, ModelCatalog.recommendedModelKey(gib(7.4), SocClass.CURRENT)) // 8 GB phone
        assertEquals(parakeet, ModelCatalog.recommendedModelKey(gib(11.6), SocClass.CURRENT)) // 12 GB phone
    }
}
