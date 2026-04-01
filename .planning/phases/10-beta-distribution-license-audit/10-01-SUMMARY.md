---
phase: 10-beta-distribution-license-audit
plan: 01
subsystem: infra
tags: [github-actions, ci-cd, android-signing, gradle, apk-release]

# Dependency graph
requires: []
provides:
  - CI workflow: lint + unit tests + licenseeDebug + assembleDebug on push/PR to main
  - Release workflow: signed APK build on v* tag with licenseeRelease + upload to GitHub Releases
  - Gradle signing config reading env vars with null-safe guard for local dev
  - .gitignore exclusion of keystore files
affects: [10-02, 10-03, 10-04]

# Tech tracking
tech-stack:
  added:
    - actions/checkout@v4
    - actions/setup-java@v4 (temurin 17)
    - gradle/actions/setup-gradle@v4
    - actions/upload-artifact@v4
    - softprops/action-gh-release@v2
  patterns:
    - Gradle signingConfigs reading from env vars with null guard (no NPE on local debug builds)
    - licenseeDebug/licenseeRelease explicitly run before assemble to ensure artifacts.json is bundled in APK assets
    - versionCode derived from git tag: MAJOR*10000 + MINOR*100 + PATCH
    - CI version override via VERSION_CODE / VERSION_NAME env vars in defaultConfig

key-files:
  created:
    - .github/workflows/ci.yml
    - .github/workflows/release.yml
  modified:
    - app/build.gradle.kts
    - .gitignore

key-decisions:
  - "licenseeDebug/licenseeRelease invoked explicitly before assembleDebug/assembleRelease: plugin is wired into check not assemble, so explicit invocation is required to ensure artifacts.json is present in APK assets"
  - "versionCode derived from semver tag via MAJOR*10000 + MINOR*100 + PATCH bash arithmetic (no external plugin needed)"
  - "signingConfigs null guard: if (keystorePath != null) prevents NPE during Gradle config phase when env vars absent in local dev"
  - "release buildType sets isMinifyEnabled = false (obfuscation not needed for open-source release)"

patterns-established:
  - "Pattern: GitHub Actions Android CI with two parallel jobs (lint, test+build) using ubuntu-latest + JDK 17 Temurin"
  - "Pattern: Release signing via base64-decoded keystore from GitHub Secret, env vars passed to assembleRelease"

requirements-completed: [BETA-01, BETA-02]

# Metrics
duration: 2min
completed: 2026-04-01
---

# Phase 10 Plan 01: CI/CD Workflows and APK Signing Summary

**GitHub Actions CI (lint+test+licenseeDebug+assembleDebug) and Release (signed APK via v* tag) workflows with env-var-based Gradle signing config and keystore gitignore**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-01T12:43:36Z
- **Completed:** 2026-04-01T12:44:51Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- CI workflow triggers on push/PR to main: runs lint, unit tests, licenseeDebug + assembleDebug in parallel lint and test jobs, uploads debug APK artifact
- Release workflow triggers on v* tag: decodes keystore from GitHub Secret, extracts semver version code from tag, runs licenseeRelease + assembleRelease for signed APK, uploads to GitHub Releases
- Signing config in app/build.gradle.kts reads from env vars with null guard — local debug builds work without signing credentials
- .gitignore updated to exclude *.jks, *.keystore, keystore.properties

## Task Commits

1. **Task 1: Create CI and Release GitHub Actions workflows** - `77165ba` (feat)
2. **Task 2: Add signing config to app/build.gradle.kts and update .gitignore** - `e7cb1de` (feat)

## Files Created/Modified

- `.github/workflows/ci.yml` — CI workflow: lint + test + licenseeDebug + assembleDebug on push/PR to main
- `.github/workflows/release.yml` — Release workflow: keystore decode + version extraction + licenseeRelease + assembleRelease + GitHub Releases upload on v* tag
- `app/build.gradle.kts` — Added signingConfigs block, CI version override in defaultConfig, release buildType wired to signing config
- `.gitignore` — Added *.jks, *.keystore, keystore.properties exclusions

## Decisions Made

- licenseeDebug/licenseeRelease invoked explicitly before assembleDebug/assembleRelease: the licensee plugin generates artifacts.json only when its task runs, and it is wired into `check` not `assemble` — without explicit invocation, CI-built APKs would have no license data in assets
- versionCode derived from semver tag with bash arithmetic (MAJOR*10000 + MINOR*100 + PATCH): simple, no extra Gradle plugin, max PATCH value 99 before overflow
- Null guard on keystorePath before calling `file()`: Gradle evaluates signingConfigs eagerly during configuration phase, so null check must wrap `file()` call to prevent NPE on local dev machines without signing env vars

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

**External services require manual configuration.** Add 4 GitHub repository secrets before the release workflow can produce a signed APK:

| Secret Name | How to Get It |
|-------------|---------------|
| `SIGNING_KEYSTORE` | Base64-encode your release keystore: `base64 -i dictus-release.jks \| tr -d '\n'` (macOS) |
| `SIGNING_KEY_ALIAS` | The key alias used when creating the keystore (e.g. `dictus`) |
| `SIGNING_KEY_PASSWORD` | The key password used when creating the keystore |
| `SIGNING_STORE_PASSWORD` | The keystore password used when creating the keystore |

Location: GitHub repo > Settings > Secrets and variables > Actions > New repository secret

**Keystore creation (if not yet created):**
```bash
keytool -genkeypair -v -keystore dictus-release.jks -alias dictus -keyalg RSA -keysize 2048 -validity 10000
```

## Next Phase Readiness

- CI and release automation infrastructure is complete for BETA-01 and BETA-02
- Plan 10-02 can proceed: cashapp/licensee plugin setup + LicencesScreen JSON reader (LIC-01/LIC-02)
- Plan 10-03 can proceed: README beta installation section (BETA-03)

---
*Phase: 10-beta-distribution-license-audit*
*Completed: 2026-04-01*
