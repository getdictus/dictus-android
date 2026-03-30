# Project Research Summary

**Project:** Dictus Android v1.1 — New Capabilities
**Domain:** Android IME — on-device voice dictation keyboard with multi-provider STT, text prediction, and OSS distribution
**Researched:** 2026-03-30
**Confidence:** MEDIUM-HIGH

## Executive Summary

Dictus Android v1.1 extends a shipped v1.0 MVP (full keyboard + offline whisper.cpp STT) into a public OSS beta with four new capability areas: production-grade text prediction, a second STT engine (Parakeet via sherpa-onnx), formalized beta distribution via GitHub Releases CI, and OSS repository scaffolding. The recommended approach for each area avoids the obvious-but-wrong paths: AOSP's C++ LatinIME engine (no Maven artifact, build toolchain too heavy), the large Parakeet 0.6B model (640 MB, OOMs in IME process), Firebase Crashlytics (breaks on custom ROMs), and Fastlane (Play Store-centric, no value for APK-only distribution). In every case, a lighter, more compatible alternative exists: binary `.dict` files with a Kotlin ranker, the 110M Parakeet CTC model at 126 MB, Timber file logging, and native GitHub Actions.

The most important architectural decision for v1.1 is the `SttProvider` interface in `core/`. This interface must be defined and `WhisperProvider` implemented before any Parakeet code is written. The entire multi-provider feature tree depends on it as a gate, and building Parakeet without it produces a god-object `DictationService` that cannot be extended. Equally critical: mutual exclusivity between loaded STT models is not optional — loading whisper.cpp and Parakeet simultaneously on a 6 GB device under Android system load will trigger the OOM killer.

The primary risk to the v1.1 release is the `libc++_shared.so` symbol collision between whisper.cpp and sherpa-onnx. If both `.so` files embed incompatible STL versions, the process crashes on first ONNX inference — intermittently, and only on some devices. The fix (build whisper.cpp with `c++_static` and hide STL symbols) must be validated before any other Parakeet UI work proceeds. OSS repo preparation has a hard gate: `truffleHog` or equivalent must scan git history before the repo goes public, since HuggingFace URLs and any tokens ever touched are permanent if committed.

## Key Findings

### Recommended Stack

The v1.1 stack adds three new components on top of the existing Kotlin + Compose + whisper.cpp + Hilt + DataStore + OkHttp baseline. For text prediction: AOSP binary `.dict` files (Apache 2.0, ~3-4 MB each for FR and EN) from `Helium314/aosp-dictionaries` on Codeberg, loaded as ByteBuffer in Kotlin with a custom prefix ranker — no NDK required. For Parakeet STT: sherpa-onnx v1.12.34 AAR (MIT, pre-built, not on Maven Central — local AAR in `asr/libs/`), using the 110M CTC model at 126 MB INT8. For CI/CD: native GitHub Actions with `r0adkll/sign-android-release` and `softprops/action-gh-release`. For license auditing: `gradle-license-plugin` v0.9.8.

**Core new technologies:**
- **sherpa-onnx 1.12.34 AAR**: Parakeet ONNX inference via JNI — pre-built, MIT, no NDK build required
- **AOSP binary .dict files (Apache 2.0)**: FR + EN word frequency data for suggestion ranking — static assets, no C++ required
- **GitHub Actions + r0adkll/sign-android-release**: Automated signed APK release on `v*` tag push
- **gradle-license-plugin 0.9.8**: License audit report generation before OSS repo goes public
- **Parakeet 110M CTC INT8 (126 MB)**: The only feasible Parakeet model for an IME process; the 0.6B variant (640 MB) is explicitly excluded

**Critical version/compatibility constraints:**
- sherpa-onnx 1.12.34 AAR is not on Maven Central — must be downloaded from GitHub Releases and stored in `asr/libs/`
- Do NOT add `com.microsoft.onnxruntime:onnxruntime-android` as a separate Gradle dependency — sherpa-onnx bundles it; duplicate inclusion causes version conflicts
- HeliBoard source code (GPL v3) must not be included — only the Apache 2.0 binary `.dict` files are safe to use

