# Feature Landscape

**Domain:** Android voice dictation custom keyboard (on-device, privacy-first)
**Researched:** 2026-03-30 (v1.1 update — appended to v1.0 research)
**Confidence:** MEDIUM-HIGH (competitive analysis, PRD, iOS parity, Android IME docs, GitHub ecosystem research)

> This file covers both v1.0 features (shipped, preserved for reference) and v1.1 new features (text prediction, Parakeet STT, beta distribution, OSS preparation). The v1.1 section is the primary focus of this research pass.

---

## Competitive Context

The Android voice dictation keyboard space has distinct tiers:

1. **Mainstream keyboards with voice** — Gboard (dominant), SwiftKey, Samsung Keyboard. Voice is a feature, not the product.
2. **Overlay dictation apps** — Wispr Flow (cloud-based, overlay bubble, works with any keyboard). Not a keyboard itself.
3. **Privacy-first keyboards** — FUTO Keyboard (full keyboard + offline Whisper voice input, open source, closest competitor).
4. **Whisper-only keyboards** — Transcribro, WhisperInput, Kaiboard. Voice-only input, no full keyboard layout.

Dictus sits at the intersection of tiers 3 and 4: a full custom keyboard with integrated on-device Whisper dictation, privacy-first, open source. FUTO Keyboard is the direct competitor.

---

## v1.0 Features (Shipped — Reference Only)

### Table Stakes (Shipped in v1.0)

| Feature | Why Expected | Complexity | Status |
|---------|--------------|------------|--------|
| Full letter layout (AZERTY/QWERTY) | Every keyboard has it. Without a complete typing experience, users return to Gboard. | Medium | Done |
| Number and symbol layers | Standard keyboard feature. Users type numbers, punctuation, special characters constantly. | Low | Done |
| Backspace, enter, space bar | Fundamental text editing. Enter key must respect `imeOptions`. | Low | Done |
| Microphone button (dictation trigger) | Core product purpose. Must be prominent and easy to reach. | Low | Done |
| On-device STT (whisper.cpp) | Core value proposition. Works offline, no cloud. | High | Done |
| Recording feedback (waveform animation) | User must know the app is listening. | Medium | Done |
| Text insertion into any app | `InputConnection.commitText()` in any text field. | Low | Done |
| Settings screen | Model selection, language, layout, haptics, sound, theme. | Low | Done |
| Onboarding flow (7 steps) | IME activation on Android is confusing. Must guide users through setup. | Medium | Done |
| Model download/management | Download from HuggingFace, show progress, storage indicator, delete. | Medium | Done |
| Haptic + sound feedback | Expected by users of any keyboard. Toggleable. | Low | Done |
| Dark theme (Material You) | Modern keyboards default to dark. | Low | Done |
| Emoji picker with categories | Full emoji input within the keyboard. | Medium | Done |
| Stub suggestion bar | Suggestion bar UI with 40-word prefix matching (StubSuggestionEngine). | Low | Done — needs replacement |
| Bilingual FR/EN UI | French default, English fallback in Android string resources. | Low | Done |
| Debug logging (Timber + file export) | Essential for beta testing without crash reporting service. | Low | Done |

---

## v1.1 Features (New — This Milestone's Focus)

### Area 1: Text Prediction (Suggestion Bar — Production Engine)

**Context:** v1.0 ships a `StubSuggestionEngine` doing prefix matching over 40 hardcoded words. This is visible in the suggestion strip but produces no useful suggestions for real input. v1.1 replaces it with a real dictionary-backed engine.

**How production Android keyboards implement suggestion strips (from research):**

The standard Android IME pattern uses `onCreateCandidatesView()` to display a horizontal strip of 3 word candidates above the keyboard. When the user taps a suggestion:
1. The IME calls `deleteSurroundingText(n, 0)` to delete the typed prefix.
2. The IME calls `commitText(suggestion, 1)` to insert the chosen word.

For Dictus specifically, the suggestion strip serves a different purpose than real-time typing prediction: it is primarily for **post-dictation correction** (showing alternative word candidates after a transcription is committed). This changes the complexity profile significantly — there is no need for sub-50ms latency or composing-text state management that per-keystroke prediction requires.

