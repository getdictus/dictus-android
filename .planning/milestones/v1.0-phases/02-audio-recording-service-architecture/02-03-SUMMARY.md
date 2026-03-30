---
phase: 02-audio-recording-service-architecture
plan: 03
subsystem: ime-recording
tags: [haptics, waveform, recording-ui, gap-closure, uat]

requires:
  - phase: 02-audio-recording-service-architecture
    plan: 01
    provides: "DictationService, AudioCaptureManager, DictationController"
  - phase: 02-audio-recording-service-architecture
    plan: 02
    provides: "RecordingScreen, WaveformBars, HapticHelper, MicButtonRow"
provides:
  - "Fixed mic tap flow — keyboard stays open during recording"
  - "Permission-safe haptic feedback via View.performHapticFeedback"
  - "RecordingScreen layout matching iOS mockup (outlined pill buttons, no bottom mic)"
  - "Responsive waveform with 20x amplification + sqrt curve"
  - "Pre-filled energy history eliminating slide-in animation artifact"
  - "Edge-clamped accent popup positioning for leftmost/rightmost keys"
affects: [phase-03-whisper, recording-ux]

tech-stack:
  added: []
  patterns:
    - "View.performHapticFeedback over Vibrator API (no VIBRATE permission needed)"
    - "Pre-filled ArrayDeque for immediate full-width waveform rendering"
    - "sqrt curve for perceptual energy normalization"
---

## Summary

Gap closure plan fixing 3 UAT failures from Phase 02, plus visual polish of the recording overlay based on user feedback comparing Android vs iOS implementations.

## Self-Check: PASSED

All 5 UAT tests verified on Pixel 4:
1. Mic tap -> recording overlay (keyboard stays open): PASS
2. Cancel/confirm -> returns to keyboard: PASS
3. Mic haptic stronger than key haptic: PASS
4. Accent popup "a" key within screen bounds: PASS
5. Accent popup "e"/"o"/"p" no regression: PASS

## What Was Built

### Bug Fixes (from plan)

1. **Mic tap crash fix (BLOCKER):** Root cause was NOT `startForegroundService` — it was `HapticHelper.performMicHaptic()` using `Vibrator.vibrate()` which threw `SecurityException` (missing VIBRATE permission), crashing the IME process. Fix: replaced with `View.performHapticFeedback(LONG_PRESS)` which is permission-free.

2. **Accent popup edge clamping (MINOR):** Added `onGloballyPositioned` to capture key's absolute X position, computed clamped horizontal shift to keep popup within `[0, screenWidth]`.

### Visual Polish (from user feedback during verification)

3. **RecordingScreen layout redesign:** Restructured to match iOS mockup — cancel (X) and confirm (✓) as outlined pill buttons in top row, waveform centered, timer + label below, no mic pill in bottom row.

4. **Waveform sensitivity:** Increased energy amplification from 5x to 20x with sqrt curve for better perceptual mapping of speech to bar height.

5. **Waveform slide-in fix:** Pre-filled energy history with 30 zeros so all bars render immediately instead of sliding in from the left.

## Deviations

- **Root cause was different than planned:** Plan assumed `startForegroundService` from IME context caused keyboard dismiss. Actual root cause was `SecurityException` in `HapticHelper.performMicHaptic()` crashing the process. Discovered via `adb logcat` stack trace.
- **Additional visual work:** RecordingScreen layout and waveform improvements were not in the original plan but were added based on user UAT feedback comparing iOS vs Android.

## Key Files

### Modified
- `ime/.../haptics/HapticHelper.kt` — Simplified to use View.performHapticFeedback only
- `ime/.../ui/RecordingScreen.kt` — Redesigned layout (outlined pills, no bottom mic)
- `app/.../service/AudioCaptureManager.kt` — Waveform amplification + pre-fill
- `ime/.../DictusImeService.kt` — Added debug logging to handleMicTap
- `ime/.../ui/KeyButton.kt` — Accent popup edge clamping (from agent commit)

## Commits

1. `e7063c6` — fix(02-03): use bound controller for mic tap
2. `d6f55c6` — fix(02-03): clamp accent popup position for edge keys
3. `2c5a93d` — fix(02-03): use View.performHapticFeedback for mic haptic
4. `2d4f924` — fix(02-03): redesign RecordingScreen layout to match iOS mockup
5. `55b7d6e` — fix(02-03): improve waveform sensitivity and fix slide-in animation
