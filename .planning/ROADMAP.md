# Roadmap: Dictus Android

## Milestones

- ✅ **v1.0 MVP** — Phases 1-6 (shipped 2026-03-30)
- 🚧 **v1.1 Public Beta** — Phases 7-11 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-6) — SHIPPED 2026-03-30</summary>

- [x] Phase 1: Core Foundation + Keyboard Shell (4 plans) — completed 2026-03-22
- [x] Phase 2: Audio Recording + Service Architecture (3 plans) — completed 2026-03-25
- [x] Phase 02.1: App Shell + Testability (2 plans, INSERTED) — completed 2026-03-25
- [x] Phase 3: Whisper Integration (3 plans) — completed 2026-03-25
- [x] Phase 4: Model Management + Onboarding (8 plans) — completed 2026-03-27
- [x] Phase 5: Polish + Differentiators (5 plans) — completed 2026-03-28
- [x] Phase 6: Release Readiness (4 plans) — completed 2026-03-30

</details>

### 🚧 v1.1 Public Beta (In Progress)

**Milestone Goal:** Extend the shipped MVP with production-grade text prediction, a second STT engine (Parakeet), formalized beta distribution via GitHub Releases CI, and a contribution-ready OSS repository — all while clearing v1.0 tech debt before going public.

- [x] **Phase 7: Foundation** — SttProvider interface + v1.0 tech debt clearance (completed 2026-03-30)
- [x] **Phase 8: Text Prediction** — Production suggestion engine replacing the stub (gap closure in progress) (completed 2026-03-31)
- [x] **Phase 9: Parakeet Integration** — Multi-provider STT with sherpa-onnx (completed 2026-04-01)
- [x] **Phase 10: Beta Distribution + License Audit** — CI pipeline, signed releases, license clean pass (completed 2026-04-01)
- [x] **Phase 11: OSS Repository** — Public repo scaffolding for open contribution (completed 2026-04-01)

## Phase Details

### Phase 7: Foundation
**Goal**: The codebase is ready for multi-provider STT and a public beta — v1.0 bugs are fixed, and the SttProvider abstraction exists before any new engine code is written.
**Depends on**: Nothing (first phase of v1.1, builds on v1.0 codebase)
**Requirements**: DEBT-01, DEBT-02, DEBT-03, STT-01
**Success Criteria** (what must be TRUE):
  1. The AZERTY/QWERTY toggle in Settings propagates to the keyboard immediately without restarting the IME
  2. Notifications display correctly on API 33+ devices (POST_NOTIFICATIONS declared in AndroidManifest)
  3. The full test suite passes with zero stale assertions (AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest fixed or removed)
  4. A `SttProvider` interface exists in `core/` and `WhisperProvider` wraps the existing whisper.cpp engine behind it — no behavior change for users, but all STT calls route through the interface
**Plans:** 3/3 plans complete
Plans:
- [ ] 07-01-PLAN.md — POST_NOTIFICATIONS manifest fix + stale test fixes (DEBT-02, DEBT-03)
- [ ] 07-02-PLAN.md — SttProvider interface extraction + WhisperProvider rename (STT-01)
- [ ] 07-03-PLAN.md — AZERTY/QWERTY DataStore observation in IME (DEBT-01)

### Phase 8: Text Prediction
**Goal**: Users see real, ranked word suggestions while typing and after dictation — the stub suggestion engine is replaced by a production binary dictionary ranker.
**Depends on**: Phase 7 (clean codebase baseline)
**Requirements**: SUGG-01, SUGG-02, SUGG-03
**Success Criteria** (what must be TRUE):
  1. While typing, the suggestion bar shows up to 3 contextually ranked candidates from a FR+EN dictionary
  2. Tapping a suggestion replaces the in-progress word and inserts a trailing space
  3. Words the user types frequently appear in suggestions over time (personal word dictionary)
  4. Suggestion lookups do not cause keyboard lag (validated by microbenchmark: 1000 sequential lookups on `dict-worker` thread)
**Plans:** 3/3 plans complete
Plans:
- [x] 08-01-PLAN.md — DictionaryEngine + PersonalDictionary + dictionary assets + full test suite (SUGG-01, SUGG-03)
- [x] 08-02-PLAN.md — Wire DictionaryEngine into DictusImeService + device verification (SUGG-02)
- [ ] 08-03-PLAN.md — Gap closure: wire PersonalDictionary.recordWordTyped into DictusImeService (SUGG-03)

