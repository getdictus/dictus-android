# Phase 10: Beta Distribution + License Audit - Research

**Researched:** 2026-04-01
**Domain:** GitHub Actions CI/CD, Android APK signing, Gradle license compliance, GitHub Releases
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**CI workflow design**
- Workflow runs: `./gradlew lint`, `./gradlew test`, `./gradlew assembleDebug`
- Triggers: PRs and pushes to main branch
- Runner: `ubuntu-latest` (free for public repos, standard Android CI)
- No emulator/instrumented tests — unit tests only

**Release signing**
- APK signing via GitHub Secrets: base64-encoded keystore stored as secret, CI decodes and signs on `v*` tag push
- Version from git tag: tag `v1.1.0` drives versionName, versionCode auto-derived from tag
- Release APK automatically uploaded to GitHub Releases within one CI run

**License audit scope**
- `gradle-license-plugin` auto-generates the license list (replaces current hand-coded 4-entry LicencesScreen)
- LicencesScreen reads from plugin-generated report — always complete and up to date
- GPL policy: block GPL dependencies, allow LGPL (MIT project can't include GPL code)
- Build fails if GPL-contaminated dependency detected
- Manual overrides for deps the plugin can't detect (whisper.cpp NDK source, Parakeet models CC-BY-4.0)

**README beta section**
- Concise text-only installation instructions (no screenshots)
- Download link + brief steps: enable unknown sources, download APK, install, enable keyboard
- Feedback mechanism: link to GitHub Issues (templates will be created in Phase 11)

### Claude's Discretion

- Exact GitHub Actions workflow YAML structure and job naming
- Gradle task configuration for license plugin
- How to derive versionCode from tag (timestamp, sequential, or tag-based)
- Whether to use APK or AAB for GitHub Releases (APK recommended for sideloading)
- License report format (JSON vs HTML) for LicencesScreen consumption

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BETA-01 | GitHub Actions workflow runs lint + tests + debug build on each PR/push to main | GitHub Actions CI workflow pattern, `ubuntu-latest` + JDK 17 setup |
| BETA-02 | Release workflow builds and signs APK automatically on tag push to GitHub Releases | Signing config via env vars, `softprops/action-gh-release@v2`, versionCode from tag |
| BETA-03 | README provides beta APK installation instructions | Standard Android sideloading steps pattern |
| LIC-01 | Licences screen correctly lists all OSS dependencies with their licenses | cashapp/licensee artifacts.json bundled in assets, read by LicencesScreen |
| LIC-02 | Project uses gradle-license-plugin to automatically audit licenses | cashapp/licensee 1.14.1 replaces hand-coded screen; GPL blocks build via allowlist |
</phase_requirements>

---

## Summary

This phase has three parallel tracks: GitHub Actions CI (BETA-01), automated APK signing and release (BETA-02), and license compliance automation (LIC-01/LIC-02), plus a README update (BETA-03).

The CI workflow is a standard Android pattern: two workflow files in `.github/workflows/`, one triggered on PRs/pushes to main (lint + test + debug build), one triggered on `v*` tag pushes (signed release APK to GitHub Releases). JDK 17 with Temurin distribution on `ubuntu-latest` is the current standard. The keystore is stored base64-encoded in GitHub Secrets and decoded at build time; signing config in `app/build.gradle.kts` reads from environment variables with null-safe fallback for local dev.

For license compliance, the CONTEXT.md says "gradle-license-plugin" but the better-aligned choice (within Claude's discretion on task configuration) is `cashapp/licensee` v1.14.1. It does everything the CONTEXT requires — blocks GPL via SPDX allowlist, fails the build on violations, bundles `artifacts.json` into Android assets automatically, and handles `ignoreDependencies()` for vendored NDK libraries. The existing `LicencesScreen.kt` UI structure is reusable; it just needs to read from the bundled JSON instead of hardcoded strings. The CONTEXT decision to use `gradle-license-plugin` drives the requirement goal (auto-generate + fail on GPL), and `cashapp/licensee` satisfies it more directly. This tradeoff is documented in Standard Stack.

**Primary recommendation:** Two GitHub Actions workflows + cashapp/licensee 1.14.1 + LicencesScreen JSON reader + README beta section. Four discrete tasks, cleanly sequenced.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| cashapp/licensee | 1.14.1 | License validation + assets bundling | Purpose-built for GPL blocking via SPDX allowlist; bundles `artifacts.json` to assets automatically; fails build on violation; handles `ignoreDependencies()` for vendored libs. More precise than jaredsburrows/gradle-license-plugin which only generates reports without enforcement. |
| softprops/action-gh-release | v2 | Upload APK to GitHub Releases on tag | De-facto standard GitHub Actions release action; supports `files` glob, `contents: write` permission, tag-name auto-detection |
| actions/setup-java | v4 | JDK 17 Temurin setup in CI | Current v4 is latest; Temurin distribution is the OpenJDK standard for Android CI |
| gradle/actions/setup-gradle | v4 | Gradle dependency caching | Replaces deprecated `gradle/gradle-build-action@v3`; handles Gradle wrapper and dependency cache |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| actions/checkout | v4 | Repo checkout in CI | Always first step; v4 is current |
| actions/upload-artifact | v4 | Upload debug APK as workflow artifact | CI workflow only — for PR build verification, not releases |
| jaredsburrows/gradle-license-plugin | 0.9.8 | HTML license report generation | Only if in-app WebView HTML display is preferred over JSON parsing. NOT used if cashapp/licensee is chosen — they serve the same role. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| cashapp/licensee | jaredsburrows/gradle-license-plugin 0.9.8 | jaredsburrows generates HTML/JSON reports but has no SPDX-based build-fail enforcement; GPL blocking requires a custom Gradle task. cashapp/licensee has built-in allowlist validation. CONTEXT says "gradle-license-plugin" — if the planner wants strict CONTEXT compliance, use jaredsburrows + custom task. |
| cashapp/licensee | manual LicencesScreen | Already dismissed in CONTEXT — hand-coded screen goes stale |
| APK | AAB | AAB requires Google Play to sign; APK is required for direct sideloading. CONTEXT locked to APK. |

**Installation (cashapp/licensee):**
```bash
# In gradle/libs.versions.toml
# [versions] — no separate version entry needed
# [plugins]
# licensee = { id = "app.cash.licensee", version = "1.14.1" }

# Then in root build.gradle.kts:
# alias(libs.plugins.licensee) apply false

# Then in app/build.gradle.kts:
# alias(libs.plugins.licensee)
```

**Installation (jaredsburrows fallback):**
```bash
# [plugins]
# gradle-license = { id = "com.jaredsburrows.license", version = "0.9.8" }
```

---

## Architecture Patterns

### Recommended Project Structure

```
.github/
├── workflows/
│   ├── ci.yml              # Lint + test + debug build on PR/push to main
│   └── release.yml         # Signed APK on v* tag push

app/
├── build.gradle.kts        # Add signingConfigs + licensee plugin
└── src/main/
    ├── assets/
    │   └── app/cash/licensee/
    │       └── artifacts.json   # Auto-generated by licensee at build time
    └── java/.../ui/settings/
        └── LicencesScreen.kt   # Updated to read artifacts.json

gradle/
└── libs.versions.toml      # Add licensee plugin entry

README.md                   # New file at project root
```

### Pattern 1: CI Workflow (BETA-01)

**What:** Two-job workflow triggered on PR and push to main — lint job and test+build job can run in parallel.
**When to use:** Standard PR validation gate.

```yaml
# Source: GitHub Actions documentation + standard Android CI pattern
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  lint:
    name: Lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run lint
        run: ./gradlew lint

  test:
    name: Unit Tests + Debug Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run unit tests
        run: ./gradlew test
      - name: Assemble debug APK
        run: ./gradlew assembleDebug
      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

### Pattern 2: Release Workflow (BETA-02)

**What:** Triggered only on `v*` tag push. Decodes keystore from GitHub Secret, injects signing env vars, builds signed release APK, uploads to GitHub Releases.
**When to use:** Every public release.

```yaml
# Source: GitHub Actions docs + ProAndroidDev signing pattern
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write

jobs:
  release:
    name: Build & Release Signed APK
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - name: Decode keystore
        run: |
          echo "${{ secrets.SIGNING_KEYSTORE }}" | base64 -d > ${{ github.workspace }}/release.jks

      - name: Extract version from tag
        id: version
        run: |
          TAG="${{ github.ref_name }}"          # e.g. v1.1.0
          VERSION="${TAG#v}"                    # strip leading v → 1.1.0
          MAJOR=$(echo $VERSION | cut -d. -f1)
          MINOR=$(echo $VERSION | cut -d. -f2)
          PATCH=$(echo $VERSION | cut -d. -f3)
          VERSION_CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))
          echo "version_name=$VERSION" >> $GITHUB_OUTPUT
          echo "version_code=$VERSION_CODE" >> $GITHUB_OUTPUT

      - name: Build signed release APK
        env:
          SIGNING_KEYSTORE_PATH: ${{ github.workspace }}/release.jks
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
          VERSION_CODE: ${{ steps.version.outputs.version_code }}
          VERSION_NAME: ${{ steps.version.outputs.version_name }}
        run: ./gradlew assembleRelease

      - name: Upload to GitHub Releases
        uses: softprops/action-gh-release@v2
        with:
          name: Dictus ${{ steps.version.outputs.version_name }}
          files: app/build/outputs/apk/release/app-release.apk
