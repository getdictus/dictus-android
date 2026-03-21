# Architecture Research

**Domain:** Android Custom Keyboard (IME) with On-Device ML Inference
**Researched:** 2026-03-21
**Confidence:** HIGH

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                      DICTUS APK (single process)                    │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    :app module                                │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │   │
│  │  │  Activities   │  │ DictationSvc │  │  ModelManager    │   │   │
│  │  │  (Onboarding, │  │ (Foreground  │  │  (Download,      │   │   │
│  │  │   Settings,   │  │  Service)    │  │   Storage,       │   │   │
│  │  │   Home)       │  │              │  │   Selection)     │   │   │
│  │  └──────────────┘  └──────┬───────┘  └──────────────────┘   │   │
│  │                           │ local Binder                     │   │
│  ├───────────────────────────┼──────────────────────────────────┤   │
│  │                    :ime module                                │   │
│  │  ┌──────────────┐  ┌─────┴────────┐  ┌──────────────────┐   │   │
│  │  │ DictusIME    │  │ ServiceConn  │  │  Compose UI      │   │   │
│  │  │ (InputMethod │  │ (binds to    │  │  (Keyboard,      │   │   │
│  │  │  Service)    │  │  DictationSvc│  │   Waveform,      │   │   │
│  │  │              │  │  via Binder) │  │   Emoji, etc.)   │   │   │
│  │  └──────┬───────┘  └─────────────┘  └──────────────────┘   │   │
│  │         │ InputConnection                                    │   │
│  ├─────────┼────────────────────────────────────────────────────┤   │
│  │         │          :core module                               │   │
│  │  ┌──────┴───────┐  ┌──────────────┐  ┌──────────────────┐   │   │
│  │  │ DictationState│  │  DataStore   │  │  Design System   │   │   │
│  │  │ Machine      │  │  (Prefs,     │  │  (Colors, Typo,  │   │   │
│  │  │ (SharedFlow) │  │   SharedKeys)│  │   Theme)         │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘   │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │                    :whisper module (JNI)                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │   │
│  │  │ WhisperBridge│  │ whisper.cpp  │  │ GGML model       │   │   │
│  │  │ (Kotlin JNI) │  │ (C++ .so)   │  │ (internal storage│   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Android Framework                          │   │
│  │  InputMethodManager ── AudioRecord ── NotificationManager    │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
         │                                          │
         ▼                                          ▼
  ┌──────────────┐                        ┌──────────────────┐
  │ Host App     │                        │ HuggingFace      │
  │ (any app     │                        │ (model download   │
  │  with text   │                        │  only, one-time)  │
  │  field)      │                        │                   │
  └──────────────┘                        └──────────────────┘
```

### Critical Architectural Insight: Same-Process IME

Unlike iOS where the Keyboard Extension runs in a **separate process** with a 50 MB memory limit, Android's `InputMethodService` runs in the **same process** as the rest of the app by default (when packaged in the same APK without `android:process` in the manifest). This is a major simplification.

**Implication:** The IME can communicate with `DictationService` via a **local Binder** -- no AIDL serialization, no Messenger, no SharedPreferences polling. Direct in-memory method calls with shared Kotlin objects (StateFlow, callbacks). This is faster, simpler, and type-safe.

**Confidence:** HIGH -- this is standard Android behavior documented in the [official Android docs](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method). The IME is a Service declared in your app's manifest; without `android:process=":ime"`, it shares the default process.

### Component Responsibilities

| Component | Responsibility | Module |
|-----------|----------------|--------|
| **DictusIME** | Extends `InputMethodService`. Creates Compose keyboard UI, handles `InputConnection` for text insertion, binds to `DictationService` | `:ime` |
| **DictationService** | Foreground Service. Owns `AudioRecord`, runs whisper.cpp inference, manages dictation state machine, shows recording notification | `:app` |
| **WhisperBridge** | Kotlin JNI wrapper around whisper.cpp. Loads model, runs `transcribe()`, frees resources. Thread-safe singleton | `:whisper` |
| **ModelManager** | Downloads GGML models from HuggingFace, tracks storage, handles active model selection | `:app` |
| **DictationStateMachine** | Shared `StateFlow<DictationStatus>` observable by both IME and app. States: idle, requested, recording, transcribing, ready, failed | `:core` |
| **Settings/DataStore** | Persists user preferences (model, language, layout, haptics, sounds, theme) via Jetpack DataStore | `:core` |
| **Design System** | Dictus brand colors, typography, Material You theme, shared Compose modifiers | `:core` |
| **Activities** | Main app screens: Onboarding, Settings, ModelManager, Home (test dictation) | `:app` |

## Recommended Project Structure

```
dictus-android/
├── app/                              # Main app module
│   ├── src/main/
│   │   ├── java/.../dictus/
│   │   │   ├── DictusApplication.kt  # @HiltAndroidApp entry point
│   │   │   ├── MainActivity.kt       # Single activity, Compose navigation
│   │   │   ├── service/
│   │   │   │   └── DictationService.kt    # Foreground service: audio + inference
│   │   │   ├── audio/
│   │   │   │   └── AudioRecorder.kt       # AudioRecord wrapper (16kHz PCM)
│   │   │   ├── model/
│   │   │   │   ├── ModelManager.kt        # Download + storage management
│   │   │   │   └── ModelRepository.kt     # Model catalog + active selection
│   │   │   ├── ui/
│   │   │   │   ├── onboarding/            # Onboarding screens
│   │   │   │   ├── settings/              # Settings screen
│   │   │   │   ├── modelmanager/          # Model download/delete UI
│   │   │   │   ├── home/                  # Home with test dictation
│   │   │   │   └── navigation/            # NavHost + routes
│   │   │   └── di/
│   │   │       └── AppModule.kt           # Hilt module for app-scoped deps
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
│
├── ime/                               # IME module (keyboard)
│   ├── src/main/
│   │   ├── java/.../dictus/ime/
│   │   │   ├── DictusIME.kt              # InputMethodService + Compose bridge
│   │   │   ├── InputViewFactory.kt        # AbstractComposeView wrapper
│   │   │   ├── connection/
│   │   │   │   └── DictationServiceConnection.kt  # Binds to DictationService
│   │   │   ├── ui/
│   │   │   │   ├── KeyboardScreen.kt      # Root Compose screen for IME
│   │   │   │   ├── KeyboardLayout.kt      # AZERTY/QWERTY key grid
│   │   │   │   ├── KeyButton.kt           # Individual key composable
│   │   │   │   ├── ToolbarRow.kt          # Top bar (dictation status)
│   │   │   │   ├── SuggestionBar.kt       # Text prediction strip
│   │   │   │   ├── WaveformView.kt        # Canvas audio visualization
│   │   │   │   ├── EmojiPicker.kt         # Emoji grid with categories
│   │   │   │   └── RecordingOverlay.kt    # Full overlay during dictation
│   │   │   ├── input/
│   │   │   │   └── TextInserter.kt        # InputConnection wrapper
│   │   │   ├── layout/
│   │   │   │   ├── KeyDefinition.kt       # Key model (char, width, action)
│   │   │   │   ├── AzertyLayout.kt        # AZERTY layout data
│   │   │   │   └── QwertyLayout.kt        # QWERTY layout data
│   │   │   └── di/
│   │   │       └── ImeModule.kt           # Hilt module for IME-scoped deps
│   │   ├── AndroidManifest.xml            # IME service declaration
│   │   └── res/xml/
│   │       └── method.xml                 # IME metadata
│   └── build.gradle.kts
│
├── core/                              # Shared module
│   ├── src/main/
│   │   ├── java/.../dictus/core/
│   │   │   ├── dictation/
│   │   │   │   ├── DictationStatus.kt     # State enum (idle, recording, etc.)
│   │   │   │   └── DictationState.kt      # Full state data class
│   │   │   ├── prefs/
│   │   │   │   ├── SharedKeys.kt          # DataStore key constants
│   │   │   │   └── UserPreferences.kt     # Preferences data class + repo
│   │   │   ├── design/
│   │   │   │   ├── DictusTheme.kt         # Material You theme wrapper
│   │   │   │   ├── DictusColors.kt        # Brand color tokens
│   │   │   │   └── DictusTypography.kt    # Typography scale
│   │   │   ├── logging/
│   │   │   │   └── DictusLogger.kt        # Timber + file appender
│   │   │   └── model/
│   │   │       └── ModelInfo.kt           # Model metadata (name, size, url)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── whisper/                           # Native JNI module
│   ├── src/main/
│   │   ├── java/.../dictus/whisper/
│   │   │   ├── WhisperBridge.kt           # Kotlin JNI bindings
│   │   │   └── WhisperConfig.kt           # Inference config (threads, lang)
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt             # NDK build config
│   │   │   ├── whisper-jni.cpp            # JNI glue code
│   │   │   └── whisper.cpp/               # git submodule or source copy
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts                # Module declarations
└── gradle/
    └── libs.versions.toml             # Version catalog
```

### Structure Rationale

- **`:app`** owns the heavyweight components (DictationService, ModelManager, Activities). It depends on all other modules.
- **`:ime`** is isolated to keyboard concerns. It depends on `:core` for state/prefs and binds to DictationService at runtime. Keeping it separate allows independent testing and clear responsibility boundaries.
- **`:core`** has zero Android UI dependencies (no Compose in core data classes). Both `:app` and `:ime` depend on it. It holds the contract (state machine, preferences, model info) that both modules agree on.
- **`:whisper`** encapsulates all native/JNI complexity. Only `:app` (via DictationService) calls into it. The IME never touches whisper directly.

## Architectural Patterns

### Pattern 1: Same-Process Bound Service with Local Binder

**What:** DictationService exposes a local `Binder` that returns a reference to the service itself. The IME binds to it and calls methods directly.

**When to use:** Always in Dictus -- the IME and service share the same process.

**Trade-offs:**
- Pro: No serialization overhead, direct Kotlin object sharing, type-safe
- Pro: Can pass `StateFlow`, callbacks, and complex objects
- Con: Only works in-process. If you ever need a separate process, you must switch to AIDL/Messenger

**Example:**
```kotlin
// DictationService.kt
class DictationService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    override fun onBind(intent: Intent): IBinder = binder

    // Direct method calls from IME
    val dictationState: StateFlow<DictationState> = _state.asStateFlow()

    fun startRecording() { /* ... */ }
    fun stopRecording() { /* ... */ }
    fun cancelRecording() { /* ... */ }
}

