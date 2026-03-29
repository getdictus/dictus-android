---
phase: 06-release-readiness
plan: 01
subsystem: ui
tags: [android, localization, string-resources, compose, bilingual, i18n]

# Dependency graph
requires:
  - phase: 04-model-management-onboarding
    provides: 7 onboarding Compose screens with hardcoded French strings

provides:
  - app/src/main/res/values/strings.xml with English default onboarding strings
  - app/src/main/res/values-fr/strings.xml with French overlay onboarding strings
  - All 7 onboarding screens use stringResource(R.string.*) — locale-agnostic

affects:
  - 06-02 (settings string extraction will follow same pattern)
  - future language additions (add values-XX/ overlay, zero code changes)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "String resource IDs: English-descriptive with screen-prefix convention (onboarding_welcome_tagline)"
    - "Format strings: $variable Kotlin interpolation converted to %1$d/%1$s Android format specifiers"
    - "Model proper names (Tiny) left untranslated intentionally"
    - "Non-UI strings (ClipData label, timer format) excluded from extraction"

key-files:
  created:
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-fr/strings.xml
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingWelcomeScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingMicPermissionScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModeSelectionScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingKeyboardSetupScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingTestRecordingScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingSuccessScreen.kt

key-decisions:
  - "English strings in values/strings.xml (no qualifier) serve as fallback — no values-en/ created to avoid resource resolution quirks"
  - "Model name 'Tiny' intentionally not extracted — it is a proper name, not translated"
  - "Timer format '%d:%02d' not extracted — numeric format is locale-neutral"
  - "ClipData label 'Dictus' not extracted — internal API label, not user-facing UI text"
  - "onboarding_test_recording_no_result uses context.getString() inside a coroutine lambda — stringResource() requires composition context"

requirements-completed: [DSG-03]

# Metrics
duration: 4min
completed: 2026-03-29
---

# Phase 6 Plan 01: Onboarding String Externalization Summary

**35 hardcoded French strings extracted from 7 onboarding Compose screens into values/strings.xml (EN) and values-fr/strings.xml (FR), enabling Android locale-aware bilingual UI**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-29T08:06:18Z
- **Completed:** 2026-03-29T08:10:38Z
- **Tasks:** 1
- **Files modified:** 9

## Accomplishments
- Created app/src/main/res/values/strings.xml with 35 English default strings covering all 7 onboarding screens
- Created app/src/main/res/values-fr/strings.xml with matching French translations preserving current UI text exactly
- Updated all 7 onboarding Compose files to use stringResource(R.string.*) — zero hardcoded French strings remain
- Converted Kotlin string interpolation (download progress %) to Android format string with %1$d and %% escaping
- App compiles successfully with BUILD SUCCESSFUL

## Task Commits

Each task was committed atomically:

1. **Task 1: Create app/ string resource files and extract onboarding strings** - `dce783e` (feat)

## Files Created/Modified
- `app/src/main/res/values/strings.xml` - English default strings for all 7 onboarding screens (35 strings)
- `app/src/main/res/values-fr/strings.xml` - French overlay strings matching current UI text exactly (35 strings)
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingWelcomeScreen.kt` - tagline + CTA + wordmark extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingMicPermissionScreen.kt` - title, body, 2 CTA variants extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModeSelectionScreen.kt` - title, body, CTA extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingKeyboardSetupScreen.kt` - title, body, 2 CTA variants extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt` - title, body, 3 CTA variants, error, retry, recommended badge, size, speed, format string extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingTestRecordingScreen.kt` - title, body, result, transcribing, no-result, tap-to-stop, CTA, 3 content descriptions extracted
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingSuccessScreen.kt` - title, body, CTA extracted

## Decisions Made
- English strings go in values/strings.xml (no qualifier) as the unqualified fallback — creating values-en/ would cause unexpected behavior per Android resource resolution rules
- Model name "Tiny" intentionally left as a hardcoded string literal — it is a proper name for the Whisper model, not a UI string that gets translated
- Timer format `"%d:%02d"` not extracted — it formats two integers for a clock display, identical in all locales
- `ClipData.newPlainText("Dictus", ...)` label not extracted — it is an internal API identifier passed to the system clipboard service, not displayed in the UI
- `(Aucun résultat)` fallback in the recording test uses `context.getString()` instead of `stringResource()` because it is accessed inside a coroutine `launch` block outside of Compose composition context

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Onboarding string extraction complete; same pattern ready to apply to settings, IME, and core module strings in subsequent plans (06-02+)
- French UI currently preserved exactly; switching device locale to English will show English strings automatically via Android resource resolution
- No code changes needed to add further languages — just add values-XX/ overlay files

## Self-Check: PASSED

- `app/src/main/res/values/strings.xml` — FOUND
- `app/src/main/res/values-fr/strings.xml` — FOUND
- Commit `dce783e` — FOUND in git log
- Zero hardcoded French strings in onboarding/ — VERIFIED

---
*Phase: 06-release-readiness*
*Completed: 2026-03-29*
