---
phase: 07-foundation
plan: 01
subsystem: testing
tags: [android, kotlin, manifest, permissions, junit, robolectric]

# Dependency graph
requires: []
provides:
  - POST_NOTIFICATIONS permission declared in AndroidManifest.xml
  - Green debug test baseline (91/91 tests passing)
  - Corrected ModelCatalogTest byte size assertions matching ModelManager.kt production values
  - OnboardingViewModelTest corrected to match 7-step state machine
  - AudioCaptureManagerTest corrected to match actual normalizeEnergy and energyHistory behavior
affects: [07-02, 07-03, all Phase 7 plans]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "OnboardingViewModel step machine triggers completeOnboarding() at step==7, not step==6"
    - "AudioCaptureManager pre-fills energyHistory with 30 zeros for immediate waveform rendering"
    - "normalizeEnergy uses rms*20 then sqrt curve (not rms*5 linear)"

key-files:
  created: []
  modified:
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
    - app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt
    - app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt
    - app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt

key-decisions:
  - "Pre-existing release variant test failures (ImeStatusCardTest, RecordingTestAreaTest) are Robolectric activity resolution issues unrelated to this plan — deferred to a separate fix"
  - "AudioCaptureManagerTest assertions updated to match actual implementation (20x+sqrt curve, pre-filled history) rather than changing production code"
  - "OnboardingViewModel.advanceStep() already had step==7 logic on disk — the test was stale from a prior refactor that moved completion from step 6 to step 7"

patterns-established:
  - "Test files must be kept in sync with production code — byte sizes, step counts, and algorithm constants must match the source of truth"

requirements-completed: [DEBT-02, DEBT-03]

# Metrics
duration: 22min
completed: 2026-03-30
---

# Phase 7 Plan 01: Fix v1.0 Tech Debt (POST_NOTIFICATIONS + Stale Tests) Summary

**POST_NOTIFICATIONS manifest declaration added and four stale test files corrected to establish a green 91/91 debug baseline before Phase 7 structural changes.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-03-30T12:29:00Z
- **Completed:** 2026-03-30T12:51:46Z
- **Tasks:** 2
- **Files modified:** 5 source files + 1 deferred-items.md

## Accomplishments
- Added `android.permission.POST_NOTIFICATIONS` to AndroidManifest.xml (DEBT-02)
- Fixed ModelCatalogTest: corrected byte sizes for base (147_951_465L), small (487_601_967L), small-q5_1 (190_085_487L) to match ModelManager.kt production values
- Fixed OnboardingViewModelTest: added second advanceStep() call to advance from step 6 to step 7 then trigger completeOnboarding() (DEBT-03)
- Fixed AudioCaptureManagerTest: corrected normalizeEnergy assertion (rms*20+sqrt, not rms*5 linear) and energyHistory assertion (pre-filled with 30 zeros, not empty)
- Confirmed OnboardingViewModel step machine was already correct (step==7 trigger) — the test was the stale artifact
- Debug test suite green: 91/91 tests passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Declare POST_NOTIFICATIONS and fix stale test assertions** - `3de2720` (fix)
2. **Task 2: Run full test suite / log deferred items** - `7a85fb5` (chore)

## Files Created/Modified
- `app/src/main/AndroidManifest.xml` - Added POST_NOTIFICATIONS permission after FOREGROUND_SERVICE_MICROPHONE
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt` - step==7 trigger for completeOnboarding (was step==6, already modified pre-session)
- `app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt` - Three corrected expectedSizeBytes values
- `app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt` - Added second advanceStep() to reach step 7 before asserting HAS_COMPLETED_ONBOARDING
- `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt` - Corrected two assertions to match actual implementation

## Decisions Made
- AudioCaptureManagerTest assertions were updated to match the production implementation rather than changing production code, since the implementation is intentional (pre-filled zeros for UX, 20x+sqrt curve for speech responsiveness)
- Release variant test failures (ImeStatusCardTest, RecordingTestAreaTest) confirmed as pre-existing Robolectric activity resolution issues — deferred to `.planning/phases/07-foundation/deferred-items.md`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed AudioCaptureManagerTest: normalizeEnergy assertion was wrong**
- **Found during:** Task 1 (after running tests)
- **Issue:** Test expected `normalizeEnergy(0.1f) == 0.5f` (using a 5x multiplier), but implementation uses `rms * 20f` then `sqrt()`, giving `1.0f` for input 0.1
- **Fix:** Updated test assertion to `1.0f` with correct comment explaining the 20x+sqrt formula
- **Files modified:** `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt`
- **Verification:** AudioCaptureManagerTest passes
- **Committed in:** `3de2720` (Task 1 commit)

**2. [Rule 1 - Bug] Fixed AudioCaptureManagerTest: energy history `isEmpty()` was wrong**
- **Found during:** Task 1 (after running tests)
- **Issue:** Test asserted `manager.getEnergyHistory().isEmpty()` but AudioCaptureManager pre-fills energyHistory with 30 zeros on init for immediate waveform display
- **Fix:** Updated test to assert `history.size == 30` and all values are 0f; renamed test to `energy history starts with 30 zero entries`
- **Files modified:** `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt`
- **Verification:** AudioCaptureManagerTest passes
- **Committed in:** `3de2720` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug, both in AudioCaptureManagerTest)
**Impact on plan:** Both fixes necessary to achieve the green test baseline. Production code is correct; tests were stale. No scope creep.

## Issues Encountered
- Release variant test suite has 9 pre-existing failures (ImeStatusCardTest x3, RecordingTestAreaTest x6) caused by Robolectric activity resolution error (`Unable to resolve activity for ComponentActivity`). Confirmed pre-existing — same failures with and without 07-01 changes. Logged in `deferred-items.md`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Clean debug baseline established (91/91 tests green)
- POST_NOTIFICATIONS declared — PermissionHelper.buildPermissionsToRequest() already handles runtime request
- Ready for 07-02: SttProvider interface extraction
- Deferred: Release variant test failures need a dedicated fix before v1.1 release

## Self-Check: PASSED

- AndroidManifest.xml: FOUND and contains POST_NOTIFICATIONS
- ModelCatalogTest.kt: FOUND and contains 147_951_465L
- OnboardingViewModelTest.kt: FOUND
- AudioCaptureManagerTest.kt: FOUND
- 07-01-SUMMARY.md: FOUND
- Commit 3de2720: FOUND
- Commit 7a85fb5: FOUND

---
*Phase: 07-foundation*
*Completed: 2026-03-30*
