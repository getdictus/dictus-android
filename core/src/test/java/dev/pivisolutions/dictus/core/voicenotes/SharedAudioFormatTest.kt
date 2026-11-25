package dev.pivisolutions.dictus.core.voicenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The container sniffer, iOS `SharedAudioFormat.sniff` (#204). */
class SharedAudioFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `the test voice note is Ogg`() {
        val head = requireNotNull(javaClass.getResourceAsStream("/voicenotes/tone-440hz-1500ms.ogg")).use {
            it.readNBytes(SharedAudioFormat.SNIFF_LENGTH)
        }
        assertEquals(SharedAudioFormat.OGG, SharedAudioFormat.sniff(head))
    }

    @Test
    fun `each container is told by its magic bytes`() {
        assertEquals(SharedAudioFormat.MPEG4, SharedAudioFormat.sniff(ascii("\u0000\u0000\u0000 ftypM4A ")))
        assertEquals(SharedAudioFormat.WAV, SharedAudioFormat.sniff(ascii("RIFF\u0000\u0000\u0000\u0000WAVE")))
        assertEquals(SharedAudioFormat.AIFF, SharedAudioFormat.sniff(ascii("FORM\u0000\u0000\u0000\u0000AIFC")))
        assertEquals(SharedAudioFormat.FLAC, SharedAudioFormat.sniff(ascii("fLaC\u0000\u0000\u0000\u0010")))
        assertEquals(SharedAudioFormat.AMR, SharedAudioFormat.sniff(ascii("#!AMR\n")))
        assertEquals(SharedAudioFormat.MATROSKA, SharedAudioFormat.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F)))
        assertEquals(SharedAudioFormat.MP3, SharedAudioFormat.sniff(ascii("ID3\u0004\u0000")))
        assertEquals(SharedAudioFormat.MP3, SharedAudioFormat.sniff(bytes(0xFF, 0xFB, 0x90, 0x00)))
        assertEquals(SharedAudioFormat.ADTS, SharedAudioFormat.sniff(bytes(0xFF, 0xF1, 0x50, 0x80)))
        assertEquals(SharedAudioFormat.SILK, SharedAudioFormat.sniff(ascii("\u0002#!SILK_V3")))
    }

    @Test
    fun `text is no audio, and SILK is recognised but not supported`() {
        assertNull(SharedAudioFormat.sniff(ascii("hello world!")))
        assertNull(SharedAudioFormat.sniff(ByteArray(0)))
        assertFalse(SharedAudioFormat.SILK.isSupported)
    }
}
