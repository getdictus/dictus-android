---
phase: 1
slug: core-foundation-keyboard-shell
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-21
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Compose UI Testing (via BOM) |
| **Config file** | None — Wave 0 installs |
| **Quick run command** | `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest` |
| **Full suite command** | `./gradlew testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | KBD-01 | unit | `./gradlew :ime:testDebugUnitTest --tests "*KeyboardLayoutTest*"` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | KBD-02 | unit | `./gradlew :ime:testDebugUnitTest --tests "*LayoutSwitchTest*"` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | KBD-03 | unit | `./gradlew :ime:testDebugUnitTest --tests "*AccentMapTest*"` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 2 | KBD-06 | manual-only | Manual: type in emulator, verify text appears | N/A | ⬜ pending |
| 01-03-01 | 03 | 1 | DSG-01 | unit | `./gradlew :core:testDebugUnitTest --tests "*DictusColorsTest*"` | ❌ W0 | ⬜ pending |
| 01-03-02 | 03 | 1 | APP-06 | unit | `./gradlew :app:testDebugUnitTest --tests "*TimberSetupTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/model/KeyboardLayoutTest.kt` — stubs for KBD-01, KBD-02
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/model/AccentMapTest.kt` — stubs for KBD-03
- [ ] `core/src/test/java/dev/pivisolutions/dictus/core/theme/DictusColorsTest.kt` — stubs for DSG-01
- [ ] `app/src/test/java/dev/pivisolutions/dictus/TimberSetupTest.kt` — stubs for APP-06
- [ ] JUnit 4 dependency in version catalog
- [ ] Compose UI test dependency in version catalog (for future phases)

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Text insertion via InputConnection | KBD-06 | Requires running IME in Android system context | 1. Install app on emulator 2. Enable Dictus in Settings > Languages & Input 3. Open any text field (e.g., Chrome) 4. Type characters, verify they appear in field |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
