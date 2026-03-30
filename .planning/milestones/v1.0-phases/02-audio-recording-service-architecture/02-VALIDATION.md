---
phase: 2
slug: audio-recording-service-architecture
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (already in project) |
| **Config file** | Per-module build.gradle.kts (testImplementation already declared) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest :ime:testDebugUnitTest --tests "*.DictationState*" -x lint` |
| **Full suite command** | `./gradlew testDebugUnitTest -x lint` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest :ime:testDebugUnitTest -x lint`
- **After every plan wave:** Run `./gradlew testDebugUnitTest -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | DICT-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest*" -x lint` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | DICT-02 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest*" -x lint` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | DICT-08 | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationServiceTest*" -x lint` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | DICT-07 | manual | N/A | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/dev/pivisolutions/dictus/service/DictationStateTest.kt` — stubs for DICT-01, DICT-02
- [ ] `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt` — covers buffer management, energy calculation
- [ ] `app/src/test/java/dev/pivisolutions/dictus/service/DictationServiceTest.kt` — covers foreground service notification (DICT-08)
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/ui/WaveformBarTest.kt` — covers energy-to-bar-height mapping
- [ ] Notification icon drawables (`ic_mic.xml`, `ic_stop.xml`) — needed for notification builder

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Haptic feedback fires on interactions | DICT-07 | Requires real device vibration motor | 1. Open any text field 2. Switch to Dictus keyboard 3. Tap keys — feel keyboard haptic 4. Tap mic — feel stronger haptic 5. During recording tap cancel/confirm — feel haptic |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
