# Roadmap: Dictus Android

## Overview

Dictus Android delivers a free, private, on-device voice dictation keyboard for Android. The roadmap front-loads the three highest-risk unknowns -- Compose rendering inside InputMethodService, foreground service audio capture, and whisper.cpp native build/JNI bridge -- before layering on model management, onboarding, and polish. UI designs arrive later via Pencil.dev; architecture and engine phases proceed first.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Core Foundation + Keyboard Shell** - Multi-module project with Compose-based IME rendering AZERTY/QWERTY, text insertion, dark theme, and structured logging (completed 2026-03-22)
- [ ] **Phase 2: Audio Recording + Service Architecture** - Foreground service with AudioRecord, IME-to-service binding via local Binder + StateFlow, mic button and haptic feedback
- [ ] **Phase 3: Whisper Integration** - whisper.cpp native build via NDK/CMake, JNI bridge, end-to-end dictation pipeline (record -> transcribe -> insert)
- [x] **Phase 4: Model Management + Onboarding** - Model download/deletion from HuggingFace, storage indicators, onboarding flow, settings screen, debug log export (completed 2026-03-26)
- [ ] **Phase 5: Polish + Differentiators** - Waveform animation, sound feedback, text suggestion bar, emoji picker
- [ ] **Phase 6: Release Readiness** - Bilingual UI (FR/EN), Follow System theme, cross-app validation, performance profiling

## Phase Details

### Phase 1: Core Foundation + Keyboard Shell
**Goal**: Users can type text into any Android app using the Dictus keyboard with full AZERTY/QWERTY layouts
**Depends on**: Nothing (first phase)
**Requirements**: KBD-01, KBD-02, KBD-03, KBD-06, DSG-01, APP-06
**Success Criteria** (what must be TRUE):
  1. User can enable Dictus as an input method in Android Settings and select it as active keyboard
  2. User can type letters, numbers, symbols, and accented characters on AZERTY layout and switch to QWERTY
  3. User can see typed text appear in any app's text field (Gmail, WhatsApp, Chrome, etc.) via InputConnection
  4. Keyboard renders with the branded Dictus dark theme
  5. Structured logs are written via Timber and can be observed in Logcat
**Plans**: 4 plans

Plans:
- [x] 01-01-PLAN.md — Multi-module Gradle project, version catalog, Hilt, Timber, Dictus dark theme
- [x] 01-02-PLAN.md — IME service with Compose lifecycle, keyboard data models, accent map
- [x] 01-03-PLAN.md — Compose keyboard UI, key interactions, InputConnection text insertion
- [ ] 01-04-PLAN.md — UAT gap closure: caps lock visual indicator, mic row positioning

### Phase 2: Audio Recording + Service Architecture
**Goal**: Users can tap the mic button on the keyboard to record audio, with the recording managed by a foreground service
**Depends on**: Phase 1
**Requirements**: DICT-01, DICT-02, DICT-07, DICT-08
**Success Criteria** (what must be TRUE):
  1. User can tap the mic button on the keyboard to start recording and tap again to stop
  2. User sees a notification while recording is active (foreground service)
  3. User feels haptic feedback on key presses and mic button taps (configurable)
  4. Dictation state machine transitions are observable: idle -> recording -> idle
**Plans**: 3 plans

Plans:
- [x] 02-01-PLAN.md — DictationState, AudioCaptureManager, DictationService foreground service with notification
- [x] 02-02-PLAN.md — RecordingScreen UI, WaveformBars, HapticHelper, IME-to-service wiring
- [x] 02-03-PLAN.md — UAT gap closure: haptic crash fix, recording UI redesign, waveform improvements, accent popup clamping

### Phase 02.1: App Shell + Testability (INSERTED)

**Goal:** Minimal app activity with IME status card, recording test surface, mic permission handling, and Compose UI test infrastructure across all modules
**Requirements**: SHELL-01, SHELL-02, SHELL-03, SHELL-04, SHELL-05
**Depends on:** Phase 2
**Success Criteria** (what must be TRUE):
  1. User opens the app and sees an IME status card showing whether Dictus keyboard is enabled/selected
  2. User taps Test Recording, grants mic permission, and sees real-time waveform + timer
  3. Permission denial shows Snackbar with Settings action
  4. FakeDictationController provides controllable test doubles for both app and ime module tests
  5. ./gradlew testAll runs all unit tests across all 3 modules
**Plans:** 3/3 plans complete

Plans:
- [ ] 02.1-01-PLAN.md — Test infrastructure, FakeDictationController, WaveformBars extraction, testAll task
- [ ] 02.1-02-PLAN.md — App test surface UI (ImeStatusCard, RecordingTestArea, permissions, MainActivity rewrite)