#### Table Stakes for Suggestion Bar (v1.1)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Show top-3 word candidates after dictation | Users expect to see alternatives if transcription is wrong. Every production keyboard offers this for typed input; for dictation, post-hoc correction is the primary use case. | MEDIUM | Load binary .dict, rank by prefix/frequency, display in existing suggestion strip UI. Existing strip UI stays unchanged. |
| Tap to replace transcribed text | Standard suggestion strip behavior: tap replaces last committed word. | LOW | `deleteSurroundingText` + `commitText`. Maps directly to existing `InputConnection` usage in v1.0. |
| Dismiss / ignore suggestions | User must be able to continue without acting on suggestions. | LOW | No action needed — the existing strip already supports this; suggestions simply update on next input event. |
| FR + EN dictionary support | Dictus is bilingual; suggestion engine must match the active transcription language. | MEDIUM | Two binary .dict files (~3 MB FR, ~4 MB EN) loaded conditionally based on `activeLanguage` in DataStore. |

#### Differentiators for Suggestion Bar (v1.1)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Bigram context scoring | Using the word before the transcribed word to rank candidates (e.g., "il fait" suggests "beau" above "bois"). Improves relevance without ML. | MEDIUM | AOSP binary .dict format includes bigram frequency data. A Kotlin reader can extract it without NDK. This is a step above pure prefix frequency. |
| Post-dictation confidence indicator | Show a subtle visual cue (color, underline) on the committed word when Whisper's confidence is below threshold. Signals to user that correction may be needed. | MEDIUM | Requires Whisper to expose per-token confidence. whisper.cpp supports this via `token.p`. Needs surfacing through JNI layer. |

#### Anti-Features for Suggestion Bar

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| AOSP LatinIME C++ port (`libjni_latinime.so`) | No Maven artifact exists. Requires full AOSP toolchain to build. HeliBoard and OpenBoard have it tightly coupled to their own IME — extracting it as a standalone Gradle dependency has never been done cleanly by the community. For post-dictation correction, the latency benefit of C++ is irrelevant. | Binary .dict files + Kotlin prefix ranker (no NDK) |
| HeliBoard's `Dictionary.kt` source code | GPL v3. Including it in a MIT-licensed app requires re-licensing the entire app under GPL v3. | Use the Apache 2.0 .dict binary files from `Helium314/aosp-dictionaries` only; write own reader. |
| Per-keystroke real-time prediction | Dictus's primary text input path is dictation, not typing. Building real-time typing prediction (< 50ms per keystroke) requires composing-text state management, a significant new complexity surface. Users who want elite typing prediction keep Gboard as a secondary keyboard. | Post-dictation correction only. Real-time typing suggestions are a v2+ consideration. |
| ML-based autocorrect | Training or shipping ML language models for autocorrect is disproportionate effort for Dictus's use case. FUTO has years of investment here. | Simple frequency-ranked dictionary suggestions. |
| User word learning (personal dictionary writes) | Persisting user corrections to a personal dictionary requires managing mutable binary .dict files or a separate SQLite store. Adds complexity without clear benefit for dictation-primary users. | Defer to v2. Read-only dictionary is correct for v1.1. |

---

### Area 2: Multi-Provider STT (Parakeet / Nvidia)

**Context:** v1.0 ships with a single STT provider (whisper.cpp). v1.1 adds Parakeet as a second provider, requiring an abstraction layer and updated model manager UX.

**How production apps handle multi-provider model management (from research):**

Apps with multiple AI models (e.g., offline speech apps) typically use a provider card pattern in the model list: each card shows the provider name/logo, model name, size, language support, and download state. The active model is indicated with a selected state (checkmark or highlighted border). Switching providers requires downloading the new provider's model first. Download UX for large models (100+ MB) requires deterministic progress display and the ability to cancel and resume.

