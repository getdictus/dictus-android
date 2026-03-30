---
phase: 05-polish-differentiators
plan: 05
subsystem: ui
tags: [jetpack-compose, settings, datastore, navigation, haptics, keyboard-ime]

# Dependency graph
requires:
  - phase: 05-02
    provides: DictusImeEntryPoint DataStore access for IME preferences

provides:
  - SettingsScreen with card-style rounded section grouping (iOS visual parity)
  - ABC/123 keyboard mode picker persisted to DataStore KEYBOARD_MODE key
  - DictusImeService reads KEYBOARD_MODE to set initial keyboard layer
  - Haptics toggle wired end-to-end: setting disables key vibration in IME
  - DebugLogsScreen with scrollable log viewer, Clear and Copy actions
  - AppDestination.DebugLogs route and navigation from Settings > A propos

affects: [06-design-system-material-you, future-settings-enhancements]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - SettingsCard composable wraps section rows with RoundedCornerShape(12.dp) + Surface background
    - hapticsEnabled propagated as parameter through KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton
    - IME reads multiple DataStore keys (THEME, KEYBOARD_MODE, HAPTICS_ENABLED) via collectAsState in KeyboardContent

key-files:
  created:
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/DebugLogsScreen.kt
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyRow.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt

key-decisions:
  - "remember(initialLayer) used in KeyboardScreen for currentLayer — ensures recomposition resets layer when KEYBOARD_MODE preference changes"
  - "hapticsEnabled propagated as parameter (not via CompositionLocal) to match existing KeyboardView/KeyRow/KeyButton parameter style"
  - "DebugLogsScreen reads log file once via LaunchedEffect(Unit) — no live-tail; full refresh on re-navigation is sufficient for debugging"
  - "DropdownMenu placed inside a Box at the overflow IconButton position for correct popup anchoring"

patterns-established:
  - "SettingsCard pattern: each section's rows wrapped in clip(RoundedCornerShape) + background(Surface) Column"
  - "IME DataStore reads: all prefs collected via entryPoint.dataStore().data.map{}.collectAsState() in KeyboardContent composable"

requirements-completed: [DICT-06]

# Metrics
duration: 20min
completed: 2026-03-28
---

# Phase 05 Plan 05: Settings Card Grouping + Debug Logs + Haptic Wiring Summary

**Settings screen redesigned with iOS-style rounded card sections, ABC/123 keyboard mode picker persisted to DataStore and read by the IME, haptics toggle wired end-to-end, and new DebugLogsScreen with Clear/Copy actions navigated from Settings.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-28T07:20:00Z
- **Completed:** 2026-03-28T07:40:00Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Redesigned SettingsScreen: each of 4 sections (TRANSCRIPTION, CLAVIER, APPARENCE, A PROPOS) wrapped in SettingsCard composable with RoundedCornerShape(12.dp) and Surface background — matches iOS grouped settings visual
- ABC/123 keyboard mode picker added to CLAVIER section; choice persisted to KEYBOARD_MODE DataStore key; DictusImeService reads this key and passes `initialLayer` (LETTERS or NUMBERS) to KeyboardScreen so the keyboard opens at the right layer
- Haptics toggle wired end-to-end: HAPTICS_ENABLED read in DictusImeService, passed as `hapticsEnabled` through KeyboardScreen → KeyboardView → KeyRow → KeyButton, where `HapticHelper.performKeyHaptic()` is guarded by an `if (hapticsEnabled)` check
- DebugLogsScreen created: LazyColumn renders log lines in 11sp monospace; 3-dot overflow menu has "Effacer" (clears file + list) and "Copier" (copies to clipboard) actions; shows "Aucun log disponible" when empty
- AppDestination.DebugLogs route added; navigation wired from Settings "Debug Logs" row; bottom nav hidden on DebugLogs screen

## Task Commits

1. **Task 1: Settings card grouping + ABC/123 picker + KEYBOARD_MODE preference** — `ccdb253` (feat)
2. **Task 2: Debug Logs viewer screen + navigation wiring** — `3d225f8` (feat)

## Files Created/Modified

- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/DebugLogsScreen.kt` — Full-screen debug log viewer (new)
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt` — Redesigned with SettingsCard groups, ABC/123 picker, onNavigateToDebugLogs
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt` — Added keyboardMode StateFlow and setKeyboardMode()
- `app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt` — Added DebugLogs destination
- `app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt` — Added DebugLogs route, wired SettingsScreen callback, updated showBottomBar condition
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — Added KEYBOARD_MODE key
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` — Reads KEYBOARD_MODE and HAPTICS_ENABLED from DataStore; passes initialLayer + hapticsEnabled to KeyboardScreen
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` — Added initialLayer + hapticsEnabled params; passes hapticsEnabled to KeyboardView
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt` — Added hapticsEnabled param, passes to KeyRow
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyRow.kt` — Added hapticsEnabled param, passes to KeyButton
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt` — Added hapticsEnabled param; guards both DELETE repeat and normal gesture haptic calls

## Decisions Made

- `remember(initialLayer)` used for `currentLayer` in KeyboardScreen so recomposition (e.g. user changes mode while keyboard is open) resets the layer correctly.
- `hapticsEnabled` passed as a parameter down the composable chain rather than via `CompositionLocal` — consistent with the existing param-passing style of KeyboardView/KeyRow/KeyButton.
- DebugLogsScreen reads the log file once on composition entry (`LaunchedEffect(Unit)`) rather than implementing live-tail — sufficient for debugging purposes and avoids complexity.
- `DropdownMenu` anchored inside a `Box` at the overflow `IconButton` to ensure correct popup positioning in the top bar.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- Pre-existing test failure discovered: `OnboardingViewModelTest > advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING to DataStore` — this is a regression from Phase 04 adding the 7th onboarding step without updating the test. Logged in `deferred-items.md`. Not caused by this plan's changes.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Settings screen is now at iOS visual parity with card groupings
- ABC/123 mode preference wired end-to-end (DataStore → IME initial layer)
- Haptics toggle end-to-end verified via code path analysis
- Debug Logs viewer available for in-app log inspection
- Phase 05 all plans complete; ready for Phase 06 Design System / Material You

---
*Phase: 05-polish-differentiators*
*Completed: 2026-03-28*
