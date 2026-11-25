package dev.pivisolutions.dictus.core.premium

import dev.pivisolutions.dictus.core.service.TranscriptionRetention

/**
 * Compile-time visibility flags for premium features, mirroring iOS `PremiumFlags`.
 *
 * WHY a compile-time constant rather than a runtime setting or a Gradle flavor:
 * no purchase flow exists on Android yet, so there is nothing a user could do to
 * unlock the feature. A single `const val` hides every entry point while keeping
 * the whole implementation compiled, unit-tested and ready to ship the day the
 * Pro tier goes live — no code is deleted, no branch has to be revived.
 *
 * WHY it lives in `core`: the entry points span the `app` module (Home, navigation,
 * DictationService) and, later, the `ime` module. One flag gating several modules
 * belongs in the shared module, not beside one of its consumers.
 *
 * Re-enable: flip the constant to `true` in the PR that ships the Pro tier.
 * On iOS the same feature is `ProFeature.history`, gated by `FeatureGate`.
 */
object PremiumFlags {

    /**
     * Controls whether transcription history is visible AND recorded.
     *
     * `false` = the app behaves as if the feature did not exist: no entry point on
     * Home, no route in the nav graph, and no transcription is ever written to the
     * Room database. Recording nothing matters as much as showing nothing — a
     * hidden screen that silently accumulates transcripts the user cannot read or
     * delete would be a privacy regression, not a hidden feature.
     */
    const val HISTORY_VISIBLE = false

    /**
     * Downgrades a caller's requested retention to what the current tier permits.
     *
     * Callers keep expressing the retention their context deserves (the IME still
     * refuses to persist from a password field, for instance); this only ever
     * removes durability, never grants it.
     */
    fun effectiveRetention(requested: TranscriptionRetention): TranscriptionRetention =
        if (HISTORY_VISIBLE) requested else TranscriptionRetention.EPHEMERAL
}
