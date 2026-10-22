package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The debug-only -Pdictus.parakeetMinRamGb override (#148) must not reach release:
 * run with ./gradlew testReleaseUnitTest -Pdictus.parakeetMinRamGb=8, it still sees 6.
 */
class ParakeetRamGateReleaseTest {

    private fun gib(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    @Test
    fun `release keeps the 6 GB Parakeet gate`() {
        assertEquals(6, ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!.minRamGb)
        assertEquals("parakeet-tdt-0.6b-v3", ModelCatalog.recommendedModelKey(gib(5.34)))
        assertEquals("small-q5_1", ModelCatalog.recommendedModelKey(gib(4.6)))
    }
}
