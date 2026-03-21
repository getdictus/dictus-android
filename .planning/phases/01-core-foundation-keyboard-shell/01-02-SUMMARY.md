---
phase: 01-core-foundation-keyboard-shell
plan: 02
subsystem: ime
tags: [compose, inputmethodservice, lifecycle, azerty, qwerty, keyboard-layout, hilt-entrypoint]

requires:
  - phase: 01-core-foundation-keyboard-shell/01
    provides: "Project skeleton, Gradle modules, DictusTheme, DictusColors, string resources"
provides:
  - "LifecycleInputMethodService base class for Compose-in-IME"
  - "DictusImeService with placeholder UI and Hilt DI"
  - "Complete keyboard layout data (AZERTY, QWERTY, numbers, symbols)"
  - "AccentMap with French accented character variants"
  - "IME manifest declaration with BIND_INPUT_METHOD and subtypes"
affects: [01-core-foundation-keyboard-shell/03, 02-ime-state-text-engine]

tech-stack:
  added: []
  patterns: [compose-in-ime-lifecycle-wiring, hilt-entrypoint-for-service, keyboard-data-model]

key-files:
  created:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/LifecycleInputMethodService.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/di/DictusImeEntryPoint.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyType.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyDefinition.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyboardLayer.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyboardLayouts.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/model/AccentMap.kt
    - ime/src/main/res/xml/method.xml
    - ime/src/test/java/dev/pivisolutions/dictus/ime/model/KeyboardLayoutTest.kt
    - ime/src/test/java/dev/pivisolutions/dictus/ime/model/AccentMapTest.kt
  modified:
    - ime/src/main/AndroidManifest.xml

key-decisions:
  - "Used full package name in manifest android:name to avoid merger ambiguity"
  - "Used Unicode escapes for special key labels (shift, delete, return, emoji) for cross-platform source compatibility"

patterns-established:
  - "Compose-in-IME: LifecycleInputMethodService wires LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner on decorView"
  - "Hilt in Service: Use @EntryPoint + EntryPointAccessors.fromApplication instead of @AndroidEntryPoint"
  - "Keyboard data: Layouts as List<List<KeyDefinition>> with KeyType enum for behavior dispatch"

requirements-completed: [KBD-01, KBD-02, KBD-03]

duration: 2min
completed: 2026-03-21
---

# Phase 01 Plan 02: IME Service + Keyboard Data Summary

**LifecycleInputMethodService with Compose lifecycle wiring, AZERTY/QWERTY/numbers/symbols layout data, and French accent map**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-21T14:00:11Z
- **Completed:** 2026-03-21T14:02:59Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- LifecycleInputMethodService solves the Compose-in-IME lifecycle problem by manually implementing LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
- DictusImeService renders a placeholder keyboard view with Hilt DI via EntryPointAccessors
- Complete keyboard layout data for 4 layers (AZERTY letters, QWERTY letters, numbers, symbols) with correct key sequences
- AccentMap provides French accented character variants for long-press selection
- 16 unit tests verify all layout structures and accent data

## Task Commits

Each task was committed atomically:

1. **Task 1: Create LifecycleInputMethodService, DictusImeService, Hilt entry point, and manifest** - `718e731` (feat)
2. **Task 2: Create keyboard data models (layouts, accent map) and unit tests** - `336dc14` (feat)

## Files Created/Modified
- `ime/src/main/java/.../LifecycleInputMethodService.kt` - Abstract base class wiring Compose lifecycle owners on IME decorView
- `ime/src/main/java/.../DictusImeService.kt` - Main IME service with placeholder UI and input connection methods
- `ime/src/main/java/.../di/DictusImeEntryPoint.kt` - Hilt entry point for Service DI
- `ime/src/main/java/.../model/KeyType.kt` - Enum of key types (CHARACTER, SHIFT, DELETE, etc.)
- `ime/src/main/java/.../model/KeyDefinition.kt` - Data class for key definition (label, output, type, width)
- `ime/src/main/java/.../model/KeyboardLayer.kt` - Enum of keyboard layers (LETTERS, NUMBERS, SYMBOLS)
- `ime/src/main/java/.../model/KeyboardLayouts.kt` - Complete AZERTY, QWERTY, numbers, symbols layout data
- `ime/src/main/java/.../model/AccentMap.kt` - French accented character variants for long-press
- `ime/src/main/AndroidManifest.xml` - IME service declaration with BIND_INPUT_METHOD permission
- `ime/src/main/res/xml/method.xml` - Input method subtypes (fr_FR AZERTY, en_US QWERTY)
- `ime/src/test/.../model/KeyboardLayoutTest.kt` - 14 tests for layout correctness
- `ime/src/test/.../model/AccentMapTest.kt` - 8 tests for accent map correctness

## Decisions Made
- Used fully-qualified class name (`dev.pivisolutions.dictus.ime.DictusImeService`) in manifest `android:name` to avoid merger ambiguity with app namespace
- Used Unicode escape sequences for special key labels (shift, delete, return, emoji) to ensure source file compatibility across editors

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- LifecycleInputMethodService and keyboard data models are ready for Plan 03 (keyboard UI rendering)
- DictusImeService.KeyboardContent() is a placeholder that Plan 03 will replace with the full keyboard Compose UI
- All layout data (AZERTY, QWERTY, numbers, symbols) and accent map are available for the UI layer

---
*Phase: 01-core-foundation-keyboard-shell*
*Completed: 2026-03-21*

## Self-Check: PASSED

All 12 files verified present. Both task commits (718e731, 336dc14) confirmed in git log.
