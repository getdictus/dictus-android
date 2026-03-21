# Project Research Summary

**Project:** Dictus Android
**Domain:** Android custom keyboard (IME) with on-device ML voice dictation
**Researched:** 2026-03-21
**Confidence:** HIGH

## Executive Summary

Dictus Android is an on-device voice dictation keyboard -- a custom `InputMethodService` that integrates whisper.cpp for fully offline speech-to-text. The expert approach is a multi-module Gradle project (app, ime, core, whisper) where the IME renders its keyboard UI via Jetpack Compose inside an `AbstractComposeView`, and delegates all audio capture and ML inference to a foreground `DictationService` via a same-process local Binder. This same-process architecture is a major simplification over iOS, where the keyboard extension runs in a separate process with strict memory limits. The stack is entirely Kotlin-first: Compose for UI, Hilt for DI, StateFlow for reactive state, DataStore for persistence, and OkHttp for one-time model downloads from HuggingFace.

The recommended approach is to build bottom-up from shared contracts (`:core` module with state machine and design system), then tackle the two highest-risk items in parallel: the whisper.cpp native build/JNI bridge (`:whisper`) and the Compose-based keyboard UI (`:ime`). These converge in the `DictationService` (`:app`) which wires audio recording to Whisper inference and exposes results via StateFlow. The app layer adds onboarding, settings, and model management. This ordering front-loads the two riskiest unknowns -- native C++ builds on Android and Compose inside `InputMethodService` -- where failure would force architectural changes.

The top risks are: (1) Compose in IME requires manual lifecycle wiring that is not documented in official Android docs and crashes without it; (2) whisper.cpp's Android examples are poorly maintained, and incorrect build flags or thread counts cause 10x slowdowns; (3) JNI memory management for 190-460MB model files requires explicit lifecycle handling or native memory leaks will crash the app. All three are well-understood and have proven solutions, but each requires careful implementation in the early phases.

## Key Findings

### Recommended Stack

The stack is mature and well-documented. Kotlin 2.3.20 with AGP 9.1.0 (which includes built-in Kotlin support). Jetpack Compose via BOM 2026.03.00 for all UI. whisper.cpp v1.8.4 compiled via NDK r28 and CMake for on-device inference. No cloud dependencies beyond one-time model downloads.

**Core technologies:**
- **Kotlin + AGP 9.1.0**: Primary language and build system -- AGP 9 has built-in Kotlin support, no separate plugin needed
- **Jetpack Compose (BOM 2026.03.00)**: Declarative UI for both keyboard and app screens -- parity with iOS SwiftUI approach
- **whisper.cpp v1.8.4 + NDK r28**: On-device Whisper inference via JNI -- same GGML models as iOS, MIT license
- **Hilt 2.57.1**: Compile-time DI -- catches injection errors at build time, critical for multi-module project
- **StateFlow + Coroutines**: Reactive state management -- powers the dictation state machine (idle, recording, transcribing, ready)
- **OkHttp 4.12.x**: Model downloads only -- no REST API, no serialization, simplest option for file download with progress
- **DataStore Preferences**: Settings persistence -- replaces SharedPreferences, coroutine-based, type-safe

### Expected Features

**Must have (P0 -- non-functional without these):**
- Custom keyboard IME with AZERTY + QWERTY, shift, caps, numbers, symbols, accented characters
- On-device STT via whisper.cpp (JNI bridge, GGML model loading, batch transcription)
- Audio recording via AudioRecord in foreground Service
- Text insertion via InputConnection into any host app
- Onboarding flow (IME activation is confusing on Android -- critical for retention)
- Model download from HuggingFace with progress UI
- Basic settings (model, language, layout selection)

**Should have (P1 -- significantly improves experience):**
- Waveform animation during recording (premium feel, differentiator vs bare-bones Whisper keyboards)
- Haptic and sound feedback for key press and dictation events
- Debug logging with Timber + file export (essential for beta)
- Dark theme with Dictus brand colors

**Defer (P2/v2+):**
- Emoji picker, suggestion bar with transcription preview, bilingual UI
- Voice Activity Detection (auto-stop), streaming transcription, GPU/NNAPI acceleration
- Swipe typing, AI text reformatting, autocorrect ML -- explicitly anti-features

### Architecture Approach

Four-module Gradle project running in a single process. The IME (`:ime`) renders Compose UI and communicates with `DictationService` (`:app`) via local Binder with shared StateFlow. The service owns audio recording and whisper.cpp inference. All shared contracts live in `:core`. The whisper JNI bridge is isolated in `:whisper`.