### Expected Features

**Must have for v1.1 public beta (P1):**
- `SttProvider` interface in `core/` — architectural gate for all multi-provider work
- Parakeet 110M integration via sherpa-onnx AAR, batch mode, English only
- Model cards with provider badge in model manager (users must know which engine backs each model)
- Binary `.dict` suggestion engine (FR + EN, top-3 candidates, tap to replace) — replaces stub `StubSuggestionEngine`
- Signed APK on GitHub Releases via CI (tag push -> build -> sign -> release)
- Semantic versioning convention (`1.1.0-beta01`, monotonically increasing `versionCode`)
- GitHub YAML issue templates (bug + feature) for actionable beta feedback
- `CONTRIBUTING.md` and `README.md` update (build prerequisites, architecture overview)
- `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1)
- License audit clean pass before public repo (`gradle-license-plugin`, no GPL contamination)
- Tech debt fixes from v1.0 (settings AZERTY toggle, POST_NOTIFICATIONS, stale test assertions)

**Should have for v1.1.x (P2):**
- Bigram context scoring in suggestion engine (improves ranking without ML)
- Post-dictation confidence indicator (requires surfacing whisper.cpp `token.p` through JNI)
- Auto-fallback to Whisper when active language conflicts with Parakeet (English-only constraint)
- In-app "check for updates" Settings link (Intent to GitHub Releases page)

**Defer to v2+ (do not implement in v1.1):**
- Streaming transcription (Parakeet CTC streaming mode — requires audio pipeline refactor)
- Voice Activity Detection (Silero VAD or WebRTC VAD)
- GPU/NNAPI acceleration (experimental on Pixel 4's Snapdragon 855)
- Personal word dictionary / user learning
- Play Store or F-Droid distribution
- Automated ktlint as CI gate (add after contributor norms are established)

### Architecture Approach

The v1.1 architecture adds one new Gradle module (`asr/`) and one new interface (`SttProvider` in `core/`) to the existing 4-module structure. The Bound Service IPC pattern between `DictusImeService` and `DictationService` is unchanged — the IME continues calling `confirmAndTranscribe()` through the same `LocalBinder`. Provider selection is internal to `DictationService`: it reads `ACTIVE_STT_PROVIDER` from DataStore and calls `getOrInitEngine()`, which enforces mutual exclusivity (release current engine before loading new one). The `BinaryDictRanker` lives in `ime/suggestion/` alongside the existing `SuggestionEngine` interface and loads `.dict` assets from `ime/src/main/assets/dicts/`. The `asr/` module depends only on `core/` (for `SttProvider`) and has no dependency on `app/` or `ime/`, preventing circular dependencies.

**Major components:**
1. **`core/stt/SttProvider` (NEW interface)** — `WhisperProvider` and `ParakeetProvider` implement it; `DictationService` holds `var activeEngine: SttProvider?`
2. **`asr/` module (NEW)** — contains only `ParakeetProvider` + sherpa-onnx AAR in `libs/`; no CMakeLists.txt needed
3. **`DictationService.getOrInitEngine()` (MODIFIED)** — provider selection with memory guard (`availMem < 1.5 GB` refuses Parakeet) and mutual exclusivity
4. **`ime/suggestion/BinaryDictRanker` (NEW)** — implements `SuggestionEngine`, reads binary `.dict` assets, runs on dedicated `dict-worker` thread via `Executors.newSingleThreadExecutor()`
5. **`ModelInfo` extended data class (MODIFIED)** — adds `provider: AiProvider`, `extractedDirName: String?`, `supportedLanguages: List<String>` for ONNX archive-based models
6. **GitHub Actions workflows (NEW)** — `ci.yml` (lint + unit tests + debug APK on push/PR), `release.yml` (signed APK on `v*` tag push)

### Critical Pitfalls

1. **`libc++_shared.so` symbol collision between whisper.cpp and sherpa-onnx (Pitfall 12)** — Build whisper.cpp with `c++_static` and hide STL symbols via `-Wl,--exclude-libs,libc++_static.a`. Validate with a standalone test loading both engines + 20 inference iterations before any UI integration. Crash is intermittent and device-specific — easy to miss in initial testing.

2. **Native text prediction called on wrong thread causes keyboard lag (Pitfall 11)** — All `BinaryDictRanker` calls must go through a dedicated `dict-worker` single-threaded executor. Never call JNI lookup on `Dispatchers.Default` or `Dispatchers.Main`. Debounce at 50ms. Validate with a microbenchmark (1000 sequential lookups) before keyboard UI integration.

3. **Parakeet + whisper.cpp simultaneous load OOMs on 6 GB devices (Pitfall 14)** — Enforce mutual exclusivity in `getOrInitEngine()`. Add explicit `ActivityManager.getMemoryInfo()` guard: if `availMem < 1.5 GB`, refuse Parakeet load and show user-facing error. Validate on Pixel 4 with `adb shell dumpsys meminfo` after each load/unload cycle.

4. **Signing key loss breaks future APK updates (Pitfall 15)** — Generate a dedicated APK signing keystore before the first public release. Back it up to at minimum two locations. Never commit `.jks` to git. Verify the GitHub Actions workflow does not expose secrets to fork PRs.

5. **Secrets or HuggingFace tokens in git history before OSS repo goes public (Pitfall 17)** — Run `truffleHog` or `git secrets --scan` on full git history before making the repo public. Store all model URLs in a versioned `assets/model_catalog.json`, not hardcoded in Kotlin. The repo going public is a one-way door.

6. **Multi-provider architecture implemented as god object (Pitfall 18)** — Define `SttProvider` interface and implement `WhisperProvider` before writing any Parakeet code. `DictationService` must hold `var activeEngine: SttProvider?` and delegate all calls to it — zero `when (provider)` blocks in the service.

## Implications for Roadmap

Based on research, four phases are recommended. The ordering is driven by three hard dependencies: (1) `SttProvider` interface gates Parakeet work, (2) OSS repo preparation gates the public release, (3) the `libc++` collision must be validated before Parakeet UI is built.

### Phase 1: Foundation — SttProvider Interface + Tech Debt
**Rationale:** The `SttProvider` interface is the single most load-bearing architectural decision in v1.1. Every subsequent phase depends on it. Defining it first and implementing `WhisperProvider` as a thin wrapper validates the abstraction before any ONNX code is written. Tech debt from v1.0 should be cleared here to avoid carrying it into a public release.
**Delivers:** `SttProvider` interface in `core/`, `WhisperProvider` adapter, extended `ModelInfo` data class, tech debt fixes (settings AZERTY toggle, POST_NOTIFICATIONS, stale test assertions)
**Addresses:** Pitfall 18 (god object anti-pattern) — interface defined before Parakeet code exists
**Research flag:** Well-established patterns — no additional research needed

### Phase 2: Text Prediction — BinaryDictRanker
**Rationale:** Suggestion engine work is fully independent of STT provider work and has no NDK complexity. It can be developed in parallel with Phase 1 or immediately after. The threading model (dedicated `dict-worker` executor) must be validated with a microbenchmark before keyboard UI integration.
**Delivers:** `BinaryDictRanker` in `ime/suggestion/`, FR + EN `.dict` assets in `ime/`, `StubSuggestionEngine` replaced, top-3 suggestion candidates after dictation, tap-to-replace interaction
**Uses:** AOSP binary `.dict` files (Apache 2.0), `Executors.newSingleThreadExecutor()` for JNI safety
**Avoids:** Pitfall 11 (wrong-thread JNI lag) — microbenchmark gates keyboard UI integration
**Research flag:** Well-documented pattern — threading validation is the key gate, not further research

### Phase 3: Parakeet Integration — sherpa-onnx + Multi-Provider UI
**Rationale:** Parakeet work can only start after `SttProvider` exists (Phase 1). The `libc++` collision validation must be the first step of this phase — if it fails, the architecture decision (separate processes vs. `c++_static` build) changes scope significantly. Only after the collision is resolved should model download, `ParakeetProvider`, and model manager UI be built.
**Delivers:** `asr/` module, `ParakeetProvider`, Parakeet 110M in `ModelCatalog`, model cards with provider badge, `ACTIVE_STT_PROVIDER` DataStore key, provider selection in Settings, English-only language warning, mutual exclusivity memory guard in `DictationService`
**Uses:** sherpa-onnx 1.12.34 AAR (local, `asr/libs/`), 110M CTC INT8 model (126 MB)
**Avoids:** Pitfall 12 (libc++ collision) — validated first; Pitfall 14 (OOM) — mutual exclusivity enforced
**Research flag:** Needs empirical validation — libc++ collision fix must be verified on Pixel 4 before UI work begins; ONNX memory release behavior must be confirmed with `dumpsys meminfo`

### Phase 4: Beta Distribution + OSS Repository
**Rationale:** OSS repo preparation is independent of feature work and can be partially parallelized with Phase 3. The hard gate is the license audit and `truffleHog` scan — the repo cannot go public until both pass. Signing key setup must happen before the first signed release APK is built.
**Delivers:** GitHub Actions CI workflow (`ci.yml`), signed release APK workflow (`release.yml`), `CONTRIBUTING.md`, `README.md` update, `CODE_OF_CONDUCT.md`, YAML issue templates (bug + feature), `NOTICES.txt` via `gradle-license-plugin`, semantic versioning convention, release keystore setup with documented backup procedure
**Avoids:** Pitfall 15 (signing key loss), Pitfall 17 (secrets in git history before public)
**Research flag:** Well-documented patterns — the only validation needed is the `truffleHog` scan result before public launch

### Phase Ordering Rationale

- Phase 1 before Phase 3: `SttProvider` interface is a hard dependency for Parakeet; `ModelInfo` extension is needed before model catalog changes
- Phase 2 is independent: binary `.dict` ranker shares no code paths with STT provider work; can run in parallel with Phase 1 or Phase 3
- Phase 4 partially independent: `CONTRIBUTING.md`, `README.md`, and templates can be written any time; license audit and signing key setup must complete before public release
- The `libc++` collision validation at the start of Phase 3 is the highest-risk gate in v1.1 — if it requires a separate-process architecture, it adds significant scope

### Research Flags

Phases needing empirical validation during implementation:
- **Phase 3:** `libc++_shared.so` collision fix must be validated on Pixel 4 before any Parakeet UI work. The recommended fix (whisper.cpp with `c++_static` + symbol visibility scripts) is documented but not tested against this project's CMakeLists.txt. If validation fails, the fallback (separate processes) changes scope significantly.
- **Phase 3:** ONNX Runtime memory release timing — the delayed release issue (sherpa-onnx #1939) may require explicit validation with `dumpsys meminfo` to confirm mutual exclusivity actually frees memory before whisper.cpp reloads.

Phases with standard patterns (no additional research needed):
- **Phase 1:** `SttProvider` interface is standard Kotlin design; `WhisperProvider` is a thin delegation wrapper
- **Phase 2:** Binary `.dict` format is documented in AOSP source; Kotlin `ByteBuffer` reader needs no external dependencies
- **Phase 4:** GitHub Actions workflow is well-established; `gradle-license-plugin` documentation is clear

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | sherpa-onnx 1.12.34 AAR verified from GitHub Releases (March 2026); AOSP .dict files verified Apache 2.0; GitHub Actions pattern HIGH. sherpa-onnx Android docs are build-from-source focused — AAR approach inferred from release assets, not explicitly documented |
| Features | MEDIUM-HIGH | Feature scope derived from PRD, iOS reference app, and competitive analysis (FUTO Keyboard). P1/P2 prioritization is based on dependency graph analysis. Bigram scoring feasibility from binary .dict is MEDIUM (documented in AOSP source, unvalidated in project) |
| Architecture | HIGH | Based on actual v1.0 source code analysis. Module dependency graph derived from existing constraints. `getOrInitEngine()` memory guard pattern is standard Android practice |
| Pitfalls | HIGH | libc++ collision documented in Android NDK official docs; OOM risk validated from sherpa-onnx issue tracker; signing key pitfall is standard OSS distribution risk; threading pitfall based on IME frame budget analysis |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **libc++ collision with existing CMake build:** The recommended fix has not been tested against the project's actual CMakeLists.txt. This must be the first task of Phase 3. If changes to the whisper.cpp submodule or build flags are required, scope may increase.
- **ONNX Runtime memory release timing:** The delayed memory release issue (sherpa-onnx #1939) is documented but unresolved as of early 2026. The mutual exclusivity guard may not be sufficient if ONNX Runtime retains allocations after `release()`. Needs empirical validation with `dumpsys meminfo`.
- **Binary `.dict` read performance:** The Kotlin `ByteBuffer` prefix ranker approach is theoretically sound but its performance on full FR/EN `.dict` files (3-4 MB each) has not been benchmarked. The microbenchmark (1000 sequential lookups) in Phase 2 is the validation gate.
- **16 KB page size (Android 15+):** The pre-built sherpa-onnx AAR's `.so` files use 4 KB alignment. Not a blocker for v1.1 (GitHub Releases APK targeting API 29+), but a known blocker for any future Play Store submission targeting API 35+. Document in `CONTRIBUTING.md`.

## Sources

### Primary (HIGH confidence)
- [sherpa-onnx GitHub Releases v1.12.34](https://github.com/k2-fsa/sherpa-onnx/releases) — AAR artifacts, Parakeet model URLs, confirmed March 26, 2026
- [sherpa-onnx Parakeet CTC 110M model](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html) — 126 MB INT8 confirmed
- [Android NDK C++ library support](https://developer.android.com/ndk/guides/cpp-support) — libc++ collision behavior documented officially
- [r0adkll/sign-android-release](https://github.com/r0adkll/sign-android-release) — APK signing GitHub Action
- [softprops/action-gh-release](https://github.com/softprops/action-gh-release) — GitHub Releases upload action
- [gradle-license-plugin v0.9.8](https://github.com/jaredsburrows/gradle-license-plugin) — license audit for Android
- [Android developer docs: Creating an Input Method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) — candidates view, suggestion commit pattern
- [Helium314/HeliBoard GitHub](https://github.com/Helium314/HeliBoard) — GPL v3 license confirmed (excluded from project)
- [Contributor Covenant 2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) — CODE_OF_CONDUCT standard

### Secondary (MEDIUM confidence)
- [Helium314/aosp-dictionaries (Codeberg)](https://codeberg.org/Helium314/aosp-dictionaries) — Apache 2.0 binary .dict files; format inferred from AOSP source documentation
- [sherpa-onnx Android Docs](https://k2-fsa.github.io/sherpa/onnx/android/index.html) — integration approach; build-from-source focused, AAR path inferred from release assets
- [sherpa-onnx Issue #2641](https://github.com/k2-fsa/sherpa-onnx/issues/2641) — 16 KB page size incompatibility
- [sherpa-onnx Issue #1939](https://github.com/k2-fsa/sherpa-onnx/issues/1939) — ONNX memory release delay
- [FUTO Keyboard](https://keyboard.futo.org/) — closest open-source competitor reference
- [GitHub Actions Android patterns](https://dev.to/ronynn/automating-android-apk-builds-with-github-actions-the-sane-way-1h95) — workflow structure reference

### Tertiary (LOW confidence)
- Parakeet 0.6B iOS memory benchmark (~1.23 GB) — inferred from independent community benchmarks, not official documentation
- [Grammarly Engineering: Building a Native Android Keyboard](https://www.grammarly.com/blog/engineering/how-grammarly-built-a-native-keyboard-for-android/) — considered for threading model analysis

---
*Research completed: 2026-03-30*
*Ready for roadmap: yes*
