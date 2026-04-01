---
phase: 11
slug: oss-repository
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | bash + grep (file existence and content validation) |
| **Config file** | none — no test framework needed for docs-only phase |
| **Quick run command** | `bash scripts/validate-oss-docs.sh` |
| **Full suite command** | `bash scripts/validate-oss-docs.sh --full` |
| **Estimated runtime** | ~2 seconds |

---

## Sampling Rate

- **After every task commit:** Run `bash scripts/validate-oss-docs.sh`
- **After every plan wave:** Run `bash scripts/validate-oss-docs.sh --full`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 2 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | OSS-01 | file-check | `test -f CONTRIBUTING.md` | ❌ W0 | ⬜ pending |
| 11-01-02 | 01 | 1 | OSS-01 | content | `grep -q '## Build' CONTRIBUTING.md` | ❌ W0 | ⬜ pending |
| 11-02-01 | 02 | 1 | OSS-02 | file-check | `test -f .github/ISSUE_TEMPLATE/bug_report.yml` | ❌ W0 | ⬜ pending |
| 11-02-02 | 02 | 1 | OSS-02 | file-check | `test -f .github/ISSUE_TEMPLATE/feature_request.yml` | ❌ W0 | ⬜ pending |
| 11-03-01 | 03 | 1 | OSS-03 | file-check | `test -f CODE_OF_CONDUCT.md` | ❌ W0 | ⬜ pending |
| 11-03-02 | 03 | 1 | OSS-03 | content | `grep -q 'Contributor Covenant' CODE_OF_CONDUCT.md` | ❌ W0 | ⬜ pending |
| 11-04-01 | 04 | 1 | OSS-04 | content | `grep -q '## Installation' README.md` | ❌ W0 | ⬜ pending |
| 11-04-02 | 04 | 1 | OSS-04 | content | `grep -q '## Contributing' README.md` | ❌ W0 | ⬜ pending |
| 11-04-03 | 04 | 1 | OSS-04 | content | `grep -q 'LICENSE' README.md` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `scripts/validate-oss-docs.sh` — validation script checking all OSS file existence and content
- [ ] No framework install needed — bash/grep sufficient for docs-only phase

*Existing infrastructure covers all phase requirements with file/content checks.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Screenshots in README | OSS-04 | Requires Pixel 4 capture | Pierre captures screenshots, places in screenshots/, verifies they render in GitHub preview |
| Contact method in CODE_OF_CONDUCT | OSS-03 | Requires Pierre's decision | Verify [INSERT CONTACT METHOD] is replaced with actual contact info |
| Build from scratch | OSS-01 | Requires clean environment | Clone repo on fresh machine, follow CONTRIBUTING.md, verify build succeeds |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 2s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
