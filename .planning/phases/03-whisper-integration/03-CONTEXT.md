# Phase 3: Whisper Integration - Context

**Gathered:** 2026-03-25
**Status:** Ready for planning

<domain>
## Phase Boundary

End-to-end on-device dictation pipeline: whisper.cpp native build via NDK/CMake, JNI bridge to Kotlin, and full record -> transcribe -> insert flow. User taps mic, speaks, and transcribed text appears at the cursor in the active text field. No model management UI (Phase 4), no streaming transcription, no GPU/NNAPI acceleration.

</domain>

<decisions>
## Implementation Decisions

### Initial model strategy
- Bundle **two models** for Phase 3: tiny (fast iteration) and small-q5_0 (perf validation target)
- **Tiny is the default** model for development — small-q5_0 available but opt-in
- Model delivery method: Claude's discretion between asset bundling and auto-download from HuggingFace — choose whichever approach reuses the most code in Phase 4's model manager
- Phase 4 replaces this with full model management (download/delete UI, storage indicators)

### Text insertion behavior
- **All at once** after transcription completes (no progressive/streaming insertion)
- **Post-processing matches iOS** (`DictationCoordinator.swift:368-372`):
  - If transcription ends with `.`, `!`, `?`, or `...` -> append just a space
  - Otherwise -> append `. ` (period + space)
  - This prevents chained dictations from sticking together
- Trim leading/trailing whitespace before post-processing (Whisper sometimes adds extra spaces)
- Insert via `InputConnection.commitText()` at current cursor position

### Language handling
- **French ("fr") hard-coded** as the transcription language for Phase 3
- Passed directly to whisper.cpp decode options (same as iOS `DecodingOptions(language: language)`)
- Phase 4 adds language selector in settings (auto/FR/EN per APP-04)

### Transcription feedback
- **Transcribing UI** already decided in Phase 2: sinusoidal waveform + "Transcription..." label, no action buttons (mockup XfNKh)
- **No cancel during transcription** — transcription is fast enough (<8s target), user waits
- **Error handling**: silent return to keyboard idle state, error logged via Timber. No visible error message in Phase 3
- **Timeout**: 30 seconds — if transcription exceeds this, treat as failure and return to idle
- **After insertion**: immediate return to keyboard (no success animation or delay)

### State machine extension
- Add `Transcribing` state to `DictationState` sealed class (currently only `Idle` and `Recording`)
- Full Phase 3 flow: Idle -> Recording -> Transcribing -> Idle
- Transcribing state drives UI switch to sinusoidal waveform screen

### Claude's Discretion
- whisper.cpp native build approach (NDK/CMake configuration, JNI bridge implementation)
- Model file format and storage location on device
- whisper.cpp threading configuration (number of threads for Pixel 4)
- Exact JNI method signatures and native wrapper design
- Coroutine dispatcher choice for transcription (Default vs IO)
- Model delivery approach (asset bundle vs auto-download) — pick what reuses most in Phase 4

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project requirements
- `.planning/REQUIREMENTS.md` — DICT-03 (on-device transcription via whisper.cpp), DICT-04 (insert at cursor)
- `.planning/PROJECT.md` — Key decisions: whisper.cpp via JNI, CPU multi-thread, Pixel 4 target, no GPU/NNAPI
- `PRD.md` — Full product requirements, architecture, performance targets

### iOS reference implementation
- `~/dev/dictus/DictusApp/Audio/TranscriptionService.swift` — Whisper transcription: decode options, language param, segment joining, trim
- `~/dev/dictus/DictusApp/DictationCoordinator.swift` lines 359-372 — Post-processing: auto-punctuation logic (`. ` if no trailing punct, ` ` if already punctuated)
- `~/dev/dictus/DictusApp/Audio/SpeechModelProtocol.swift` — Engine interface contract (transcribe method signature)
- `~/dev/dictus/DictusKeyboard/KeyboardState.swift` lines 309-367 — Text insertion into text field via proxy

### Design mockups
- `design/dictus-android-design.pen` frame `XfNKh` — Transcribing state UI (sinusoidal waveform + "Transcription..." label)
- `design/dictus-android-design.pen` frame `VUDbq` — Waveform technical spec (bar dimensions, colors, visual states)
- `design/README.md` — Design system: colors, typography, waveform spec

### Phase 2 context (predecessor)
- `.planning/phases/02-audio-recording-service-architecture/02-CONTEXT.md` — Audio capture decisions, recording UI, state machine, service architecture

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DictationService.kt` — Foreground service, `stopRecording()` already returns `FloatArray` of 16kHz mono Float32 samples ready for whisper.cpp
- `AudioCaptureManager.kt` — Captures at 16kHz mono Float32, in-memory buffer, no temp files
- `DictationController.kt` — Interface in core module (`startRecording()`, `stopRecording(): FloatArray`, `cancelRecording()`). Needs extending for transcription result
- `DictationState.kt` — Sealed class in core module. Currently `Idle` and `Recording`. Add `Transcribing` state
- `RecordingScreen.kt` — Recording overlay UI. Needs a transcribing variant (sinusoidal waveform)
- `DictusImeService.kt` — IME with Compose lifecycle, already binds to DictationService, has InputConnection for text insertion

### Established Patterns
- Hilt DI via `EntryPointAccessors.fromApplication()` for IME (not @AndroidEntryPoint)
- StateFlow from service observed by IME via bound service connection
- Timber structured logging throughout
- Coroutine scopes tied to service lifecycle (`SupervisorJob() + Dispatchers.Main`)

### Integration Points
- `DictationService.stopRecording()` -> returns FloatArray -> feed to whisper.cpp JNI -> get text -> post-process -> `InputConnection.commitText()`
- `DictationState` sealed class needs new `Transcribing` variant
- `DictusImeService` observes state -> swap UI to transcribing screen when state is `Transcribing`
- New native library (`.so` files) built via NDK/CMake, loaded via `System.loadLibrary()`

</code_context>

<specifics>
## Specific Ideas

- Follow iOS audio pipeline: Float32 samples passed directly to whisper engine, no intermediate WAV file
- Post-processing logic is a direct port of `DictationCoordinator.swift:368-372` — same punctuation rules
- Performance target: small-q5_0 model transcribes 10s of audio in under 8s on Pixel 4 (Snapdragon 855, 6GB RAM)
- Refer to Pencil mockups for transcribing UI visual spec (sinusoidal waveform)
- STATE.md notes: "whisper.cpp Android examples are poorly maintained — plan for build debugging time"

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-whisper-integration*
*Context gathered: 2026-03-25*
