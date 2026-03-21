# Dictus Android — Product Requirements Document

> **Version** 1.0 - Draft
> **Date** March 2026
> **Author** Pierre / PIVI Solutions
> **Status** Ready for development
> **iOS Reference** v1.2 Beta Ready (iOS app in TestFlight phase)
> **Licence** MIT (open source)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [iOS App Recap — What Dictus Is](#2-ios-app-recap--what-dictus-is)
3. [Android Feature Parity Matrix](#3-android-feature-parity-matrix)
4. [Technical Architecture — Android](#4-technical-architecture--android)
5. [Pixel 4 Feasibility Analysis](#5-pixel-4-feasibility-analysis)
6. [Android-Specific Constraints](#6-android-specific-constraints)
7. [Design — Material You Adaptation](#7-design--material-you-adaptation)
8. [Features — MVP Android](#8-features--mvp-android)
9. [IPC & Cross-Process Communication](#9-ipc--cross-process-communication)
10. [Speech Engine Options](#10-speech-engine-options)
11. [Distribution & Open Source](#11-distribution--open-source)
12. [Risks & Mitigations](#12-risks--mitigations)
13. [Glossary — iOS to Android Mapping](#13-glossary--ios-to-android-mapping)

---

## 1. Executive Summary

Dictus Android is the Android port of the Dictus iOS app — a free, open-source voice dictation keyboard that runs 100% on-device. No server, no account, no subscription.

The Android version aims for feature parity with the iOS v1.2 app while adapting to Android platform conventions (Material You design, IME framework, different IPC mechanisms).

### What makes Dictus unique (same value prop as iOS)

| | Free | On-device | Custom keyboard | AZERTY support | Open source |
|---|---|---|---|---|---|
| Google Voice Typing | Partial | Partial (cloud) | No (built-in) | Yes | No |
| Whisper-based apps | Varies | Yes | No | No | Some |
| **Dictus** | **Yes** | **Yes** | **Yes** | **Yes** | **Yes** |

---

## 2. iOS App Recap — What Dictus Is

### Core concept

Dictus is a custom keyboard with built-in voice dictation. The user switches to the Dictus keyboard via the system keyboard switcher, taps the mic button, speaks, and the transcribed text is inserted directly into any text field.

### Architecture (iOS — for reference)

The iOS app is split into 3 targets:

| Target | Role | Android equivalent |
|---|---|---|
| **DictusApp** | Main app: onboarding, settings, model manager, dictation engine host | Main Activity / app module |
| **DictusKeyboard** | Keyboard Extension: custom keyboard layout + dictation trigger | IME Service (`InputMethodService`) |
| **DictusCore** | Shared framework: App Group storage, models, preferences, design system | Shared module (Kotlin library) |

### Key iOS design decisions carried over to Android

1. **Recording happens in the main app, not the keyboard** — On iOS, the keyboard extension has a ~50MB memory limit, so WhisperKit runs in the main app process. On Android, IMEs have more memory headroom, but it's still better to run the heavy ML inference in a separate service to avoid ANR (Application Not Responding) in the IME.

2. **Cross-process IPC for keyboard-app communication** — iOS uses Darwin notifications + App Group UserDefaults. Android will use a bound Service + SharedPreferences or ContentProvider.

3. **Audio engine stays warm** — On iOS, the audio engine is kept running between recordings (Wispr Flow pattern) to avoid cold start latency. On Android, a foreground Service with a persistent notification achieves the same effect.

4. **Dictation state machine** — The same states apply:
   - `idle` → `requested` → `recording` → `transcribing` → `ready` → `idle`
   - `failed` can occur from any state

### Features implemented in iOS v1.2

| Feature | Status | Notes |
|---|---|---|
| Custom keyboard (AZERTY/QWERTY) | Done | Full layout with numbers, symbols, accented chars |
| On-device STT (WhisperKit) | Done | Small, Small Quantized, Medium models |
| Parakeet engine (alternative STT) | Done | NVIDIA-based, English-focused |
| Model manager (download/delete) | Done | Multiple models, storage indicator |
| Onboarding flow | Done | 6 steps: welcome, mic perm, mode, model download, test, success |
| Sound feedback (start/stop) | Done | Configurable sounds + volume |
| Haptic feedback | Done | Configurable |
| Waveform animation | Done | Real-time audio energy visualization |
| Recording overlay | Done | Full-screen with stop/cancel buttons |
| Live Activity + Dynamic Island | Done | iOS-specific, no Android equivalent |
| Cold start audio bridge | Done | Keyboard triggers app when app was killed |
| Auto-return to source app | Done | Opens previous app after dictation |
| Text prediction / autocorrect | Done | Basic suggestion bar |
| Emoji picker | Done | French search, categories, recents |
| Debug logging | Done | Structured, privacy-safe, exportable |
| Bilingual UI (FR/EN) | Done | French primary, English secondary |
| Privacy policy | Done | "Data Not Collected" |
| Smart modes (LLM) | Not yet | Planned for future: email, SMS, Slack reformatting via user's API key |

---

## 3. Android Feature Parity Matrix

### MVP (v1.0 Android)

| Feature | iOS Implementation | Android Implementation | Priority |
|---|---|---|---|
| Custom keyboard (AZERTY/QWERTY) | SwiftUI custom keyboard | `InputMethodService` + Jetpack Compose | P0 |
| On-device STT | WhisperKit (CoreML) | whisper.cpp / Sherpa-ONNX (see Section 10) | P0 |
| Model manager | WhisperKit model download | Manual GGML/ONNX download from HuggingFace | P0 |
| Mic recording | AVFoundation | Android `AudioRecord` API | P0 |
| Text insertion | `textDocumentProxy.insertText()` | `InputConnection.commitText()` | P0 |
| Onboarding | SwiftUI pages | Compose screens | P0 |
| Settings | SwiftUI SettingsView | Compose SettingsScreen + DataStore | P0 |
| Waveform animation | Custom SwiftUI view | Custom Compose Canvas | P1 |
| Sound feedback | AVAudioPlayer + SystemSoundID | `SoundPool` or `MediaPlayer` | P1 |
| Haptic feedback | UIImpactFeedbackGenerator | `HapticFeedbackConstants` / `Vibrator` | P1 |
| Text prediction | UILexicon + custom | Android `SuggestionSpan` or custom | P2 |
| Emoji picker | Custom SwiftUI grid | Compose LazyGrid | P2 |
| Debug logging | Structured Logger → file | Timber + file appender | P1 |

### Deferred to v1.1+

| Feature | Why deferred |
|---|---|
| Smart modes (LLM) | Not yet on iOS either |
| Auto-return to source app | Less relevant on Android (keyboard stays in-process) |
| Live Activity | iOS-specific (no Android equivalent) |

---

## 4. Technical Architecture — Android

### Recommended stack

| Component | Technology | Why |
|---|---|---|
| Language | Kotlin | Modern Android standard, coroutines for async |
| UI framework | Jetpack Compose | Declarative UI, equivalent to SwiftUI |
| Speech recognition | whisper.cpp (via JNI) or Sherpa-ONNX | On-device, no cloud, see Section 10 |
| Audio capture | `AudioRecord` API | Low-level PCM capture, required for ML input |
| Keyboard | `InputMethodService` | Android IME framework |
| Text insertion | `InputConnection` | Standard IME text input API |
| Preferences | Jetpack DataStore (Preferences) | Replaces SharedPreferences, type-safe |
| Local storage | Room (if needed) or Files | Model files + logs |
| DI | Hilt | Standard Android dependency injection |
| Min SDK | API 29 (Android 10) | See Pixel 4 section |
| Build | Gradle (Kotlin DSL) | Standard Android build system |

### Project structure (proposed)

```
dictus-android/
├── app/                          # Main app module
│   ├── ui/
│   │   ├── onboarding/           # Onboarding Compose screens
│   │   ├── settings/             # Settings screen
│   │   ├── modelmanager/         # Model download/delete UI
│   │   ├── home/                 # Home screen with test dictation
│   │   └── theme/                # Material You theme + Dictus colors
│   ├── audio/
│   │   ├── AudioRecorder.kt      # AudioRecord wrapper
│   │   └── TranscriptionService.kt # whisper.cpp / Sherpa-ONNX bridge
│   ├── models/
│   │   ├── ModelManager.kt       # Download, compile, manage models
│   │   └── ModelInfo.kt          # Model catalog (equivalent to iOS)
│   └── services/
│       └── DictationService.kt   # Foreground service for recording
│
├── ime/                          # IME module (keyboard)
│   ├── DictusIME.kt              # InputMethodService subclass
│   ├── ui/
│   │   ├── KeyboardView.kt       # Compose keyboard layout
│   │   ├── KeyButton.kt          # Individual key
│   │   ├── ToolbarView.kt        # Top bar (mode + transcription preview)
│   │   ├── WaveformView.kt       # Audio visualization
│   │   ├── EmojiPickerView.kt    # Emoji grid
│   │   └── SuggestionBar.kt      # Text predictions
│   └── models/
│       ├── KeyboardLayout.kt     # AZERTY/QWERTY layout data
│       └── KeyDefinition.kt      # Key model
│
├── core/                         # Shared module
│   ├── DictationStatus.kt        # State machine enum
│   ├── SharedKeys.kt             # DataStore keys
│   ├── design/
│   │   ├── DictusColors.kt       # Brand colors
│   │   ├── DictusTypography.kt   # Typography
│   │   └── GlassModifier.kt      # Glass-like visual effects
│   ├── logging/
│   │   └── DictusLogger.kt       # Structured logging
│   └── models/
│       └── ModelInfo.kt          # Model catalog shared between modules
│
└── whisper/                      # Native module (JNI bridge)
    ├── cpp/                      # whisper.cpp source or prebuilt .so
    └── WhisperBridge.kt          # Kotlin JNI bindings
```

### Key architectural decisions

**1. IME vs Foreground Service split**

The keyboard (`InputMethodService`) handles UI and text insertion. Heavy work (model loading, audio recording, transcription) runs in a bound foreground `Service` in the app process. The IME binds to this service via AIDL or Messenger.

**Why:** Keeps the IME responsive. Android kills slow IMEs aggressively. ML inference can take 2-10+ seconds — that must not block the IME thread.

**2. Communication flow**

```
[User taps mic in keyboard]
        │
        ▼
[IME sends "start" to DictationService]
        │
        ▼
[DictationService starts AudioRecord]
[Shows foreground notification: "Recording..."]
        │
        ▼
[User taps stop OR speaks → silence detection]
        │
        ▼
[DictationService runs whisper.cpp inference]
        │
        ▼
[Result written to SharedPreferences / broadcast]
        │
        ▼
[IME reads result → InputConnection.commitText()]
```

**3. Model storage**

Models stored in app-internal storage (`getFilesDir()`). No SD card — models are large (250-800MB) and need fast I/O.

---

## 5. Pixel 4 Feasibility Analysis

### Pixel 4 specs

| Spec | Value |
|---|---|
| SoC | Qualcomm Snapdragon 855 |
| CPU | 1x Kryo 485 Prime @ 2.84 GHz + 3x Kryo 485 Gold @ 2.42 GHz + 4x Kryo 485 Silver @ 1.78 GHz |
| GPU | Adreno 640 |
| RAM | 6 GB |
| Storage | 64 GB / 128 GB |
| NPU | Hexagon 690 DSP (limited NNAPI support) |
| Android version | Shipped with Android 10, last official update Android 13 |
| Min API level | API 29 (Android 10) |

### Performance expectations with whisper.cpp

Based on community benchmarks for Snapdragon 855 with whisper.cpp:

| Model | Size | Expected inference time (10s audio) | Feasible? |
|---|---|---|---|
| tiny | ~75 MB | ~1-2s | Yes |
| base | ~140 MB | ~2-4s | Yes |
| small | ~460 MB | ~8-15s | Yes, but slow |
| small (q5_0 quantized) | ~190 MB | ~4-8s | Yes, recommended |
| medium | ~1.5 GB | ~30-60s+ | Marginal, not recommended |

### Recommendation for Pixel 4

- **Default model:** `small` quantized (q5_0 or q8_0) — best accuracy/speed tradeoff
- **Fast model option:** `base` or `tiny` for quick dictations
- **Medium:** Not recommended on Pixel 4 (too slow for interactive use)
- **RAM:** 6 GB is sufficient for small models. The app + IME + model should stay under 1.5 GB total
- **Quantized models are key:** whisper.cpp supports GGML quantization which dramatically reduces model size and inference time with minimal quality loss. This is the biggest advantage over iOS where CoreML quantization is less flexible.

### Verdict: Pixel 4 is fully viable

The Pixel 4 can run Dictus Android comfortably with `tiny`, `base`, and `small` (quantized) models. Set `minSdk = 29` (Android 10) to support it.

### NNAPI / GPU acceleration

The Snapdragon 855's Hexagon DSP supports NNAPI, but whisper.cpp's NNAPI backend is experimental. Stick with CPU inference (multi-threaded, 4 threads recommended on the big cores) for reliability. GPU compute via Vulkan is possible but adds complexity — defer to v1.1.

---

## 6. Android-Specific Constraints

### IME (Input Method Editor) differences vs iOS Keyboard Extension

| Aspect | iOS Keyboard Extension | Android IME |
|---|---|---|
| Memory limit | ~50 MB hard limit | No hard limit (but Android kills heavy IMEs) |
| Process model | Separate process, limited APIs | Same app process or separate (configurable) |
| Microphone access | Needs Full Access permission | Standard `RECORD_AUDIO` permission |
| Network access | Needs Full Access | Standard `INTERNET` permission |
| UI framework | UIKit/SwiftUI in extension | Compose (rendered as IME window) |
| Text insertion | `textDocumentProxy.insertText()` | `InputConnection.commitText()` |
| Keyboard switching | Globe button (system) | System keyboard switcher |
| IPC with host app | App Group + Darwin notifications | Bound Service / AIDL / SharedPreferences |
| Background work | Limited (needs UIBackgroundModes) | Foreground Service (standard) |

### Key Android advantages

1. **No memory limit on IME** — Can potentially load models directly in the IME process (but still not recommended for UX responsiveness)
2. **Standard permissions** — No "Full Access" confusion. Just `RECORD_AUDIO`
3. **Foreground Service** — Clean pattern for long-running audio recording with notification
4. **No provisioning profiles** — Sideloading via APK is trivial for testing

### Key Android challenges

1. **IME rendering** — Android IMEs render in a special window. Compose in IME is supported but has quirks (window insets, animation performance)
2. **IME lifecycle** — `InputMethodService` has a different lifecycle than Activities. Must handle `onCreateInputView()` / `onStartInputView()` / `onFinishInput()` correctly
3. **Keyboard height** — Android doesn't auto-size IME height. Must measure and set explicitly
4. **Device fragmentation** — Testing on Pixel 4 is great, but behavior varies across Samsung, Xiaomi, etc.
5. **whisper.cpp JNI** — Requires native C++ compilation via NDK. More build complexity than pure Kotlin

---

## 7. Design — Material You Adaptation

iOS Dictus uses Liquid Glass (iOS 26). Android cannot replicate Liquid Glass, but can achieve a similar premium feel using Material Design 3 / Material You.

### Design principles (adapted)

| iOS (Liquid Glass) | Android (Material You) |
|---|---|
| Glassmorphism / blur | `MaterialTheme` surfaces with elevation tints |
| `.ultraThinMaterial` | `Surface(tonalElevation = ...)` |
| Liquid Glass refraction | Dynamic color from wallpaper (`dynamicColorScheme`) |
| SF Pro Rounded / SF Pro Text | Google Sans / Roboto (or DM Sans to match brand kit) |
| Haptic via `UIImpactFeedbackGenerator` | `HapticFeedbackConstants` in Compose |

### Color palette mapping

| Token | iOS value | Android (Dark) | Android (Light) |
|---|---|---|---|
| Background | `#0A1628` | `#0A1628` (custom) or dynamic | Dynamic from wallpaper |
| Accent | `#3D7EFF` | `#3D7EFF` (primary) | `#2563EB` |
| Accent highlight | `#6BA3FF` | `#6BA3FF` (primaryContainer) | `#93C5FD` |
| Surface | `#161C2C` | `#161C2C` or `surface` | `surface` |
| Recording | `#EF4444` | `#EF4444` (error) | `#DC2626` |
| Smart mode | `#8B5CF6` | `#8B5CF6` (tertiary) | `#7C3AED` |
| Success | `#22C55E` | `#22C55E` (custom) | `#16A34A` |

### Approach: custom dark theme with dynamic color option

- Default: Dictus branded dark theme (matching iOS feel)
- Optional: "Follow system" toggle that uses Material You dynamic colors from wallpaper
- This way the app feels premium by default but respects users who prefer system theming

### Key UI components to adapt

| Component | iOS | Android |
|---|---|---|
| Mic button | 72pt Liquid Glass circle | 72dp `FloatingActionButton` with custom shape |
| Key buttons | Glass material + shadows | `Surface` with `tonalElevation` + rounded corners |
| Suggestion bar | Glass strip above keyboard | `Surface` strip with chips |
| Recording overlay | Full-screen blur + waveform | Full-screen `Surface` + Canvas waveform |
| Onboarding | Glass cards with gradient bg | Material cards with gradient bg |
| Settings | iOS List/Form | `LazyColumn` with Material list items |

---

## 8. Features — MVP Android

### 8.1 Main App

#### Onboarding (first launch)
1. Welcome screen — 3 slides (what Dictus does, privacy, free)
2. Microphone permission request (`RECORD_AUDIO`)
3. Keyboard layout selection: AZERTY (default) / QWERTY
4. Enable Dictus IME: open `Settings.ACTION_INPUT_METHOD_SETTINGS` (can't deep-link directly)
5. Set as default keyboard: `InputMethodManager.showInputMethodPicker()`
6. Model download — recommended model based on device RAM
7. Test dictation screen

#### Model Manager

Same models as iOS, but using GGML format instead of CoreML:

| Model | GGML Size | Speed on Pixel 4 | Accuracy FR/EN |
|---|---|---|---|
| tiny | ~75 MB | ~1-2s | Fair |
| base | ~140 MB | ~2-4s | Good |
| small (quantized) | ~190 MB | ~4-8s | Very good |
| small | ~460 MB | ~8-15s | Very good |
| medium | ~1.5 GB | ~30-60s | Excellent (not recommended on Pixel 4) |

- Download from HuggingFace (GGML models: `ggerganov/whisper.cpp` or `ggml-org/whisper-ggml`)
- Storage indicator per model
- Delete unused models
- Active model persisted in DataStore

#### Settings
- Active Whisper model
- Transcription language: Auto-detect (default) / Francais / English
- Keyboard layout: AZERTY / QWERTY
- Haptic feedback: on / off
- Sound feedback: on / off (+ volume slider)
- Theme: Dictus Dark (default) / Follow System
- Debug logs export

### 8.2 Keyboard (IME)

#### Layout

Same concept as iOS — full keyboard with dictation:

```
+-----------------------------------------------------+
|  [Suggestion 1]  [Suggestion 2]  [Suggestion 3]     |
+-----------------------------------------------------+
|                                                       |
|   FULL AZERTY/QWERTY layout                          |
|   with standard Android key sizes                     |
|                                                       |
+-----+---------+------------------------+------+------+
| ?123 |  ,     |       space            |  .   |  ->| |
+------+---------+------------------------+------+------+
                     [mic button]
```

The mic button is placed in the bottom toolbar area, large and accessible.

#### Dictation flow
1. User switches to Dictus keyboard via system switcher
2. Taps mic button -> recording starts (visual + haptic + sound feedback)
3. User speaks in French or English
4. Taps mic again to stop -> whisper.cpp transcribes on-device
5. Text inserted via `InputConnection.commitText()`
6. Transcription also shown in suggestion bar area for review

#### Keyboard layers (same as iOS)
- **Letters** — AZERTY or QWERTY with shift/caps lock
- **Numbers/Symbols** — `?123` toggle
- **More symbols** — `=\<` toggle
- **Accented characters** — long-press on AZERTY keys (e, a, u, o, i, c)
- **Emoji picker** — emoji button opens grid

---

## 9. IPC & Cross-Process Communication

### iOS approach (for reference)

iOS uses Darwin notifications (lightweight cross-process pings with no payload) + App Group UserDefaults (shared storage). The keyboard posts a Darwin notification to tell the app to start recording, and the app writes results to shared UserDefaults and posts a "transcription ready" notification back.

### Android approach

Android offers several IPC options. Recommended approach:

**Option A: Bound Service (recommended)**

The IME binds to a `DictationService` in the main app process via AIDL or Messenger:

```kotlin
// IME binds to DictationService
val intent = Intent(context, DictationService::class.java)
context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

// Call methods on the service
dictationService.startRecording()
dictationService.stopRecording()

// Service notifies IME via callback
interface DictationCallback {
    fun onStatusChanged(status: DictationStatus)
    fun onWaveformUpdate(energy: FloatArray)
    fun onTranscriptionReady(text: String)
}
```

**Why Bound Service:** Same process or cross-process, type-safe, lifecycle-aware. The service can be a foreground service with a notification showing recording status.

**Option B: SharedPreferences + BroadcastReceiver (fallback)**

Similar to iOS App Group pattern — write to SharedPreferences, send LocalBroadcast:

```kotlin
// Write result
prefs.edit().putString("lastTranscription", text).apply()

// Notify
LocalBroadcastManager.sendBroadcast(Intent("dictus.TRANSCRIPTION_READY"))
```

**Why fallback:** Less type-safe, but simpler to implement initially.

### State machine keys (Android DataStore equivalent of iOS SharedKeys)

```kotlin
object SharedKeys {
    const val DICTATION_STATUS = "dictus.dictationStatus"
    const val LAST_TRANSCRIPTION = "dictus.lastTranscription"
    const val ACTIVE_MODEL = "dictus.activeModel"
    const val KEYBOARD_LAYOUT = "dictus.keyboardLayout"
    const val LANGUAGE = "dictus.language"
    const val HAPTICS_ENABLED = "dictus.hapticsEnabled"
    const val SOUND_FEEDBACK_ENABLED = "dictus.soundFeedbackEnabled"
    const val HAS_COMPLETED_ONBOARDING = "dictus.hasCompletedOnboarding"
    const val WAVEFORM_ENERGY = "dictus.waveformEnergy"
    const val RECORDING_ELAPSED_SECONDS = "dictus.recordingElapsedSeconds"
}
```

---

## 10. Speech Engine Options

### Option 1: whisper.cpp (recommended)

[whisper.cpp](https://github.com/ggerganov/whisper.cpp) — C++ implementation of OpenAI Whisper, optimized for edge devices.

| Aspect | Details |
|---|---|
| Language | C++ (accessed via JNI from Kotlin) |
| Model format | GGML (quantized variants available) |
| Quantization | q5_0, q8_0, f16 — huge speed gains |
| Android support | Official Android example in repo |
| Community | Very active, 40K+ stars |
| French quality | Same as Whisper (excellent with small+ models) |
| License | MIT |

**Integration approach:**
- Use the official `whisper.cpp` Android bindings or build JNI bridge
- Compile via NDK (CMake)
- Pre-built `.so` for arm64-v8a (Pixel 4 is 64-bit)
- Load GGML model from internal storage

```kotlin
// Example JNI bridge
class WhisperBridge {
    init { System.loadLibrary("whisper") }

    external fun initModel(modelPath: String): Long
    external fun transcribe(contextPtr: Long, audioData: FloatArray, language: String): String
    external fun freeModel(contextPtr: Long)
}
```

### Option 2: Sherpa-ONNX (alternative)

[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — production-ready on-device speech recognition with ONNX runtime.

| Aspect | Details |
|---|---|
| Language | C++ with Kotlin/Java bindings (official) |
| Model format | ONNX |
| Models | Whisper, Zipformer, Paraformer, and more |
| Android support | Official Android library + examples |
| Streaming | Yes (real-time streaming transcription) |
| License | Apache 2.0 |

**Advantage over whisper.cpp:** Pre-built Android AAR, streaming support, more model options.
**Disadvantage:** Larger dependency, potentially slower for non-streaming Whisper.

### Recommendation

Start with **whisper.cpp** for simplicity and direct parity with the iOS Whisper models. If build complexity becomes an issue, switch to **Sherpa-ONNX** which provides pre-built Android bindings.

---

## 11. Distribution & Open Source

### Play Store

- Free on the Play Store under MIT licence
- Category: Tools / Productivity
- No ads, no tracking, no data collection
- Privacy label: "No data collected"

### GitHub

- Separate repo: `github.com/Pivii/dictus-android`
- Same MIT license as iOS repo
- README with setup, architecture, contribution guide
- Link to iOS repo for cross-reference

### Sideloading (for testing)

Unlike iOS, Android allows direct APK installation:
- Build APK from Android Studio: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
- Transfer to Pixel 4 via USB or adb: `adb install dictus.apk`
- No developer account needed for testing

---

## 12. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| whisper.cpp JNI complexity | Medium | Start with official Android example, or use Sherpa-ONNX as fallback |
| IME Compose rendering quirks | Medium | Test early on Pixel 4, follow Google's IME Compose guidelines |
| Model download size (user friction) | Medium | Default to smallest viable model, show clear size/speed tradeoff |
| Pixel 4 performance with small model | Low | Quantized models (q5_0) bring inference to acceptable range |
| Device fragmentation (Samsung, Xiaomi) | High | Focus on Pixel 4 for MVP, expand device testing in v1.1 |
| Play Store rejection | Low | No sensitive permissions beyond RECORD_AUDIO, no network in MVP |
| Compose in IME performance | Medium | Profile early, fallback to View-based keyboard if needed |

---

## 13. Glossary — iOS to Android Mapping

Quick reference for translating iOS concepts used throughout the Dictus iOS codebase:

| iOS Concept | Android Equivalent | Notes |
|---|---|---|
| Keyboard Extension | `InputMethodService` (IME) | Android's keyboard framework |
| `textDocumentProxy` | `InputConnection` | API to insert/delete text in the active field |
| App Group | `SharedPreferences` / `ContentProvider` | Cross-component data sharing |
| UserDefaults | `DataStore` (Preferences) | Key-value persistence |
| Darwin notifications | `BroadcastReceiver` / Bound Service | Cross-process signaling |
| `UIApplication.shared` | `Context` / `startActivity()` | Not restricted in IME (unlike iOS extension) |
| SwiftUI | Jetpack Compose | Declarative UI |
| `@StateObject` / `@ObservedObject` | `viewModel()` / `collectAsState()` | State management |
| `@Published` | `StateFlow` / `MutableStateFlow` | Observable state |
| `AVAudioSession` | `AudioManager` | Audio routing and configuration |
| `AVFoundation` recording | `AudioRecord` | Low-level audio capture |
| WhisperKit (CoreML) | whisper.cpp (GGML) or Sherpa-ONNX | On-device Whisper inference |
| Core ML | NNAPI / TFLite / ONNX Runtime | ML inference runtime |
| `UIBackgroundModes: audio` | Foreground Service | Long-running background work |
| SF Symbols | Material Icons | System icon set |
| Liquid Glass Material | Material You / Dynamic Color | Design system |
| `UIImpactFeedbackGenerator` | `HapticFeedbackConstants` | Haptic feedback |
| Provisioning Profile | Signing config (keystore) | App signing |
| TestFlight | Play Console Internal Testing | Beta distribution |
| App Store Review | Play Store Review | Publication review |
| `Bundle ID` | `applicationId` | App identifier |
| `Info.plist` | `AndroidManifest.xml` | App metadata and permissions |
| DictusCore (Framework) | Shared Kotlin module (`:core`) | Shared code library |
| Live Activity / Dynamic Island | N/A | No Android equivalent |
| `CFNotificationCenter` (Darwin) | `BroadcastReceiver` / AIDL | Cross-process IPC |

---

*Dictus Android PRD v1.0 — PIVI Solutions — March 2026*
