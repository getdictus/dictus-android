---
phase: 04-model-management-onboarding
plan: "06"
subsystem: onboarding
tags: [SavedStateHandle, process-death, android, kotlin, compose, animation, sine-wave, WaveformBars]

# Dependency graph
requires:
  - phase: 04-model-management-onboarding
    provides: OnboardingViewModel, OnboardingWelcomeScreen, MainActivity service binding baseline
provides:
  - SavedStateHandle-backed onboarding state (currentStep, micPermissionGranted, imeActivated)
  - Deferred DictationService binding gated behind onboarding completion
  - Animated sine-wave waveform on welcome screen using shared WaveformBars component
affects: [UAT, phase-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - savedStateHandle.getStateFlow() for process-death-safe ViewModel state
    - LaunchedEffect(hasCompletedOnboarding) for deferred service binding in Activity setContent
    - rememberInfiniteTransition + tween sine-wave driver for decorative waveform animation

key-files:
  created: []
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingWelcomeScreen.kt
    - app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt

key-decisions:
  - "savedStateHandle.getStateFlow() used for currentStep/micPermissionGranted/imeActivated — these must survive process death caused by system permission dialogs"
  - "selectedLayout/_downloadProgress/_modelDownloadComplete/_downloadError remain regular MutableStateFlow — transient, should reset on process death (download restarts, layout re-chosen)"
  - "DictationService binding deferred to LaunchedEffect(hasCompletedOnboarding) inside setContent — removes service from memory footprint during onboarding, reducing LMKD kill risk when mic permission dialog is shown"
  - "Static WaveformDecoration + private lerp removed; replaced with shared WaveformBars from core/ui driven by sine-wave — matches iOS parity and reuses existing battle-tested component"

patterns-established:
  - "Process-death-safe ViewModel state: use savedStateHandle.getStateFlow(key, default) for read, savedStateHandle[key] = value for write"
  - "Service binding deferred via LaunchedEffect gated on DataStore boolean — standard Android pattern for conditional service lifecycle"

requirements-completed: [APP-01]

# Metrics
duration: 3min
completed: 2026-03-26
---

# Phase 04 Plan 06: Gap Closure — Process Death Fix + Animated Waveform Summary

**SavedStateHandle-backed OnboardingViewModel survives mic permission dialog process death; DictationService binding deferred to post-onboarding; welcome screen replaced with animated 30-bar sine-wave WaveformBars matching iOS parity**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-26T22:31:33Z
- **Completed:** 2026-03-26T22:34:43Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- OnboardingViewModel now uses `savedStateHandle.getStateFlow()` for `currentStep`, `micPermissionGranted`, and `imeActivated` — the system can kill the process when showing the mic permission dialog (step 2) and the app resumes at the correct step, not step 1
- MainActivity no longer calls `bindDictationService()` eagerly in `onCreate()`; binding is now gated behind `hasCompletedOnboarding == true` via `LaunchedEffect` inside `setContent` — reduces memory pressure and LMKD kill risk during onboarding
- Welcome screen `WaveformDecoration` (static Canvas, 15 bars, custom lerp) replaced with `WaveformBars` from core/ui driven by a `rememberInfiniteTransition` sine-wave (30 bars, 2000ms loop) — matches iOS transcription waveform pattern

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix process death — SavedStateHandle + deferred service binding** - `845a3ff` (feat + test, TDD)
2. **Task 2: Animate welcome screen waveform with sine-wave driver** - `a03a1d2` (feat)

**Plan metadata:** (docs commit — see below)

_Note: Task 1 combined test update, implementation, and MainActivity change in a single atomic commit per TDD green phase._

## Files Created/Modified

- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt` — SavedStateHandle injection; `currentStep`/`micPermissionGranted`/`imeActivated` now backed by SavedStateHandle; `advanceStep()`/`goBack()`/`setMicPermissionGranted()`/`setImeActivated()` write through `savedStateHandle[key]`
- `app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt` — Removed eager `bindDictationService()` from `onCreate()`; added `LaunchedEffect(hasCompletedOnboarding)` inside `setContent` to bind after onboarding is complete
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingWelcomeScreen.kt` — Removed `WaveformDecoration` Canvas composable and `lerp` helper; added `rememberInfiniteTransition` sine-wave driver; replaced with `WaveformBars(energyLevels = sineEnergy)`
- `app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt` — Updated `setUp()` to pass `SavedStateHandle()`; added 5 new tests for SavedStateHandle restoration and write-through behaviour

## Decisions Made

- `savedStateHandle.getStateFlow()` used for the three onboarding flags that must survive process death. Transient state (`_selectedLayout`, download progress) stays as regular `MutableStateFlow` — no benefit to persisting them.
- Service binding deferred via `LaunchedEffect` is the idiomatic Android pattern; no structural change to service lifecycle or `onDestroy()` unbind.
- `WaveformDecoration` removal eliminates 60+ lines of custom Canvas code in favour of the existing shared component. No callers outside this file, so no compatibility concerns.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None. TDD RED/GREEN cycle completed cleanly. Build passed with no errors or warnings relevant to changed files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Process death fix unblocks the 14 skipped UAT tests (blocked on mic permission dialog)
- Animated waveform provides iOS parity for welcome screen
- Phase 04 gap closure complete; all three original UAT issues now addressed (this plan + 04-05)
- Ready for UAT re-run or Phase 05 progression

---
*Phase: 04-model-management-onboarding*
*Completed: 2026-03-26*