### Phase 3: Whisper Integration
**Goal**: Users can dictate text that is transcribed on-device and inserted at the cursor position
**Depends on**: Phase 2
**Requirements**: DICT-03, DICT-04
**Success Criteria** (what must be TRUE):
  1. User can record speech and see transcribed text appear at the cursor in the active text field
  2. Transcription runs entirely on-device with no network requests
  3. Transcription of 10 seconds of audio completes in under 8 seconds on Pixel 4 (small-q5_0 model)
**Plans**: 3 plans

Plans:
- [x] 03-01-PLAN.md — whisper.cpp git submodule, whisper/ Android library module with NDK/CMake, JNI bridge, Kotlin wrapper
- [x] 03-02-PLAN.md — DictationState.Transcribing, TranscriptionEngine interface, TextPostProcessor, ModelManager with TDD
- [x] 03-03-PLAN.md — End-to-end pipeline: DictationService orchestration, IME text insertion, TranscribingScreen UI

### Phase 4: Model Management + Onboarding
**Goal**: Users can set up Dictus from scratch and manage Whisper models
**Depends on**: Phase 3
**Requirements**: APP-01, APP-02, APP-03, APP-04, APP-05
**Success Criteria** (what must be TRUE):
  1. User can complete onboarding: welcome, mic permission, layout choice, IME activation, model download, test dictation
  2. User can download and delete Whisper models (tiny, base, small, small-quantized) with progress indication
  3. User can see storage used per model and total storage consumed
  4. User can configure active model, transcription language, keyboard layout, haptic, sound, and theme from settings
  5. User can export debug logs from the settings screen
**Plans**: 8 plans

Plans:
- [ ] 04-01-PLAN.md — Foundation: Navigation dep, DM Sans font, GlassCard, DictusColors extension, PreferenceKeys, ModelManager refactor with provider abstraction
- [ ] 04-02-PLAN.md — ModelDownloader progress Flow, DataStore module, AppNavHost, DictusBottomNavBar, HomeScreen, ModelsScreen, MainActivity rewrite
- [ ] 04-03-PLAN.md — 6-step onboarding flow: Welcome, Mic, Keyboard, Mode, Model Download, Success + OnboardingViewModel
- [ ] 04-04-PLAN.md — SettingsScreen (3 sections), FileLoggingTree, log export ZIP + share sheet, DictationService DataStore wiring
- [ ] 04-05-PLAN.md — Gap closure: THEME preference key, SettingsViewModel.theme StateFlow, APPARENCE section in SettingsScreen (APP-04 completion)
- [ ] 04-06-PLAN.md — UAT gap closure: SavedStateHandle for onboarding process death fix, deferred service binding, animated welcome waveform
- [ ] 04-07-PLAN.md — UAT gap closure: fix model download persistence, model card redesign (description + bars), model selection from card tap, remove Settings model picker
- [ ] 04-08-PLAN.md — UAT gap closure: FakeSettingsCard Android-native redesign, RecordingScreen + Home wiring, Licences section in Settings

### Phase 5: Polish + Differentiators
**Goal**: Users experience premium feedback and convenience features during dictation and typing
**Depends on**: Phase 3
**Requirements**: DICT-05, DICT-06, KBD-04, KBD-05
**Success Criteria** (what must be TRUE):
  1. User sees a real-time waveform animation while recording audio
  2. User hears configurable sounds when recording starts and stops
  3. User can see 3 text suggestions above the keyboard and tap to insert
  4. User can open an emoji picker with categories and recent emojis
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

### Phase 6: Release Readiness
**Goal**: Users can use Dictus in French or English with system theme integration
**Depends on**: Phase 4, Phase 5
**Requirements**: DSG-02, DSG-03
**Success Criteria** (what must be TRUE):
  1. All UI strings appear in French by default and in English when device language is English
  2. User can toggle "Follow System" to switch from Dictus dark theme to Material You dynamic colors
  3. Text insertion works correctly across 10+ common apps (messaging, email, browser, notes)
**Plans**: TBD

Plans:
- [ ] 06-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 (and 5 in parallel) -> 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Core Foundation + Keyboard Shell | 3/4 | Gap closure | 2026-03-22 |
| 2. Audio Recording + Service Architecture | 3/3 | Complete | 2026-03-25 |
| 3. Whisper Integration | 3/3 | Complete | 2026-03-25 |
| 4. Model Management + Onboarding | 8/8 | Complete   | 2026-03-27 |
| 5. Polish + Differentiators | 0/2 | Not started | - |
| 6. Release Readiness | 0/1 | Not started | - |