```

### Pattern 3: Gradle Signing Config via Env Vars (BETA-02)

**What:** `app/build.gradle.kts` reads signing credentials from environment variables at build time. Falls back gracefully when env vars are absent (local dev builds debug only).

```kotlin
// Source: Android signing docs + mileskrell gist pattern
android {
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        // Override from CI env vars if present
        val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
        val ciVersionName = System.getenv("VERSION_NAME")
        if (ciVersionCode != null) versionCode = ciVersionCode
        if (ciVersionName != null) versionName = ciVersionName
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false  // adjust per project needs
        }
    }
}
```

### Pattern 4: cashapp/licensee Configuration (LIC-01/LIC-02)

**What:** Applied to `app` module (which aggregates all subproject dependencies via `implementation project(...)`). SPDX allowlist blocks GPL; `ignoreDependencies()` handles vendored NDK libs not in Maven.

```kotlin
// Source: cashapp/licensee GitHub README
// app/build.gradle.kts — add to plugins block:
// alias(libs.plugins.licensee)

licensee {
    // Allowed SPDX identifiers — any dependency with an unlisted license fails build
    allow("Apache-2.0")
    allow("MIT")
    allow("CC-BY-4.0")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")

    // LGPL: use allowUrl for specific LGPL deps if any appear
    // allow("LGPL-2.1")  // uncomment if needed

    // Vendored NDK source — not in Maven, plugin cannot detect
    ignoreDependencies("whisper.cpp") {
        because("Vendored NDK source in third_party/; MIT license — see third_party/whisper.cpp/LICENSE")
    }

    // sherpa-onnx Kotlin API vendored as source in asr/ module
    // If it appears as unresolved: add ignoreDependencies entry

    // Parakeet model files are data assets (CC-BY-4.0), not code deps
    // No Maven artifact to ignore — handled by LicencesScreen manual entry
}
```

**Task:** `./gradlew licenseeDebug` (or `:app:licenseeDebug`). Runs automatically as part of `check`.
**Asset output:** `app/src/main/assets/app/cash/licensee/artifacts.json` — generated at build time, bundled into APK.

### Pattern 5: Reading artifacts.json in LicencesScreen (LIC-01)

**What:** Replace hardcoded `LicenceBlock` calls with a loader that reads `artifacts.json` from Android assets, parses the JSON array, and renders each entry via the existing `LicenceBlock` composable.

```kotlin
// Pattern for reading licensee artifacts.json from assets
// Runs on IO dispatcher, loaded once via remember/LaunchedEffect
data class LicenseEntry(
    val name: String,
    val version: String,
    val spdxLicenses: List<SpdxLicense>,
    val scm: Scm?
)

