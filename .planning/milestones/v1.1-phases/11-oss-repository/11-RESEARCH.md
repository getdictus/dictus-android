# Phase 11: OSS Repository - Research

**Researched:** 2026-04-01
**Domain:** GitHub community standards, OSS documentation, repository metadata
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**CONTRIBUTING.md**
- Quick start build guide only — assumes contributor knows Android dev basics
- Prerequisites: JDK, Android SDK, NDK 27.2, then `./gradlew assembleDebug`
- Brief module map: one-liner per module (app, ime, core, whisper, asr) with key entry points
- Brief testing tips: `./gradlew test` for unit tests, how to enable Dictus keyboard for IME testing — 2-3 lines
- Written in English

**Issue templates**
- Two YAML templates: bug report and feature request
- Bug report fields: device + Android version, STT provider + model, steps to reproduce, app version
- Feature request: use case description + proposed solution
- No config.yml external links, no Discussions/Discord for now

**PR template**
- Simple checklist: description field + checklist items (tests pass, no new warnings, tested on device if UI change)
- Not a detailed template with dropdowns/screenshots sections

**README enhancement**
- Add CI status, license (MIT), Android min SDK badges at the top (3-4 badges max)
- Add 2-3 screenshots: keyboard in action, model manager, settings
- Add short "Contributing" section linking to CONTRIBUTING.md (2-3 lines)
- Written in English (consistent with CONTRIBUTING.md)
- Keep existing content (features, beta install, tech stack, license) and enhance

**Contribution rules**
- Branch naming: `type/description` (e.g., `fix/keyboard-crash`, `feat/emoji-search`)
- Commit messages: Conventional Commits suggested (`feat:`, `fix:`, `docs:`) but not enforced with tooling
- Code review: maintainer review required on all PRs before merge
- Code language: English for comments and variable names, bilingual FR+EN for UI strings via strings.xml

**CODE_OF_CONDUCT.md**
- Contributor Covenant 2.1 (standard, per requirements)

**LICENSE file**
- MIT license file at repo root (currently missing — project is MIT per PRD.md and CLAUDE.md)

### Claude's Discretion
- Exact YAML template field types (dropdown vs textarea vs input)
- Badge styling/service (shields.io vs GitHub native)
- Screenshot placement and sizing in README
- CODE_OF_CONDUCT contact method (email vs GitHub issues)
- Exact wording of PR checklist items

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| OSS-01 | Le repo contient CONTRIBUTING.md avec instructions de build et soumission de PR | CONTRIBUTING.md structure, Gradle build commands, module map, branch naming conventions |
| OSS-02 | Le repo contient des issue templates YAML (bug report, feature request) et un PR template | GitHub YAML issue form syntax (.github/ISSUE_TEMPLATE/), PR template location (.github/PULL_REQUEST_TEMPLATE.md) |
| OSS-03 | Le repo contient CODE_OF_CONDUCT.md (Contributor Covenant) | Contributor Covenant 2.1 full text, contact method placeholder |
| OSS-04 | Le README est complet (description, screenshots, installation, contribution, licence) | Existing README base, shields.io badge URL format, screenshot Markdown syntax, Contributing section pattern |
</phase_requirements>

---

## Summary

Phase 11 is purely a documentation and repository metadata phase. There is no new application code to write. All four requirements (OSS-01 through OSS-04) map directly to creating or editing specific files in the repo root and the `.github/` directory.

The existing README already has strong content (features, beta install, tech stack, license sections) and must be enhanced rather than rewritten — adding badges, screenshots, and a Contributing section. The five new files to create are: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`, `.github/ISSUE_TEMPLATE/bug_report.yml`, `.github/ISSUE_TEMPLATE/feature_request.yml`, and `.github/PULL_REQUEST_TEMPLATE.md`.

The only Claude-discretion decisions requiring research are: badge URL format (shields.io is the standard, CI workflow filenames are `ci.yml` and `release.yml`), YAML template field type selection (resolved below), and CODE_OF_CONDUCT contact method (GitHub Issues is consistent with Phase 10 decision to use GitHub Issues for feedback).

**Primary recommendation:** Create 6 files and enhance 1 existing file in a single wave. All content is deterministic — no implementation choices remain ambiguous after this research.

---

## Standard Stack

### Core — Files to Create/Edit

| File | Location | Requirement |
|------|----------|-------------|
| `CONTRIBUTING.md` | repo root | OSS-01 |
| `.github/ISSUE_TEMPLATE/bug_report.yml` | `.github/ISSUE_TEMPLATE/` | OSS-02 |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | `.github/ISSUE_TEMPLATE/` | OSS-02 |
| `.github/PULL_REQUEST_TEMPLATE.md` | `.github/` | OSS-02 |
| `CODE_OF_CONDUCT.md` | repo root | OSS-03 |
| `LICENSE` | repo root | OSS-04 (also needed for README license badge) |
| `README.md` | repo root | OSS-04 (enhance existing) |

### No New Dependencies

This phase introduces zero code dependencies. No `build.gradle.kts` changes, no new Gradle plugins, no new libraries.

---

## Architecture Patterns

### Recommended Repository Structure After Phase 11

```
.github/
├── ISSUE_TEMPLATE/
│   ├── bug_report.yml
│   └── feature_request.yml
├── PULL_REQUEST_TEMPLATE.md
└── workflows/
    ├── ci.yml        (existing)
    └── release.yml   (existing)
