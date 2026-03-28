---
phase: 05-polish-differentiators
plan: 02
subsystem: ui
tags: [compose, theme, material3, light-mode, dark-mode, bottom-nav, model-card, datastore]

# Dependency graph
requires:
  - phase: 04-model-management-onboarding
    provides: DictusTheme, DictusColors, ModelCard, DictusBottomNavBar, DataStore, PreferenceKeys
provides:
  - ThemeMode enum (DARK, LIGHT, AUTO) in core/theme/DictusTheme.kt
  - Light color palette tokens in DictusColors.kt (LightBackground=#F2F2F7, LightSurface=#FFFFFF, etc.)
  - DictusTheme(themeMode) composable accepting ThemeMode parameter
  - Theme wired end-to-end: DataStore -> MainActivity -> DictusTheme and DataStore -> DictusImeService -> KeyboardScreen
  - Settings picker with 3 options: Sombre, Clair, Automatique
  - Bottom nav with Memory icon for Modeles and filled pill active indicator
  - ModelCard redesigned with side-by-side layout and WK provider badge
affects: [05-03, 05-04, ime, app, settings, model-management]

# Tech tracking
tech-stack:
  added: ["datastore.preferences explicit dependency in ime module"]
  patterns:
    - "ThemeMode enum + when() mapping string key -> ThemeMode at reading site"
    - "DictusTheme(themeMode) composable wraps content, themeMode defaults to DARK for backward compat"
    - "DataStore theme read in KeyboardContent() composable via entryPoint.dataStore()"

key-files:
  created:
    - core/src/test/java/dev/pivisolutions/dictus/core/theme/DictusThemeTest.kt
  modified:
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/navigation/DictusBottomNavBar.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/di/DictusImeEntryPoint.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/build.gradle.kts

key-decisions:
  - "DictusTheme defaults themeMode=DARK for backward compatibility — no breaking changes to existing call sites"
  - "Theme string->ThemeMode mapping done at reading site (MainActivity, DictusImeService) not in ViewModel — keeps core ThemeMode pure"
  - "DictusImeEntryPoint adds dataStore() method — DictusImeService can now read any DataStore preference without app module dependency"
  - "ime module adds explicit datastore.preferences dependency — transitive from core was insufficient for compile-time API access"
  - "ModelCard description font size changed 14sp -> 13sp for side-by-side layout (tighter space on RIGHT column)"
  - "Bottom nav active indicator: replaced 8dp dot + Spacer with filled translucent pill behind icon+label Box"

patterns-established:
  - "IME theme reading: collectAsState on entryPoint.dataStore().data.map in KeyboardContent()"
  - "ThemeMode mapping: string key from DataStore mapped via when() to ThemeMode enum at each reading site"
  - "Light palette naming: LightBackground, LightSurface, LightOnBackground etc. — mirrors iOS token naming convention"

requirements-completed: [DICT-05]

# Metrics
duration: 25min
completed: 2026-03-28
---

# Phase 05 Plan 02: Light/Auto Theme, Bottom Nav Polish, Model Card Redesign Summary

**ThemeMode enum (DARK/LIGHT/AUTO) with light palette, end-to-end theme wiring to MainActivity and DictusImeService, bottom nav Memory icon + pill indicator, and iOS-parity model card with side-by-side segmented layout**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-28
- **Completed:** 2026-03-28
- **Tasks:** 2
- **Files modified:** 10 (including 1 created)

## Accomplishments

- Added ThemeMode enum and light color scheme to DictusTheme — app can now switch between dark, light, and system-auto modes
- Wired theme preference end-to-end: DataStore THEME key -> ThemeMode -> DictusTheme in both MainActivity and DictusImeService (IME keyboard respects theme too)
- Bottom nav polished: Memory (chip) icon for Modeles, filled translucent pill behind active tab instead of small dot
- ModelCard redesigned to iOS segmented style: description LEFT, precision/vitesse bars RIGHT in a Row, plus "WK" provider badge

## Task Commits

1. **Task 1: Light theme colors, ThemeMode enum, bottom nav polish, model card redesign** - `74f00b4` (feat)
2. **Task 2: Wire theme to MainActivity, DictusImeService, Settings picker** - `48d881b` (feat)

## Files Created/Modified

- `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt` - Added 8 light palette colors (LightBackground=#F2F2F7, LightSurface=#FFFFFF, etc.)
- `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt` - Added ThemeMode enum, DictusLightColorScheme, themeMode parameter on DictusTheme()
- `core/src/test/java/dev/pivisolutions/dictus/core/theme/DictusThemeTest.kt` - Created: ThemeMode enum tests and light/dark color token verification tests
- `app/src/main/java/dev/pivisolutions/dictus/ui/navigation/DictusBottomNavBar.kt` - Memory icon for Modeles, filled pill indicator, muted white unselected tint
- `app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt` - Redesigned layout (description + bars side by side), WK provider badge
- `app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt` - Reads THEME from DataStore, maps to ThemeMode, passes to DictusTheme
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt` - Theme picker shows Sombre/Clair/Automatique options
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` - Reads THEME from DataStore, maps to ThemeMode, passes to KeyboardScreen
- `ime/src/main/java/dev/pivisolutions/dictus/ime/di/DictusImeEntryPoint.kt` - Added dataStore() method for IME DataStore access
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` - Added themeMode param, passes to DictusTheme
- `ime/build.gradle.kts` - Added explicit datastore.preferences dependency

## Decisions Made

- `DictusTheme` defaults `themeMode=DARK` for backward compatibility — existing call sites in tests/previews don't break
- Theme string-to-ThemeMode mapping done at each reading site (not in a shared utility) — keeps the enum decoupled from DataStore
- `DictusImeEntryPoint` adds `dataStore()` method — allows IME to read any preference without violating ime->app circular dependency rule
- `ime` module needed explicit `datastore.preferences` dependency; transitive from `:core` wasn't enough for compile-time access to `DataStore<Preferences>` type in entry point interface
- ModelCard description font dropped from 14sp to 13sp to fit comfortably in the narrower left column of the new side-by-side layout

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added explicit datastore.preferences dependency to ime/build.gradle.kts**
- **Found during:** Task 2 (DictusImeEntryPoint creation)
- **Issue:** `DataStore<Preferences>` type unresolved in `DictusImeEntryPoint` — `ime` module can use DataStore transitively at runtime but Kotlin compiler requires explicit dependency for compile-time type reference
- **Fix:** Added `implementation(libs.datastore.preferences)` to `ime/build.gradle.kts`
- **Files modified:** `ime/build.gradle.kts`
- **Verification:** `./gradlew assembleDebug` succeeds
- **Committed in:** `48d881b` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 - Blocking)
**Impact on plan:** Necessary fix for compile-time type resolution. No scope creep.

## Issues Encountered

- DictusThemeTest used `.value.toInt()` comparison which failed for ULong Color values — fixed to use `Color(0xFF...)` equality comparison, matching DictusColorsTest pattern.
- KeyboardScreen had evolved since plan was written (added emoji picker, suggestions) — added `themeMode` param as the last optional parameter to preserve backward compatibility with existing callers.
- Pre-existing test failures in `:app` module (ModelCatalogTest, AudioCaptureManagerTest, OnboardingViewModelTest) are out-of-scope — confirmed pre-existing by stash verification.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- ThemeMode infrastructure is complete. Phase 5 plans 03+ can use `DictusTheme(themeMode)` if needed.
- Settings screen correctly shows 3 theme options and persists the selection to DataStore.
- Both app and keyboard IME respond to theme changes at next compose.
- Pre-existing test failures in ModelCatalog/AudioCapture remain deferred (unrelated to this plan).

## Self-Check: PASSED

- All 10 modified/created files exist on disk
- Both task commits exist: 74f00b4 and 48d881b
- `./gradlew :core:testDebugUnitTest` passes (31 tests)
- `./gradlew assembleDebug` succeeds

---
*Phase: 05-polish-differentiators*
*Completed: 2026-03-28*