**Major components:**
1. **DictusIME** (`:ime`) -- `InputMethodService` with Compose keyboard UI, binds to DictationService, handles InputConnection
2. **DictationService** (`:app`) -- Foreground Service owning AudioRecord + WhisperBridge, single source of truth for dictation state
3. **WhisperBridge** (`:whisper`) -- Kotlin JNI wrapper around whisper.cpp, thread-safe singleton, explicit lifecycle management
4. **ModelManager** (`:app`) -- Downloads GGML models from HuggingFace, manages storage and active model selection
5. **DictationStateMachine** (`:core`) -- Shared `StateFlow<DictationState>` with states: idle, requested, recording, transcribing, ready, failed

### Critical Pitfalls

1. **ComposeView lifecycle crash in IME** -- `InputMethodService` lacks `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`. Must implement all three manually and wire them to `ComposeView`. Without this, the keyboard will not render at all. Address in Phase 1.

2. **whisper.cpp build misconfiguration causing 10x slowdowns** -- Official Android examples are poorly maintained. Must use CMake with `-DCMAKE_BUILD_TYPE=Release -DGGML_NEON=ON`, pin to a specific release tag, and set thread count to 4 (matching Pixel 4's big cores). Validate inference speed independently before integrating. Address in Phase 3.

3. **JNI native memory leaks** -- The 190-460MB whisper context lives outside JVM garbage collection. Must wrap native pointer in `Closeable`, call `whisper_free()` in `Service.onDestroy()` and `try/finally`, verify with `dumpsys meminfo`. Address in Phase 3.

4. **Foreground service restrictions on API 31+** -- `ForegroundServiceStartNotAllowedException` if `startForeground()` is not called immediately. Must declare `foregroundServiceType="microphone"`, create notification channel in `Application.onCreate()`, call `startForeground()` as first line of `onStartCommand()`. Address in Phase 2.

5. **Model download corruption** -- Interrupted downloads of 190-460MB files leave partial files that crash whisper. Must download to `.tmp`, rename after completion + SHA-256 hash verification, support HTTP range resume. Address in Phase 4.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Core Foundation + Keyboard Shell
**Rationale:** Everything depends on `:core` contracts and a rendering keyboard. The Compose-in-IME lifecycle wiring is the first architectural risk to retire. Text insertion must be validated across multiple apps early.
**Delivers:** `:core` module (state machine, DataStore prefs, design system, logging), `:ime` module with Compose keyboard rendering AZERTY/QWERTY layouts, key press handling, text insertion via InputConnection
**Addresses:** Full letter layout, number/symbol layers, backspace/enter/space, haptic feedback, dark theme, text insertion into any app
**Avoids:** ComposeView lifecycle crash (Pitfall 1), Compose recomposition lag (Pitfall 8), InputConnection failures (Pitfall 9)

### Phase 2: Audio Recording + Service Architecture
**Rationale:** The IPC binding pattern (local Binder + StateFlow) and foreground service setup must be validated before whisper.cpp integration. Audio recording quality must be independently verified.
**Delivers:** `DictationService` as foreground service, AudioRecord wrapper (16kHz mono PCM), IME-to-service binding with StateFlow, recording notification, basic recording UI state in keyboard
**Addresses:** Microphone button (dictation trigger), recording feedback (visual), permission handling
**Avoids:** Foreground service race condition (Pitfall 2), IPC binding failures (Pitfall 5), AudioRecord buffer underrun (Pitfall 7)

### Phase 3: whisper.cpp Native Integration
**Rationale:** Highest technical risk. The native build, JNI bridge, and inference performance must be validated in isolation before wiring to the full pipeline. Phases 2 and 3 can partially overlap if Phase 2's audio capture is stubbed.
**Delivers:** `:whisper` module with CMake build, JNI bridge (initModel, transcribe, freeModel), thread management (4 threads on SD855), end-to-end dictation flow (record -> transcribe -> insert text)
**Addresses:** On-device speech-to-text (core value proposition)
**Avoids:** Build misconfiguration slowdowns (Pitfall 3), JNI memory leaks (Pitfall 4), model loading ANR (Pitfall 10)

### Phase 4: Model Management + Onboarding
**Rationale:** With the dictation pipeline working, users need a way to download models and set up the keyboard. Onboarding is critical for retention -- Android IME activation is a confusing multi-step process.
**Delivers:** Model download from HuggingFace with progress/resume/hash verification, model storage management, onboarding flow (enable IME, set default, mic permission, download model, test dictation), settings screen
**Addresses:** Model download/management, onboarding flow, settings screen, multiple Whisper model sizes
**Avoids:** Model download corruption (Pitfall 6)

### Phase 5: Polish + Differentiators
**Rationale:** With core functionality complete, add the features that create competitive advantage and premium feel.
**Delivers:** Waveform animation during recording, dictation start/stop audio cues, sound feedback on key press, suggestion bar with transcription preview, emoji picker
**Addresses:** Waveform animation, sound feedback, dictation audio cues, emoji picker, suggestion bar
**Avoids:** Waveform causing keyboard stutter (Pitfall 8 -- isolate Canvas from keyboard state)

### Phase 6: Release Preparation
**Rationale:** Final validation, edge case handling, and release packaging.
**Delivers:** Cross-app text insertion testing, long recording validation, bilingual UI (FR/EN), "Follow System" theme, Play Store listing, performance profiling on Pixel 4
**Addresses:** Remaining P2 features, "Looks Done But Isn't" checklist items

### Phase Ordering Rationale

- **Dependencies flow downward:** Core contracts must exist before IME or app modules. Audio recording must work before whisper.cpp can process audio. Model management must work before onboarding can guide users through download.
- **Risk front-loading:** Phases 1-3 retire the three biggest architectural risks (Compose in IME, foreground service on API 31+, whisper.cpp native build). If any of these fail, the project needs to pivot early rather than late.
- **Phases 2 and 3 are parallelizable:** The audio service and whisper.cpp native build have no compile-time dependency on each other. The `:whisper` module can be built and tested with standalone audio files while the audio pipeline is being built.
- **Pitfall coverage:** Each phase has specific pitfalls to validate against. No pitfall is deferred past the phase where its failure would be most costly.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** Compose IME lifecycle wiring is not in official docs -- rely on community examples and the Gist/Medium sources identified in research. Hilt injection in `InputMethodService` requires `EntryPointAccessors`, not `@AndroidEntryPoint`.
- **Phase 3:** whisper.cpp Android build is the highest-risk item. The official examples are acknowledged as poorly maintained. Plan for 2-3 days of build debugging. Reference the `LocalMind` Medium article and `whisper.android` example for working configurations.

Phases with standard patterns (skip research-phase):
- **Phase 2:** Foreground services, AudioRecord, and bound services are well-documented in official Android docs. Standard patterns apply.
- **Phase 4:** OkHttp file downloads with progress are a solved problem. Onboarding UI is standard Compose navigation.
- **Phase 5:** Compose Canvas animations and emoji grids are well-documented with official samples.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official release notes. AGP 9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.00 all confirmed current stable. |
| Features | MEDIUM-HIGH | Competitive analysis covers the landscape well. FUTO Keyboard is the closest competitor and is well-documented. Feature prioritization aligns with PRD. |
| Architecture | HIGH | Same-process IME architecture is standard Android. Local Binder pattern is documented in official docs. Multi-module structure follows established Gradle patterns. |
| Pitfalls | HIGH | All 10 pitfalls are backed by GitHub issues, official docs, or real-world implementation reports. Recovery strategies are concrete. |

**Overall confidence:** HIGH

### Gaps to Address

- **Compose IME performance on Pixel 4:** No benchmark data for Compose-based keyboards on SD855. FUTO Keyboard uses Views, not Compose. If Compose performance is unacceptable after optimization, fallback to View-based rendering would be a significant architectural change (HIGH recovery cost). Profile early in Phase 1.
- **whisper.cpp optimal thread count:** The 4-thread recommendation for SD855 comes from community benchmarks, not official guidance. Must profile on a real Pixel 4 during Phase 3.
- **Hilt DI in InputMethodService:** `@AndroidEntryPoint` does not work on `InputMethodService`. Must use `EntryPointAccessors.fromApplication()`. This is documented but not widely covered. Validate in Phase 1.
- **ComposeView recreation on configuration change:** When the soft keyboard is shown/hidden or the device rotates, `onCreateInputView()` may be called again. The lifecycle wiring must handle recreation without leaking the previous `ComposeView`. Needs testing in Phase 1.

## Sources

### Primary (HIGH confidence)
- [Android Developers: InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService) -- IME framework
- [Android Developers: Creating an Input Method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) -- official IME guide
- [Android Developers: Bound Services](https://developer.android.com/develop/background-work/services/bound-services) -- local Binder pattern
- [Android Developers: Foreground Services](https://developer.android.com/develop/background-work/services/fgs/troubleshooting) -- API 31+ restrictions
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp) -- v1.8.4, official Android example
- [AGP 9.1.0 Release Notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) -- build system
- [Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom) -- UI framework versioning

### Secondary (MEDIUM confidence)
- [Compose IME Gist](https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff) -- lifecycle wiring pattern
- [Custom Soft Keyboard with Compose (Medium)](https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd) -- real implementation
- [Building LocalMind: Whisper on Android via JNI (Medium)](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc) -- JNI bridge reference
- [whisper.cpp Issues #1022, #1070, #856, #962](https://github.com/ggml-org/whisper.cpp/issues) -- Android build and performance issues
- [FUTO Keyboard](https://keyboard.futo.org/) -- closest open-source competitor

### Tertiary (LOW confidence)
- [whisper.cpp Discussion #3567](https://github.com/ggml-org/whisper.cpp/discussions/3567) -- thread count recommendation for SD855 (community benchmarks, needs validation on real hardware)

---
*Research completed: 2026-03-21*
*Ready for roadmap: yes*
