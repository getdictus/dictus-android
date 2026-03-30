# Phase 2: Audio Recording + Service Architecture - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Foreground service capturing audio via AudioRecord API, bound to the IME via local Binder + StateFlow. Mic button becomes functional (start/stop recording). Keyboard transforms into recording UI (waveform + controls). Haptic feedback on all key interactions. No transcription — that's Phase 3.

</domain>

<decisions>
## Implementation Decisions

### Recording UI state
- Full keyboard replacement during recording — keys disappear, waveform + controls take over the entire keyboard area (matches mockup WwpNL)
- Recording screen shows: 30-bar waveform, timer (MM:SS), "En écoute..." label, X (cancel) and ✓ (confirm) buttons
- X button = discard recording, return to keyboard idle
- ✓ button = stop recording, trigger transcription (wired in Phase 3, placeholder action in Phase 2)
- No maximum recording duration — timer is informational only, user controls stop
- Transcribing state UI (mockup XfNKh) shows sinusoidal waveform + "Transcription..." label, no action buttons — but transcription logic is Phase 3
- Bottom row (globe + mic) persists across all states
- Theme: start with dark theme for Phase 2. Both dark and light themes must eventually sync with Android system theme (full theme support in Phase 6 / DSG-02)

### Notification design
- Minimal notification: title "Dictus - Recording" + single Stop action button
- Tapping notification body = no-op (keyboard is already visible, no app to navigate to)
- Notification required by Android for foreground service compliance

### Haptic feedback
- Haptics on ALL interactions: key press, special keys (shift, delete, return), mic button, cancel/confirm during recording
- Enabled by default, light intensity
- Mic button gets stronger/more pronounced haptic than regular keys to signal recording state change
- Haptic toggle will be in settings (Phase 4) — for Phase 2, always enabled
- Use Android `HapticFeedbackConstants` or `VibrationEffect` API

### Audio capture
- In-memory buffer approach (same as iOS): accumulate 16kHz mono Float32 samples in a `FloatArray`
- No intermediate WAV file — buffer passed directly to whisper.cpp in Phase 3
- AudioRecord API with 16kHz sample rate, mono channel, Float encoding
- Audio capture runs in DictationService (foreground service), not in IME process

### State machine
- Phase 2 states: idle → recording → idle (simplified, no transcribing yet)
- Transcribing state added in Phase 3
- State exposed via StateFlow from DictationService, observed by IME via bound service connection
- State changes drive UI transitions (keyboard ↔ recording overlay)

### Claude's Discretion
- Exact waveform energy calculation from audio buffer (RMS, peak, etc.)
- DictationService lifecycle management details
- Binder/ServiceConnection implementation approach
- AudioRecord buffer size configuration
- Notification channel setup
- Haptic intensity values (light vs. strong for mic)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project requirements
- `.planning/REQUIREMENTS.md` — Phase-mapped requirements (DICT-01, DICT-02, DICT-07, DICT-08 for this phase)
- `.planning/PROJECT.md` — Key decisions: Bound Service IPC, Foreground Service, Compose in IME
- `PRD.md` — Full product requirements, architecture, iOS-to-Android mapping

### Design mockups
- `design/dictus-keyboard-design.pen` — Keyboard mockups with 3 states:
  - `RTyxV` — Keyboard Idle (AZERTY, mic button, gear)
  - `WwpNL` — Keyboard Recording (waveform, X/✓, timer, "En écoute...")
  - `XfNKh` — Keyboard Transcribing (sinusoidal waveform, "Transcription...")
- `design/README.md` — Design system: colors, typography, waveform spec, glass effect

### iOS reference
- `~/dev/dictus/DictusApp/Audio/UnifiedAudioEngine.swift` — iOS audio capture: 16kHz mono Float32, in-memory buffer, waveform energy calculation
- `~/dev/dictus/DictusKeyboard/Views/RecordingOverlay.swift` — iOS recording overlay UI
- `~/dev/dictus/DictusKeyboard/KeyboardState.swift` — iOS keyboard state machine

### Waveform spec
- `design/dictus-android-design.pen` node `VUDbq` — Waveform technical spec: 30 bars, colors, 3 visual states, dimensions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MicButtonRow.kt` — Existing mic button UI, currently placeholder. Needs to be wired to DictationService state
- `DictusImeService.kt` — IME service with Compose lifecycle, ready for service binding
- `DictusImeEntryPoint.kt` — Hilt entry point, empty, ready to expose DictationService dependencies
- `LifecycleInputMethodService.kt` — Compose lifecycle wiring for IME
- `core/preferences/PreferenceKeys.kt` — Preference keys exist, can add haptic toggle key
- `core/theme/DictusColors.kt` — Color tokens for recording red (#EF4444), accent blue (#3D7EFF)

### Established Patterns
- Hilt DI via `EntryPointAccessors.fromApplication()` for IME (not @AndroidEntryPoint)
- Compose-based keyboard rendering in `KeyboardScreen.kt`
- Timber structured logging throughout

### Integration Points
- `DictusImeService.KeyboardContent()` — swap between KeyboardScreen and RecordingScreen based on state
- `MicButtonRow` onMicTap callback — wire to start/stop recording via service binding
- `DictusImeEntryPoint` — add service connection or state accessor
- New `DictationService` in `app` module — foreground service with AudioRecord

</code_context>

<specifics>
## Specific Ideas

- Follow iOS approach for audio: in-memory Float32 buffer at 16kHz mono, no temp files
- Mockups are in light theme but current keyboard is dark — start dark, light theme comes in Phase 6
- Pierre explicitly wants both themes eventually synced with Android system theme
- Waveform rendering should use Canvas (not 30 separate Views) per design README spec
- iOS uses 30 waveform bars — same count on Android

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-audio-recording-service-architecture*
*Context gathered: 2026-03-23*
