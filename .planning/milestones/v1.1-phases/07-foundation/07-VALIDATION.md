---
phase: 7
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK (Android unit tests) |
| **Config file** | `app/build.gradle.kts` |
| **Quick run command** | `./gradlew testDebugUnitTest` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | DEBT-01 | manual+unit | IME layout toggle test | ❌ W0 | ⬜ pending |
| 07-01-02 | 01 | 1 | DEBT-02 | unit | `./gradlew testDebugUnitTest` | ✅ | ⬜ pending |
| 07-02-01 | 02 | 1 | DEBT-03 | unit | `./gradlew testDebugUnitTest` | ✅ | ⬜ pending |
| 07-03-01 | 03 | 2 | STT-01 | unit | `./gradlew testDebugUnitTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing test infrastructure covers DEBT-02 (POST_NOTIFICATIONS manifest) and DEBT-03 (stale test fixes)
- DEBT-01 (AZERTY toggle) may need manual IME verification — no automated IME test harness exists
- STT-01 (SttProvider interface) needs unit test stubs for the new interface contract

*Existing infrastructure covers most phase requirements. Wave 0 adds SttProvider contract tests.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| AZERTY/QWERTY toggle propagates to keyboard without IME restart | DEBT-01 | IME behavior requires real device/emulator | 1. Open Settings → toggle layout 2. Switch to keyboard → verify layout changed 3. No app restart needed |
| Notifications display on API 33+ | DEBT-02 | Requires API 33+ device/emulator | 1. Install on API 33+ emulator 2. Trigger notification 3. Verify notification appears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
