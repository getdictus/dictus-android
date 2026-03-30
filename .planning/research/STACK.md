# Stack Research — v1.1 New Capabilities

**Domain:** Android IME — text prediction, Parakeet STT, beta distribution, OSS tooling
**Researched:** 2026-03-30
**Confidence:** MEDIUM-HIGH (verified against official repos and Maven Central; model feasibility on Pixel 4 is LOW due to RAM constraints)

> This file extends the v1.0 STACK.md. It covers ONLY new additions for v1.1.
> Do not re-research: Kotlin, AGP, Compose, Hilt, DataStore, whisper.cpp, OkHttp, Timber.

---

## Area 1: Text Prediction Engine (Suggestions Bar)

### Decision: AOSP LatinIME Native C++ — NOT recommended as primary path

After research, the AOSP LatinIME C++ prediction engine (libjni_latinime.so + binary .dict files) is **not suitable** as a standalone Gradle dependency. The build pipeline requires the full AOSP toolchain, and porting it to Android Studio is a long-standing community pain point with no clean Gradle artifact. The existing v1.0 stub (suggestion bar UI, no real engine) should be replaced using a lighter alternative.

### Recommended: Pre-compiled binary dictionaries + simple bigram model (Kotlin)

| Component | Version | Purpose | Why |
|-----------|---------|---------|-----|
| AOSP binary .dict files | n/a (static assets) | Word frequency data for FR + EN | The `.dict` files compiled from AOSP LatinIME word lists are redistributable (Apache 2.0). Available pre-compiled from HeliBoard/aosp-dictionaries on Codeberg. Drop into `assets/`, load as ByteBuffer in Kotlin. |
| Custom Kotlin suggestion ranker | N/A (project code) | Score candidates from typed prefix against dict | A prefix trie or binary search over the .dict word list is sufficient for the Dictus use case (post-dictation correction, not real-time typing suggestions). No C++ NDK needed. |

**Why not the AOSP C++ suggestion engine:**
- Requires building `libjni_latinime.so` from AOSP source — no Maven artifact exists
- Community ports (HeliBoard, OpenBoard) have this tightly coupled to their own IME; extracting it as a standalone library requires significant effort
- For Dictus, suggestions appear after transcription (not per-keystroke), so the C++ engine's low-latency advantage is irrelevant
- The simpler Kotlin + binary dict approach (what HeliBoard uses under the hood anyway) is adequate for suggestion ranking at transcription time

**Alternative if richer prediction is required later:**
Use the HeliBoard dictionary files + their open-source `Dictionary.kt` lookup code (GPL v3 — requires license compatibility review before including).

### Binary Dictionary Sources

| Language | Source | License | Size |
|----------|--------|---------|------|
| French | `Helium314/aosp-dictionaries` (Codeberg) | Apache 2.0 | ~3 MB |
| English | `Helium314/aosp-dictionaries` (Codeberg) | Apache 2.0 | ~4 MB |

Load pattern:
```kotlin
// In core module — no NDK required
val dictBytes = context.assets.open("dicts/fr_FR.dict").readBytes()
val ranker = BinaryDictRanker(dictBytes)
val suggestions = ranker.topK(prefix = "bonjou", k = 3)
```

**Confidence: MEDIUM** — binary dict format is documented in AOSP source; the Kotlin reader is project code (no external library needed). No NDK, no .so file.

---

## Area 2: Parakeet / Nvidia STT — Second Provider

### Decision: sherpa-onnx via AAR, with on-device feasibility caveat

Parakeet on Android is feasible via **sherpa-onnx** (k2-fsa), which ships pre-built Android AAR files and provides Kotlin/Java bindings. This is the only production-ready path that does not require building ONNX Runtime from source.

