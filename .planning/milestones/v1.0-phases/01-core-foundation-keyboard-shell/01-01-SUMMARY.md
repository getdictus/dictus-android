---
phase: 01-core-foundation-keyboard-shell
plan: 01
subsystem: infra
tags: [gradle, kotlin, compose, hilt, timber, material3, android]

# Dependency graph
requires: []
provides:
  - Multi-module Gradle project (app, ime, core) with version catalog
  - Dictus dark theme with iOS brand color tokens as Material 3 ColorScheme
  - Timber logging initialization in Application.onCreate()
  - Hilt DI setup with @HiltAndroidApp
  - DataStore PreferenceKeys for keyboard layout, model, language
affects: [01-02, 01-03, 02, 03]

# Tech tracking
tech-stack:
  added: [kotlin-2.1.10, agp-8.7.3, compose-bom-2025.03.00, hilt-2.51.1, ksp-2.1.10-1.0.31, timber-5.0.1, datastore-1.1.3]
  patterns: [multi-module-gradle, version-catalog, dark-only-theme, conditional-timber]

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - gradle.properties
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/dev/pivisolutions/dictus/DictusApplication.kt
    - app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt
    - core/build.gradle.kts
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTypography.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/logging/TimberSetup.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt
    - core/src/main/res/values/strings.xml
    - core/src/main/res/values/colors.xml
    - core/src/test/java/dev/pivisolutions/dictus/core/theme/DictusColorsTest.kt
    - core/src/test/java/dev/pivisolutions/dictus/core/logging/TimberSetupTest.kt
    - ime/build.gradle.kts
  modified: []

key-decisions:
  - "Used Hilt 2.51.1 instead of plan's 2.57.1 (2.57.1 not found in Maven repos)"
  - "Used Compose BOM 2025.03.00 instead of plan's 2026.03.00 (future version not yet published)"
  - "Used arm64-v8a system image for AVD (macOS Apple Silicon requires ARM, not x86_64)"
  - "Created .gitignore with standard Android exclusions (build/, local.properties, .idea/)"
  - "Set JDK target to 17 for AGP 8.x compatibility"

patterns-established:
  - "Version catalog: all dependency versions in gradle/libs.versions.toml"
  - "Module structure: app depends on core and ime; ime depends on core"
  - "Theme: always-dark via DictusTheme composable wrapping MaterialTheme"
  - "Logging: TimberSetup.init(BuildConfig.DEBUG) in Application.onCreate()"

requirements-completed: [APP-06, DSG-01]

# Metrics
duration: 13min
completed: 2026-03-21
---

# Phase 1 Plan 01: Project Skeleton Summary

**3-module Gradle project (app/ime/core) with Hilt DI, Dictus dark theme using iOS color tokens, Timber logging, and version catalog**

## Performance

- **Duration:** 13 min
- **Started:** 2026-03-21T13:43:46Z
- **Completed:** 2026-03-21T13:56:53Z
- **Tasks:** 3 (including env setup)
- **Files modified:** 21

## Accomplishments
- Android SDK 35, build-tools 35, and Pixel 4 API 29 AVD installed and configured
- Multi-module Gradle project builds successfully with ./gradlew assembleDebug
- Dictus dark theme defines exact iOS color tokens as Material 3 ColorScheme
- Timber initialized in Application.onCreate() with conditional DebugTree
- Unit tests verify color token values and Timber initialization behavior

## Task Commits

Each task was committed atomically:

1. **Task 0: Set up Android development environment** - no commit (env setup only: SDK, AVD, JDK 17)
2. **Task 1: Create multi-module Gradle project** - `623aaae` (feat)
3. **Task 2: Create Dictus dark theme and unit tests** - `fb6d340` (feat)