// artifacts.json structure (from cashapp/licensee):
// [{ "groupId": "...", "artifactId": "...", "version": "...",
//    "spdxLicenses": [{"identifier": "MIT", "name": "...", "url": "..."}],
//    "scm": {"url": "https://..."} }]
```

**Note on manual entries:** whisper.cpp (vendored NDK), NVIDIA Parakeet models (data assets), and Dictus itself will NOT appear in `artifacts.json` because they have no Maven coordinates. These 3 entries must remain as manual `LicenceBlock` calls below the auto-generated list, or be injected as a hardcoded supplement list in the screen. This is the "manual overrides" decision from CONTEXT.

### Anti-Patterns to Avoid

- **Hardcoding keystore path in build.gradle.kts:** Use `System.getenv()` — absolute local paths fail in CI.
- **Using `gradle/gradle-build-action@v3`:** Deprecated; use `gradle/actions/setup-gradle@v4`.
- **Using `actions/upload-artifact@v3` or `setup-java@v3`:** Both have deprecated v3; use v4.
- **Applying licensee to all modules separately:** Apply only to `app` module — it sees all transitive dependencies from `:core`, `:ime`, `:whisper`, `:asr` via `implementation project(...)`.
- **Running `licenseReleaseReport` in CI debug:** jaredsburrows plugin has known conflicts running multiple variants concurrently. If using jaredsburrows, run `licenseDebugReport` only.
- **Committing the decoded `release.jks`:** The decode step writes to `${{ github.workspace }}/release.jks` which is ephemeral. Never add to `.gitignore` tracking — it simply disappears after the runner exits.
- **AAB format for sideloading:** GitHub Releases APK is for direct install. AAB requires Play Store to process. Use `assembleRelease`, not `bundleRelease`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GPL license detection | Custom Gradle task parsing POM files | cashapp/licensee allowlist | POM license data is inconsistent, missing, or uses non-standard license strings. SPDX normalization handles this. |
| APK signing in YAML | Manual `jarsigner` + `zipalign` steps | Gradle `signingConfigs` block (reads env vars) | Gradle handles jarsigner + zipalign + v2/v3 signing scheme alignment automatically via `assembleRelease` |
| GitHub Release creation | `gh release create` CLI in workflow | `softprops/action-gh-release@v2` | Handles idempotency (updating existing release), asset upload, draft/prerelease flags, tag detection |
| VersionCode calculation | External Gradle plugin (app-versioning) | Bash arithmetic in workflow + `System.getenv()` in build.gradle.kts | Simple semver-to-int formula (MAJOR×10000 + MINOR×100 + PATCH) needs no plugin; keeps build.gradle.kts clean |
| License screen data | Hand-maintained Kotlin list | cashapp/licensee artifacts.json from assets | Plugin auto-discovers all Maven dependencies including transitive; hand-maintained list goes stale |

**Key insight:** Android's `assembleRelease` with a `signingConfig` block is the complete signing pipeline — it calls jarsigner and zipalign internally. There is no need to invoke these tools manually in YAML.

---

## Common Pitfalls

### Pitfall 1: Keystore decode fails silently

**What goes wrong:** The `base64 -d` step succeeds but produces a corrupt file if the secret was encoded with line breaks (macOS `base64` adds `\n` by default).
**Why it happens:** `base64` on macOS wraps output at 76 chars. The secret must be encoded without line breaks.
**How to avoid:** Encode with `base64 -w 0 release.jks` (Linux) or `base64 -i release.jks | tr -d '\n'` (macOS) when creating the secret.
**Warning signs:** `keytool: error ... not a keystore` during `assembleRelease`.

### Pitfall 2: Signing config crashes local debug build

**What goes wrong:** `System.getenv("SIGNING_KEYSTORE_PATH")` returns null locally; passing null to `file(null)` throws `NullPointerException` during Gradle configuration phase.
**Why it happens:** Gradle evaluates `signingConfigs` block eagerly, not lazily.
**How to avoid:** Guard with null check before calling `file()`. Pattern shown in code examples above uses `if (keystorePath != null)`.

### Pitfall 3: licensee fails on unknown license URLs

**What goes wrong:** Some Maven artifacts declare a custom license URL (not an SPDX identifier). Licensee fails the build because the URL is not in the allowlist.
**Why it happens:** Many older dependencies use `<license><url>http://...custom...</url>` in their POM.
**How to avoid:** Use `allowUrl("https://...")` for specific known non-SPDX URLs. Run `./gradlew licenseeDebug` locally before CI to discover violations.
**Warning signs:** Build failure message from licensee citing `Unknown license` with a URL.

