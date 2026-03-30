---
phase: 04-model-management-onboarding
plan: 08
subsystem: onboarding-ui, navigation, settings
tags: [onboarding, navigation, settings, recording, uat-gap-closure]
dependency_graph:
  requires: []
  provides:
    - FakeSettingsCard Android-native redesign (Gboard + Dictus keyboard list)
    - RecordingScreen standalone dictation flow
    - AppDestination.Recording navigation route
    - LICENCES section in SettingsScreen
  affects:
    - AppNavHost (HomeScreen onNewDictation wired, bottom nav hidden during recording)
    - AppDestination (new Recording destination)
tech_stack:
  added: []
  patterns:
    - Layered Box layout for fixed-position button (same as OnboardingTestRecordingScreen)
    - LaunchedEffect(Unit) for auto-start recording on first composition
    - Conditional bottom bar visibility via showBottomBar flag in Scaffold
key_files:
  created:
    - app/src/main/java/dev/pivisolutions/dictus/recording/RecordingScreen.kt
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/ui/onboarding/FakeSettingsCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
decisions:
  - "FakeSettingsCard uses KeyboardEntryRow (name + subtitle) to match Android Clavier a l ecran panel instead of iOS breadcrumb"
  - "RecordingScreen in new recording/ package — single responsibility, separate from onboarding"
  - "Auto-start recording via LaunchedEffect(Unit) for iOS parity — Nouvelle dictee starts immediately"
  - "Bottom nav hidden via showBottomBar flag (conditional if) rather than AnimatedVisibility — no animation spec in design"
  - "Re-record in result state resets transcriptionResult to null and calls startRecording() inline"
metrics:
  duration: "~3 min"
  completed: "2026-03-27"
  tasks: 3
  files: 5
---

# Phase 04 Plan 08: UAT Gap Closure — FakeSettingsCard, Recording Flow, Licences Summary

Android-native FakeSettingsCard redesign, standalone RecordingScreen wired to Home, and LICENCES section added to Settings — closing UAT tests 3, 8, and 16.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Redesign FakeSettingsCard to Android-native style | 8b4dc7c | FakeSettingsCard.kt |
| 2 | Create RecordingScreen and wire Home Nouvelle dictee | 8263d8f | RecordingScreen.kt, AppDestination.kt, AppNavHost.kt |
| 3 | Add LICENCES section to SettingsScreen | 9481f92 | SettingsScreen.kt |

## What Was Built

### Task 1 — FakeSettingsCard Android-native redesign

Replaced the iOS-style "Reglages > Dictus" breadcrumb with an Android "Clavier a l'ecran" settings panel:
- Section header text "Clavier a l'ecran" (14sp, TextSecondary, FontWeight.Medium) replaces the Settings gear icon + breadcrumb
- Two `KeyboardEntryRow` entries: Gboard (Google) and Dictus (PIVI Solutions) — each with name, subtitle, and animated toggle
- Sequential toggle animation preserved: Gboard animates on at 800ms, Dictus at 1400ms
- Removed "Autoriser l'acces complet" row (iOS-only concept, no Android equivalent)
- FakeToggle composable and outer card styling unchanged

### Task 2 — Standalone RecordingScreen + Navigation wiring

Created `RecordingScreen` in the new `recording/` package:
- Full dictation flow: idle → recording → transcribing → result, identical to OnboardingTestRecordingScreen
- No onboarding-specific elements (no progress dots, no Passer/Continuer buttons)
- Back button (ArrowBack icon, top-left) calls `onBack` to pop back stack
- `LaunchedEffect(Unit)` auto-starts recording so user lands directly in recording state
- Result state shows blue mic button to re-record (resets result + calls startRecording())
- `AppDestination.Recording("recording")` added to sealed class
- `AppNavHost.MainTabsScreen`: HomeScreen `onNewDictation` now navigates to Recording route
- Bottom nav bar hidden when `currentRoute == AppDestination.Recording.route` (conditional if in Scaffold bottomBar)

### Task 3 — LICENCES section in Settings

Inserted between APPARENCE and A PROPOS sections:
- `WhisperKit — MIT, Argmax Inc.` links to https://github.com/argmaxinc/WhisperKit
- `Dictus — MIT, PIVI Solutions 2026` links to https://github.com/pivisolutions/dictus-android
- Uses existing `SettingLinkRow` composable (opens URL in browser on tap)

## UAT Coverage

- **Test 3** (iOS-style FakeSettingsCard): CLOSED — card now shows Android Clavier a l'ecran panel
- **Test 8** (Nouvelle dictee button noop): CLOSED — tapping navigates to RecordingScreen
- **Test 16** (Licences section): CLOSED — LICENCES section with two entries in Settings

## Verification

Build: `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (56 tasks)

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

Files created/modified:
- FOUND: app/src/main/java/dev/pivisolutions/dictus/recording/RecordingScreen.kt
- FOUND: app/src/main/java/dev/pivisolutions/dictus/ui/onboarding/FakeSettingsCard.kt
- FOUND: app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt
- FOUND: app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
- FOUND: app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt

Commits:
- 8b4dc7c: feat(04-08): redesign FakeSettingsCard to Android-native keyboard settings style
- 8263d8f: feat(04-08): create RecordingScreen and wire Home 'Nouvelle dictee' button
- 9481f92: feat(04-08): add LICENCES section to SettingsScreen