#### Table Stakes for Parakeet / Multi-Provider (v1.1)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `SttProvider` interface in `core` module | Without an abstraction, adding a second provider requires forking `DictationService` logic. This is the architectural foundation everything else rests on. | LOW | `interface SttProvider { val id, displayName; suspend fun transcribe(...); fun isModelReady() }`. Whisper implements it, Parakeet implements it. |
| Model cards with provider badge in model manager | Users need to understand which engine backs each model. "Whisper (tiny)", "Whisper (small.q5_1)", "Parakeet (110M)" should be visually distinct. | MEDIUM | Add `provider: SttProvider` field to `ModelInfo` data class. Update model list UI to show provider badge. Existing model manager screens need visual update. |
| Active provider persisted in DataStore | The selected STT provider/model must survive app restarts. | LOW | Add `active_stt_provider` key to DataStore. `DictationService` reads it on bind to select the right `SttProvider` implementation. |
| Download UX for 126 MB Parakeet model | 126 MB is ~4x larger than whisper tiny quantized (~37 MB). Users must see meaningful progress (not just a spinner) and be able to cancel. | MEDIUM | Reuse OkHttp download flow from v1.0 model manager. Show MB downloaded / total MB. Persist partial downloads across app restarts (ETag + Range headers). |
| Switch provider in Settings | Users must be able to change the active STT engine from the Settings screen. | LOW | Extend existing "Active model" settings row to show provider group, or add a separate "STT Engine" setting. |
| English-only Parakeet label | Parakeet 110M CTC supports English only. The UI must make this clear to avoid user confusion when Whisper is set to French. | LOW | Show language support tag on model card ("English only"). Warn user if active language is French and they select Parakeet. |

#### Differentiators for Parakeet (v1.1)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Provider notes / description on model card | A short text blurb on each model card ("Fast English-only transcription, Nvidia NeMo architecture" vs "Multilingual, French + English, OpenAI Whisper") helps users make an informed choice. | LOW | Static strings per provider. No network lookup needed. |
| Auto-fallback to Whisper if Parakeet model not downloaded | If user switches language to French while Parakeet is active, automatically fall back to the best available Whisper model and surface a toast explaining why. | MEDIUM | Language-to-provider compatibility check in `DictationService.onStartCommand`. |

#### Anti-Features for Parakeet

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Parakeet TDT 0.6B model | 640 MB total (INT8). IME process memory budget is typically 256–512 MB on most OEMs. This model will OOM in the IME process on Pixel 4 and most real-world devices. | 110M CTC model (126 MB INT8) only. |
| NNAPI execution provider for sherpa-onnx | Marked experimental in ONNX Runtime. Causes crashes on some Snapdragon 855 configurations (Pixel 4's SoC). | CPU execution provider (XNNPACK optional but safe). |
| Building sherpa-onnx from source | Multi-hour NDK build, no added benefit. AAR pre-built artifact exists at v1.12.34. | Use the pre-built AAR from GitHub Releases. |
| Streaming transcription (CTC streaming mode) | Parakeet 110M CTC supports streaming, but Dictus's audio pipeline is built for batch mode (record → stop → transcribe). Adapting the pipeline adds significant complexity for marginal UX gain in v1.1. | Batch mode only in v1.1. Streaming is a v2 feature. |
| Adding `com.microsoft.onnxruntime:onnxruntime-android` as a separate Gradle dep | sherpa-onnx AAR bundles ONNX Runtime internally. Adding it separately creates version conflicts at runtime. | Let sherpa-onnx AAR manage its own ONNX Runtime. |

---

### Area 3: Beta Distribution (APK via GitHub Releases)

**Context:** v1.0 was distributed via direct APK sideloading. v1.1 formalizes this into a repeatable GitHub Releases CI/CD pipeline with versioning conventions and feedback collection.

**How OSS Android apps handle beta distribution without Play Store (from research):**

The standard pattern is: git tag push → GitHub Actions CI → signed APK → GitHub Release with changelog. Users subscribe to GitHub Release notifications or check the Releases page. For feedback, GitHub Issues is the lowest-friction option that doesn't introduce any cloud dependencies. In-app update checks can be implemented against the GitHub Releases API (polling latest release tag vs the app's current `versionName`).

