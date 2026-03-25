---
phase: 3
slug: whisper-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-25
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric 4.14.1 (already configured) |
| **Config file** | `app/build.gradle.kts` testOptions |
| **Quick run command** | `./gradlew :app:testDebugUnitTest --tests "*.whisper.*"` |
| **Full suite command** | `./gradlew testAll` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew testAll`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | DICT-03 | unit | `./gradlew :core:testDebugUnitTest --tests "*.TextPostProcessorTest"` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | DICT-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelManagerTest"` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | DICT-04 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest"` | Partial | ⬜ pending |
| 03-02-01 | 02 | 1 | DICT-03 | integration | Manual: requires native lib + model on device | ❌ W0 | ⬜ pending |
| 03-02-02 | 02 | 1 | DICT-04 | integration | Manual: requires running IME | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `core/src/test/.../whisper/TextPostProcessorTest.kt` — stubs for DICT-03 post-processing
- [ ] `app/src/test/.../model/ModelManagerTest.kt` — stubs for DICT-03 model path/URL logic
- [ ] `app/src/test/.../service/DictationStateTest.kt` — extend existing test for Transcribing state (DICT-04)
- [ ] `whisper/` module build verification — CMake compiles without errors (`./gradlew :whisper:assembleDebug`)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| whisper.cpp transcribes audio to text | DICT-03 | Requires native lib + model loaded on real device | 1. Install on Pixel 4 2. Record 5s speech 3. Verify text output |
| Text inserted at cursor via InputConnection | DICT-04 | Requires running IME with active text field | 1. Open text editor 2. Dictate 3. Verify text appears at cursor |
| Transcription <8s on small-q5_1 for 10s audio | DICT-03 | Performance benchmark requires real device | 1. Load small-q5_1 model 2. Record 10s 3. Time transcription |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
