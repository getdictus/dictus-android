# Deferred Items — Phase 07 Foundation

## Pre-existing Release Test Failures (Out of Scope for 07-01)

**Discovered during:** Task 2 (full test suite run)

**Failing tests (release variant only):**
- `ImeStatusCardTest` — 3 failures
- `RecordingTestAreaTest` — 6 failures

**Root cause:** `java.lang.RuntimeException: Unable to resolve activity for Intent` — Robolectric cannot resolve the ComponentActivity in the release build configuration. This is a pre-existing issue unrelated to the 07-01 changes (confirmed: failures exist before and after 07-01 changes).

**Debug variant:** All tests pass (91/91).

**Reference:** https://github.com/robolectric/robolectric/pull/4736

**Recommendation:** Fix in a dedicated plan — likely requires updating `testOptions.unitTests.includeAndroidResources` in the release build config or adding a Robolectric qualifier for the release manifest.

**Status:** Not fixed in 07-01. Logged for future attention.
