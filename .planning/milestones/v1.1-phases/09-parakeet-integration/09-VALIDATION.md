---
phase: 9
slug: parakeet-integration
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-31
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric |
| **Config file** | `testOptions.unitTests.isIncludeAndroidResources = true` in `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 09-01-01 | 01 | 1 | STT-02 | compile | `./gradlew assembleDebug 2>&1 \| tail -5` | pending |
| 09-01-02 | 01 | 1 | STT-02 | compile | `./gradlew :asr:compileDebugKotlin 2>&1 \| tail -5` | pending |
| 09-02-01 | 02 | 1 | STT-03, STT-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | pending |
| 09-02-02 | 02 | 1 | STT-02, STT-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | pending |
| 09-03-01 | 03 | 2 | STT-03, STT-05 | compile | `./gradlew :app:compileDebugKotlin 2>&1 \| tail -5` | pending |
| 09-03-02 | 03 | 2 | STT-04, STT-05 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" --tests "*.ModelCatalogTest" -x lint` | pending |
| 09-03-03 | 03 | 2 | STT-02-05 | manual | Device verification (checkpoint:human-verify) | pending |

**Notes:**
- Wave 1 (Plans 01, 02) verifies compile-only for the asr/ module (no unit tests for native JNI wrappers) and unit tests for ModelCatalog entries.
- Wave 2 (Plan 03) creates ModelsViewModelTest.kt with full behavior tests for language dialog, RAM guard, and snackbar.
- Device verification in Task 09-03-03 covers end-to-end STT transcription which cannot be unit-tested.

---

## Wave 0 Requirements

None — Wave 1 tasks verify via compile checks and existing ModelCatalogTest extensions. ModelsViewModelTest is created in Plan 03 (Wave 2) alongside the ViewModel changes it tests.

*Pattern reference: existing `SettingsViewModelTest.kt` shows Robolectric + DataStore fake pattern.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| libc++_shared.so build with both native libs | STT-02 | Build/device test | `./gradlew assembleDebug` succeeds with both whisper + sherpa-onnx .so |
| ONNX memory release after engine swap | STT-03 | Device memory test | `adb shell dumpsys meminfo dev.pivisolutions.dictus` after swap — verify RSS drops |
| Parakeet transcription produces text | STT-02 | Device/model test | Record audio on device, verify English transcription output |
| Snackbar appears during engine swap | STT-03 | Visual/UX test | Switch between Whisper and Parakeet models, verify "Chargement du modele..." snackbar |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or are manual-only with justification
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] No Wave 0 gaps — ModelsViewModelTest created in same plan as ViewModel changes
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
