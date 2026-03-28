---
phase: 5
slug: polish-differentiators
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-28
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Android Instrumented Tests (JUnit 4 + Compose UI Testing) |
| **Config file** | `app/build.gradle.kts` (androidTestImplementation deps) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew :app:connectedDebugAndroidTest` |
| **Estimated runtime** | ~45 seconds (unit), ~120 seconds (instrumented) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew :app:connectedDebugAndroidTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | DICT-05 | manual | visual waveform inspection | N/A | ⬜ pending |
| 05-01-02 | 01 | 1 | DICT-06 | manual | audio playback check | N/A | ⬜ pending |
| 05-02-01 | 02 | 1 | KBD-04 | manual | suggestion bar tap test | N/A | ⬜ pending |
| 05-02-02 | 02 | 1 | KBD-05 | manual | emoji picker UI check | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test framework needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Waveform animation smoothing matches iOS feel | DICT-05 | Visual/perceptual quality requires human judgment | Record audio, observe waveform bars rise/fall smoothly with decay |
| Sound plays on recording start/stop | DICT-06 | Audio output verification on device | Toggle recording, listen for start/stop sounds |
| Suggestion bar shows 3 words above keyboard | KBD-04 | IME rendering in host app context | Open keyboard in any text field, type partial words, verify suggestions appear |
| Emoji picker opens and inserts emoji | KBD-05 | IME interaction in host app context | Tap emoji key, select emoji, verify insertion in text field |
| Light theme renders correctly across app + IME | DSG-02 | Visual consistency check across all screens | Switch to light theme in settings, navigate all screens + open keyboard |
| Bottom nav pill visibility improved | Visual parity | Perceptual design quality | Compare bottom nav with iOS reference screenshot |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