CONTRIBUTING.md       (new)
CODE_OF_CONDUCT.md    (new)
LICENSE               (new)
README.md             (enhanced)
```

### Pattern 1: GitHub YAML Issue Form

**What:** Structured issue templates that replace free-text with typed fields. Placed in `.github/ISSUE_TEMPLATE/` with `.yml` extension. GitHub automatically presents a template chooser when multiple templates exist.

**Top-level required fields:** `name`, `description`, `body`

**Field types available:** `markdown` (static text), `input` (single-line), `textarea` (multi-line), `dropdown` (options list), `checkboxes`

**Recommended field type selection for Dictus bug report:**
- Device + Android version → `input` (short, specific, no enumerable options)
- STT provider → `dropdown` (enumerable: Whisper / Parakeet)
- STT model → `input` (version strings vary too much for dropdown)
- Steps to reproduce → `textarea` (multi-line, numbered list)
- App version → `input` (version strings vary)
- Expected / actual behavior → `textarea`

**Example bug report template:**
```yaml
# Source: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms
name: Bug report
description: Something isn't working as expected
labels: ["bug"]
body:
  - type: input
    id: device
    attributes:
      label: Device and Android version
      placeholder: "e.g. Pixel 6, Android 13 (API 33)"
    validations:
      required: true
  - type: dropdown
    id: stt-provider
    attributes:
      label: STT provider
      options:
        - Whisper
        - Parakeet
    validations:
      required: true
  - type: input
    id: stt-model
    attributes:
      label: STT model
      placeholder: "e.g. Whisper tiny, Parakeet 110M INT8"
    validations:
      required: true
  - type: input
    id: app-version
    attributes:
      label: App version
      placeholder: "e.g. 1.1.0"
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: Steps to reproduce
      value: |
        1.
        2.
        3.
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
    validations:
      required: true
  - type: textarea
    id: actual
    attributes:
      label: Actual behavior
    validations:
      required: true
```

**Recommended field types for feature request:**
- Use case → `textarea` (open-ended)
- Proposed solution → `textarea` (open-ended)

**Example feature request template:**
```yaml
# Source: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms
name: Feature request
description: Suggest an improvement or new feature
labels: ["enhancement"]
body:
  - type: textarea
    id: use-case
    attributes:
      label: Use case
      description: What problem does this solve? What are you trying to do?
    validations:
      required: true
  - type: textarea
    id: proposed-solution
    attributes:
      label: Proposed solution
      description: How would you like this to work?
    validations:
      required: false
```

### Pattern 2: PR Template

**What:** A Markdown file at `.github/PULL_REQUEST_TEMPLATE.md`. GitHub auto-populates the PR body with this content when a contributor opens a pull request.

**Structure for Dictus (per locked decisions):**

```markdown
## Description

<!-- Describe your changes -->

## Checklist