## Files Created/Modified
- `settings.gradle.kts` - Root project config with 3 modules
- `build.gradle.kts` - Root build with plugin declarations
- `gradle/libs.versions.toml` - Version catalog with all dependencies
- `gradle.properties` - Gradle JVM and Android settings
- `app/build.gradle.kts` - App module with Hilt, Compose, KSP
- `app/src/main/AndroidManifest.xml` - Application and MainActivity declarations
- `app/.../DictusApplication.kt` - @HiltAndroidApp entry point with Timber init
- `app/.../MainActivity.kt` - @AndroidEntryPoint with DictusTheme
- `core/build.gradle.kts` - Library module with Compose and Timber
- `core/.../DictusColors.kt` - Brand color tokens (0A1628, 3D7EFF, etc.)
- `core/.../DictusTypography.kt` - Typography scale
- `core/.../DictusTheme.kt` - Material 3 darkColorScheme theme
- `core/.../TimberSetup.kt` - Conditional DebugTree initialization
- `core/.../PreferenceKeys.kt` - DataStore keys for layout, model, language
- `core/.../strings.xml` - App name and IME strings
- `core/.../colors.xml` - XML color resources
- `core/.../DictusColorsTest.kt` - Color token verification tests
- `core/.../TimberSetupTest.kt` - Timber initialization tests
- `ime/build.gradle.kts` - IME library module with Hilt
- `ime/src/main/AndroidManifest.xml` - Empty manifest shell

## Decisions Made
- **Hilt 2.51.1 instead of 2.57.1:** Plan specified 2.57.1 but it was not found in Maven Central/Google repos. Used 2.51.1 which is the latest published stable version.
- **Compose BOM 2025.03.00:** Plan specified 2026.03.00 which is a future date. Used 2025.03.00 as the latest available BOM.
- **ARM64 system image:** Plan specified x86_64 but macOS Apple Silicon requires arm64-v8a system images for emulator.
- **JDK 17 via Homebrew formula:** Temurin cask required sudo; OpenJDK 17 formula installed without elevated privileges.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed Hilt version (2.57.1 not published)**
- **Found during:** Task 1 (Gradle project creation)
- **Issue:** Hilt 2.57.1 does not exist in Maven repositories, causing Gradle plugin resolution failure
- **Fix:** Used Hilt 2.51.1 (latest stable published version)
- **Files modified:** gradle/libs.versions.toml
- **Verification:** ./gradlew assembleDebug builds successfully
- **Committed in:** 623aaae (Task 1 commit)

**2. [Rule 3 - Blocking] Fixed Compose BOM version (2026.03.00 not published)**
- **Found during:** Task 1 (Gradle project creation)
- **Issue:** Compose BOM 2026.03.00 is a future version not yet available
- **Fix:** Used 2025.03.00 (latest available BOM)
- **Files modified:** gradle/libs.versions.toml
- **Verification:** ./gradlew assembleDebug resolves all Compose dependencies
- **Committed in:** 623aaae (Task 1 commit)

**3. [Rule 3 - Blocking] Used ARM64 system image instead of x86_64**
- **Found during:** Task 0 (Environment setup)
- **Issue:** macOS Apple Silicon cannot run x86_64 emulator images
- **Fix:** Installed arm64-v8a system image for API 29
- **Verification:** avdmanager list avd shows Pixel4_API29 created successfully

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All fixes necessary to resolve unavailable/incompatible versions. No scope creep.

## Issues Encountered
- Gradle wrapper generation required creating settings.gradle.kts first (Gradle 9.x refuses to run wrapper task without a build definition). Resolved by generating wrapper in a temp directory and copying files.
- JDK 11 was the only installed JDK; AGP 8.x requires JDK 17. Installed OpenJDK 17 via Homebrew formula.

## User Setup Required
None - all development environment setup was automated.

## Next Phase Readiness
- Project skeleton ready for Plan 02 (InputMethodService and Compose-in-IME lifecycle)
- The ime module has Hilt and Compose dependencies wired; needs service class and lifecycle owners
- DictusTheme is available for keyboard UI rendering in Plan 02/03

## Self-Check: PASSED

All 14 key files verified present. Both task commits (623aaae, fb6d340) verified in git log.

---
*Phase: 01-core-foundation-keyboard-shell*
*Completed: 2026-03-21*