### Core Technologies

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| sherpa-onnx AAR | 1.12.34 | JNI wrapper around ONNX Runtime for ASR | Ships as a standalone `.aar` with `libonnxruntime.so` and `libsherpa-onnx-jni.so` bundled. Kotlin-callable Java API. Supports Parakeet NeMo models natively. MIT license. Latest release: March 26, 2026. |
| onnxruntime-android | 1.24.3 | (transitive via sherpa-onnx AAR — do not add separately) | sherpa-onnx bundles ONNX Runtime internally. Adding `com.microsoft.onnxruntime:onnxruntime-android` separately risks version conflicts. Only add if building sherpa-onnx from source. |

### Recommended Parakeet Model for Dictus

| Model | Size (INT8) | Language | Why |
|-------|------------|---------|-----|
| `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8` | **126 MB** | English only | The 110M parameter CTC variant is 5x smaller than the 0.6B transducer variant (~640 MB). 126 MB fits on Pixel 4 (6 GB RAM). CTC decoding is simpler and faster than transducer (no separate decoder/joiner). Supports punctuation and casing. |

**Do NOT use parakeet-tdt-0.6b-v3-int8 for the initial implementation:** encoder alone is 622 MB, total model ~640 MB INT8. Pixel 4 has 6 GB RAM but the IME process has a limited memory budget (typically 256-512 MB on most OEMs). A 640 MB model will OOM in an IME context. The 110M CTC model at 126 MB is the feasible starting point.

### Integration Approach

sherpa-onnx does **not** publish to Maven Central. Integration is via a locally-stored AAR:

```kotlin
// whisper module's build.gradle.kts — or create new 'asr' module
dependencies {
    // Local AAR — place sherpa-onnx-1.12.34.aar in app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```

Download AAR from: `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.34/sherpa-onnx-1.12.34.aar`

Model download at runtime (same pattern as GGML models — OkHttp from HuggingFace):
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2
```

### Multi-Provider Architecture

Add a `SttProvider` interface in the `core` module:

```kotlin
interface SttProvider {
    val id: String          // "whisper" | "parakeet"
    val displayName: String
    suspend fun transcribe(audioFile: File, language: Language): String
    fun isModelReady(): Boolean
}
```

`DictationService` selects the active provider from `DataStore`. This isolates whisper.cpp (existing `whisper` module) from sherpa-onnx (new `asr` module or `whisper` module extension).

**New Gradle module recommendation:** Create `asr/` module (parallel to `whisper/`) to keep NDK-free Kotlin code separate from whisper.cpp's CMake/JNI complexity. The sherpa-onnx AAR ships its own .so files — no CMakeLists.txt needed.

**Confidence: MEDIUM** — sherpa-onnx Android support is documented and the AAR approach is verified from release assets. The 110M model feasibility on Pixel 4 is HIGH (126 MB model, reasonable RAM). The 0.6B model feasibility in an IME process is LOW.

---

## Area 3: Beta Distribution — APK via GitHub Releases

No new Android libraries needed. This is purely CI/CD and workflow tooling.

### Tools

| Tool | Version | Purpose | Why |
|------|---------|---------|-----|
| GitHub Actions | N/A (cloud) | Build, sign, and publish APK on tag push | Native to the repo; no external service account needed for GitHub Releases (unlike Play Store). Free for public repos. |
| `r0adkll/sign-android-release` | v1 (pinned SHA) | Sign APK with release keystore in CI | Well-maintained GitHub Action. Takes base64-encoded keystore from GitHub Secret. Outputs signed APK path. |
| `actions/upload-artifact` | v4 | Store APK artifact per workflow run | Standard GitHub Action. |
| `softprops/action-gh-release` | v2 | Create GitHub Release and upload APK as release asset | Triggered on `v*` tag push. |

### Workflow: `.github/workflows/release.yml`

```yaml
name: Release APK
on:
  push:
    tags: ['v*']

jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew assembleRelease
      - uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY_BASE64 }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*-signed.apk
