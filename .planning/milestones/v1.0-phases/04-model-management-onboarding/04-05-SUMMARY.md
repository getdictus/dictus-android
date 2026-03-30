---
phase: 04-model-management-onboarding
plan: 05
subsystem: ui
tags: [settings, preferences, datastore, jetpack-compose, kotlin]

# Dependency graph
requires:
  - phase: 04-model-management-onboarding/04-04
    provides: SettingsViewModel with language/model/haptics/sound/keyboard StateFlows, SettingsScreen with TRANSCRIPTION and CLAVIER sections
provides:
  - PreferenceKeys.THEME stringPreferencesKey
  - SettingsViewModel.theme StateFlow + setTheme()
  - SettingsScreen APPARENCE section with theme picker row
  - APP-04 fully satisfied (all 6 configurable items present)
affects: [phase-06-dsg-02, material-you-dynamic-colors]

# Tech tracking
tech-stack:
  added: []
  patterns: ["SettingsViewModel StateFlow pattern extended for String theme preference with default \"dark\""]

key-files:
  created: []
  modified:
    - core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt

key-decisions:
  - "Phase 4 theme picker has single option (dark/Sombre); Phase 6 DSG-02 adds system/Automatique (Systeme) when Material You is implemented"
  - "Theme default is \"dark\" — no light theme exists in this project phase"

patterns-established:
  - "PreferenceKeys grouped by phase with comment headers"
  - "SettingsViewModel StateFlow + setter pair for each preference, using SharingStarted.Eagerly"
  - "SettingsScreen PickerBottomSheet pattern for single-select preferences with future extensibility"

requirements-completed: [APP-04]

# Metrics
duration: 5min
completed: 2026-03-26
---

# Phase 04 Plan 05: APP-04 Gap Closure — Theme Setting Summary

**Added THEME preference key to DataStore, theme StateFlow + setTheme() to SettingsViewModel, and APPARENCE section with picker to SettingsScreen — APP-04 fully satisfied with all 6 configurable items**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-26T21:25:00Z
- **Completed:** 2026-03-26T21:30:29Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- PreferenceKeys.THEME declared as `stringPreferencesKey("theme")` under Appearance settings group
- SettingsViewModel.theme StateFlow reads from DataStore (default "dark"), setTheme() writes back
- SettingsScreen gains 4th section APPARENCE (between CLAVIER and A PROPOS) with a "Thème" picker row
- Theme picker bottom sheet opens on tap with single "Sombre" option; Phase 6 will add "Automatique (Système)"
- APP-04 requirement closed: active model, language, keyboard layout, haptics, sound, and theme all configurable

## Task Commits

Each task was committed atomically:

1. **Task 1: Add THEME preference key and SettingsViewModel state** - `4af1dd9` (feat)
2. **Task 2: Add APPARENCE section with theme picker to SettingsScreen** - `37697ab` (feat)

## Files Created/Modified
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` - Added THEME stringPreferencesKey under Appearance settings group
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt` - Added theme StateFlow + setTheme() following existing pattern
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt` - Added theme state collection, showThemePicker state, APPARENCE section, theme PickerBottomSheet

## Decisions Made
- Phase 4 theme picker intentionally has a single option ("dark"/"Sombre"). The PickerBottomSheet infrastructure is in place; Phase 6 DSG-02 adds the "system"/"Automatique (Système)" option when Material You dynamic colors are implemented.
- Theme default is "dark" since no light theme variant exists in this project phase.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- APP-04 gap is closed; Phase 4 now has all 5/5 success criteria satisfied
- Phase 6 DSG-02 can extend the theme picker with "system" option without changing existing structure
- All 3 files compile without errors

---
*Phase: 04-model-management-onboarding*
*Completed: 2026-03-26*