#### Table Stakes for Beta Distribution (v1.1)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Signed release APK on GitHub Releases | Beta testers need a signed, installable APK. Unsigned debug builds won't install on devices with standard security settings. | MEDIUM | GitHub Actions + `r0adkll/sign-android-release` action. Release keystore stored as base64 GitHub Secret. |
| Automated APK build on git tag | Manual build-and-upload is error-prone. CI must produce the artifact automatically on `v*` tag push. | LOW | `.github/workflows/release.yml` triggered on `push: tags: ['v*']`. |
| Semantic versioning (`versionName` + `versionCode`) | Users need to identify which version they have. `versionName` = `1.1.0-beta01`, `versionCode` = integer monotonically increasing. | LOW | Convention: `MAJOR.MINOR.PATCH-betaNN`. Update `versionCode` on every release (CI can derive from tag). `versionName` from git tag. |
| Changelog in GitHub Release body | Beta testers need to know what changed. Without a changelog, feedback is not contextualized. | LOW | Markdown changelog written manually in the GitHub Release description. For v1.1, manual is fine; semantic-release automation is a v2 optimization. |
| GitHub Issues for feedback collection | Zero-friction feedback channel. Users already have GitHub accounts if they found a privacy-first OSS keyboard. | LOW | YAML issue templates (bug report + feature request). Link from Settings > Feedback and release notes. |

#### Differentiators for Beta Distribution (v1.1)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| In-app "check for updates" link in Settings | Beta testers forget to check for updates. A Settings row linking to the GitHub Releases page (no in-app download, just a link to browser) reduces the discovery gap without introducing any update-install permissions or complexity. | LOW | `Intent.ACTION_VIEW` to `https://github.com/[repo]/releases`. No library needed. |
| Version info in Settings footer | Showing `versionName` + `versionCode` in Settings > About lets testers quickly verify which build they're running when filing bugs. | LOW | Already common in Android apps. Static display from `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`. |
| Debug log export for bug reports | Testers on non-developer devices cannot ADB logcat. The existing Timber file logger + export path in Settings is the only way to get logs from testers. | LOW | Already exists in v1.0. Ensure it's prominent in the bug report issue template (ask users to attach exported log). |

#### Anti-Features for Beta Distribution

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Firebase Crashlytics | Introduces a Google cloud dependency. Contradicts Dictus's "no cloud, no tracking" value proposition. Users who chose a privacy-first keyboard will notice. | Timber file logger (already in v1.0). Export path in Settings for bug reports. |
| Play Store Internal Testing / Beta track | Requires a paid Google Play Developer account ($25). For a v1.1 private beta of an OSS app, this is unnecessary overhead. | GitHub Releases APK distribution. |
| Firebase App Distribution | Same concern as Crashlytics — introduces Google cloud account dependency, requires testers to install the Firebase App Distribution apk. | GitHub Releases + direct APK install. |
| Fastlane | Fastlane adds Ruby dependency overhead and is optimized for Play Store (`supply`) and TestFlight lanes. No value for GitHub Releases-only distribution. | Native GitHub Actions. |
| Automated in-app APK download and install | Triggering APK download and `PackageInstaller` from inside the app requires `REQUEST_INSTALL_PACKAGES` permission, which is flagged as high-risk by security scanners and confusing to users. | Link to GitHub Releases in browser. User downloads and installs manually. |

---

### Area 4: OSS Repository Preparation

**Context:** The repo will be made public for v1.1. Contributors need standard OSS scaffolding: issue templates, PR template, CONTRIBUTING.md, CODE_OF_CONDUCT, CI checks. This is infrastructure, not features — but it directly affects whether contributors can engage.

**What contributors expect in OSS Android projects (from research):**

Contributors expect: a working build on first checkout (no manual setup steps beyond documented ones), a clear PR checklist that doesn't require maintainer clarification, and CI that validates their contribution before review. The most common friction points are: undocumented local build requirements (NDK version, Java version), no clear guidance on where to add new code, and PRs merged without tests. For Android specifically: contributors expect `./gradlew build` to work on a clean clone with documented Java/NDK prerequisites.