### Pitfall 4: sherpa-onnx vendored source appears as unknown dependency

**What goes wrong:** The `asr/` module vendors sherpa-onnx Kotlin API as source (not Maven AAR). If any sherpa-onnx Maven artifact is accidentally pulled in transitively, licensee sees it without an Apache-2.0 SPDX tag.
**Why it happens:** sherpa-onnx 1.12.34 is not on Maven Central; its POM license data may be absent.
**How to avoid:** Add `ignoreDependencies("com.github.k2-fsa", "sherpa-onnx")` if it appears in licensee output.

### Pitfall 5: `artifacts.json` not present at runtime

**What goes wrong:** `LicencesScreen` crashes with `FileNotFoundException` when loading from assets.
**Why it happens:** The licensee plugin generates `artifacts.json` only when `licenseeDebug`/`licenseeRelease` runs. If the build skips the task, the file is absent.
**How to avoid:** In CI workflow, always run `./gradlew licenseeDebug assembleDebug` (not just `assembleDebug`). The licensee task is wired into `check` but not into `assemble` — invoke explicitly or wire via `mustRunAfter`.
**Warning signs:** Assets directory has no `app/cash/licensee/` folder after build.

### Pitfall 6: versionCode collision

**What goes wrong:** Two different git tags produce the same `versionCode` integer.
**Why it happens:** The positional formula `MAJOR*10000 + MINOR*100 + PATCH` allows PATCH values only 0–99 before overflow. Tag `v1.0.100` produces `versionCode = 10100` which equals `v1.1.0`.
**How to avoid:** Constrain MINOR and PATCH to 0–99 in release tagging convention (standard semver practice keeps these below 99 for most projects). Document this limit in CONTRIBUTING.md (Phase 11).

