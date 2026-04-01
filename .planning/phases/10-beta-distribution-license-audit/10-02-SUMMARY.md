---
phase: 10-beta-distribution-license-audit
plan: 02
subsystem: infra
tags: [licensee, cashapp, license-audit, gradle, kotlin, jetpack-compose]

# Dependency graph
requires:
  - phase: 10-01
    provides: CI/CD workflows and signing config in app/build.gradle.kts

provides:
  - cashapp/licensee 1.14.1 plugin configured across all Gradle files with SPDX allowlist
  - Build fails automatically if a GPL-contaminated dependency is added
  - LicencesScreen reads auto-generated artifacts.json from assets + preserves 4 manual entries
  - GPL enforcement gate active on every build via licenseeDebug/licenseeRelease

affects: [11-oss-repository, app-release]

# Tech tracking
tech-stack:
  added: [cashapp/licensee 1.14.1]
  patterns:
    - "licensee plugin applied to app module only (sees all transitive deps via implementation project(...))"
    - "artifacts.json loaded via context.assets.open with graceful emptyList() fallback on IOException"
    - "org.json.JSONArray from Android SDK for zero-dependency JSON parsing"

key-files:
  created: []
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - app/build.gradle.kts
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/LicencesScreen.kt

key-decisions:
  - "licensee applied to app module only: aggregates all transitive deps from :core, :ime, :whisper, :asr"
  - "org.json.JSONArray (Android SDK) used for artifacts.json parsing: zero new dependency, sufficient for flat structure"
  - "Graceful emptyList() fallback in LicencesScreen: artifacts.json absent when licenseeDebug skipped locally"
  - "CC-BY-4.0 and BSD-2-Clause kept in allowlist despite current zero usage: proactive allowlist for Parakeet and future deps"

patterns-established:
  - "LicencesScreen pattern: auto-generated Maven entries first, then Vendored & Non-Maven section header, then manual entries"
  - "licenseeDebug must be run explicitly before assembleDebug in CI (plugin wired into check, not assemble)"

requirements-completed: [LIC-01, LIC-02]

# Metrics
duration: 2min
completed: 2026-04-01
---

# Phase 10 Plan 02: License Audit Summary

**cashapp/licensee 1.14.1 auto-generates OSS license list into assets and blocks GPL at build time; LicencesScreen reads artifacts.json with graceful fallback and preserves manual entries for whisper.cpp, sherpa-onnx, Parakeet, and Dictus**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-01T12:47:00Z
- **Completed:** 2026-04-01T12:49:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- cashapp/licensee 1.14.1 declared in version catalog, registered in root build, applied in app module with Apache-2.0/MIT/BSD-2-Clause/BSD-3-Clause/CC-BY-4.0 SPDX allowlist
- `./gradlew :app:licenseeDebug` passes with no GPL violations (2 harmless unused-allowlist warnings)
- LicencesScreen rewritten to load from `app/cash/licensee/artifacts.json` at runtime, renders Maven entries first, then "Vendored & Non-Maven" section, then 4 preserved manual entries
- All 108 unit tests pass with no compilation errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure cashapp/licensee plugin in Gradle files** - `5aea5af` (chore)
2. **Task 2: Update LicencesScreen to load from artifacts.json + manual entries** - `98a6120` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified

- `gradle/libs.versions.toml` - Added `licensee = { id = "app.cash.licensee", version = "1.14.1" }` to [plugins]
- `build.gradle.kts` - Added `alias(libs.plugins.licensee) apply false` to root plugins block
- `app/build.gradle.kts` - Applied licensee plugin + licensee {} allowlist block after android {} block
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/LicencesScreen.kt` - Added MavenLicense data class, parseLicenseeArtifacts(), assets loading with fallback, Maven entries rendering, "Vendored & Non-Maven" section header; preserved all 4 manual LicenceBlock calls

## Decisions Made

- **licensee applied to app module only:** The app module has `implementation project(":core")`, `implementation project(":whisper")`, `implementation project(":asr")`, `implementation project(":ime")` — all transitive deps flow through app's dependency graph. Confirmed by successful licenseeDebug run.
- **org.json.JSONArray for JSON parsing:** kotlinx.serialization not in libs.versions.toml; adding it would require plugin + dep. org.json is available from Android SDK at zero cost. The artifacts.json structure is flat enough to parse manually.
- **Graceful emptyList() fallback:** artifacts.json is only generated when licenseeDebug/licenseeRelease runs, not on plain assembleDebug. The screen gracefully shows only manual entries when the file is absent (local dev without licensee task).
- **CC-BY-4.0 and BSD-2-Clause in allowlist despite zero current use:** CC-BY-4.0 is Parakeet's license (model weights, not a Maven artifact), BSD-2-Clause is a common license that may appear transitively. Proactive inclusion avoids build failures when deps are upgraded.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `./gradlew :app:licenseeDebug` passed on first run. Two `WARNING: Allowed SPDX identifier 'CC-BY-4.0' is unused` and `'BSD-2-Clause' is unused` messages are expected and harmless — these identifiers are in the allowlist as a safeguard.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- License compliance gate active: build will fail if a GPL-contaminated Maven dependency is added
- LicencesScreen is future-proof: always shows complete OSS list without manual maintenance
- Phase 10 Plan 03 (README beta section) or Phase 11 (OSS Repository) can proceed
- CI workflows (Plan 01) should explicitly run `./gradlew :app:licenseeDebug` before `assembleDebug` to ensure artifacts.json is bundled (already done in 10-01-SUMMARY.md decisions)

---
*Phase: 10-beta-distribution-license-audit*
*Completed: 2026-04-01*