#### Table Stakes for OSS Repo (v1.1)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `CONTRIBUTING.md` (root level) | The first file contributors look for. Without it, setup questions land in Issues, wasting maintainer time. | LOW | Must include: prerequisites (Java 17, NDK 27.2, Android Studio version), clone steps, how to run on device, how to run tests, branch naming convention, commit message convention. |
| `.github/ISSUE_TEMPLATE/bug_report.yml` | YAML form templates enforce required fields (OS version, Android version, Dictus version, steps to reproduce, expected vs actual). Markdown templates allow submitting half-empty reports. | LOW | YAML format with required: true on key fields. Include a "logs" textarea. |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | Separate template from bug reports. Prevents feature requests from being filed as bugs. | LOW | Standard OSS feature request template. Keep short. |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR checklist prevents common mistakes: untested on device, missing translations, lint not run. | LOW | Checklist: tested on physical device, `./gradlew lintDebug` passes, `./gradlew testDebugUnitTest` passes, strings added in both `values/` and `values-fr/`, no TODO comments left. |
| `CODE_OF_CONDUCT.md` | Standard for any public OSS project. Contributor Covenant 2.1 is the de facto standard in 2026. | LOW | Copy Contributor Covenant 2.1. Set contact email. |
| CI workflow (lint + unit tests + debug build) | PRs without CI checks require manual verification by the maintainer. CI that runs lint, unit tests, and assembleDebug gives confidence that the contribution compiles and passes basic quality gates. | MEDIUM | `.github/workflows/ci.yml` triggered on push to `main` and PR to `main`. Steps: `lintDebug` → `testDebugUnitTest` → `assembleDebug` → upload debug APK as artifact. |
| `README.md` update | The public-facing introduction. Must explain what Dictus is, why it exists, how to install, how to build from source. | LOW | Add: screenshots, feature list, install instructions (download APK from Releases), build-from-source instructions, license badge, contribution invite. |
| OSS license audit report | Before going public, verify all dependencies are license-compatible with MIT distribution. AOSP .dict files (Apache 2.0) and sherpa-onnx AAR (MIT) are clear. HeliBoard source (GPL v3) must not be included. | MEDIUM | Run `./gradlew licenseDebugReport` with `gradle-license-plugin`. Review output for any GPL/LGPL/AGPL deps that conflict with MIT. Update `app/src/main/assets/open_source_licenses.html` in-app. |

#### Differentiators for OSS Repo (v1.1)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Build reproducibility section in CONTRIBUTING.md | Documenting exact NDK version (27.2), Java version (17 Temurin), and AGP version eliminates the most common "it doesn't build for me" contributor friction. This is specific to Android NDK projects where version mismatches cause silent failures. | LOW | One paragraph. Exact version pins. |
| `NOTICES.txt` / license attribution file | Good practice for MIT projects that bundle Apache 2.0 and MIT dependencies. Required if distributing to F-Droid eventually (F-Droid checks license compliance). | LOW | Generated by `gradle-license-plugin`. Commit to repo. Update in CI. |
| Architecture overview in CONTRIBUTING.md | A 10-line module map (app / ime / core / whisper / asr) prevents contributors from placing code in the wrong module. Without it, PRs regularly put things in `app/` when they belong in `core/`. | LOW | Describe the 5-module layout, what belongs in each, and the IPC pattern (Bound Service between `ime` and `app`). |

#### Anti-Features for OSS Repo

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Automated code formatting enforcement in CI (ktfmt/ktlint as CI gate) | For a first-time OSS release, adding a formatter as a hard CI gate immediately blocks all external contributors who don't have it configured in their IDE. | Document the code style (Kotlin official style guide) in CONTRIBUTING.md. Run ktlint locally as a suggestion, not a hard gate, in v1.1. Add it as a CI gate in v1.2 after the project has established contributor norms. |
| CLA (Contributor License Agreement) | Heavy legal overhead for a small OSS project. Discourages casual contributors. MIT license already handles rights adequately for a one-person maintainer project. | MIT license in repo root. No CLA. |
| Mandatory issue → PR linking | Requiring every PR to link an issue discourages small documentation fixes and typo PRs. | Recommend but don't enforce. Note it in CONTRIBUTING.md as "preferred for non-trivial changes". |
| Dependabot auto-merge PRs | sherpa-onnx AAR is a manually managed local file, not a Gradle dep that Dependabot can update. Auto-merge for Android deps is risky (AGP updates break NDK builds). | Enable Dependabot for awareness. Never enable auto-merge for Android projects with NDK. |

---

## Feature Dependencies (v1.1)

