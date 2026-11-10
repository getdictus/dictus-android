package dev.pivisolutions.dictus.model

/**
 * How widely a model covers spoken languages. Drives the one-line summary on
 * the model card ("English only", "25 European languages", "~99 languages").
 */
enum class LanguageCoverage { ENGLISH_ONLY, EUROPEAN, MULTILINGUAL }

/**
 * Spoken languages a model transcribes, as BCP-47 language codes.
 *
 * WHY static curated data (same choice as iOS dictus#240): language coverage is a
 * property of the model family documented by its vendor, it never changes at runtime.
 * The UI turns codes into names with [java.util.Locale], so no translation table is
 * kept here.
 *
 * @param coverage Coverage class for the card summary.
 * @param codes    Supported language codes, the Dictus-tested ones (fr, en, es, de)
 *                 first when the model supports them.
 */
data class ModelLanguages(
    val coverage: LanguageCoverage,
    val codes: List<String>,
) {
    /** The Dictus-tested languages this model supports, in [PRIORITY_CODES] order. */
    val highlights: List<String> get() = PRIORITY_CODES.filter { it in codes }

    /** Every other supported language, beyond [highlights]. */
    val others: List<String> get() = codes.filterNot { it in PRIORITY_CODES }

    companion object {
        /** Languages Dictus is tested with, always listed first. */
        val PRIORITY_CODES = listOf("fr", "en", "es", "de")

        /** Parakeet 110M (parakeet_tdt_ctc_110m-en): trained on English only. */
        val ENGLISH = ModelLanguages(LanguageCoverage.ENGLISH_ONLY, listOf("en"))

        /**
         * Parakeet TDT 0.6B v3: exactly the 25 European languages listed on NVIDIA's
         * model card, https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3 ("Supported
         * Languages"). No other language is transcribed.
         */
        val PARAKEET_V3 = ModelLanguages(
            LanguageCoverage.EUROPEAN,
            PRIORITY_CODES + listOf(
                "bg", "cs", "da", "el", "et", "fi", "hr", "hu", "it", "lt", "lv",
                "mt", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "uk",
            ),
        )

        /**
         * Multilingual Whisper (tiny to medium, no ".en" suffix): the 99 languages of
         * OpenAI's whisper tokenizer (LANGUAGES in openai/whisper whisper/tokenizer.py).
         * Cantonese ("yue") is left out: it only exists in the large-v3 tokenizer, which
         * Dictus does not ship. Javanese is stored as ISO "jv" instead of Whisper's legacy
         * "jw" so Locale can name it (display only, never sent to the engine).
         * Quality is very uneven across this list; per-language tiers are out of scope.
         */
        val WHISPER_MULTILINGUAL = ModelLanguages(
            LanguageCoverage.MULTILINGUAL,
            PRIORITY_CODES + listOf(
                "af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo", "br", "bs",
                "ca", "cs", "cy", "da", "el", "et", "eu", "fa", "fi", "fo", "gl", "gu",
                "ha", "haw", "he", "hi", "hr", "ht", "hu", "hy", "id", "is", "it", "ja",
                "jv", "ka", "kk", "km", "kn", "ko", "la", "lb", "ln", "lo", "lt", "lv",
                "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my", "ne", "nl", "nn",
                "no", "oc", "pa", "pl", "ps", "pt", "ro", "ru", "sa", "sd", "si", "sk",
                "sl", "sn", "so", "sq", "sr", "su", "sv", "sw", "ta", "te", "tg", "th",
                "tk", "tl", "tr", "tt", "uk", "ur", "uz", "vi", "yi", "yo", "zh",
            ),
        )
    }
}