```

### GitHub Secrets Required

| Secret | Value |
|--------|-------|
| `SIGNING_KEY_BASE64` | `openssl base64 < dictus-release.jks \| tr -d '\n'` |
| `KEY_ALIAS` | Key alias in the keystore |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

### Feedback Collection

No Play Store means no Play Store reviews. Options for beta feedback, in order of effort:

1. **GitHub Issues** (zero effort) — link from README and release description. Use issue templates (see Area 4).
2. **Google Forms** (low effort) — embed link in release notes and app Settings > Feedback.
3. **Firebase Crashlytics** — requires `com.google.firebase:firebase-crashlytics:19.x` + `google-services.json`. Adds a Google dependency; conflicts with the "no-cloud" philosophy. **Avoid for v1.1 unless Pierre explicitly wants crash reporting.**

**Recommendation:** GitHub Issues only for v1.1 beta. Add Crashlytics only if crash rate proves hard to diagnose from logs alone.

**Confidence: HIGH** — GitHub Actions + r0adkll/sign-android-release is a well-documented, widely-used pattern.

---

## Area 4: OSS Repo Tooling — GitHub Templates and CI

### Files to Create

| File | Purpose |
|------|---------|
| `.github/ISSUE_TEMPLATE/bug_report.yml` | Structured bug report (YAML form format, not Markdown) |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | Feature request form |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR checklist: tested on device, lint passes, translations updated |
| `.github/CONTRIBUTING.md` (or `CONTRIBUTING.md` at root) | How to build, run, submit PRs |
| `CODE_OF_CONDUCT.md` | Contributor Covenant 2.1 (standard OSS) |
| `README.md` | Project overview, screenshots, install instructions |

### CI Workflow: `.github/workflows/ci.yml`

Triggers: `push` to `main`, `pull_request` to `main`.

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew lintDebug
      - run: ./gradlew testDebugUnitTest
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

**Why not Fastlane:** Fastlane adds Ruby dependency overhead and is optimized for Play Store / App Store lane automation. For GitHub Releases-only distribution, native GitHub Actions is simpler, faster to set up, and has no external dependencies. Fastlane adds value only when deploying to Play Store (supply lane) or TestFlight. Skip Fastlane for v1.1.

### License Audit Tooling

| Tool | Purpose | How to Use |
|------|---------|-----------|
| `gradle-license-plugin` | Generate `NOTICES.txt` from all Gradle dependencies | `id("com.jaredsburrows.license") version "0.9.8"` in build.gradle.kts. Run `./gradlew licenseDebugReport`. |

```kotlin
// Root build.gradle.kts
plugins {
    id("com.jaredsburrows.license") version "0.9.8" apply false
}
// app/build.gradle.kts
plugins {
    id("com.jaredsburrows.license")
}
```

This generates `app/build/reports/licenses/licenseDebugReport.html` and `.json` listing all dependencies and their licenses. Required for the "audit et correction des licences OSS" requirement.

**Confidence: HIGH** — GitHub Actions workflow pattern is well-established; gradle-license-plugin is the standard Android choice for license auditing.

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `parakeet-tdt-0.6b-v3-int8` (full model) | 640 MB total; will OOM in IME process on most devices | `parakeet_tdt_ctc_110m-en-36000-int8` (126 MB) |
| `com.microsoft.onnxruntime:onnxruntime-android` directly | sherpa-onnx AAR bundles it; separate dep causes version conflicts | Use sherpa-onnx AAR only |
| AOSP libjni_latinime.so | No Maven artifact; AOSP build toolchain required; overkill for post-dictation suggestions | Binary .dict files + Kotlin prefix ranker |
| Firebase Crashlytics / Analytics | Introduces Google cloud dependency; against Dictus's no-cloud value proposition | Timber file logger (already in v1.0) + GitHub Issues |
| Fastlane | Ruby overhead, Play Store-optimized; no benefit for GitHub Releases-only distribution | Native GitHub Actions |
| HeliBoard source code (dictionary lookup) | GPL v3 — license incompatible with MIT distribution unless entire app re-licensed | Apache 2.0 binary .dict files + own Kotlin reader |
| ONNX Runtime NNAPI execution provider | Marked experimental in ONNX Runtime; causes crashes on some Snapdragon 855 configurations | CPU execution provider only (XNNPACK optional) |

---

## Version Compatibility

| Component | Compatible With | Notes |
|-----------|-----------------|-------|
| sherpa-onnx 1.12.34 AAR | Min SDK 21+ | Dictus is Min SDK 29 — compatible |
| sherpa-onnx 1.12.34 | NDK r26+ bundled internally | No NDK version conflict with project's NDK r28 |
| gradle-license-plugin 0.9.8 | AGP 8.x–9.x | Verified against AGP 9.x |
| r0adkll/sign-android-release v1 | ubuntu-latest runners | Pin to commit SHA in production workflows |
| AOSP .dict (Apache 2.0) | Any Android version | Static asset; no API constraints |

---

## New Module Layout (v1.1 addition)

```
dictus-android/
├── app/          # (existing) — add SttProvider selection logic
├── ime/          # (existing) — no changes for these features
├── core/         # (existing) — add SttProvider interface
├── whisper/      # (existing) — implement SttProvider for whisper.cpp
└── asr/          # NEW — sherpa-onnx AAR + Parakeet SttProvider implementation
    ├── libs/
    │   └── sherpa-onnx-1.12.34.aar
    └── src/main/kotlin/dev/pivisolutions/dictus/asr/
        └── ParakeetProvider.kt
