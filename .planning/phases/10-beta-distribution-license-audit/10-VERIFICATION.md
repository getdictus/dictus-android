---
phase: 10-beta-distribution-license-audit
verified: 2026-04-01T14:55:00Z
status: passed
score: 7/7 must-haves verified
re_verification: false
---

# Phase 10: Beta Distribution & License Audit Verification Report

**Phase Goal:** Every push to main is validated by CI, every version tag produces a signed APK on GitHub Releases, and the project's OSS dependency licenses are audited and correct.
**Verified:** 2026-04-01T14:55:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                               | Status     | Evidence                                                                              |
|----|-----------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------|
| 1  | A push/PR to main triggers CI: lint + test + licenseeDebug + assembleDebug                         | VERIFIED   | `.github/workflows/ci.yml` lines 3–7 (push/PR triggers), lines 23–43 (jobs)          |
| 2  | A v* tag push triggers release: signed APK built and uploaded to GitHub Releases                    | VERIFIED   | `.github/workflows/release.yml` lines 3–6 (tag trigger), lines 25–55 (full pipeline) |
| 3  | Local debug builds work without signing env vars (null guard prevents NPE)                          | VERIFIED   | `app/build.gradle.kts` lines 20–26: `if (keystorePath != null)` guards `file()` call |
| 4  | CI-built APKs include artifacts.json (licenseeDebug/licenseeRelease run before assemble)            | VERIFIED   | `ci.yml` line 43: `./gradlew licenseeDebug assembleDebug`; `release.yml` line 49: `./gradlew licenseeRelease assembleRelease` |
| 5  | Licenses screen shows auto-generated Maven entries from artifacts.json                              | VERIFIED   | `LicencesScreen.kt` lines 90–111: `context.assets.open("app/cash/licensee/artifacts.json")` + loop render |
| 6  | Licenses screen preserves manual entries for whisper.cpp, sherpa-onnx, NVIDIA Parakeet, Dictus     | VERIFIED   | `LicencesScreen.kt` lines 125–164: all 4 manual `LicenceBlock` calls present         |
| 7  | README provides step-by-step sideloading instructions with GitHub Releases link and Issues link     | VERIFIED   | `README.md`: "## Beta Installation" section, `releases/latest` link, `issues` link   |

**Score:** 7/7 truths verified

---

## Required Artifacts

| Artifact                                                                   | Provides                                        | Status     | Details                                                        |
|----------------------------------------------------------------------------|-------------------------------------------------|------------|----------------------------------------------------------------|
| `.github/workflows/ci.yml`                                                 | CI workflow: lint + test + build on push/PR     | VERIFIED   | 50 lines, correct triggers, 2 parallel jobs, upload artifact   |
| `.github/workflows/release.yml`                                            | Release workflow: signed APK on v* tag          | VERIFIED   | 56 lines, v* trigger, keystore decode, softprops upload        |
| `app/build.gradle.kts`                                                     | Signing config + licensee plugin + buildTypes   | VERIFIED   | signingConfigs, System.getenv, licensee {}, buildTypes.release |
| `gradle/libs.versions.toml`                                                | licensee plugin declaration                     | VERIFIED   | Line 66: `licensee = { id = "app.cash.licensee", version = "1.14.1" }` |
| `build.gradle.kts` (root)                                                  | licensee plugin registered apply false          | VERIFIED   | Line 9: `alias(libs.plugins.licensee) apply false`             |
| `app/src/main/java/dev/pivisolutions/dictus/ui/settings/LicencesScreen.kt`| Auto + manual license display                   | VERIFIED   | MavenLicense, parseLicenseeArtifacts, assets.open, 4 manual entries |
| `README.md`                                                                | Beta installation instructions                  | VERIFIED   | 46 lines, sideloading steps, releases/latest, issues links     |
| `.gitignore`                                                               | Keystore files excluded                         | VERIFIED   | Lines 19–22: *.jks, *.keystore, keystore.properties            |

---

## Key Link Verification

| From                          | To                            | Via                                      | Status   | Details                                                              |
|-------------------------------|-------------------------------|------------------------------------------|----------|----------------------------------------------------------------------|
| `.github/workflows/ci.yml`    | `licenseeDebug`               | explicit Gradle task before assembleDebug | WIRED    | Line 43: `./gradlew licenseeDebug assembleDebug`                     |
| `.github/workflows/release.yml` | `licenseeRelease`           | explicit Gradle task before assembleRelease | WIRED  | Line 49: `./gradlew licenseeRelease assembleRelease`                 |
| `.github/workflows/release.yml` | `app/build.gradle.kts`      | env vars SIGNING_KEYSTORE_PATH etc.      | WIRED    | Lines 43–48: all 4 signing env vars + VERSION_CODE/VERSION_NAME      |
| `app/build.gradle.kts`        | `System.getenv`               | signing config reads env vars at build time | WIRED  | Lines 16–19: System.getenv for all 4 signing vars                    |
| `app/build.gradle.kts`        | `gradle/libs.versions.toml`  | `alias(libs.plugins.licensee)`           | WIRED    | Line 7 of app/build.gradle.kts; line 66 of libs.versions.toml       |
| `LicencesScreen.kt`           | `assets/app/cash/licensee/artifacts.json` | `context.assets.open`          | WIRED    | Lines 92–94: assets.open with exact path + graceful catch fallback   |

