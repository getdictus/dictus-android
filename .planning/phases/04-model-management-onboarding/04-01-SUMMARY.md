---
phase: 04-model-management-onboarding
plan: 01
subsystem: ui
tags: [kotlin, compose, typography, navigation, datastore, design-system, model-management]

# Dependency graph
requires:
  - phase: 03-whisper-integration
    provides: ModelManager with tiny + small-q5_1 models, DictationService, whisper.cpp

provides:
  - DM Sans brand font (5 weights) bundled in core/res/font/
  - DictusTypography with DmSans FontFamily replacing Roboto
  - DictusColors extended with 9 new UI-SPEC tokens (AccentDark, SuccessDark, TextPrimary, etc.)
  - GlassCard composable in core/ui for glass-morphism card pattern
  - SkeletonCard composable in core/ui for shimmer loading placeholders
  - PreferenceKeys extended with HAS_COMPLETED_ONBOARDING, HAPTICS_ENABLED, SOUND_ENABLED
  - AppDestination sealed class with Onboarding, Main, Home, Models, Settings routes
  - ModelManager refactored with AiProvider enum and 4-model ModelCatalog
  - ModelCatalog.canDelete() guard preventing deletion of last model
  - ModelManager.storageUsedBytes(), getDownloadedModels(), deleteModel() methods
  - navigation-compose 2.8.9 dependency added
  - 27 unit tests (ModelCatalogTest x10, ModelManagerTest x17), all green

affects: [04-02-onboarding-screens, 04-03-model-management-ui, 04-04-settings-navigation]

# Tech tracking
tech-stack:
  added:
    - androidx.navigation:navigation-compose:2.8.9
    - com.squareup.okhttp3:mockwebserver:4.12.0 (test)
    - DM Sans font family (Google Fonts, static TTF, 5 weights)
  patterns:
    - Sealed class AppDestination for type-safe navigation routes
    - ModelCatalog object as single source of truth for model metadata
    - AiProvider enum for exhaustive download URL routing
    - GlassCard as shared composable wrapping Column with clip/background/border/padding
    - InfiniteTransition shimmer for SkeletonCard loading placeholders

key-files:
  created:
    - core/src/main/res/font/dm_sans_extralight.ttf
    - core/src/main/res/font/dm_sans_regular.ttf
    - core/src/main/res/font/dm_sans_medium.ttf
    - core/src/main/res/font/dm_sans_semibold.ttf
    - core/src/main/res/font/dm_sans_bold.ttf
    - core/src/main/java/dev/pivisolutions/dictus/core/ui/GlassCard.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/ui/SkeletonCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt
    - app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTypography.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt
    - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    - app/src/test/java/dev/pivisolutions/dictus/model/ModelManagerTest.kt

key-decisions:
  - "ModelCatalog extracted as top-level object (not companion) so tests can access catalog logic without Android context"
  - "ModelInfo data class fully rewritten with key + displayName + qualityLabel + provider — old nested class removed"
  - "GlassCard uses Column not Box to expose ColumnScope for direct content composition"
  - "SkeletonCard shimmer uses animateFloat 0..1 mapped to x-offset range for smooth linear sweep"
  - "AppDestination uses sealed class not enum to support future data class variants with route params"
  - "Pre-existing AudioCaptureManagerTest failures (2 tests) deferred — out of scope for Phase 4"

patterns-established:
  - "GlassCard pattern: clip(16dp) + background(Surface) + border(1dp, GlassBorder) + padding(20dp)"
  - "ModelCatalog is single source of truth — ModelManager delegates all lookups to ModelCatalog.findByKey"
  - "canDelete guard: downloaded.size > 1 && key in downloaded — prevents zero-model state"

requirements-completed: [APP-02, APP-03, APP-04]

# Metrics
duration: 6min
completed: 2026-03-26
---

# Phase 04 Plan 01: Foundation Layer Summary

**DM Sans typography, GlassCard/SkeletonCard design components, 4-model ModelCatalog with AiProvider abstraction, canDelete guard, and Navigation Compose routes — all Phase 4 screens now have a buildable foundation**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-26T17:31:31Z
- **Completed:** 2026-03-26T17:37:40Z
- **Tasks:** 3 completed
- **Files modified:** 14 files (5 font TTFs + 9 code files)

