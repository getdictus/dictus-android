---
phase: 07-foundation
plan: "03"
subsystem: ime/keyboard
tags: [keyboard-layout, datastore, compose, azerty, qwerty, debt-fix]
dependency_graph:
  requires: [07-01]
  provides: [KEYBOARD_LAYOUT reactive propagation from Settings to IME]
  affects: [KeyboardScreen, DictusImeService, SettingsViewModel]
tech_stack:
  added: []
  patterns: [DataStore collectAsState, remember(key) recomposition trigger]
key_files:
  created: []
  modified:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
decisions:
  - "Use remember(keyboardLayout) to trigger recomposition when DataStore value changes — same pattern as remember(initialLayer) already used for KEYBOARD_MODE"
  - "Default value azerty in both parameter and collectAsState to preserve backward compatibility"
metrics:
  duration: "~10 min"
  completed: "2026-03-30"
  tasks_completed: 2
  files_modified: 2
requirements: [DEBT-01]
---

# Phase 7 Plan 03: Wire KEYBOARD_LAYOUT Settings Toggle to IME — Summary

**One-liner:** DataStore KEYBOARD_LAYOUT preference now flows reactively from Settings to KeyboardScreen via collectAsState in DictusImeService, so AZERTY/QWERTY toggle takes effect immediately without restarting the IME.

## What Was Built

DEBT-01 is resolved. The Settings screen already had a working toggle that wrote `KEYBOARD_LAYOUT` to DataStore, but the IME never read it — the keyboard was hardcoded to "azerty". This plan wired the missing connection.

**KeyboardScreen.kt:**
- Added `keyboardLayout: String = "azerty"` parameter to the composable signature
- Changed `var currentLayout by remember { mutableStateOf("azerty") }` to `var currentLayout by remember(keyboardLayout) { mutableStateOf(keyboardLayout) }`
- The `remember(keyboardLayout)` key ensures Compose reinitializes `currentLayout` whenever the DataStore preference changes — same recomposition pattern already used by `remember(initialLayer)` for KEYBOARD_MODE

**DictusImeService.kt:**
- Added DataStore observation for `PreferenceKeys.KEYBOARD_LAYOUT` via `collectAsState(initial = "azerty")` in the `KeyboardContent()` composable
- Follows the identical pattern used for THEME and KEYBOARD_MODE preferences already in that composable
- Passes `keyboardLayout = keyboardLayout` to the `KeyboardScreen(...)` call in the Idle branch

## Verification

- `./gradlew :ime:compileDebugKotlin :app:compileDebugKotlin` — PASSED
- `./gradlew :app:testDebugUnitTest :ime:testDebugUnitTest :core:testDebugUnitTest` — PASSED (91/91 debug tests)
- `./gradlew assembleDebug` — PASSED

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Accidentally deleted WhisperTranscriptionEngine.kt and TranscriptionEngine.kt**

- **Found during:** Task 2 verification (release compile failure)
- **Issue:** When staging task 1 files, `git status` showed these files as deleted in the working tree (they were removed from git tracking in a prior session but the files had been deleted from disk). The `git add` of modified files pulled in the deletions. The debug variant compiled fine because `DictationService.kt` had already been migrated to use `WhisperProvider/SttProvider` in 07-02, but the release variant had a stale cache that revealed the missing `TranscriptionEngine.kt`.
- **Fix:** Restored both files from git history (commit 0bad50b). Also staged the uncommitted `DictationService.kt` changes from 07-02 that were still in the working tree.
- **Files modified:** `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt` (restored), `app/src/main/java/dev/pivisolutions/dictus/service/WhisperTranscriptionEngine.kt` (restored), `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` (staged 07-02 changes)
- **Commits:** a25977e, 5cd53a2

**2. [Rule 3 - Blocking] KSP cache file lock from background Gradle daemon**

- **Found during:** Task 2 `assembleDebug` verification
- **Issue:** `IllegalStateException: Storage for kspCaches/debug/symbolLookups/file-to-id.tab is already registered` — a background Gradle daemon was holding the KSP file lock
- **Fix:** Ran `./gradlew --stop` to kill all daemons, then reran `assembleDebug` successfully
- **Files modified:** None

## Self-Check

Files exist:
- `ime/.../DictusImeService.kt` — FOUND (modified)
- `ime/.../ui/KeyboardScreen.kt` — FOUND (modified)
- `core/.../whisper/TranscriptionEngine.kt` — FOUND (restored)

Commits exist:
- 7fd2bc3 feat(07-03): wire KEYBOARD_LAYOUT DataStore preference — FOUND
- a25977e chore(07-03): restore TranscriptionEngine.kt — FOUND
- 5cd53a2 chore(07-03): stage DictationService.kt SttProvider migration — FOUND

## Self-Check: PASSED
