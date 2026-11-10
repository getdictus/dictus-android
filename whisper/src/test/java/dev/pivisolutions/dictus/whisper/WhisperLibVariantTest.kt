package dev.pivisolutions.dictus.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperLibVariantTest {

    private fun arm64CpuInfo(features: String) = """
        processor	: 0
        BogoMIPS	: 38.40
        Features	: $features
        CPU implementer	: 0x51
        CPU architecture: 8
        CPU variant	: 0xd
        CPU part	: 0x805
        CPU revision	: 14

        Hardware	: Qualcomm Technologies, Inc
    """.trimIndent()

    private fun arm32CpuInfo(features: String) = """
        processor	: 0
        model name	: ARMv7 Processor rev 4 (v7l)
        BogoMIPS	: 38.40
        Features	: $features
        CPU implementer	: 0x41
        CPU architecture: 7
        CPU variant	: 0x0
        CPU part	: 0xc07
        CPU revision	: 4

        Hardware	: Qualcomm Technologies, Inc MSM8909
    """.trimIndent()

    // Snapdragon 855 (Pixel 4): Cortex-A76 and A55, ARMv8.2.
    private val withFp16 = arm64CpuInfo(
        "fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp",
    )

    // Cortex-A53 / A73: ARMv8.0, no half-precision arithmetic.
    private val withoutFp16 = arm64CpuInfo("fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid")

    // Cortex-A7: the VFPv4 hwcap sits after vfpv3 and tls.
    private val withVfpv4 =
        arm32CpuInfo("half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 idiva idivt vfpd32 lpae evtstrm")

    // Cortex-A9: NEON and VFPv3 only.
    private val withoutVfpv4 = arm32CpuInfo("half thumb fastmult vfp edsp neon vfpv3 tls vfpd32")

    @Test
    fun `arm64 with fphp loads the FP16 build`() {
        assertEquals("whisper_v8fp16_va", selectWhisperLib("arm64-v8a", withFp16))
    }

    @Test
    fun `arm64 without fphp loads the default build`() {
        assertEquals("whisper", selectWhisperLib("arm64-v8a", withoutFp16))
    }

    @Test
    fun `armeabi-v7a with vfpv4 loads the vfpv4 build`() {
        assertEquals("whisper_vfpv4", selectWhisperLib("armeabi-v7a", withVfpv4))
    }

    @Test
    fun `armeabi-v7a without vfpv4 loads the default build`() {
        assertEquals("whisper", selectWhisperLib("armeabi-v7a", withoutVfpv4))
    }

    @Test
    fun `empty cpuinfo loads the default build`() {
        assertEquals("whisper", selectWhisperLib("arm64-v8a", ""))
        assertEquals("whisper", selectWhisperLib("armeabi-v7a", ""))
    }

    @Test
    fun `unreadable cpuinfo loads the default build`() {
        assertEquals("whisper", selectWhisperLib("arm64-v8a", null))
        assertEquals("whisper", selectWhisperLib("armeabi-v7a", null))
    }

    @Test
    fun `cpuinfo without a Features line loads the default build`() {
        assertEquals("whisper", selectWhisperLib("arm64-v8a", "processor\t: 0\nCPU architecture: 8\n"))
    }

    @Test
    fun `a feature name outside the Features line does not count`() {
        val cpuInfo = withoutFp16 + "\nmodel name\t: fphp vfpv4 board\n"
        assertEquals("whisper", selectWhisperLib("arm64-v8a", cpuInfo))
    }

    @Test
    fun `a longer word containing the feature name does not count`() {
        assertEquals("whisper", selectWhisperLib("arm64-v8a", arm64CpuInfo("fp asimd xfphp")))
        assertEquals("whisper", selectWhisperLib("armeabi-v7a", arm32CpuInfo("neon vfpv3 vfpv4d16")))
    }

    @Test
    fun `every core must report the feature`() {
        val mixedCores = "$withFp16\n\n$withoutFp16"
        assertEquals("whisper", selectWhisperLib("arm64-v8a", mixedCores))
    }

    @Test
    fun `a feature of the other ABI does not select a build`() {
        assertEquals("whisper", selectWhisperLib("armeabi-v7a", withFp16))
        assertEquals("whisper", selectWhisperLib("arm64-v8a", withVfpv4))
    }

    @Test
    fun `other ABIs load the default build`() {
        assertEquals("whisper", selectWhisperLib("x86_64", withFp16))
    }
}