### Phase 9: Parakeet Integration
**Goal**: Users can download and use a Parakeet/Nvidia STT model as an alternative to Whisper — both engines coexist safely without OOM or crashes.
**Depends on**: Phase 7 (SttProvider interface must exist)
**Requirements**: STT-02, STT-03, STT-04, STT-05
**Success Criteria** (what must be TRUE):
  1. The user can download the Parakeet 110M CTC model from the model manager and use it for transcription
  2. The user can switch the active STT provider (Whisper / Parakeet) in Settings and the change takes effect on the next recording
  3. The model manager shows a provider badge (Whisper / Parakeet) on each model card with a descriptive note per provider
  4. When a Parakeet model is selected and the UI language is not English, the user sees a visible warning about the English-only limitation
  5. Loading a Parakeet model automatically unloads the Whisper engine (and vice versa) — simultaneous load is blocked
**Plans:** 3/3 plans complete
Plans:
- [ ] 09-01-PLAN.md — asr/ module + sherpa-onnx native libs + ParakeetProvider (STT-02)
- [ ] 09-02-PLAN.md — Parakeet catalog entries + download infrastructure + tests (STT-02, STT-04)
- [ ] 09-03-PLAN.md — Dynamic provider dispatch + UI badges/warnings + activation dialog + device verify (STT-03, STT-04, STT-05)

### Phase 10: Beta Distribution + License Audit
**Goal**: Every push to main is validated by CI, every version tag produces a signed APK on GitHub Releases, and the project's OSS dependency licenses are audited and correct.
**Depends on**: Phase 9 (full feature set built before first public release)
**Requirements**: BETA-01, BETA-02, BETA-03, LIC-01, LIC-02
**Success Criteria** (what must be TRUE):
  1. Pushing to main or opening a PR triggers a GitHub Actions workflow that runs lint, unit tests, and a debug APK build — results are visible on the PR
  2. Pushing a `v*` tag produces a signed release APK automatically uploaded to GitHub Releases within one CI run
  3. The README contains step-by-step instructions for downloading and sideloading the beta APK
  4. The in-app licenses screen correctly lists all OSS dependencies (whisper.cpp, sherpa-onnx, FlorisBoard-derived components, etc.) with their licenses
  5. `gradle-license-plugin` runs as part of the build and its audit report shows no GPL-contaminated dependencies
**Plans:** 3/3 plans complete
Plans:
- [ ] 10-01-PLAN.md — CI + Release GitHub Actions workflows + APK signing config (BETA-01, BETA-02)
- [ ] 10-02-PLAN.md — cashapp/licensee license audit plugin + LicencesScreen auto-generation (LIC-01, LIC-02)
- [ ] 10-03-PLAN.md — README with beta installation instructions (BETA-03)

### Phase 11: OSS Repository
**Goal**: The GitHub repository is ready for public contributions — it has the documentation, templates, and community standards that make contribution frictionless.
**Depends on**: Phase 10 (license audit must pass before repo goes public)
**Requirements**: OSS-01, OSS-02, OSS-03, OSS-04
**Success Criteria** (what must be TRUE):
  1. A contributor can clone the repo and build the app by following CONTRIBUTING.md without asking questions (build prerequisites, architecture overview, PR process)
  2. Filing a bug or feature request presents the user with a structured YAML template that collects actionable information
  3. CODE_OF_CONDUCT.md (Contributor Covenant 2.1) is present and linked from README
  4. The README provides a complete project description, screenshots, installation instructions, contribution guide, and license notice
**Plans:** 3/3 plans complete
Plans:
- [ ] 11-01-PLAN.md — LICENSE, CODE_OF_CONDUCT.md, CONTRIBUTING.md (OSS-01, OSS-03)
- [ ] 11-02-PLAN.md — Issue templates (bug report, feature request) + PR template (OSS-02)
- [ ] 11-03-PLAN.md — README enhancement with badges, screenshots, contributing section (OSS-04)

## Progress

**Execution Order:** 7 → 8 → 9 → 10 → 11

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Core Foundation + Keyboard Shell | v1.0 | 4/4 | Complete | 2026-03-22 |
| 2. Audio Recording + Service Architecture | v1.0 | 3/3 | Complete | 2026-03-25 |
| 2.1 App Shell + Testability | v1.0 | 2/2 | Complete | 2026-03-25 |
| 3. Whisper Integration | v1.0 | 3/3 | Complete | 2026-03-25 |
| 4. Model Management + Onboarding | v1.0 | 8/8 | Complete | 2026-03-27 |
| 5. Polish + Differentiators | v1.0 | 5/5 | Complete | 2026-03-28 |
| 6. Release Readiness | v1.0 | 4/4 | Complete | 2026-03-30 |
| 7. Foundation | v1.1 | 3/3 | Complete | 2026-03-30 |
| 8. Text Prediction | 3/3 | Complete   | 2026-03-31 | - |
| 9. Parakeet Integration | 3/3 | Complete   | 2026-04-01 | - |
| 10. Beta Distribution + License Audit | 3/3 | Complete    | 2026-04-01 | - |
| 11. OSS Repository | 3/3 | Complete    | 2026-04-01 | - |
