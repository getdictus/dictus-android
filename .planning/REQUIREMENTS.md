# Requirements: Dictus Android

**Defined:** 2026-03-21
**Core Value:** La dictée vocale on-device qui fonctionne comme clavier système — gratuite, privée, sans cloud.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Keyboard (IME)

- [ ] **KBD-01**: User can type on a full AZERTY keyboard layout with shift, caps lock, numbers, and symbols
- [ ] **KBD-02**: User can switch between AZERTY and QWERTY layouts
- [ ] **KBD-03**: User can access accented characters via long-press (é, è, ê, à, ù, ç, etc.)
- [ ] **KBD-04**: User can see 3 text suggestions above the keyboard and tap to insert
- [ ] **KBD-05**: User can open an emoji picker with categories and recent emojis
- [ ] **KBD-06**: User can insert text into any app via InputConnection

### Dictation

- [ ] **DICT-01**: User can tap the mic button to start voice recording
- [ ] **DICT-02**: User can tap again to stop recording and trigger transcription
- [ ] **DICT-03**: Transcription runs on-device via whisper.cpp with GGML models
- [ ] **DICT-04**: Transcribed text is inserted at cursor position in the active text field
- [ ] **DICT-05**: User sees a real-time waveform animation during recording
- [ ] **DICT-06**: User hears a sound when recording starts and stops (configurable)
- [ ] **DICT-07**: User feels haptic feedback on key press and mic button (configurable)
- [ ] **DICT-08**: Recording runs in a foreground Service with visible notification

### App

- [ ] **APP-01**: User completes onboarding: welcome slides, mic permission, layout choice, IME activation, model download, test dictation
- [ ] **APP-02**: User can download/delete Whisper models (tiny, base, small, small-quantized) from the model manager
- [ ] **APP-03**: User sees storage indicator per model and total used
- [ ] **APP-04**: User can configure: active model, transcription language (auto/FR/EN), keyboard layout, haptic, sound, theme
- [ ] **APP-05**: User can export debug logs from settings
- [ ] **APP-06**: Structured logging via Timber throughout the app

### Design

- [ ] **DSG-01**: App uses a branded dark theme matching Dictus iOS colors by default
- [ ] **DSG-02**: User can toggle "Follow System" to use Material You dynamic colors
- [ ] **DSG-03**: UI is bilingual French (default) / English

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Speech Engines

- **STT-01**: User can switch between Whisper and Parakeet STT engines
- **STT-02**: Parakeet v3 engine support for English-focused dictation

### Smart Modes

- **SMART-01**: User can reformat text for email, SMS, Slack via LLM (user's API key)

### Advanced Features

- **ADV-01**: Voice Activity Detection (auto-stop on silence)
- **ADV-02**: GPU/NNAPI acceleration for whisper.cpp inference
- **ADV-03**: Streaming transcription (real-time text as user speaks)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Swipe typing | Enormous engineering cost (FUTO has worked on it for years, still alpha). Dictus's value is voice, not typing intelligence |
| ML-powered autocorrect | Requires training data and large language models. Not core to voice dictation value |
| Live Activity / Dynamic Island | iOS-specific concept, no Android equivalent |
| Auto-return to source app | Less relevant on Android (keyboard stays in-process) |
| Cloud-based STT | Breaks core privacy promise — 100% on-device is non-negotiable |
| Multi-device testing (Samsung, Xiaomi) | Focus on Pixel 4 for MVP, expand in v1.1 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| KBD-01 | — | Pending |
| KBD-02 | — | Pending |
| KBD-03 | — | Pending |
| KBD-04 | — | Pending |
| KBD-05 | — | Pending |
| KBD-06 | — | Pending |
| DICT-01 | — | Pending |
| DICT-02 | — | Pending |
| DICT-03 | — | Pending |
| DICT-04 | — | Pending |
| DICT-05 | — | Pending |
| DICT-06 | — | Pending |
| DICT-07 | — | Pending |
| DICT-08 | — | Pending |
| APP-01 | — | Pending |
| APP-02 | — | Pending |
| APP-03 | — | Pending |
| APP-04 | — | Pending |
| APP-05 | — | Pending |
| APP-06 | — | Pending |
| DSG-01 | — | Pending |
| DSG-02 | — | Pending |
| DSG-03 | — | Pending |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 0
- Unmapped: 23

---
*Requirements defined: 2026-03-21*
*Last updated: 2026-03-21 after initial definition*
