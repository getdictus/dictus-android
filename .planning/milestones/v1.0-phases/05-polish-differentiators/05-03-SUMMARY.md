---
phase: 05-polish-differentiators
plan: 03
subsystem: ui
tags: [android, compose, ime, emoji, androidx-emoji2, keyboard]

# Dependency graph
requires:
  - phase: 01-compose-in-ime
    provides: KeyboardScreen, KeyType.EMOJI, LifecycleInputMethodService, DictusImeService
provides:
  - EmojiPickerScreen composable wrapping EmojiPickerView via AndroidView
  - Emoji picker state hoisted to DictusImeService with toggle and back-key dismissal
  - Full KBD-05 emoji picker: categories, recent emojis, emoji insertion via InputConnection
affects: [05-polish-differentiators, keyboard, ime]

# Tech tracking
tech-stack:
  added: [androidx.emoji2:emoji2-emojipicker:1.5.0]
  patterns:
    - AndroidView wrapping View-based components inside Compose-in-IME
    - State hoisting to IME service for back key handling (onKeyDown override instead of BackHandler)
    - Fixed-height picker (310.dp) matches keyboard total height to prevent IME window resize

key-files:
  created:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/EmojiPickerScreen.kt
  modified:
    - gradle/libs.versions.toml
    - ime/build.gradle.kts
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt

key-decisions:
  - "State hoisted to DictusImeService (not local to KeyboardScreen) so onKeyDown override can dismiss picker on KEYCODE_BACK"
  - "BackHandler (Compose) not used: back key is not dispatched through Compose back handler stack in InputMethodService"
  - "EmojiPickerScreen height fixed at 310.dp to match keyboard total height and prevent IME window resize"
  - "Rapid emoji entry: emoji picker does not auto-dismiss after selection, user taps emoji key again to return"

patterns-established:
  - "IME back key handling: override onKeyDown in LifecycleInputMethodService, not BackHandler"
  - "AndroidView for View-based libraries: wrap in @Composable with fixed Modifier dimensions"

requirements-completed: [KBD-05]

# Metrics
duration: 3min
completed: 2026-03-28
---

# Phase 05 Plan 03: Emoji Picker Summary

**emoji2-emojipicker EmojiPickerView integrated into Dictus IME keyboard via AndroidView, with state hoisted to DictusImeService and back-key dismissal via onKeyDown override**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-28T19:20:09Z
- **Completed:** 2026-03-28T19:23:12Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added androidx.emoji2:emoji2-emojipicker dependency and created EmojiPickerScreen composable
- Wired emoji picker state to KeyboardScreen with toggle/selection callbacks hoisted to DictusImeService
- Implemented back key dismissal via DictusImeService.onKeyDown override (not BackHandler which silently fails in IME)
- All IME tests pass and full debug build succeeds

## Task Commits

Each task was committed atomically:

1. **Task 1: Add emoji2-emojipicker dependency + EmojiPickerScreen composable** - `6d23b88` (feat)
2. **Task 2: Wire emoji picker into KeyboardScreen + DictusImeService with back key handling** - `6d6780e` (feat)

## Files Created/Modified
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/EmojiPickerScreen.kt` - AndroidView wrapper around EmojiPickerView, fixed 310.dp height
- `gradle/libs.versions.toml` - Added emoji2-emojipicker v1.5.0 library entry
- `ime/build.gradle.kts` - Added emoji2.emojipicker implementation dependency
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` - Added isEmojiPickerOpen, onEmojiToggle, onEmojiSelected params; conditional EmojiPickerScreen vs keyboard Column
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` - Added _isEmojiPickerOpen StateFlow, pass to KeyboardScreen, onKeyDown KEYCODE_BACK override

## Decisions Made
- State hoisted to DictusImeService (not local to KeyboardScreen) so back key can be intercepted via onKeyDown override. BackHandler does not work in IME context because back key is not dispatched through the Compose navigation back stack in an InputMethodService.
- EmojiPickerScreen height fixed at 310.dp to match keyboard total height (46.dp mic row + 264.dp keyboard), preventing IME window from resizing when switching to emoji picker.
- Rapid emoji entry design: picker does not auto-dismiss after emoji selection. User taps emoji key again to return to keyboard.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Emoji picker fully functional: opens on emoji key tap, inserts emojis via InputConnection.commitText, dismisses via emoji key re-tap or back button
- KBD-05 requirement satisfied
- Remaining Phase 05 plans can proceed independently

---
*Phase: 05-polish-differentiators*
*Completed: 2026-03-28*
