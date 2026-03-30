# Deferred Items — Phase 05 Polish Differentiators

## Pre-existing test failures (out of scope)

### OnboardingViewModelTest - advanceStep from step 6
- **Discovered during:** Plan 05-05, Task 1 verification
- **Issue:** `OnboardingViewModelTest > advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING to DataStore FAILED`
- **Root cause:** Phase 04 added a 7th onboarding step (OnboardingTestRecordingScreen) and updated OnboardingViewModel to call `completeOnboarding()` at step 7. The test was written for a 6-step flow and still calls `advanceStep()` expecting step 6 to complete onboarding, but it now advances to step 7 instead.
- **Fix needed:** Update the test to advance through 5 steps (not 4), then handle download, then advance to step 7, then advance once more to trigger `completeOnboarding()`.
- **Files:** `app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt`
- **Existing failures (unrelated):** ModelCatalogTest (3 failures), AudioCaptureManagerTest (2 failures)
