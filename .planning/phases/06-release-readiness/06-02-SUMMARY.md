---
phase: 06-release-readiness
plan: "02"
subsystem: i18n
tags: [localization, appcompat, stringResource, generateLocaleConfig, French, bilingual]

# Dependency graph
requires:
  - phase: 06-release-readiness
    provides: Phase 6 context and DSG-02/DSG-03 requirements defined
provides:
  - LanguagePickerTest.kt Wave 0 stubs (5 tests, Nyquist contract)
  - IME string resources extracted (en/fr overlays for 3 hardcoded strings)
  - core/ French overlay for ime_name and subtype labels
  - appcompat 1.7.0 dependency added to version catalog and app module
  - generateLocaleConfig enabled with resources.properties in app/src/main/res/
  - AppLocalesMetadataHolderService declared in AndroidManifest for API 24-32 locale persistence
  - MainActivity extended from AppCompatActivity (was ComponentActivity)
affects: [06-03, language-picker-implementation]

# Tech tracking
tech-stack:
  added: [androidx.appcompat:1.7.0]
  patterns: [stringResource() for all IME user-visible text, values-fr/ overlay per module]

key-files:
  created:
    - app/src/test/java/dev/pivisolutions/dictus/ui/settings/LanguagePickerTest.kt
    - ime/src/main/res/values/strings.xml
    - ime/src/main/res/values-fr/strings.xml
    - core/src/main/res/values-fr/strings.xml
    - app/src/main/res/resources.properties
  modified:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/RecordingScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/TranscribingScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/EmojiPickerScreen.kt
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt

key-decisions:
  - "resources.properties must be in app/src/main/res/ not app/src/main/ — AGP extractSupportedLocales task requires it inside the res source set"
  - "AppCompatActivity extends ComponentActivity so all existing APIs (setContent, enableEdgeToEdge, Hilt) remain available without changes"
  - "ime_emoji_return_keyboard (ABC) is identical in both languages — French overlay still provides the string for consistency"

patterns-established:
  - "String extraction pattern: create values/strings.xml in module, add values-fr/strings.xml overlay, replace hardcoded text with stringResource(R.string.xxx)"
  - "French overlay files do NOT duplicate app_name — Dictus is the same in both languages"

requirements-completed: [DSG-02, DSG-03]

# Metrics
duration: 4min
completed: 2026-03-29
---

# Phase 6 Plan 02: Localization Infrastructure Summary

**IME strings extracted to resources with FR overlay, appcompat 1.7.0 wired for AppCompatDelegate locale switching, and Wave 0 test stubs created for plan 06-03**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-29T08:06:09Z
- **Completed:** 2026-03-29T08:09:54Z
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- 5 Wave 0 LanguagePickerTest stubs created (Nyquist requirement for plan 06-03)
- 3 IME screens (RecordingScreen, TranscribingScreen, EmojiPickerScreen) now use `stringResource()` instead of hardcoded French text
- core/ and ime/ both have `values-fr/strings.xml` overlays covering all user-visible strings
- Full localization infrastructure in place: appcompat dependency, `generateLocaleConfig`, `resources.properties`, `AppLocalesMetadataHolderService`, `AppCompatActivity`

## Task Commits

Each task was committed atomically:

1. **Task 0: Create LanguagePickerTest.kt stub** - `b35da95` (test)
2. **Task 1: Extract ime/ strings and add core/ French overlay** - `4d27bc9` (feat)
3. **Task 2: Localization infrastructure setup** - `4296657` (feat)

## Files Created/Modified

- `app/src/test/java/dev/pivisolutions/dictus/ui/settings/LanguagePickerTest.kt` - 5 Wave 0 stub tests for setUiLanguage/getCurrentUiLanguage
- `ime/src/main/res/values/strings.xml` - English defaults: Listening, Transcribing, ABC
- `ime/src/main/res/values-fr/strings.xml` - French overlay: En écoute, Transcription, ABC
- `core/src/main/res/values-fr/strings.xml` - French overlay: Clavier Dictus, AZERTY (Français), QWERTY (Anglais)
- `app/src/main/res/resources.properties` - Declares unqualifiedResLocale=en-US for generateLocaleConfig
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/RecordingScreen.kt` - Uses `stringResource(R.string.ime_recording_listening)`
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/TranscribingScreen.kt` - Uses `stringResource(R.string.ime_transcribing)`
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/EmojiPickerScreen.kt` - Uses `stringResource(R.string.ime_emoji_return_keyboard)`
- `gradle/libs.versions.toml` - appcompat version and library alias added
- `app/build.gradle.kts` - `implementation(libs.androidx.appcompat)` and `generateLocaleConfig = true`
- `app/src/main/AndroidManifest.xml` - `AppLocalesMetadataHolderService` service declaration
- `app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt` - Extends `AppCompatActivity` instead of `ComponentActivity`

## Decisions Made

- `resources.properties` belongs in `app/src/main/res/` (inside the res source set), not `app/src/main/` — AGP's `extractSupportedLocales` task cannot find it at the `src/main/` level.
- `AppCompatActivity` is a drop-in replacement for `ComponentActivity` since it extends it transitively; no existing API calls needed updating.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] resources.properties placed in correct res/ directory**
- **Found during:** Task 2 (localization infrastructure)
- **Issue:** Plan specified `app/src/main/resources.properties` but AGP task `:app:extractDebugSupportedLocales` requires the file inside the res source set at `app/src/main/res/resources.properties`
- **Fix:** File created at `app/src/main/res/resources.properties` instead of `app/src/main/resources.properties`
- **Files modified:** app/src/main/res/resources.properties
- **Verification:** `:app:assembleDebug` passes without the "No resources.properties file found" error
- **Committed in:** 4296657 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Essential correction; wrong path caused build failure. No scope creep.

## Issues Encountered

None beyond the auto-fixed resources.properties path issue.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 06-03 can proceed: LanguagePickerTest.kt stubs are in place, AppCompatActivity and appcompat dependency are wired — implementing `AppCompatDelegate.setApplicationLocales()` is the only missing piece
- All IME modules compile with string resources; French locale will activate automatically at runtime when the user selects French in the language picker
- `generateLocaleConfig` is enabled so the system language selector in Android 13+ will show Dictus as supporting en and fr

---
*Phase: 06-release-readiness*
*Completed: 2026-03-29*