// In DictusIME.kt
private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        val binder = service as DictationService.LocalBinder
        dictationService = binder.getService()
        // Now observe state directly
        scope.launch {
            dictationService.dictationState.collect { state ->
                // Update Compose UI
            }
        }
    }
    override fun onServiceDisconnected(name: ComponentName) {
        dictationService = null
    }
}
```

### Pattern 2: Compose in InputMethodService via AbstractComposeView

**What:** Android's `onCreateInputView()` returns a `View`, but we want Compose. Wrap a `@Composable` function in `AbstractComposeView` and wire up the lifecycle.

**When to use:** Any Compose-based IME.

**Trade-offs:**
- Pro: Full Compose expressiveness for keyboard UI
- Pro: Shared design system with app screens
- Con: Requires manual lifecycle owner setup (`ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`)
- Con: Potential performance issues with complex animations (profile early)

**Example:**
```kotlin
// DictusIME.kt
class DictusIME : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DictusIME)
            setViewTreeSavedStateRegistryOwner(this@DictusIME)
            setContent {
                DictusTheme {
                    KeyboardScreen(/* ... */)
                }
            }
        }
        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
```

**Confidence:** HIGH -- this pattern is [well-documented](https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff) and [proven in production keyboards](https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd).

### Pattern 3: Foreground Service for Audio Recording

**What:** DictationService starts as a foreground service with a persistent notification when recording begins. This prevents Android from killing the process during transcription.

**When to use:** Always when recording audio or running long inference.

**Trade-offs:**
- Pro: System won't kill the service during recording/inference
- Pro: User sees a notification confirming recording is active (transparency)
- Con: Requires `FOREGROUND_SERVICE_MICROPHONE` permission (API 34+), `POST_NOTIFICATIONS` (API 33+)

**Example:**
```kotlin
class DictationService : Service() {
    fun startRecording() {
        val notification = buildRecordingNotification()
        startForeground(NOTIFICATION_ID, notification)
        audioRecorder.start()
        _state.value = DictationState(status = DictationStatus.RECORDING)
    }

