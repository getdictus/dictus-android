# Deferred Items — Phase 04

## Pre-existing test failures (out of scope)

**File:** app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt

**Failing tests:**
- `energy history starts empty` — AssertionError at line 75
- `normalizeEnergy typical speech value` — AssertionError at line 56

**Status:** Pre-existing failures present before Phase 04 execution began.
These tests are in AudioCaptureManagerTest (Phase 3 code) and are unrelated to
any Phase 4 changes. They do not block Phase 4 plan execution.

**Action needed:** Investigate AudioCaptureManager implementation vs test expectations
in a dedicated fix (Phase 3 cleanup task).
