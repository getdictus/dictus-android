---
phase: 10
slug: beta-distribution-license-audit
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric 4.14.1 |
| **Config file** | None — configured via `testOptions` in `app/build.gradle.kts` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `./gradlew testAll && ./gradlew :app:licenseeDebug` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew testAll && ./gradlew :app:licenseeDebug`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | BETA-01 | smoke | `./gradlew lint` | ✅ existing | ⬜ pending |
| 10-01-02 | 01 | 1 | BETA-01 | smoke | `./gradlew testAll` | ✅ existing | ⬜ pending |
| 10-01-03 | 01 | 1 | BETA-01 | smoke | `./gradlew assembleDebug` | ✅ existing | ⬜ pending |
| 10-01-04 | 01 | 1 | BETA-01 | manual | Inspect `.github/workflows/ci.yml` | ❌ W0 | ⬜ pending |
| 10-02-01 | 02 | 1 | BETA-02 | smoke | `./gradlew assembleRelease` (with env vars) | ✅ build task | ⬜ pending |
| 10-02-02 | 02 | 1 | BETA-02 | manual | Inspect `.github/workflows/release.yml` | ❌ W0 | ⬜ pending |
| 10-03-01 | 03 | 2 | LIC-01, LIC-02 | smoke | `./gradlew :app:licenseeDebug` | ❌ W0 | ⬜ pending |
| 10-03-02 | 03 | 2 | LIC-01 | unit | `./gradlew :app:testDebugUnitTest --tests "*.LicencesScreenTest"` | ❌ W0 | ⬜ pending |
| 10-04-01 | 04 | 2 | BETA-03 | manual | `ls README.md && grep -q "sideload" README.md` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `.github/workflows/ci.yml` — CI workflow for BETA-01
- [ ] `.github/workflows/release.yml` — Release workflow for BETA-02
- [ ] `README.md` — Root README for BETA-03
- [ ] `app/src/test/java/dev/pivisolutions/dictus/ui/settings/LicencesScreenTest.kt` — covers LIC-01 (JSON loading + render)
- [ ] `gradle/libs.versions.toml` entry for licensee plugin — prerequisite for LIC-02
- [ ] `app/build.gradle.kts` signing config + licensee plugin application — prerequisites for BETA-02 + LIC-02

*These are created as part of Wave 1 plans — no separate Wave 0 needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CI workflow triggers on PR/push to main | BETA-01 | Requires actual GitHub Actions runner | Push a branch, open PR, verify checks appear |
| Release workflow triggers on v* tag | BETA-02 | Requires actual GitHub Actions runner + secrets | Push `v0.0.1-test` tag, verify release APK appears |
| README sideload instructions are clear | BETA-03 | Subjective readability | Read README.md beta section, verify steps are complete |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