```
[SttProvider interface in core]
    └──required by──> [ParakeetProvider (asr module)]
    └──required by──> [WhisperProvider refactor]
                          └──enables──> [Provider selection in DictationService]
                                            └──enables──> [Provider badge in model manager UI]
                                                              └──enables──> [Switch engine in Settings]

[Binary .dict files in assets]
    └──required by──> [Kotlin BinaryDictRanker]
                          └──replaces──> [StubSuggestionEngine]
                                            └──feeds──> [Existing suggestion strip UI] (no UI change needed)

[Whisper JNI token confidence]
    └──optional for──> [Post-dictation confidence indicator on suggestion]

[GitHub Actions CI workflow]
    └──required by──> [Signed release APK on GitHub Releases]
    └──required by──> [PR validation (lint + tests)]

[GitHub issue templates]
    └──required by──> [Meaningful beta feedback]

[License audit (gradle-license-plugin)]
    └──required by──> [OSS public release (no GPL contamination)]

[CONTRIBUTING.md + README]
    └──required by──> [First external contributor can build and submit PR]
```

### Dependency Notes

- **SttProvider interface must exist before Parakeet work starts:** Both Whisper and Parakeet implementations depend on the same interface in `core`. Implementing Parakeet first (without the interface) creates a tangled merge later.
- **Binary .dict ranker is independent of STT work:** Can be developed in parallel with Parakeet integration. No shared code paths.
- **OSS repo prep (CI, templates, CONTRIBUTING.md) is independent of feature work:** Can be done in parallel or first. Doing it first means all v1.1 feature PRs go through the same CI pipeline.
- **License audit must complete before public repo:** A GPL-contaminated public repo is harder to fix retroactively than preventing it pre-launch.

---

## MVP Definition for v1.1

### Launch With (v1.1 Public Beta)

- [ ] **SttProvider interface** — foundation for all multi-provider work
- [ ] **Parakeet 110M integration** (sherpa-onnx AAR, batch mode, English only) — the headline new feature
- [ ] **Model cards with provider badge** — users must understand which engine they're selecting
- [ ] **Binary .dict suggestion engine** (FR + EN, top-3 candidates, tap to replace) — replaces stub, closes the most visible "not production-ready" gap
- [ ] **Signed APK on GitHub Releases via CI** — required for beta distribution
- [ ] **Semantic versioning convention** (`1.1.0-beta01`) — beta testers need version identification
- [ ] **GitHub issue templates** (bug + feature) — enables actionable feedback
- [ ] **CONTRIBUTING.md + README update** — required for public OSS repo
- [ ] **CODE_OF_CONDUCT.md** — standard for any public repo
- [ ] **License audit** — must be clean before public release
- [ ] **Tech debt fixes** (settings AZERTY toggle, POST_NOTIFICATIONS, stale test assertions) — ship quality matters

### Add After Validation (v1.1.x)

- [ ] **Bigram context scoring** — once basic suggestions are validated, improve ranking quality
- [ ] **Post-dictation confidence indicator** — requires surfacing whisper.cpp `token.p` through JNI
- [ ] **Auto-fallback to Whisper when language conflicts with Parakeet** — quality of life, not blocker
- [ ] **In-app "check for updates" Settings link** — low effort, helps testers stay current
- [ ] **ktlint as CI gate** — add after contributor norms are established

### Future Consideration (v2+)

- [ ] **Streaming transcription** (Parakeet CTC streaming mode) — requires audio pipeline refactor
- [ ] **Voice Activity Detection (auto-stop on silence)** — Silero VAD or WebRTC VAD
- [ ] **GPU/NNAPI acceleration** — experimental on Pixel 4's Snapdragon 855
- [ ] **Personal word dictionary (user learning)** — mutable .dict or SQLite
- [ ] **Play Store distribution** — deferred; requires Google account, policy compliance review for keyboard apps
- [ ] **F-Droid distribution** — feasible post v1.1 if license audit is clean; requires reproducible builds

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| SttProvider interface | LOW (invisible) | LOW | P1 — architectural gate |
| Parakeet 110M integration | HIGH | MEDIUM | P1 |
| Model cards with provider badge | HIGH | MEDIUM | P1 |
| Binary .dict suggestion engine (basic) | HIGH | MEDIUM | P1 |
| Signed APK CI pipeline | HIGH (beta access) | LOW | P1 |
| Versioning convention | MEDIUM | LOW | P1 |
| GitHub issue templates | MEDIUM | LOW | P1 |
| CONTRIBUTING.md + README | MEDIUM | LOW | P1 |
| CODE_OF_CONDUCT | LOW (but required) | LOW | P1 |
| License audit | LOW (invisible) | MEDIUM | P1 — pre-public gate |
| Tech debt fixes (v1.0) | MEDIUM | LOW | P1 |
| Bigram context scoring | MEDIUM | MEDIUM | P2 |
| Post-dictation confidence indicator | MEDIUM | MEDIUM | P2 |
| Auto-fallback language ↔ provider | MEDIUM | LOW | P2 |
| In-app update link | LOW | LOW | P2 |
| ktlint CI gate | LOW | LOW | P3 |