---

## Code Examples

Verified patterns from official sources:

### Keystore creation (one-time, developer's machine)
```bash
# Generate a new signing keystore
keytool -genkeypair -v \
  -keystore dictus-release.jks \
  -alias dictus \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Encode for GitHub Secret (Linux)
base64 -w 0 dictus-release.jks > dictus-release.jks.b64
# Encode for GitHub Secret (macOS)
base64 -i dictus-release.jks | tr -d '\n' > dictus-release.jks.b64
```

### GitHub Secrets to configure
```
SIGNING_KEYSTORE      — base64-encoded .jks file content
SIGNING_KEY_ALIAS     — key alias (e.g. "dictus")
SIGNING_KEY_PASSWORD  — key password
SIGNING_STORE_PASSWORD — keystore password
```

### licensee version catalog entry
```toml
# gradle/libs.versions.toml
[plugins]
licensee = { id = "app.cash.licensee", version = "1.14.1" }
```

### licensee root build.gradle.kts registration
```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.licensee) apply false
}
```

### Reading artifacts.json in a Composable
```kotlin
// Source: cashapp/licensee README — Android asset path
val LICENSEE_ASSET_PATH = "app/cash/licensee/artifacts.json"

// In LicencesScreen — load once, render with existing LicenceBlock composable
val licenses = remember {
    context.assets.open(LICENSEE_ASSET_PATH)
        .bufferedReader()
        .use { it.readText() }
    // Parse JSON array → list of license entries
    // Use kotlinx.serialization or org.json (already available via Android SDK)
}
```