## Accomplishments
- DM Sans bundled in 5 weights; DictusTypography fully rewritten with DmSans FontFamily and UI-SPEC scale
- 9 new DictusColors tokens (AccentDark, SuccessDark, SuccessSubtle, TextPrimary, TextSecondary, BorderSubtle, GlassBorder, InactiveDot, IconBackground)
- GlassCard and SkeletonCard composables ready for all Phase 4 screens
- PreferenceKeys extended with 3 new boolean keys for Phase 4 settings
- AppDestination sealed class defines all navigation routes (Onboarding, Main, Home, Models, Settings)
- ModelManager refactored with AiProvider enum, 4-model catalog (tiny/base/small/small-q5_1), canDelete guard, deleteModel, storageUsedBytes, getDownloadedModels
- 27 unit tests added (ModelCatalogTest x10, ModelManagerTest x17), all green; build passes

## Task Commits

Each task was committed atomically:

1. **Task 1: Font files + Gradle dependencies setup** - `e65f040` (chore)
2. **Task 2: Theme updates, GlassCard, PreferenceKeys, navigation routes** - `42a7a63` (feat)
3. **Task 3: ModelManager refactor with provider abstraction, 4-model catalog, and tests** - `0b621f3` (feat)

## Files Created/Modified
- `gradle/libs.versions.toml` - Added navigation = "2.8.9", navigation-compose, okhttp-mockwebserver libraries
- `app/build.gradle.kts` - Added navigation-compose and okhttp-mockwebserver dependencies
- `core/src/main/res/font/dm_sans_*.ttf` - 5 DM Sans weight variants (ExtraLight/Regular/Medium/SemiBold/Bold)
- `core/.../theme/DictusTypography.kt` - Full rewrite: DmSans FontFamily, 9-style Typography scale
- `core/.../theme/DictusColors.kt` - Added 9 new color tokens from UI-SPEC
- `core/.../ui/GlassCard.kt` - Glass-morphism card composable (new file)
- `core/.../ui/SkeletonCard.kt` - Shimmer loading placeholder composable (new file)
- `core/.../preferences/PreferenceKeys.kt` - Added HAS_COMPLETED_ONBOARDING, HAPTICS_ENABLED, SOUND_ENABLED
- `app/.../navigation/AppDestination.kt` - Sealed class with 5 route destinations (new file)
- `app/.../model/ModelManager.kt` - Refactored with AiProvider, ModelInfo, ModelCatalog, canDelete/deleteModel/storageUsedBytes
- `app/.../model/ModelCatalogTest.kt` - 10 pure-Kotlin ModelCatalog unit tests (new file)
- `app/.../model/ModelManagerTest.kt` - Rewritten with 17 tests covering all new ModelManager behavior

## Decisions Made
- ModelCatalog extracted as top-level object so tests can reference catalog logic without Android context
- ModelInfo data class fully rewritten; old nested `data class ModelInfo(val fileName: String, val expectedSize: Long)` removed; all fields now explicit
- GlassCard uses Column (not Box) to expose ColumnScope for direct trailing lambda composition
- AppDestination uses sealed class instead of enum to allow future `data class` variants with route parameters

## Deviations from Plan

None — plan executed exactly as written.

**Pre-existing failures (out of scope):** 2 AudioCaptureManagerTest tests were already failing before Plan 01 execution. Logged in deferred-items.md. Not caused by any Plan 01 changes.

## Issues Encountered
- Google Fonts ZIP download via `fonts.google.com/download` URL returned an HTML page, not a ZIP. Resolved by querying the Google Fonts CSS API to extract individual TTF file URLs from `fonts.gstatic.com`.

## Next Phase Readiness
- All foundation types (colors, typography, fonts, cards, preferences, navigation routes) are in place
- ModelCatalog and ModelManager with 4 models ready for download UI in Plan 02/03
- Plan 02 (onboarding screens) and Plan 03 (model management UI) can proceed independently in Wave 2

---
*Phase: 04-model-management-onboarding*
*Completed: 2026-03-26*
