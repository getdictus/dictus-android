---
phase: 05-polish-differentiators
plan: 01
subsystem: ui
tags: [waveform, animation, soundpool, audio-feedback, kotlin, compose, tdd]

# Dependency graph
requires:
  - phase: 04-model-management-onboarding
    provides: WaveformBars composable, DictationState.Recording.energy field, SOUND_ENABLED PreferenceKey
  - phase: 03-whisper-integration
    provides: DictationService with AudioCaptureManager energy history

provides:
  - WaveformDriver: smooth rise/decay interpolation driver (core module)
  - DictationSoundPlayer: SoundPool wrapper for recording lifecycle sounds (app module)
  - 29 WAV sound files bundled in app/res/raw/
  - WaveformDriver wired into DictusImeService Recording state
  - Sound feedback wired into DictationService state transitions

affects: [05-02, 05-03, ime-recording-ui]

# Tech tracking
tech-stack:
  added: [SoundPool (Android audio API), withInfiniteAnimationFrameNanos (Compose animation)]
  patterns:
    - "Animation driver pattern: companion object pure functions (tickLevels, interpolateToBarTargets) tested in isolation; instance handles coroutine loop"
    - "LaunchedEffect(Unit) auto-cancels waveform loop when Recording composable leaves composition"
    - "SoundPool pre-loaded in onCreate() for sub-10ms playback latency"
    - "Reactive SOUND_ENABLED: dataStore.data.map{}.collect{} in onCreate() coroutine — no service restart needed"

key-files:
  created:
    - core/src/main/java/dev/pivisolutions/dictus/core/ui/WaveformDriver.kt
    - app/src/main/java/dev/pivisolutions/dictus/audio/DictationSoundPlayer.kt
    - core/src/test/java/dev/pivisolutions/dictus/core/ui/WaveformDriverTest.kt
    - app/src/test/java/dev/pivisolutions/dictus/audio/DictationSoundPlayerTest.kt
    - app/src/main/res/raw/ (29 WAV files)
  modified:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt

key-decisions:
  - "withInfiniteAnimationFrameNanos from androidx.compose.animation.core — withFrameNanos (platform) is internal in Compose 1.x"
  - "LaunchedEffect(Unit) for runLoop() — auto-cancels on composition exit, no manual stop() needed in normal Recording exit"
  - "soundPlayer.release() guarded with ::soundPlayer.isInitialized to handle destroy before onCreate edge case"
  - "playStop() in both stopRecording() and confirmAndTranscribe() — both paths end active recording for the user"

patterns-established:
  - "Pure companion functions for testable math (tickLevels, interpolateToBarTargets) without Android context"
  - "Animation driver as a plain class (not a Composable) — state hoisted in service field, Compose only collects StateFlow"

requirements-completed: [DICT-05, DICT-06]

# Metrics
duration: 12min
completed: 2026-03-28
---

# Phase 05 Plan 01: Waveform Animation + Sound Feedback Summary

**WaveformDriver with iOS-matching smoothingFactor=0.3/decayFactor=0.85 interpolation and SoundPool-backed recording start/stop/cancel sounds from 29 bundled WAV files**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-03-28T07:07:22Z
- **Completed:** 2026-03-28T07:19:00Z
- **Tasks:** 2 (1 TDD + 1 wiring)
- **Files modified:** 6 (2 new source, 2 new test, 2 modified)
- **Files created:** 33 (source + tests + 29 WAV files)

## Accomplishments
- WaveformDriver: per-frame animation with rise (0.3) and decay (0.85) factors, fully unit-tested with pure companion functions
- DictationSoundPlayer: SoundPool wrapper loading electronic_01f/02b/03c WAV files, playStart/playStop/playCancel
- All 29 iOS WAV files bundled in app/res/raw/ for future variant expansion
- DictusImeService wired: LaunchedEffect drives runLoop(), smoothed energy replaces raw energy in RecordingScreen
- DictationService wired: onCreate() loads sounds and subscribes to SOUND_ENABLED reactively, all 4 transition points play correct sounds

## Task Commits

Each task was committed atomically:

1. **RED: failing tests** - `def9596` (test)
2. **GREEN: WaveformDriver + DictationSoundPlayer + WAV files** - `1627103` (feat)
3. **Task 2: Wire into IME and DictationService** - `dd07db7` (feat)

_Note: TDD task has two commits (test RED → feat GREEN)_

## Files Created/Modified
- `core/src/main/java/.../core/ui/WaveformDriver.kt` — animation driver with smoothing/decay interpolation
- `app/src/main/java/.../audio/DictationSoundPlayer.kt` — SoundPool wrapper for 3 sound events
- `core/src/test/.../core/ui/WaveformDriverTest.kt` — 7 unit tests for tickLevels and interpolateToBarTargets
- `app/src/test/.../audio/DictationSoundPlayerTest.kt` — API smoke tests (SoundPool requires real Android context)
- `app/src/main/res/raw/electronic_*.wav` — 29 WAV sound files copied from iOS Dictus/Sounds/
- `ime/.../DictusImeService.kt` — added WaveformDriver field + LaunchedEffect + smoothedEnergy in Recording branch
- `app/.../service/DictationService.kt` — added DictationSoundPlayer, onCreate(), sound calls on all state transitions

## Decisions Made
- `withInfiniteAnimationFrameNanos` from `androidx.compose.animation.core` — the platform-level `withFrameNanos` is internal in Compose 1.x and triggers a compiler error
- `LaunchedEffect(Unit)` for `runLoop()` — auto-cancels when the Recording composable leaves composition; no need to call `stop()` explicitly for the normal case
- `playStop()` triggered in both `stopRecording()` and `confirmAndTranscribe()` — both are user-visible "recording ended" transitions
- `soundPlayer.release()` guarded with `::soundPlayer.isInitialized` to survive edge case where `onDestroy()` fires before `onCreate()` completes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used withInfiniteAnimationFrameNanos instead of withFrameNanos**
- **Found during:** Task 1 (WaveformDriver implementation)
- **Issue:** `withFrameNanos` is internal to Compose platform, causing compiler error "it is internal in file"
- **Fix:** Used `withInfiniteAnimationFrameNanos` from `androidx.compose.animation.core` which is the public API equivalent
- **Files modified:** core/src/main/java/.../core/ui/WaveformDriver.kt
- **Verification:** `./gradlew :core:compileDebugKotlin` succeeds
- **Committed in:** 1627103 (Task 1 feat commit)

---

**Total deviations:** 1 auto-fixed (1 blocking import)
**Impact on plan:** Minor — same semantic as planned, just different import path. No scope creep.

## Issues Encountered
- Pre-existing test failures in ModelCatalogTest, OnboardingViewModelTest, AudioCaptureManagerTest — unrelated to this plan, not introduced by these changes. Left as-is per scope boundary rule.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- DICT-05 and DICT-06 complete
- WaveformDriver ready to be adopted by any future composable needing smoothed energy
- DictationSoundPlayer can be extended with additional sound IDs from the bundled WAV variants
- Remaining plan 05-01 requirements fulfilled; 05-02 (light theme + visual parity) can proceed

---
*Phase: 05-polish-differentiators*
*Completed: 2026-03-28*