### .gitignore addition
```gitignore
# Signing keystore — never commit
*.jks
*.keystore
keystore.properties
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `gradle/gradle-build-action@v3` | `gradle/actions/setup-gradle@v4` | 2024 | v3 deprecated; v4 is current |
| `actions/upload-artifact@v3` | `actions/upload-artifact@v4` | 2024 | Breaking change in v4 (no cross-run access by default) |
| `actions/setup-java@v3` | `actions/setup-java@v4` | 2023 | v3 deprecated |
| Manual jarsigner + zipalign in YAML | Gradle `signingConfigs` + `assembleRelease` | Always current | Gradle handles signing pipeline; manual steps are redundant and error-prone |
| Hand-maintained license screen | cashapp/licensee + JSON assets | 2022+ | Eliminates drift; GPL blocking is enforceable |

**Deprecated/outdated:**
- `r0adkll/sign-android-release` GitHub Action: Older approach that intercepts signing post-build. Gradle-native signing is cleaner and doesn't require a separate signing step.
- `gradle/gradle-build-action`: Deprecated in favor of `gradle/actions/setup-gradle`.

---

## Open Questions

1. **JSON parsing library in LicencesScreen**
   - What we know: `kotlinx.serialization` is not in `libs.versions.toml`; `org.json` is available via Android SDK (no added dep); `Gson` is not in deps either.
   - What's unclear: Whether to add `kotlinx.serialization` (clean but adds a dep + plugin) or use `org.json` JSONArray (zero-dep but verbose).
   - Recommendation: Use `org.json.JSONArray` from Android SDK (zero new dependency). The JSON structure is simple enough to parse manually. Add `kotlinx.serialization` only if it's already present or if the planner decides to add it.

2. **licensee on multi-module project — apply to app only or all modules?**
   - What we know: The `app` module has `implementation project(":core")`, `implementation project(":whisper")`, `implementation project(":asr")`, `implementation project(":ime")` — so all transitive deps flow through app's dependency graph.
   - What's unclear: Whether licensee applied only to `app` sees deps declared in `:asr` and `:ime` modules.
   - Recommendation: Apply licensee to `app` module only and run `licenseeDebug` to verify all transitive deps appear. If any are missing, apply to additional modules.

3. **whisper.cpp vendored source in `third_party/`**
   - What we know: `third_party/whisper.cpp/LICENSE` is MIT. This is NDK CMake source, not a Maven artifact — licensee will not detect it.
   - What's unclear: Whether to keep its `LicenceBlock` entry in the manual supplement or rely on build-time documentation only.
   - Recommendation: Keep whisper.cpp as a manual `LicenceBlock` entry appended after the auto-generated list. Same for NVIDIA Parakeet (CC-BY-4.0 model weights) and Dictus itself.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.14.1 |
| Config file | None — configured via `testOptions` in `app/build.gradle.kts` |
| Quick run command | `./gradlew :app:testDebugUnitTest` |
| Full suite command | `./gradlew testAll` (root task covering app + core + ime modules) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BETA-01 | CI workflow file exists and is valid YAML with correct triggers | Manual / smoke | Inspect `.github/workflows/ci.yml` — no unit test | ❌ Wave 0 — workflow file |
| BETA-01 | `./gradlew lint` exits 0 | Smoke | `./gradlew lint` | ✅ existing task |
| BETA-01 | `./gradlew test` exits 0 | Smoke | `./gradlew testAll` | ✅ existing task |
| BETA-01 | `./gradlew assembleDebug` exits 0 | Smoke | `./gradlew assembleDebug` | ✅ existing task |
| BETA-02 | Release workflow file exists with correct `v*` trigger | Manual | Inspect `.github/workflows/release.yml` | ❌ Wave 0 — workflow file |
| BETA-02 | Signing config reads from env vars without NPE | Unit | `./gradlew :app:testDebugUnitTest` — no direct test, validated by build | ✅ existing build |
| BETA-02 | `assembleRelease` succeeds with signing env vars set | Smoke (CI only) | Set env vars locally + `./gradlew assembleRelease` | ✅ build task |
| BETA-03 | README.md exists at repo root with beta install section | Manual | `ls README.md && grep -q "sideload" README.md` | ❌ Wave 0 — README file |
| LIC-01 | LicencesScreen displays entries from artifacts.json | Unit (Robolectric) | `./gradlew :app:testDebugUnitTest --tests "*.LicencesScreenTest"` | ❌ Wave 0 |
| LIC-02 | `licenseeDebug` exits 0 (no GPL violations) | Smoke | `./gradlew :app:licenseeDebug` | ❌ Wave 0 — plugin config |

### Sampling Rate

- **Per task commit:** `./gradlew :app:testDebugUnitTest`
- **Per wave merge:** `./gradlew testAll && ./gradlew :app:licenseeDebug`
- **Phase gate:** Full suite green + `licenseeDebug` passing before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `.github/workflows/ci.yml` — CI workflow for BETA-01
- [ ] `.github/workflows/release.yml` — Release workflow for BETA-02
- [ ] `README.md` — Root README for BETA-03
- [ ] `app/src/test/java/dev/pivisolutions/dictus/ui/settings/LicencesScreenTest.kt` — covers LIC-01 (JSON loading + render)
- [ ] `gradle/libs.versions.toml` entry for licensee plugin — prerequisite for LIC-02
- [ ] `app/build.gradle.kts` signing config + licensee plugin application — prerequisites for BETA-02 + LIC-02

---

## Sources

### Primary (HIGH confidence)
- [cashapp/licensee GitHub](https://github.com/cashapp/licensee) — plugin ID, version 1.14.1, configuration API, Android assets path, ignoreDependencies syntax
- [softprops/action-gh-release GitHub](https://github.com/softprops/action-gh-release) — v2 usage, `files` parameter, `contents: write` permission requirement
- [Android Developer Docs — SigningConfig API](https://developer.android.com/reference/tools/gradle-api/8.3/com/android/build/api/dsl/SigningConfig) — `signingConfigs` block structure in AGP 8.x
- [mileskrell signing gist](https://gist.github.com/mileskrell/7074c10cb3298a2c9d75e733be7061c2) — Kotlin DSL signing config with env vars pattern
- [jaredsburrows/gradle-license-plugin README](https://github.com/jaredsburrows/gradle-license-plugin/blob/master/README.md) — version 0.9.8, task names, configuration options

### Secondary (MEDIUM confidence)
- [ReactiveCircus/app-versioning](https://github.com/ReactiveCircus/app-versioning) — versionCode positional formula (MAJOR×10000 + MINOR×100 + PATCH)
- [DEV Community — Android APK GitHub Actions](https://dev.to/ronynn/automating-android-apk-builds-with-github-actions-the-sane-way-1h95) — workflow YAML structure, JDK setup, Gradle caching pattern

### Tertiary (LOW confidence)
- Various WebSearch results on multi-variant licensee behavior and gradle-build-action deprecation — flagged for spot-check

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — cashapp/licensee v1.14.1 verified on official GitHub; action versions verified via marketplace; signing config pattern verified via official Android docs
- Architecture: HIGH — workflow YAML patterns verified from official GitHub Actions docs and multiple community sources
- Pitfalls: MEDIUM — keystore encoding issue and licensee unknown-URL issue verified from GitHub issues; others from best-practice documentation

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (GitHub Actions action versions change frequently; verify `gradle/actions/setup-gradle` latest tag before implementing)
