---
phase: 01-core-foundation-keyboard-shell
plan: 03
subsystem: ui
tags: [compose, ime, keyboard, azerty, qwerty, accents, inputconnection]

# Dependency graph
requires:
  - phase: 01-core-foundation-keyboard-shell/01-02
    provides: DictusImeService with Compose lifecycle, KeyDefinition, KeyboardLayouts, AccentMap
  - phase: 01-core-foundation-keyboard-shell/01-01
    provides: DictusTheme, DictusColors, multi-module Gradle project
provides:
  - Complete Compose keyboard UI with AZERTY/QWERTY layouts
  - Key interactions (tap, shift, caps lock, layer switching, delete repeat)
  - Long-press accent popup with accent selection
  - MicButtonRow placeholder for Phase 2 mic integration
  - InputConnection text insertion via DictusImeService callbacks
affects: [02, 05]

# Tech tracking
tech-stack:
  added: []
  patterns: [compose-keyboard-ui, pointer-input-gestures, accent-popup, key-repeat-coroutine, rememberUpdatedState-for-callbacks]

key-files:
  created:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyRow.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/AccentPopup.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/MicButtonRow.kt
  modified:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt

key-decisions:
  - "Used rememberUpdatedState for KeyButton callbacks to avoid stale closure bugs with layer switching"
  - "Set AccentPopup focusable=false to prevent stealing IME focus and dismissing the keyboard"

patterns-established:
  - "Keyboard composable hierarchy: KeyboardScreen > KeyboardView > KeyRow > KeyButton"
  - "Use rememberUpdatedState for callbacks passed to pointerInput to avoid stale closures"
  - "Popup with focusable=false inside IME to prevent focus theft"
  - "Delete key repeat via coroutine delay loop in pointerInput"

requirements-completed: [KBD-06]

# Metrics
duration: ~22h (spread across two sessions with checkpoint)
completed: 2026-03-22
---

# Phase 1 Plan 03: Keyboard UI Summary

**Full Compose keyboard UI with AZERTY/QWERTY letter entry, shift/caps lock, layer switching, long-press accent popup, delete repeat, and MicButtonRow placeholder -- all wired to InputConnection**

## Performance

- **Started:** 2026-03-21T15:08:25Z
- **Completed:** 2026-03-22T13:26:49Z
- **Tasks:** 2 (1 auto + 1 checkpoint verification)
- **Files created:** 6
- **Files modified:** 1

## Accomplishments
- Complete keyboard UI renders inside IME with DictusTheme dark colors
- All key interactions work: letter entry, shift/caps, layer switching (letters/numbers/symbols), delete with repeat, accent popup on long-press
- Text insertion verified end-to-end on emulator via InputConnection
- MicButtonRow placeholder ready for Phase 2 mic integration

## Task Commits

Each task was committed atomically:

1. **Task 1: Build keyboard UI composables and wire to InputConnection** - `eb72391` (feat)
2. **Task 2: Verify keyboard works end-to-end on emulator** - `6024c34` (fix -- 2 bug fixes found during verification)

## Files Created/Modified
- `ime/.../ui/KeyboardScreen.kt` - Root composable managing keyboard state (layer, shift, caps, accent popup)
- `ime/.../ui/KeyboardView.kt` - Renders correct key rows for current layer (letters/numbers/symbols)
- `ime/.../ui/KeyRow.kt` - Horizontal row of KeyButton composables with weight-based sizing
- `ime/.../ui/KeyButton.kt` - Individual key with tap/long-press gestures, visual styling per key type
- `ime/.../ui/AccentPopup.kt` - Long-press popup showing accented character variants
- `ime/.../ui/MicButtonRow.kt` - Placeholder row with mic icon and keyboard switcher
- `ime/.../DictusImeService.kt` - Updated to use KeyboardScreen instead of placeholder Box

## Decisions Made
- **rememberUpdatedState for callbacks:** KeyButton's pointerInput captures callback lambdas at creation time. Without rememberUpdatedState, layer switching and shift toggling would use stale closures, causing keys to stop responding after state changes.
- **AccentPopup focusable=false:** Inside an IME, a focusable Popup steals focus from the InputMethodService, causing the entire keyboard to dismiss. Setting focusable=false prevents this while still allowing accent selection.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AccentPopup stealing IME focus**
- **Found during:** Task 2 (checkpoint verification on emulator)
- **Issue:** Long-pressing a key to show the accent popup caused the entire keyboard to dismiss because the Popup was focusable by default, stealing focus from the InputMethodService
- **Fix:** Set `focusable = false` on the PopupProperties in AccentPopup.kt
- **Files modified:** ime/.../ui/AccentPopup.kt
- **Verification:** Accent popup displays without dismissing keyboard
- **Committed in:** 6024c34

**2. [Rule 1 - Bug] Stale callbacks in KeyButton pointerInput**
- **Found during:** Task 2 (checkpoint verification on emulator)
- **Issue:** After switching layers (e.g., tapping ?123 then ABC), key presses stopped working because pointerInput's onTap/onLongPress closures captured the original callback references
- **Fix:** Used `rememberUpdatedState` for onPress and onLongPress callbacks so pointerInput always invokes the latest reference
- **Files modified:** ime/.../ui/KeyButton.kt
- **Verification:** Layer switching and shift toggling work correctly across all transitions
- **Committed in:** 6024c34

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both bugs were IME-specific runtime issues not catchable at compile time. Fixes are minimal and targeted. No scope creep.

## Issues Encountered
- Both bugs above were only discoverable at runtime on an actual emulator/device, not during compilation. This validates the plan's inclusion of a human-verify checkpoint.

## User Setup Required
None - keyboard is installed via `./gradlew installDebug` and enabled in system settings.

## Next Phase Readiness
- Phase 1 is now complete: full keyboard shell with text insertion working end-to-end
- MicButtonRow is ready to receive Phase 2 mic integration (currently logs placeholder message)
- DictusImeService exposes clean callback interface for future audio recording service binding

## Self-Check: PASSED

All 7 key files verified present. Both task commits (eb72391, 6024c34) verified in git log.

---
*Phase: 01-core-foundation-keyboard-shell*
*Completed: 2026-03-22*
