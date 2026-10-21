package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The debug-only -Pdictus.parakeetMinRamGb override (#148) must not reach release:
 * run with ./gradlew testReleaseUnitTest -Pdictus.parakeetMinRamGb=6, it still sees 8.
 */
class ParakeetRamGateReleaseTest {

    @Test
    fun `release keeps the 8 GB Parakeet gate`() {
        assertEquals(8, ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!.minRamGb)
        assertEquals("small-q5_1", ModelCatalog.recommendedModelKey(6L * 1024 * 1024 * 1024))
    }
}
