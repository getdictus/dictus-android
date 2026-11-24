package dev.pivisolutions.dictus.core.premium

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
 * History is no longer a flag since #202: it follows the Pro entitlement (`HistoryAvailability`).
 */
object PremiumFlags {

    /**
     * Whether Dictus Pro may be sold and promoted, iOS `PremiumFlags.paywallVisible` (#207).
     *
     * `false` = the app looks like there is no subscription at all: no Home Pro card, no
     * « Dictus Pro » row in Settings. Nothing is sold on Android yet (no paywall, no purchase
     * channel), so it stays down until Play Billing (#5) ships. As on iOS, the Debug override
     * of [ProStatus] still opens the hub from Settings, and never the Home card.
     */
    const val PAYWALL_VISIBLE = false

    /**
     * Controls whether custom vocabulary (#203) rewrites transcripts.
     *
     * `false` = `CustomVocabulary` returns every transcript unchanged without reading the
     * store, and `VocabularyStore.add` refuses. Removal stays possible either way. Whether
     * vocabulary ends up free or behind the Pro tier is decided when it ships.
     */
    const val VOCABULARY = false
}
