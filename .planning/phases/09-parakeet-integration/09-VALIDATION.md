---
phase: 9
slug: parakeet-integration
status: draft
nyquist_compliant: false
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

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | STT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ | ⬜ pending |
| 09-01-02 | 01 | 1 | STT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ | ⬜ pending |
| 09-02-01 | 02 | 1 | STT-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` | ❌ W0 | ⬜ pending |
| 09-03-01 | 03 | 2 | STT-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ extend | ⬜ pending |
| 09-04-01 | 04 | 2 | STT-05 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` | ❌ W0 | ⬜ pending |
| 09-04-02 | 04 | 2 | STT-05 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/.../models/ModelsViewModelTest.kt` — Parakeet language dialog trigger tests (STT-05)
- [ ] Extend `ModelCatalogTest` — add Parakeet model entries assertions (STT-02, STT-04)

*Pattern reference: existing `SettingsViewModelTest.kt` shows Robolectric + DataStore fake pattern.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| libc++_shared.so build with both native libs | STT-02 | Build/device test | `./gradlew assembleDebug` succeeds with both whisper + sherpa-onnx .so |
| ONNX memory release after engine swap | STT-03 | Device memory test | `adb shell dumpsys meminfo dev.pivisolutions.dictus` after swap — verify RSS drops |
| Parakeet transcription produces text | STT-02 | Device/model test | Record audio on device, verify English transcription output |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
