# Roadmap: Dictus Android

## Overview

Dictus Android delivers a free, private, on-device voice dictation keyboard for Android. The roadmap front-loads the three highest-risk unknowns -- Compose rendering inside InputMethodService, foreground service audio capture, and whisper.cpp native build/JNI bridge -- before layering on model management, onboarding, and polish. UI designs arrive later via Pencil.dev; architecture and engine phases proceed first.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Core Foundation + Keyboard Shell** - Multi-module project with Compose-based IME rendering AZERTY/QWERTY, text insertion, dark theme, and structured logging
- [ ] **Phase 2: Audio Recording + Service Architecture** - Foreground service with AudioRecord, IME-to-service binding via local Binder + StateFlow, mic button and haptic feedback
- [ ] **Phase 3: Whisper Integration** - whisper.cpp native build via NDK/CMake, JNI bridge, end-to-end dictation pipeline (record -> transcribe -> insert)
- [ ] **Phase 4: Model Management + Onboarding** - Model download/deletion from HuggingFace, storage indicators, onboarding flow, settings screen, debug log export
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
**Plans**: TBD

Plans:
- [ ] 01-01: TBD
- [ ] 01-02: TBD
- [ ] 01-03: TBD

### Phase 2: Audio Recording + Service Architecture
**Goal**: Users can tap the mic button on the keyboard to record audio, with the recording managed by a foreground service
**Depends on**: Phase 1
**Requirements**: DICT-01, DICT-02, DICT-07, DICT-08
**Success Criteria** (what must be TRUE):
  1. User can tap the mic button on the keyboard to start recording and tap again to stop
  2. User sees a notification while recording is active (foreground service)
  3. User feels haptic feedback on key presses and mic button taps (configurable)
  4. Dictation state machine transitions are observable: idle -> recording -> idle
**Plans**: TBD

Plans:
- [ ] 02-01: TBD
- [ ] 02-02: TBD

### Phase 3: Whisper Integration
**Goal**: Users can dictate text that is transcribed on-device and inserted at the cursor position
**Depends on**: Phase 2
**Requirements**: DICT-03, DICT-04
**Success Criteria** (what must be TRUE):
  1. User can record speech and see transcribed text appear at the cursor in the active text field
  2. Transcription runs entirely on-device with no network requests
  3. Transcription of 10 seconds of audio completes in under 8 seconds on Pixel 4 (small-q5_0 model)
**Plans**: TBD

Plans:
- [ ] 03-01: TBD
- [ ] 03-02: TBD

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
**Plans**: TBD

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD
- [ ] 04-03: TBD

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
| 1. Core Foundation + Keyboard Shell | 0/3 | Not started | - |
| 2. Audio Recording + Service Architecture | 0/2 | Not started | - |
| 3. Whisper Integration | 0/2 | Not started | - |
| 4. Model Management + Onboarding | 0/3 | Not started | - |
| 5. Polish + Differentiators | 0/2 | Not started | - |
| 6. Release Readiness | 0/1 | Not started | - |