    fun stopRecording() {
        val audioData = audioRecorder.stop()
        _state.value = DictationState(status = DictationStatus.TRANSCRIBING)

        serviceScope.launch(Dispatchers.Default) {
            val result = whisperBridge.transcribe(audioData, language)
            _state.value = DictationState(
                status = DictationStatus.READY,
                transcription = result
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }
}
```

### Pattern 4: Thread Management for whisper.cpp

**What:** Run inference on a dedicated thread pool limited to performance cores. Android's big.LITTLE architecture means naively using all cores can schedule work on efficiency cores, making inference 5x slower.

**When to use:** All whisper.cpp calls.

**Trade-offs:**
- Pro: Predictable performance by pinning to big cores
- Con: Harder to manage than a simple coroutine dispatch

**Example:**
```kotlin
// WhisperBridge.kt -- use 4 threads (matching Pixel 4's 4 big cores)
external fun transcribe(
    contextPtr: Long,
    audioData: FloatArray,
    language: String,
    nThreads: Int = 4  // 1 prime + 3 gold cores on SD855
): String

// DictationService.kt -- dispatch to Default (CPU-bound) dispatcher
serviceScope.launch(Dispatchers.Default) {
    val text = whisperBridge.transcribe(contextPtr, samples, lang, nThreads = 4)
    withContext(Dispatchers.Main) {
        _state.value = DictationState(status = READY, transcription = text)
    }
}
```

**Confidence:** MEDIUM -- the 4-thread recommendation for SD855 comes from [community benchmarks](https://github.com/ggml-org/whisper.cpp/discussions/3567). Actual optimal thread count should be profiled on a real Pixel 4.

## Data Flow

### Primary Data Flow: Dictation Lifecycle

```
[User taps mic in Keyboard UI]
        │
        ▼
[DictusIME] ──bindService()──> [DictationService]
        │                              │
        │  startRecording()            │
        ├─────────────────────────────>│
        │                              ├── startForeground(notification)
        │                              ├── AudioRecorder.start()
        │                              ├── emit(RECORDING) via StateFlow
        │                              │
        │  <── StateFlow(RECORDING) ───┤
        │  (Compose UI shows waveform) │
        │                              ├── waveformEnergy via SharedFlow
        │  <── SharedFlow(energy[]) ───┤
        │                              │
[User taps stop]                       │
        │  stopRecording()             │
        ├─────────────────────────────>│
        │                              ├── AudioRecorder.stop() → FloatArray
        │                              ├── emit(TRANSCRIBING)
        │                              │
        │  <── StateFlow(TRANSCRIBING)─┤
        │  (Compose UI shows spinner)  │
        │                              │
        │                              ├── WhisperBridge.transcribe(audio)
        │                              │   (on Dispatchers.Default, 4 threads)
        │                              │
        │                              ├── emit(READY, text="Bonjour")
        │  <── StateFlow(READY, text)──┤
        │                              │
[DictusIME]                            ├── stopForeground()
        ├── InputConnection            │
        │   .commitText("Bonjour")     │
        │   (text inserted into host)  │
        │                              │
        ├── emit(IDLE) ───────────────>│
        ▼
[Host app receives text]
```

### Secondary Data Flow: Model Download

```
[User opens Model Manager screen]
        │
        ▼
[ModelManager UI] ──> [ModelRepository]
        │                    │
        │ downloadModel()    ├── Check: model already on disk?
        │                    │   YES → skip. NO → continue
        │                    │
        │                    ├── OkHttp GET from HuggingFace
        │                    │   (with progress callback)
        │                    │
        │  <── Flow(progress)┤
        │  (progress bar)    │
        │                    ├── Write to getFilesDir()/models/
        │                    │
        │                    ├── Update DataStore: activeModel
        │                    │
        ▼                    ▼
[Model ready on disk] → [WhisperBridge.initModel(path)]
```

### State Management

```
                    :core module
                ┌─────────────────┐
                │ DictationStatus │  (enum: IDLE, REQUESTED, RECORDING,
                │                 │   TRANSCRIBING, READY, FAILED)
                └────────┬────────┘
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
    ┌──────────────┐      ┌──────────────┐
    │ DictationSvc │      │  DictusIME   │
    │ (producer)   │      │  (consumer)  │
    │              │      │              │
    │ MutableState │──────│ StateFlow    │
    │ Flow<State>  │      │ .collect {}  │
    └──────────────┘      └──────────────┘
```

**Key design:** The DictationService is the **single source of truth** for dictation state. It exposes a `StateFlow<DictationState>`. The IME only observes and triggers actions -- it never mutates state directly.

### Key Data Flows

1. **Audio data:** `AudioRecord` (16kHz, mono, 16-bit PCM) -> `FloatArray` -> `WhisperBridge.transcribe()` -> `String`
2. **Waveform energy:** `AudioRecord` callback -> RMS energy calculation -> `SharedFlow<FloatArray>` -> Compose Canvas
3. **User preferences:** `DataStore<Preferences>` -> `Flow<UserPreferences>` -> Compose UI (both app and IME)
4. **Model files:** HuggingFace HTTPS -> `getFilesDir()/models/*.bin` -> `WhisperBridge.initModel(path)`

## Scaling Considerations

Scaling is not a concern for Dictus (it is a local-only app). However, the following device-scaling concerns apply:

| Concern | Pixel 4 (SD855) | Modern Flagships (SD 8 Gen 3+) | Low-End Devices |
|---------|------------------|-------------------------------|-----------------|
| Model size | small-q5_0 max | medium feasible | tiny only |
| Thread count | 4 threads | 6-8 threads | 2 threads |
| RAM headroom | ~1.5 GB for app | 3+ GB available | tight, 500 MB |
| Inference time (10s audio) | 4-8s (small-q5) | 1-3s (small-q5) | 2-4s (tiny) |

### Scaling Priorities

1. **First bottleneck:** Model loading time at cold start. The GGML model takes 1-3 seconds to load into memory. **Mitigation:** Keep DictationService alive (foreground service stays warm); lazy-load model on first dictation request, not at IME creation.
2. **Second bottleneck:** Inference duration with larger models. **Mitigation:** Default to quantized models; show progress/waveform during transcription so users perceive activity.

## Anti-Patterns

### Anti-Pattern 1: Running Whisper in the IME Thread

**What people do:** Call `WhisperBridge.transcribe()` directly from the IME's main thread or coroutine scope.
**Why it's wrong:** IME runs in a UI-sensitive context. Android's watchdog will ANR the IME if it blocks for >5 seconds. Whisper inference takes 4-15 seconds. This will crash the keyboard.
**Do this instead:** Always run inference in DictationService on `Dispatchers.Default`. The IME only observes `StateFlow` updates.

### Anti-Pattern 2: AIDL/Messenger for Same-Process IPC

**What people do:** Use AIDL or Messenger to communicate between the IME and DictationService because "they're different components."
**Why it's wrong:** If both are in the same process (default when same APK), AIDL adds unnecessary serialization/deserialization overhead and boilerplate. Messenger serializes to a single thread.
**Do this instead:** Use a local `Binder` that returns the service instance directly. Share Kotlin objects (StateFlow, callbacks) in memory.

### Anti-Pattern 3: SharedPreferences Polling for IPC

**What people do:** Write transcription results to SharedPreferences in the service, then poll from the IME using a timer.
**Why it's wrong:** Polling wastes CPU, has latency, and SharedPreferences writes can be lost if the process crashes mid-write. It also lacks type safety.
**Do this instead:** Use `StateFlow` observed via coroutines. Immediate, reactive, zero polling.

### Anti-Pattern 4: Loading Model in onCreate of InputMethodService

**What people do:** Initialize `WhisperBridge.initModel()` in the IME's `onCreate()`.
**Why it's wrong:** Model loading takes 1-3 seconds and happens on the main thread, causing a visible keyboard delay. The model may not even be needed (user might just type).
**Do this instead:** Lazy-load the model in `DictationService` when the first dictation is requested. The IME should start instantly with just the keyboard UI.

### Anti-Pattern 5: Using All CPU Cores for Inference

**What people do:** Set `nThreads = Runtime.getRuntime().availableProcessors()` (e.g., 8 on Pixel 4).
**Why it's wrong:** Android's big.LITTLE architecture has 4 performance cores and 4 efficiency cores. Using all 8 threads may schedule work on efficiency cores, making inference 5x slower than using only the 4 big cores.
**Do this instead:** Use 4 threads on Pixel 4 (SD855). Profile on target device. whisper.cpp's community recommends matching the number of performance cores.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| HuggingFace (model download) | OkHttp HTTPS GET with progress | One-time download per model. No auth needed for public GGML models. Use `ggml-org/whisper-ggml` repo for URLs |
| Android System (IME framework) | `InputMethodService` + `InputConnection` | Declared in manifest with `BIND_INPUT_METHOD` permission. System binds automatically |
| Android System (audio) | `AudioRecord` with `RECORD_AUDIO` permission | 16kHz, mono, 16-bit PCM. Must request runtime permission |
| Android System (notifications) | `NotificationManager` + `ForegroundServiceType.MICROPHONE` | Required for foreground service. API 34+ needs `FOREGROUND_SERVICE_MICROPHONE` |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `:ime` <-> `:app` (DictationService) | Local Binder + StateFlow | Same process. IME binds on `onStartInputView()`, unbinds on `onFinishInput()` |
| `:app` <-> `:whisper` | Direct Kotlin function calls | WhisperBridge is a singleton provided by Hilt. Only DictationService calls it |
| `:ime` <-> `:core` | Direct dependency | IME reads `DictationStatus` enum, `UserPreferences` flow, design tokens |
| `:app` <-> `:core` | Direct dependency | App reads/writes DataStore prefs, uses shared models and design system |
| `:ime` <-> Host App | `InputConnection.commitText()` | Standard Android IME API. No custom integration needed |

## Module Dependency Graph

```
:app ──────> :core
  │  ──────> :whisper
  │
:ime ──────> :core
  │  (runtime bind to :app's DictationService)
  │
:whisper ──> (no Kotlin module deps; links whisper.cpp .so via NDK)
  │
:core ──────> (no module deps; depends on Jetpack DataStore, Compose foundation)
```

### Build Order (based on dependency graph)

1. **Phase 1:** `:core` -- shared contracts, state machine, design system, DataStore prefs. Zero dependencies on other modules. Everything else needs this first.
2. **Phase 2a:** `:whisper` -- JNI bridge + whisper.cpp native build. Can be built independently and tested with a simple Android app (no IME needed).
3. **Phase 2b:** `:ime` -- Keyboard UI with Compose. Can be tested with mock state (no real transcription). Depends on `:core` only.
4. **Phase 3:** `:app` -- DictationService wiring everything together. Activities for onboarding, settings, model management. Depends on `:core` + `:whisper`.
5. **Phase 4:** Integration -- IME binds to real DictationService. End-to-end dictation flow on Pixel 4.

**Rationale:** Phases 2a and 2b are parallelizable. The whisper JNI build is the highest-risk item (C++ toolchain, NDK configuration) and should start early. The keyboard UI is the highest-effort item and benefits from early iteration.

## Sources

- [Android InputMethodService official docs](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Creating an Input Method -- Android Developers](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Bound Services -- Android Developers](https://developer.android.com/develop/background-work/services/bound-services)
- [Compose in InputMethodService -- GitHub Gist](https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff)
- [Custom Soft Keyboard with Compose -- Medium](https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd)
- [whisper.cpp official Android example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android)
- [whisper.cpp Java JNI bindings](https://github.com/ggml-org/whisper.cpp/blob/master/bindings/java/README.md)
- [whisper.cpp streaming performance on Android](https://github.com/ggml-org/whisper.cpp/discussions/3567)
- [Building whisper.cpp on Android via JNI -- Medium](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc)
- [Hilt DI for Android Services](https://developer.android.com/training/dependency-injection/hilt-android)
- [AIDL -- Android Developers](https://developer.android.com/develop/background-work/services/aidl)

---
*Architecture research for: Android IME + On-Device ML Inference (Dictus Android)*
*Researched: 2026-03-21*
