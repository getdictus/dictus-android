# Phase 11: OSS Repository - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

The GitHub repository is ready for public contributions — it has the documentation, templates, and community standards that make contribution frictionless. A contributor can clone, build, and submit a PR without asking questions.

</domain>

<decisions>
## Implementation Decisions

### CONTRIBUTING.md
- Quick start build guide only — assumes contributor knows Android dev basics
- Prerequisites: JDK, Android SDK, NDK 27.2, then `./gradlew assembleDebug`
- Brief module map: one-liner per module (app, ime, core, whisper, asr) with key entry points
- Brief testing tips: `./gradlew test` for unit tests, how to enable Dictus keyboard for IME testing — 2-3 lines
- Written in English

### Issue templates
- Two YAML templates: bug report and feature request
- Bug report fields: device + Android version, STT provider + model, steps to reproduce, app version
- Feature request: use case description + proposed solution
- No config.yml external links, no Discussions/Discord for now

### PR template
- Simple checklist: description field + checklist items (tests pass, no new warnings, tested on device if UI change)
- Not a detailed template with dropdowns/screenshots sections

### README enhancement
- Add CI status, license (MIT), Android min SDK badges at the top (3-4 badges max)
- Add 2-3 screenshots: keyboard in action, model manager, settings
- Add short "Contributing" section linking to CONTRIBUTING.md (2-3 lines)
- Written in English (consistent with CONTRIBUTING.md)
- Keep existing content (features, beta install, tech stack, license) and enhance

### Contribution rules
- Branch naming: `type/description` (e.g., `fix/keyboard-crash`, `feat/emoji-search`)
- Commit messages: Conventional Commits suggested (`feat:`, `fix:`, `docs:`) but not enforced with tooling
- Code review: maintainer review required on all PRs before merge
- Code language: English for comments and variable names, bilingual FR+EN for UI strings via strings.xml

### CODE_OF_CONDUCT.md
- Contributor Covenant 2.1 (standard, per requirements)

### LICENSE file
- MIT license file at repo root (currently missing — project is MIT per PRD.md and CLAUDE.md)

### Claude's Discretion
- Exact YAML template field types (dropdown vs textarea vs input)
- Badge styling/service (shields.io vs GitHub native)
- Screenshot placement and sizing in README
- CODE_OF_CONDUCT contact method (email vs GitHub issues)
- Exact wording of PR checklist items

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing README
- `README.md` — Current text-only README from Phase 10 to be enhanced (not replaced)

### Existing CI workflows
- `.github/workflows/ci.yml` — CI workflow for badge reference
- `.github/workflows/release.yml` — Release workflow for badge reference

### Project identity
- `PRD.md` — Project description, feature matrix, distribution section for README content
- `CLAUDE.md` — Architecture overview, conventions (EN code, FR+EN strings), color palette

### Requirements
- `.planning/REQUIREMENTS.md` — OSS-01, OSS-02, OSS-03, OSS-04 requirements

### Prior phase context
- `.planning/phases/10-beta-distribution-license-audit/10-CONTEXT.md` — Phase 10 decisions: text-only README, getdictus/dictus-android slug, GitHub Issues for feedback

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `README.md`: Existing README with features, beta install, tech stack, license — enhance, don't rewrite
- `.github/workflows/ci.yml` + `release.yml`: CI already set up — badge URLs can reference these

### Established Patterns
- Multi-module Gradle project: `app`, `ime`, `core`, `whisper`, `asr` — CONTRIBUTING.md module map follows this
- English code comments, bilingual FR/EN strings.xml — contribution rules match existing convention
- MIT license declared in PRD.md and CLAUDE.md but no LICENSE file at repo root

### Integration Points
- `.github/ISSUE_TEMPLATE/` — New directory for bug and feature YAML templates
- `.github/PULL_REQUEST_TEMPLATE.md` — New file for PR checklist
- `CONTRIBUTING.md` — New file at repo root
- `CODE_OF_CONDUCT.md` — New file at repo root
- `LICENSE` — New file at repo root (MIT)
- `README.md` — Existing file to be enhanced with badges, screenshots, contributing section

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard OSS approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 11-oss-repository*
*Context gathered: 2026-04-01*
