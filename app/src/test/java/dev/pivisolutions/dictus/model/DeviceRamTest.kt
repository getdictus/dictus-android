package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the tolerant RAM threshold (issues #121, #148), with totalMem readings in the
 * range real phones report: the OS sees less than the marketed size.
 */
class DeviceRamTest {

    private val parakeet = ModelCatalog.findByKey("parakeet-tdt-0.6b-v3")!!
    private val tiny = ModelCatalog.findByKey("tiny")!!

    private fun gib(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    @Test
    fun `marketedGb rounds totalMem up to the marketed tier`() {
        assertEquals(4, DeviceRam.marketedGb(gib(3.6)))
        assertEquals(5, DeviceRam.marketedGb(gib(4.6)))
        assertEquals(6, DeviceRam.marketedGb(gib(5.34)))
        assertEquals(6, DeviceRam.marketedGb(gib(5.5)))
        assertEquals(8, DeviceRam.marketedGb(gib(7.4)))
        assertEquals(8, DeviceRam.marketedGb(gib(7.9)))
        assertEquals(8, DeviceRam.marketedGb(gib(8.0)))
        assertEquals(12, DeviceRam.marketedGb(gib(11.6)))
    }

    @Test
    fun `Parakeet needs 6 GB, like iOS`() {
        assertEquals(6, parakeet.minRamGb)
    }

    @Test
    fun `6 GB phones reporting 5_34 to 5_9 GiB meet the 6 GB requirement`() {
        // 5.34 GiB is what the Pixel 4 (6 GB) reports
        assertTrue(DeviceRam.meetsRequirement(parakeet, gib(5.34)))
        assertTrue(DeviceRam.meetsRequirement(parakeet, gib(5.6)))
        assertTrue(DeviceRam.meetsRequirement(parakeet, gib(5.9)))
    }

    @Test
    fun `8 and 12 GB phones meet the 6 GB requirement`() {
        assertTrue(DeviceRam.meetsRequirement(parakeet, gib(7.4)))
        assertTrue(DeviceRam.meetsRequirement(parakeet, gib(11.6)))
    }

    @Test
    fun `4 and 5 GB phones do not meet the 6 GB requirement`() {
        assertFalse(DeviceRam.meetsRequirement(parakeet, gib(3.6)))
        assertFalse(DeviceRam.meetsRequirement(parakeet, gib(4.6)))
        assertFalse(DeviceRam.meetsRequirement(parakeet, gib(5.0)))
    }

    @Test
    fun `models without a requirement are always runnable`() {
        assertTrue(DeviceRam.meetsRequirement(tiny, 0L))
    }
}