**Priority key:**
- P1: Must have for v1.1 public beta launch
- P2: Should have, add in v1.1.x if time permits
- P3: Nice to have, v1.2+

---

## Competitor Feature Analysis (v1.1 Scope)

| Feature | FUTO Keyboard | HeliBoard | Dictus v1.1 |
|---------|---------------|-----------|-------------|
| Multi-provider STT | No (Whisper only) | No (no STT) | Yes — Whisper + Parakeet |
| Text prediction engine | Full LatinIME C++ port | Full LatinIME C++ port | Binary .dict + Kotlin ranker (lighter, no NDK) |
| Beta distribution | GitHub Releases + F-Droid | F-Droid | GitHub Releases only (F-Droid deferred) |
| OSS repo health | CONTRIBUTING.md, CI | CONTRIBUTING.md, CI | Full set: templates, CI, COC, license audit |
| License compliance tooling | Not documented | Not documented | `gradle-license-plugin` generating `NOTICES.txt` |
| In-app update notification | Not present | Not present | Settings link to Releases page (v1.1.x) |

---

## Sources

- [Android developer docs: Creating an Input Method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) — candidates view, suggestion commit pattern (HIGH)
- [Helium314/HeliBoard GitHub](https://github.com/Helium314/HeliBoard) — binary .dict usage, GPL v3 license (HIGH)
- [Helium314/aosp-dictionaries (Codeberg)](https://codeberg.org/Helium314/aosp-dictionaries) — Apache 2.0 binary .dict files for FR + EN (MEDIUM)
- [FUTO Keyboard](https://keyboard.futo.org/) — closest open-source competitor with offline Whisper (HIGH)
- [sherpa-onnx GitHub Releases v1.12.34](https://github.com/k2-fsa/sherpa-onnx/releases) — AAR artifacts, Parakeet model URLs (HIGH)
- [sherpa-onnx Parakeet CTC 110M model docs](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html) — 126 MB INT8 confirmed (HIGH)
- [r0adkll/sign-android-release](https://github.com/r0adkll/sign-android-release) — GitHub Action for APK signing (HIGH)
- [softprops/action-gh-release](https://github.com/softprops/action-gh-release) — GitHub Releases upload (HIGH)
- [gradle-license-plugin](https://github.com/jaredsburrows/gradle-license-plugin) — license audit for Android (HIGH)
- [javiersantos/AppUpdater](https://github.com/javiersantos/AppUpdater) — in-app update check against GitHub Releases (MEDIUM — considered, not recommended for v1.1)
- [Android versioning docs](https://developer.android.com/studio/publish/versioning) — versionCode / versionName conventions (HIGH)
- [Semantic versioning 2.0.0](https://semver.org/) — version naming convention (HIGH)
- [Contributor Covenant 2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) — CODE_OF_CONDUCT standard (HIGH)
- [AOSP CONTRIBUTING.md](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/CONTRIBUTING.md) — contributor expectation reference (MEDIUM)
- [Grammarly Engineering: Building a Native Android Keyboard](https://www.grammarly.com/blog/engineering/how-grammarly-built-a-native-keyboard-for-android/) — production keyboard architecture patterns (MEDIUM)

---
*Feature research for: Dictus Android v1.1 — text prediction, Parakeet STT, beta distribution, OSS preparation*
*Researched: 2026-03-30*