- [ ] Tests pass (`./gradlew test`)
- [ ] No new lint warnings (`./gradlew lint`)
- [ ] Tested on device (if UI change)
```

### Pattern 3: shields.io Badges for README

**What:** SVG badges via shields.io or GitHub's native badge endpoint.

**Recommendation: shields.io** (more control over style, label, color; GitHub native badges are limited to specific metadata types).

**Badge URL format — CI workflow status:**
```
https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/ci.yml?branch=main&label=CI
```

**Badge URL format — Release workflow:**
```
https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/release.yml?branch=main&label=Release
```

**Badge URL format — License:**
```
https://img.shields.io/github/license/getdictus/dictus-android
```

**Badge URL format — Min SDK (static):**
```
https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-green?logo=android
```

**Rendered in README:**
```markdown
[![CI](https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/ci.yml?branch=main&label=CI)](https://github.com/getdictus/dictus-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/release.yml?branch=main&label=Release)](https://github.com/getdictus/dictus-android/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/github/license/getdictus/dictus-android)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-green?logo=android)](https://developer.android.com/about/versions/10)
```

Note: workflow filename parameter must match the file name on disk (`ci.yml`, `release.yml`) — not the `name:` field inside the YAML. This changed in shields.io v2 routing.

### Pattern 4: MIT LICENSE File

**What:** Plain text file at repo root named `LICENSE` (no extension — this is what GitHub detects automatically for badge and repo metadata).

**Standard MIT text (source: choosealicense.com):**
```
MIT License

Copyright (c) 2024 Pierre Vivière

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Use year 2024 (project creation year, not current year — standard practice for copyright notices).

### Pattern 5: CODE_OF_CONDUCT.md

**What:** Contributor Covenant 2.1 verbatim text, with contact method filled in.

**Contact method decision (Claude's discretion):** Use `conduct@getdictus.com` as the email placeholder per CC standard format — OR, since Pierre is a solo maintainer without a dedicated conduct email, use a GitHub Issues link with a specific label. Per Phase 10's established pattern of routing feedback to GitHub Issues, recommend: "Please open a private GitHub issue or contact the maintainer directly via GitHub."

However, the Contributor Covenant explicitly expects an email address. The pragmatic solo-project solution: use a GitHub-based contact like `[your GitHub username]` or a generic note. The exact placeholder is `[INSERT CONTACT METHOD]` in the template — the planner should use `conduct@getdictus.com` (forward to Pierre) or note it as the one item Pierre must fill in.

**Attribution line required at bottom of CODE_OF_CONDUCT.md:**
```
This Code of Conduct is adapted from the [Contributor Covenant][homepage],
version 2.1, available at
https://www.contributor-covenant.org/version/2/1/code_of_conduct.html.

Community Impact Guidelines were inspired by
[Mozilla's code of conduct enforcement ladder][Mozilla CoC].

[homepage]: https://www.contributor-covenant.org
[Mozilla CoC]: https://github.com/mozilla/diversity
```

### Pattern 6: CONTRIBUTING.md Structure

Per locked decisions, structure is:
1. Prerequisites (JDK 17, Android SDK, NDK 27.2)
2. Build: `./gradlew assembleDebug`
3. Module map table (one-liner per module)
4. Testing: `./gradlew test`, IME keyboard enable steps
5. Contribution rules: branch naming, commit message convention, PR process

**Module map for CONTRIBUTING.md:**
| Module | Purpose | Key entry point |
|--------|---------|-----------------|
| `app` | Main app UI, onboarding, settings, model manager | `MainActivity.kt` |
| `ime` | System keyboard implementation, suggestion bar | `DictusImeService.kt` |
| `core` | Shared models, design system, STT interface | `SttProvider.kt` |
| `whisper` | Whisper.cpp JNI bridge and native libs | `WhisperProvider.kt` |
| `asr` | Sherpa-onnx Kotlin API and Parakeet provider | `ParakeetProvider.kt` |

### Anti-Patterns to Avoid

- **Using `config.yml` in ISSUE_TEMPLATE/**: Locked decision — no external links or Discussions/Discord links needed.
- **Naming the license file `LICENSE.md` or `LICENSE.txt`**: GitHub auto-detects `LICENSE` (no extension) for the repo license badge. `LICENSE.md` also works but plain `LICENSE` is the most universal convention.
- **Using `name:` field value in shields.io badge URL**: The workflow parameter must be the filename (`ci.yml`), not the `name: CI` from inside the YAML file.
- **Placing screenshots in README before they exist**: Screenshots must be added to a `screenshots/` or `docs/assets/` directory in the repo and committed before the README references them. Alternatively, reference them from a GitHub release or use absolute URLs. For a public repo, relative paths work once committed.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Code of Conduct text | Custom community standards document | Contributor Covenant 2.1 verbatim | Industry standard, legally reviewed, recognized by OSS community |
| Badge SVG images | Custom badge images | shields.io dynamic badges | Auto-updates with live CI status, standard across GitHub OSS |
| Issue form validation | Custom GitHub App or bot | YAML `validations: required: true` | Native GitHub feature, zero maintenance |
| LICENSE legal text | Custom license wording | Standard MIT boilerplate from choosealicense.com | Legal precision matters; custom wording creates ambiguity |

**Key insight:** Every artifact in this phase has an established, copy-paste standard. Deviation from standard formats (CC 2.1, MIT boilerplate, YAML issue forms) introduces friction without benefit.

---

## Common Pitfalls

### Pitfall 1: Wrong shields.io workflow badge parameter
**What goes wrong:** Badge shows "no status" or broken image.
**Why it happens:** Using the workflow `name:` value (`CI`) instead of the filename (`ci.yml`) in the badge URL.
**How to avoid:** Badge URL path parameter must be the filename: `.../workflow/status/getdictus/dictus-android/ci.yml`.
**Warning signs:** Badge renders as grey/unknown immediately after adding.

### Pitfall 2: ISSUE_TEMPLATE directory has no effect
**What goes wrong:** GitHub shows a blank issue form, not the YAML templates.
**Why it happens:** YAML files placed in wrong location (repo root, `.github/` directly) instead of `.github/ISSUE_TEMPLATE/`.
**How to avoid:** Path must be exactly `.github/ISSUE_TEMPLATE/bug_report.yml` (capital ISSUE_TEMPLATE).
**Warning signs:** Opening a new issue on GitHub shows no template chooser.

### Pitfall 3: LICENSE file not detected by GitHub
**What goes wrong:** Repo metadata doesn't show "MIT" label; shields.io license badge shows "unknown".
**Why it happens:** File named `LICENSE.txt` on some configs, or file not at repo root.
**How to avoid:** Name the file exactly `LICENSE` (no extension) at the repo root.
**Warning signs:** GitHub repo page doesn't show a license badge in the right sidebar.

### Pitfall 4: PR template not shown to contributors
**What goes wrong:** PR body is empty, not pre-filled with checklist.
**Why it happens:** Template placed in wrong location (`.github/ISSUE_TEMPLATE/` instead of `.github/PULL_REQUEST_TEMPLATE.md`).
**How to avoid:** File must be at `.github/PULL_REQUEST_TEMPLATE.md` exactly.

### Pitfall 5: Screenshots referenced in README before being committed
**What goes wrong:** README shows broken image links.
**Why it happens:** Screenshot paths added to README in the same commit but actual image files missing or wrong path.
**How to avoid:** Screenshots must be captured from a physical device (Pixel 4 per user profile), stored in `docs/screenshots/` or `screenshots/` directory, and committed in the same wave as the README update. Alternatively, defer screenshot addition or note it as a manual step.

---

## Code Examples

### Complete bug_report.yml
```yaml
# Source: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms
name: Bug report
description: Something isn't working as expected
labels: ["bug"]
body:
  - type: input
    id: device
    attributes:
      label: Device and Android version
      placeholder: "e.g. Pixel 6, Android 13 (API 33)"
    validations:
      required: true
  - type: dropdown
    id: stt-provider
    attributes:
      label: STT provider
      options:
        - Whisper
        - Parakeet
    validations:
      required: true
  - type: input
    id: stt-model
    attributes:
      label: STT model
      placeholder: "e.g. Whisper tiny, Parakeet 110M INT8"
    validations:
      required: true
  - type: input
    id: app-version
    attributes:
      label: App version
      placeholder: "e.g. 1.1.0"
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: Steps to reproduce
      value: |
        1.
        2.
        3.
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
    validations:
      required: true
  - type: textarea
    id: actual
    attributes:
      label: Actual behavior
    validations:
      required: true
```

### Complete feature_request.yml
```yaml
# Source: https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms
name: Feature request
description: Suggest an improvement or new feature
labels: ["enhancement"]
body:
  - type: textarea
    id: use-case
    attributes:
      label: Use case
      description: What problem does this solve? What are you trying to do?
    validations:
      required: true
  - type: textarea
    id: proposed-solution
    attributes:
      label: Proposed solution
      description: How would you like this to work?
    validations:
      required: false
```

### README badge block (top of file, after H1)
```markdown
[![CI](https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/ci.yml?branch=main&label=CI)](https://github.com/getdictus/dictus-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/actions/workflow/status/getdictus/dictus-android/release.yml?branch=main&label=Release)](https://github.com/getdictus/dictus-android/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/github/license/getdictus/dictus-android)](LICENSE)
[![Android 10+](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
```

### README Contributing section (to add before Tech Stack)
```markdown
## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for build setup, module overview, and PR guidelines.

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md).
```

### README screenshot placement
```markdown
## Screenshots

| Keyboard | Model Manager | Settings |
|----------|---------------|----------|
| ![Keyboard in action](screenshots/keyboard.png) | ![Model manager](screenshots/models.png) | ![Settings](screenshots/settings.png) |
```
Screenshots stored at `screenshots/keyboard.png`, `screenshots/models.png`, `screenshots/settings.png` (PNG, taken from Pixel 4, portrait orientation).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Markdown issue templates (`.md`) | YAML issue forms (`.yml`) | GitHub ~2021 | Structured fields, auto-labeling, required validation |
| `github/workflow/status/{name}` badge URL | `github/actions/workflow/status/{repo}/{file.yml}` badge URL | shields.io ~2022 | Must use filename, not workflow name |
| `CODE_OF_CONDUCT.md` linked separately | GitHub detects and surfaces it automatically | GitHub ~2019 | GitHub shows "Community Standards" health check |

**Deprecated/outdated:**
- `/.github/ISSUE_TEMPLATE/*.md` (plain Markdown templates): Still works but lacks structured fields, auto-labeling, required validation. Use YAML `.yml` templates instead.
- `https://img.shields.io/github/workflow/status/...` badge URL format: Deprecated. Use `github/actions/workflow/status/...` with filename.

---

## Open Questions

1. **Screenshots: manual capture or defer?**
   - What we know: OSS-04 requires screenshots; the locked decision specifies 2-3 screenshots (keyboard, model manager, settings); no existing screenshots in repo.
   - What's unclear: Whether screenshots should be captured as part of this phase's automated plan, or flagged as a manual step Pierre must do on his Pixel 4.
   - Recommendation: Flag as a required manual step in the plan. The implementer creates the `screenshots/` directory, adds placeholder README references, and documents the manual capture step. Pierre takes screenshots on device and commits them. This is the standard OSS approach — no automation needed.

2. **CODE_OF_CONDUCT contact method**
   - What we know: Contributor Covenant expects `[INSERT CONTACT METHOD]` to be filled in; typical value is an email address.
   - What's unclear: Pierre hasn't specified a conduct email.
   - Recommendation: Use `pierre@getdictus.com` (or the email associated with the GitHub account) as the contact method. If that email doesn't exist, the safest fallback is opening a GitHub issue tagged `[conduct]` — but this is less private than email (CC 2.1 emphasizes reporter confidentiality). The planner should leave a clear placeholder comment and note this as the one item requiring Pierre's input.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric (existing, all modules) |
| Config file | `build.gradle.kts` in each module (`testImplementation("junit:junit:4.13.2")`) |
| Quick run command | `./gradlew testDebugUnitTest` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OSS-01 | CONTRIBUTING.md exists and contains required sections | manual-only | N/A — file existence check: `ls CONTRIBUTING.md` | N/A |
| OSS-02 | Issue template YAML files parse correctly | manual-only | N/A — validated by GitHub UI when pushed | N/A |
| OSS-02 | PR template exists | manual-only | `ls .github/PULL_REQUEST_TEMPLATE.md` | N/A |
| OSS-03 | CODE_OF_CONDUCT.md exists and is Contributor Covenant 2.1 | manual-only | N/A — content review | N/A |
| OSS-04 | README has badges, screenshots, contributing section, license notice | manual-only | N/A — content review | N/A |

**Note:** All OSS phase requirements are documentation artifacts. Automated unit tests cannot meaningfully validate documentation completeness. The validation gate is a human review checklist against the success criteria.

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest` (ensures no accidental code regression from any file changes)
- **Per wave merge:** `./gradlew test lint`
- **Phase gate:** All 6 files created + README enhanced, human review of each file against success criteria before `/gsd:verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers all phase requirements. This phase adds no new code, so no new test files are needed.

---

## Sources

### Primary (HIGH confidence)
- [GitHub Docs: Syntax for issue forms](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms) — YAML field types, required top-level fields, body structure
- [GitHub Docs: Syntax for GitHub form schema](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-githubs-form-schema) — input/textarea/dropdown/checkboxes attributes
- [choosealicense.com: MIT License](https://choosealicense.com/licenses/mit/) — MIT license exact text with placeholders
- [contributor-covenant.org v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) — Contributor Covenant 2.1 structure and enforcement ladder
- [shields.io: GitHub Actions Workflow Status](https://shields.io/badges/git-hub-actions-workflow-status) — Badge URL format with filename parameter

### Secondary (MEDIUM confidence)
- [shields.io GitHub issue #8671](https://github.com/badges/shields/issues/8671) — Confirmed routing change from workflow name to filename (cross-verified with official shields.io docs)

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack (files/locations): HIGH — GitHub documentation is authoritative, file locations are fixed conventions
- Architecture (YAML template field types): HIGH — verified directly from GitHub's form schema docs
- Badge URL format: HIGH — verified from shields.io official docs + known routing change
- MIT license text: HIGH — from choosealicense.com (OSI-maintained source)
- Contributor Covenant 2.1: HIGH — from contributor-covenant.org official site
- Pitfalls: MEDIUM — based on known GitHub behavior patterns; YAML template location pitfall is well-documented

**Research date:** 2026-04-01
**Valid until:** 2026-07-01 (stable GitHub conventions; shields.io URL format stable since 2022)
