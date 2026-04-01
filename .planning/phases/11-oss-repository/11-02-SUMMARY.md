---
phase: 11-oss-repository
plan: "02"
subsystem: infra
tags: [github, issue-templates, pr-template, yaml, contributor-experience]

requires: []
provides:
  - "GitHub YAML issue templates for bug reports and feature requests"
  - "PR template with description area and 3-item quality checklist"
affects: [oss-repository]

tech-stack:
  added: []
  patterns:
    - "GitHub YAML form syntax for structured issue collection"
    - "Minimal PR template: description + checklist only, no dropdowns or screenshot sections"

key-files:
  created:
    - .github/ISSUE_TEMPLATE/bug_report.yml
    - .github/ISSUE_TEMPLATE/feature_request.yml
    - .github/PULL_REQUEST_TEMPLATE.md
  modified: []

key-decisions:
  - "No config.yml in ISSUE_TEMPLATE: no external links or Discussions routing per locked decision"
  - "Bug report STT provider as dropdown (Whisper/Parakeet) not free text: ensures consistent triage data"
  - "PR template intentionally minimal: 3 checkboxes only, no elaborate categories or screenshot requirements"

patterns-established:
  - "YAML issue forms over Markdown templates: structured fields enforce required data at submission time"

requirements-completed: [OSS-02]

duration: 1min
completed: 2026-04-01
---

# Phase 11 Plan 02: GitHub Issue Templates and PR Template Summary

**Structured YAML issue forms for bug reports (device/STT provider dropdown/model/version/steps) and feature requests (use case/solution), plus a minimal 3-item PR checklist**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-04-01T18:23:50Z
- **Completed:** 2026-04-01T18:24:34Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Bug report template uses YAML form syntax with required fields: device, STT provider (dropdown with Whisper/Parakeet options), model, app version, steps to reproduce, expected behavior, actual behavior
- Feature request template collects use case (required) and proposed solution (optional)
- PR template pre-fills description placeholder and 3-item checklist covering test pass, lint warnings, and device testing for UI changes
- No config.yml created, no external links — per locked decision

## Task Commits

Each task was committed atomically:

1. **Task 1: Create YAML issue templates** - `26f854e` (feat)
2. **Task 2: Create PR template** - `84c8e7e` (feat)

## Files Created/Modified

- `.github/ISSUE_TEMPLATE/bug_report.yml` - Structured bug report form with 7 fields including STT provider dropdown
- `.github/ISSUE_TEMPLATE/feature_request.yml` - Feature request form with use case and proposed solution fields
- `.github/PULL_REQUEST_TEMPLATE.md` - Description area + 3-item quality checklist

## Decisions Made

- No config.yml: GitHub Discussions routing and external links were explicitly excluded per locked context decision
- STT provider as dropdown: Ensures triage data is always one of {Whisper, Parakeet} rather than free text variations
- PR template intentionally minimal: 3 checkboxes only matching the Android project's gradlew tooling (test, lint, device)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. Templates are active on GitHub once pushed.

## Next Phase Readiness

- GitHub issue chooser will display "Bug report" and "Feature request" when contributors open a new issue
- PR body auto-filled for all contributors opening pull requests
- Ready for Plan 03 (remaining OSS repository work)

---
*Phase: 11-oss-repository*
*Completed: 2026-04-01*
