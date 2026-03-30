---
phase: 4
slug: model-management-onboarding
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-26
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AndroidX Test + Compose UI Test |
| **Config file** | `app/build.gradle.kts` (testImplementation block) |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-xx | 01 | 1 | APP-01 | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 04-02-xx | 02 | 1 | APP-02, APP-03 | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 04-03-xx | 03 | 2 | APP-04, APP-05 | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Test infrastructure verified — JUnit 5 + MockK already in dependencies
- [ ] `app/src/test/java/.../onboarding/OnboardingViewModelTest.kt` — stubs for APP-01
- [ ] `app/src/test/java/.../models/ModelManagerTest.kt` — stubs for APP-02, APP-03
- [ ] `app/src/test/java/.../settings/SettingsViewModelTest.kt` — stubs for APP-04, APP-05

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Onboarding visual flow | APP-01 | Compose UI navigation + permission dialogs | Walk through all 6 steps on device |
| Model download with real network | APP-02 | Requires actual HTTP download | Download tiny model on device, verify progress bar |
| IME activation via system Settings | APP-01 | Requires OS-level Settings app interaction | Open Settings, enable Dictus keyboard, return to app |
| Log export via share sheet | APP-05 | Requires Android share sheet | Tap export, verify ZIP in share sheet |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
