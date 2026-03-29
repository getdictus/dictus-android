---
phase: 06-release-readiness
plan: "04"
subsystem: app-strings
tags: [localization, i18n, strings, settings, home, models, recording]
dependency_graph:
  requires: [06-01]
  provides: [DSG-03-partial]
  affects: [settings, home, models, recording, onboarding]
tech_stack:
  added: []
  patterns: [stringResource, context.getString for lambdas]
key_files:
  created: []
  modified:
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-fr/strings.xml
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SoundSettingsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/DebugLogsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/LicencesScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/ImeStatusCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/RecordingTestArea.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/onboarding/FakeSettingsCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/home/HomeScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/recording/RecordingScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt
decisions:
  - "noResultLabel captured via remember{stringResource()} before coroutine scope in RecordingScreen to avoid composable context restriction"
  - "~$sizeMb Mo size labels left as dynamic strings — identical in both locales and not user-facing prose"
  - "FakeSettingsCard strings extracted but note: French translation uses localized subtitles (Gestion des claviers, Saisie multilingue, AZERTY)"
metrics:
  duration: "9 minutes"
  completed_date: "2026-03-29"
  tasks_completed: 2
  files_modified: 13
---

# Phase 06 Plan 04: App String Externalization Summary

All hardcoded French/English user-visible strings extracted from settings, home, models, recording, and shared UI component screens into Android string resource files.

## What Was Built

Externalized 74 new strings (129 total in both resource files) from 11 Kotlin files:

**Task 1 — Settings and UI components:**
- `SettingsScreen.kt`: all 4 section headers, 12 row labels, language/theme picker values, picker sheet titles, Toast messages
- `SoundSettingsScreen.kt`: back button CD, title, 2 section headers, 5 row labels
- `DebugLogsScreen.kt`: title, empty state, menu item labels, content descriptions
- `LicencesScreen.kt`: title and back button content description
- `ImeStatusCard.kt`: 3 status messages, 2 button labels
- `RecordingTestArea.kt`: title, 3 state labels, 3 button labels
- `FakeSettingsCard.kt`: header text, 2 keyboard subtitles

**Task 2 — Home, models, and recording screens:**
- `ModelCard.kt`: active chip, download/retry buttons, progress label, error label, metric bar labels, delete CD
- `HomeScreen.kt`: active model label, last transcription label, copy CD, CTA button
- `RecordingScreen.kt`: result title, back/stop/record/copy CDs, idle title/body, transcribing text, tap-to-stop label, no-result fallback
- `ModelsScreen.kt`: title, 2 section headers, storage used format string, delete confirmation title/body/buttons

## Decisions Made

- **noResultLabel pattern**: `stringResource()` cannot be called inside a coroutine lambda (non-composable context). Captured the value via `val noResultLabel = stringResource(R.string.recording_no_result)` before the launch block — consistent with the existing pattern from plan 06-01's onboarding test recording screen.
- **Size labels (~$sizeMb Mo)**: Left as dynamic string literals — both locales use "Mo" and this is a technical value format, not translatable prose.
- **FakeSettingsCard**: "Manage keyboards" header and keyboard subtitles extracted. French translation uses localized versions (Gérer les claviers, Saisie multilingue, AZERTY (Français)) for locale correctness in the onboarding animation.

## Deviations from Plan

### Out-of-Scope Discoveries

**Pre-existing test failures (not caused by this plan):**
- `ModelCatalogTest` (3 failures) — model size assertions failing
- `AudioCaptureManagerTest` (2 failures) — energy normalization failures
- `OnboardingViewModelTest` (1 failure) — due to unstaged modification in `OnboardingViewModel.kt` (step count changed 6→7 in working directory, unrelated to this plan)

These failures existed before this plan's execution and are out of scope. Documented here for traceability.

## Verification

- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- 129 strings in values/strings.xml (English defaults)
- 129 strings in values-fr/strings.xml (French translations)
- Zero hardcoded French prose strings remain in settings, home, models, or recording screens
- Only technical/format strings remain as literals (~$sizeMb Mo, WK badge, checkmark ✓)

## Self-Check: PASSED

- values/strings.xml: FOUND
- values-fr/strings.xml: FOUND
- 06-04-SUMMARY.md: FOUND
- Commit f735f0a (Task 1): FOUND
- Commit ef55323 (Task 2): FOUND
