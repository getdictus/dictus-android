package dev.pivisolutions.dictus.core.voicenotes

/**
 * The container a shared audio file is in, read from its first bytes. Port of iOS
 * `SharedAudioFormat` (#204).
 *
 * WHY sniff rather than trust the MIME type: the sharing app names the type, and a voice note
 * often arrives as a generic audio or `application/octet-stream` type whatever it holds. The bytes decide,
 * as on iOS.
 */
enum class SharedAudioFormat(val fileExtension: String) {
    OGG("ogg"),
    MPEG4("m4a"),
    MP3("mp3"),
    WAV("wav"),
    CAF("caf"),
    AIFF("aiff"),
    FLAC("flac"),
    AMR("amr"),
    ADTS("aac"),
    MATROSKA("webm"),

    /** WeChat's voice notes. Recognised so the refusal can name it; no decoder reads it. */
    SILK("silk"),
    ;

    val isSupported: Boolean get() = this != SILK

    companion object {
        /** Bytes [sniff] needs. */
        const val SNIFF_LENGTH = 12

        /** The container of a file starting with [head], or null when none is recognised. */
        fun sniff(head: ByteArray): SharedAudioFormat? {
            val bytes = head.take(SNIFF_LENGTH)
            fun ascii(from: Int, to: Int): String? =
                if (bytes.size >= to) String(bytes.subList(from, to).toByteArray(), Charsets.US_ASCII) else null
            fun byte(index: Int): Int = bytes[index].toInt() and 0xFF

            if (ascii(0, 4) == "OggS") return OGG
            if (ascii(4, 8) == "ftyp") return MPEG4
            if (ascii(0, 4) == "RIFF" && ascii(8, 12) == "WAVE") return WAV
            if (ascii(0, 4) == "caff") return CAF
            if (ascii(0, 4) == "FORM" && ascii(8, 12).let { it == "AIFF" || it == "AIFC" }) return AIFF
            if (ascii(0, 4) == "fLaC") return FLAC
            if (ascii(0, 5) == "#!AMR") return AMR
            if (bytes.size >= 4 && byte(0) == 0x1A && byte(1) == 0x45 && byte(2) == 0xDF && byte(3) == 0xA3) return MATROSKA
            if (ascii(0, 9) == "#!SILK_V3" || ascii(1, 10) == "#!SILK_V3") return SILK
            if (ascii(0, 3) == "ID3") return MP3
            if (bytes.size >= 2 && byte(0) == 0xFF && (byte(1) and 0xF6) == 0xF0) return ADTS
            if (bytes.size >= 2 && byte(0) == 0xFF && (byte(1) and 0xE0) == 0xE0 && (byte(1) and 0x06) != 0) return MP3
            return null
        }
    }
}