```

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| sherpa-onnx AAR (local) | Build ONNX Runtime from source | Multi-hour NDK build; no benefit; AAR is pre-built and tested |
| sherpa-onnx AAR (local) | whisper-jni for Parakeet | whisper-jni only supports Whisper/GGML, not ONNX NeMo models |
| 110M CTC model (126 MB) | 0.6B transducer model (640 MB) | 640 MB is infeasible in IME process; 110M is the only safe choice |
| GitHub Actions native | Fastlane | Fastlane adds Ruby, is Play Store-centric; no value for GitHub Releases-only |
| YAML issue templates | Markdown issue templates | YAML forms enforce required fields; better signal-to-noise in bug reports |
| Binary .dict + Kotlin ranker | Full LatinIME C++ port | LatinIME C++ has no standalone Maven artifact; port cost is disproportionate to the value for post-dictation suggestions |

---

## Sources

- [sherpa-onnx GitHub Releases v1.12.34](https://github.com/k2-fsa/sherpa-onnx/releases) — AAR artifacts, version confirmed March 26, 2026 (HIGH)
- [sherpa-onnx Android Docs](https://k2-fsa.github.io/sherpa/onnx/android/index.html) — integration approach (MEDIUM — build-from-source focused, AAR approach inferred from release assets)
- [sherpa-onnx Parakeet CTC 110M model](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html) — 126 MB INT8, confirmed (HIGH)
- [sherpa-onnx Parakeet TDT 0.6B INT8](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html) — 640 MB total, confirmed too large for IME (HIGH)
- [com.microsoft.onnxruntime:onnxruntime-android 1.24.3](https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android) — Maven Central, verified (HIGH)
- [Helium314/aosp-dictionaries](https://codeberg.org/Helium314/aosp-dictionaries) — Apache 2.0 binary .dict files (MEDIUM)
- [r0adkll/sign-android-release](https://github.com/r0adkll/sign-android-release) — GitHub Action for APK signing (HIGH)
- [softprops/action-gh-release](https://github.com/softprops/action-gh-release) — GitHub Releases upload action (HIGH)
- [gradle-license-plugin](https://github.com/jaredsburrows/gradle-license-plugin) — v0.9.8 on Maven Central (HIGH)
- [HeliBoard GitHub](https://github.com/HeliBorg/HeliBoard) — GPL v3 license confirmed (HIGH — license incompatibility with MIT)
- [GitHub Actions Android patterns](https://dev.to/ronynn/automating-android-apk-builds-with-github-actions-the-sane-way-1h95) — workflow structure (MEDIUM)
- [Mobile CI/CD 2025 guide](https://developersvoice.com/blog/mobile/mobile-cicd-blueprint/) — Fastlane vs GitHub Actions analysis (MEDIUM)

---
*Stack research for: Dictus Android v1.1 — new capabilities only*
*Researched: 2026-03-30*