---

## Requirements Coverage

| Requirement | Source Plan | Description                                                           | Status    | Evidence                                                              |
|-------------|-------------|-----------------------------------------------------------------------|-----------|-----------------------------------------------------------------------|
| BETA-01     | 10-01       | CI GitHub Actions: lint + tests + debug build on PR/push main        | SATISFIED | `ci.yml` triggers on push/PR to main, runs lint + test + assembleDebug |
| BETA-02     | 10-01       | Release workflow: signs APK automatically on tag push to GitHub Releases | SATISFIED | `release.yml` triggers on v* tags, builds signed APK, uploads via softprops/action-gh-release@v2 |
| BETA-03     | 10-03       | README provides beta APK installation instructions                    | SATISFIED | `README.md` has "## Beta Installation" with 6-step sideloading guide |
| LIC-01      | 10-02       | Licenses screen correctly references all OSS dependencies             | SATISFIED | `LicencesScreen.kt` auto-loads Maven deps from artifacts.json + 4 manual entries (whisper.cpp, sherpa-onnx, NVIDIA Parakeet, Dictus) |
| LIC-02      | 10-02       | Project uses gradle-license-plugin for automatic license auditing     | SATISFIED | cashapp/licensee 1.14.1 applied in app module with SPDX allowlist; build fails on GPL deps |

**Orphaned requirements:** None. All 5 requirements mapped to Phase 10 in REQUIREMENTS.md are claimed and satisfied across the 3 plans.

---

## Anti-Patterns Found

None. No TODO/FIXME/placeholder comments, no stub returns, no empty handlers found in any phase 10 files.

---

## Commits Verified

All commits documented in summaries exist in git history:

| Commit   | Message                                                                    |
|----------|----------------------------------------------------------------------------|
| 77165ba  | feat(10-01): create CI and Release GitHub Actions workflows                |
| e7cb1de  | feat(10-01): add signing config to build.gradle.kts and update .gitignore |
| 5aea5af  | chore(10-02): configure cashapp/licensee 1.14.1 plugin                     |
| 98a6120  | feat(10-02): update LicencesScreen to load from artifacts.json + manual entries |
| 5d524be  | feat(10-03): create README with beta installation instructions             |

---

## Human Verification Required

### 1. CI workflow execution on GitHub

**Test:** Push a commit to main (or open a PR targeting main) and observe the Actions tab on GitHub.
**Expected:** Two jobs appear ("Lint" and "Unit Tests + Debug Build"), both pass green, and a debug-apk artifact is downloadable from the workflow run.
**Why human:** The workflow YAML is syntactically correct and all required steps are present, but actual runner execution (Gradle cache, Android SDK availability on ubuntu-latest, build success) can only be confirmed by GitHub Actions itself.

### 2. Release workflow on tag push

**Test:** Push a tag `v0.1.0` to the repository after configuring the 4 GitHub Secrets (SIGNING_KEYSTORE, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, SIGNING_STORE_PASSWORD).
**Expected:** A GitHub Release named "Dictus 0.1.0" appears with `app-release.apk` attached; the APK installs on an Android device.
**Why human:** Requires a real signing keystore to be created and the 4 secrets to be added in GitHub repository settings. These are external-service prerequisites that cannot be verified from the codebase alone.

### 3. LicencesScreen runtime display

**Test:** Run the app, open Settings, tap Licences. Observe the screen content.
**Expected:** A list of Maven dependencies appears first (auto-generated from artifacts.json), followed by a "Vendored & Non-Maven" section header, then 4 manual entries (whisper.cpp, sherpa-onnx, NVIDIA Parakeet, Dictus).
**Why human:** The `artifacts.json` asset is only generated when `licenseeDebug` runs as part of the build. A plain `assembleDebug` without `licenseeDebug` will produce an APK where the screen gracefully shows only manual entries. Verifying the auto-generated section requires a build that explicitly ran `licenseeDebug`.

---

## Gaps Summary

No gaps. All automated verification checks passed across all 7 observable truths, all 8 required artifacts, and all 6 key links. All 5 requirements (BETA-01, BETA-02, BETA-03, LIC-01, LIC-02) are satisfied. No anti-patterns were found. Three human verification items remain for runtime and external-service behavior that cannot be checked from static code analysis.

---

_Verified: 2026-04-01T14:55:00Z_
_Verifier: Claude (gsd-verifier)_
