package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** CPU generation read from /proc/cpuinfo (#309). */
class SocClassTest {

    private fun cpuInfo(featuresLine: String) = "processor\t: 0\nBogoMIPS\t: 38.40\n$featuresLine\nCPU implementer\t: 0x51\n"

    @Test
    fun `an ARMv8_2 CPU is current`() {
        val snapdragon855 = "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp"
        assertEquals(SocClass.CURRENT, SocClass.fromCpuInfo(cpuInfo(snapdragon855)))
    }

    @Test
    fun `an ARMv8_0 CPU is legacy`() {
        val cortexA53 = "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid"
        assertEquals(SocClass.LEGACY, SocClass.fromCpuInfo(cpuInfo(cortexA53)))
    }

    @Test
    fun `a 32-bit kernel is legacy`() {
        val armv7 = "Features\t: half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 idiva idivt"
        assertEquals(SocClass.LEGACY, SocClass.fromCpuInfo(cpuInfo(armv7)))
    }

    @Test
    fun `an x86 emulator has no chip tier`() {
        assertEquals(SocClass.CURRENT, SocClass.fromCpuInfo("processor\t: 0\nflags\t\t: fpu vme sse4_2\n"))
    }
}
