package dev.pivisolutions.dictus.model

import kotlin.math.ln

/**
 * The Precision and Speed bars of the model cards, derived from measurements (#180).
 *
 * WHY measured and not hand-set: the hand-set bars showed Parakeet 0.6B v3 at 2/5 speed while it
 * returns a 60 s take in under 10 s on the Pixel 4, as fast as Tiny. A rule over measured numbers
 * keeps the bars honest when a model or an engine changes: re-measure, replace [PIXEL_4], and the
 * bars follow.
 *
 * Source: Pixel 4 (6 GB, Snapdragon 855, LineageOS), app 1.4.0-135-g42ebc04, 2026-11-10. In-app
 * dictation (« Nouvelle dictée ») of AI-generated clips from getdictus/dictus-test-voices played
 * through the phone's speaker: a short take (~4 s of speech) and a long one (~57 s), in French and
 * in English, 3 runs each (6 for Parakeet 0.6B v3). Time = `Recording stopped` → `Dictation result
 * shown` minus the model load. WER = word edits / reference words after lowercasing and removing
 * punctuation. Table: #180; runs, clips, reference texts and scripts: pilotage/dictus-android/
 * proofs/180 (outside git). The thresholds below were written down before the first run.
 */
object ModelBars {

    enum class Clip { FR_SHORT, FR_LONG, EN_SHORT, EN_LONG }

    /**
     * One model on one device.
     *
     * @param medianSeconds  Median Stop → text over the runs, per clip that counts for the model.
     * @param wordErrors     Word edits summed over every run of the clips its precision is rated
     *                       on, or null when its precision is not rated from this measurement.
     * @param referenceWords Reference words summed over the same runs.
     */
    data class Measurement(val medianSeconds: Map<Clip, Double>, val wordErrors: Int?, val referenceWords: Int) {
        val wer: Double? get() = wordErrors?.let { it.toDouble() / referenceWords }
    }

    private const val ALL_CLIPS_WORDS = 1_257 // 3 runs x (14 + 196 + 15 + 196) reference words

    val PIXEL_4: Map<String, Measurement> = mapOf(
        "tiny" to Measurement(
            mapOf(Clip.FR_SHORT to 1.97, Clip.FR_LONG to 8.03, Clip.EN_SHORT to 1.94, Clip.EN_LONG to 5.63),
            wordErrors = 145, referenceWords = ALL_CLIPS_WORDS, // 11.5 %
        ),
        "base" to Measurement(
            mapOf(Clip.FR_SHORT to 4.70, Clip.FR_LONG to 19.89, Clip.EN_SHORT to 4.66, Clip.EN_LONG to 17.80),
            wordErrors = 53, referenceWords = ALL_CLIPS_WORDS, // 4.2 %
        ),
        "small-q5_1" to Measurement(
            mapOf(Clip.FR_SHORT to 12.69, Clip.FR_LONG to 51.21, Clip.EN_SHORT to 12.01, Clip.EN_LONG to 46.48),
            wordErrors = 35, referenceWords = ALL_CLIPS_WORDS, // 2.8 %
        ),
        "small" to Measurement(
            mapOf(Clip.FR_SHORT to 21.10, Clip.FR_LONG to 84.37, Clip.EN_SHORT to 21.47, Clip.EN_LONG to 79.14),
            wordErrors = 17, referenceWords = ALL_CLIPS_WORDS, // 1.4 %
        ),
        // Short takes only. Every long take hit the 120 s transcription timeout (at least 160 s
        // FR / 154 s EN, text cut), so their time and WER are not measurements of the model.
        "medium-q5_0" to Measurement(
            mapOf(Clip.FR_SHORT to 44.33, Clip.EN_SHORT to 42.61),
            wordErrors = 0, referenceWords = 87, // 0 %, 3 runs x (14 + 15) words
        ),
        // English only, the model's only language.
        "parakeet-ctc-110m-int8" to Measurement(
            mapOf(Clip.EN_SHORT to 0.29, Clip.EN_LONG to 3.29),
            wordErrors = 98, referenceWords = 627, // 15.6 %, 3 runs x (15 + 196) words
        ),
        // Precision not rated (#330): on the long French take the model drifts into English-like
        // words or returns nothing in 5 runs of 6, and is near-perfect on every other clip (0.3 %
        // WER on English). Its time does not depend on the drift.
        "parakeet-tdt-0.6b-v3" to Measurement(
            mapOf(Clip.FR_SHORT to 0.84, Clip.FR_LONG to 9.83, Clip.EN_SHORT to 0.81, Clip.EN_LONG to 9.62),
            wordErrors = null, referenceWords = 0,
        ),
    )

    /**
     * Speed segments, 1 to 5. Per clip, the model's median over the fastest model's median on that
     * same clip; the geometric mean of those ratios over the model's clips; then each segment down
     * doubles the wait: ≤ 1.5 → 5, ≤ 3 → 4, ≤ 6 → 3, ≤ 12 → 2, else 1.
     */
    fun speedSegments(key: String, measurements: Map<String, Measurement> = PIXEL_4): Int {
        val model = measurements.getValue(key)
        val ratios = model.medianSeconds.map { (clip, seconds) ->
            seconds / measurements.values.mapNotNull { it.medianSeconds[clip] }.min()
        }
        // Compared in log space: exp(ln 3) is 3.0000000000000004, which would miss the 3 boundary.
        val logRatio = ratios.sumOf { ln(it) } / ratios.size
        return when {
            logRatio <= ln(1.5) -> 5
            logRatio <= ln(3.0) -> 4
            logRatio <= ln(6.0) -> 3
            logRatio <= ln(12.0) -> 2
            else -> 1
        }
    }

    /**
     * Precision segments, 1 to 5, from the pooled WER (all word errors over all reference words, so
     * a long take weighs by its length): ≤ 5 % → 5, ≤ 10 % → 4, ≤ 20 % → 3, ≤ 35 % → 2, else 1.
     * Null when the model's precision is not rated ([Measurement.wordErrors] null).
     */
    fun precisionSegments(key: String, measurements: Map<String, Measurement> = PIXEL_4): Int? {
        val wer = measurements.getValue(key).wer ?: return null
        return when {
            wer <= 0.05 -> 5
            wer <= 0.10 -> 4
            wer <= 0.20 -> 3
            wer <= 0.35 -> 2
            else -> 1
        }
    }

    /** Bar value for [ModelInfo.speed]: the card fills `value * 5` segments. */
    fun speed(key: String): Float = speedSegments(key) / SEGMENTS

    /** Bar value for [ModelInfo.precision], or null when not rated: the catalog keeps its own value. */
    fun precision(key: String): Float? = precisionSegments(key)?.let { it / SEGMENTS }

    private const val SEGMENTS = 5f
}
