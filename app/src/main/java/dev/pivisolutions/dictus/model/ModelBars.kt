package dev.pivisolutions.dictus.model

import kotlin.math.ln

/**
 * The Speed bar of the model cards, derived from measurements (#180).
 *
 * WHY measured and not hand-set: the hand-set bar showed Parakeet 0.6B v3 at 2/5 speed while it
 * returns a 60 s take in under 10 s on the Pixel 4, as fast as Tiny. A rule over measured numbers
 * keeps the bar honest when a model or an engine changes: re-measure, replace [PIXEL_4], and the
 * bars follow.
 *
 * WHY speed only: the same run gave word error rates on AI-generated voices, where Base, Small and
 * Medium all came out near-perfect. Precision will be measured with Pierre's real voice; until
 * then the Precision bar keeps its hand-set value in [ModelCatalog].
 *
 * Source: Pixel 4 (6 GB, Snapdragon 855, LineageOS), app 1.4.0-135-g42ebc04, 2026-11-10. In-app
 * dictation (« Nouvelle dictée ») of AI-generated clips from getdictus/dictus-test-voices played
 * through the phone's speaker: a short take (~4 s of speech) and a long one (~57 s), in French and
 * in English, 3 runs each (6 for Parakeet 0.6B v3). Time = `Recording stopped` → `Dictation result
 * shown` minus the model load. Table: #180; runs, clips and scripts: pilotage/dictus-android/
 * proofs/180 (outside git). The thresholds below were written down before the first run.
 */
object ModelBars {

    enum class Clip { FR_SHORT, FR_LONG, EN_SHORT, EN_LONG }

    /** Median Stop → text in seconds, per model and per clip that counts for it. */
    val PIXEL_4: Map<String, Map<Clip, Double>> = mapOf(
        "tiny" to mapOf(Clip.FR_SHORT to 1.97, Clip.FR_LONG to 8.03, Clip.EN_SHORT to 1.94, Clip.EN_LONG to 5.63),
        "base" to mapOf(Clip.FR_SHORT to 4.70, Clip.FR_LONG to 19.89, Clip.EN_SHORT to 4.66, Clip.EN_LONG to 17.80),
        "small-q5_1" to mapOf(Clip.FR_SHORT to 12.69, Clip.FR_LONG to 51.21, Clip.EN_SHORT to 12.01, Clip.EN_LONG to 46.48),
        "small" to mapOf(Clip.FR_SHORT to 21.10, Clip.FR_LONG to 84.37, Clip.EN_SHORT to 21.47, Clip.EN_LONG to 79.14),
        // Short takes only: every long take hit the 120 s transcription timeout (at least 160 s FR,
        // 154 s EN), so its time is not a measurement of the model.
        "medium-q5_0" to mapOf(Clip.FR_SHORT to 44.33, Clip.EN_SHORT to 42.61),
        // English only, the model's only language.
        "parakeet-ctc-110m-int8" to mapOf(Clip.EN_SHORT to 0.29, Clip.EN_LONG to 3.29),
        "parakeet-tdt-0.6b-v3" to mapOf(Clip.FR_SHORT to 0.84, Clip.FR_LONG to 9.83, Clip.EN_SHORT to 0.81, Clip.EN_LONG to 9.62),
    )

    /**
     * Speed segments, 1 to 5. Per clip, the model's median over the fastest model's median on that
     * same clip; the geometric mean of those ratios over the model's clips; then each segment down
     * doubles the wait: ≤ 1.5 → 5, ≤ 3 → 4, ≤ 6 → 3, ≤ 12 → 2, else 1.
     */
    fun speedSegments(key: String, medians: Map<String, Map<Clip, Double>> = PIXEL_4): Int {
        val ratios = medians.getValue(key).map { (clip, seconds) ->
            seconds / medians.values.mapNotNull { it[clip] }.min()
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

    /** Bar value for [ModelInfo.speed]: the card fills `value * 5` segments. */
    fun speed(key: String): Float = speedSegments(key) / SEGMENTS

    private const val SEGMENTS = 5f
}
