# Technology Stack

**Project:** Dictus Android
**Researched:** 2026-03-21
**Mode:** Ecosystem research for Android on-device voice dictation keyboard (IME)

## Recommended Stack

### Core Language & Build

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Kotlin | 2.3.20 | Primary language | Current stable (March 2026). Built-in coroutines for async audio/ML work. AGP 9+ has built-in Kotlin support. | HIGH |
| Android Gradle Plugin (AGP) | 9.1.0 | Build system | Current stable (March 2026). Built-in Kotlin support eliminates need for separate kotlin-android plugin. Requires Gradle 9.1+ and JDK 17+. | HIGH |
| Gradle | 9.3.0 | Build orchestration | Compatible with AGP 9.1.0 and Kotlin 2.3.20. | HIGH |
| Android NDK | r28 | Native C++ compilation for whisper.cpp | Latest stable (Feb 2026). Updated LLVM clang, C++23 support. Required for JNI bridge. | HIGH |
| CMake | 3.22+ | Native build system | whisper.cpp uses CMake. NDK r28 requires minimum 3.10, but 3.22+ recommended for modern features. | HIGH |
| Min SDK | API 29 (Android 10) | Minimum supported version | Pixel 4 shipped with Android 10. Covers 95%+ of active devices. | HIGH |
| Target SDK | API 35 (Android 15) | Target API level | Current Play Store requirement. Use latest stable target. | MEDIUM |

### UI Framework

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Jetpack Compose BOM | 2026.03.00 | UI toolkit (BOM manages versions) | Declarative UI, parity with SwiftUI on iOS. BOM ensures compatible versions across all Compose libraries. Maps to Compose 1.10.x stable. | HIGH |
| Compose UI | (via BOM) | Core UI components | Foundation, Material 3, runtime, all versioned via BOM. | HIGH |
| Compose Material 3 | (via BOM) | Material You design system | Dynamic color, tonal elevation, dark theme support. Matches PRD design requirements. | HIGH |
| AbstractComposeView | (platform API) | Bridge Compose into InputMethodService | `onCreateInputView()` returns a View; wrap Compose content in AbstractComposeView subclass. This is the established pattern for Compose-based IMEs. | HIGH |

**IME + Compose integration pattern:**
```kotlin
class DictusKeyboardView(context: Context) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        DictusTheme {
            KeyboardLayout(/* ... */)
        }
    }
}

// In InputMethodService:
override fun onCreateInputView(): View {
    return DictusKeyboardView(this).apply {
        // Bind lifecycle owner for Compose
        ViewTreeLifecycleOwner.set(this, this@DictusIME)
        ViewTreeSavedStateRegistryOwner.set(this, this@DictusIME)
    }
}
```

### Speech Engine (On-Device STT)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| whisper.cpp | v1.8.4 | On-device Whisper inference | Latest stable (March 2025). Direct C++ port of OpenAI Whisper. Same GGML models as used in community. Official Android examples in repo (`examples/whisper.android`). MIT license. CPU multi-threaded inference is reliable on Snapdragon 855. | HIGH |
| JNI bridge (custom) | N/A | Kotlin-to-C++ interface | Build custom JNI bridge wrapping whisper.cpp. Official Android example provides the pattern. Expose: `initModel()`, `transcribe()`, `freeModel()`, `getProgress()`. | HIGH |
| GGML models | whisper-ggml | Pre-trained Whisper models | Download from HuggingFace (`ggml-org/whisper-ggml`). Quantized variants (q5_0, q8_0) critical for Pixel 4 performance. | HIGH |

**Why whisper.cpp over alternatives:**
- **vs Sherpa-ONNX:** whisper.cpp gives direct parity with iOS (same Whisper models, same inference behavior). Sherpa-ONNX adds ONNX Runtime dependency (~15MB) and uses different model format. whisper.cpp has a simpler, self-contained build.
- **vs on-device Google Speech API:** Requires Google Play Services, not fully offline, no control over model selection.
- **vs TensorFlow Lite Whisper:** Less mature, fewer optimizations, no official Whisper support.

### Audio

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| AudioRecord | (platform API) | Low-level PCM audio capture | Direct access to raw PCM samples at 16kHz mono (what Whisper expects). Runs in DictationService (foreground service), not in IME. | HIGH |
| SoundPool | (platform API) | Playback of start/stop recording sounds | Low-latency playback of short audio clips. Better than MediaPlayer for UI feedback sounds. | HIGH |

### Architecture & DI

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Hilt (Dagger) | 2.57.1 | Dependency injection | Google-recommended DI for Android. Compile-time verification. Works with Services, Activities, and ViewModels. AGP 9 compatible. | HIGH |
| Lifecycle ViewModel | 2.10.0 | ViewModel + state management | Current stable. `rememberViewModelStoreOwner` for Compose scoping. StateFlow-based reactive state. | HIGH |
| Kotlin Coroutines | 1.10.x | Async operations | Audio recording, model loading, transcription -- all async. Structured concurrency with proper cancellation. Flow for reactive streams. | HIGH |
| Kotlin StateFlow | (via coroutines) | Observable state for dictation machine | Type-safe, lifecycle-aware state. Maps directly from iOS `@Published` pattern. Powers the idle -> recording -> transcribing -> ready state machine. | HIGH |

### Data & Persistence

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Jetpack DataStore (Preferences) | 1.2.1 | Key-value settings persistence | Replaces SharedPreferences. Coroutine-based, type-safe. Stores: active model, language, layout, haptic/sound prefs, onboarding state. | HIGH |
| File I/O (internal storage) | (platform API) | GGML model file storage | Models stored in `getFilesDir()`. No Room needed -- models are binary blobs, not relational data. | HIGH |

### Networking (Model Download Only)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| OkHttp | 4.12.x | HTTP client for model downloads | Mature, reliable. Supports progress callbacks for download UI. Only used for downloading GGML models from HuggingFace. No other network usage. | HIGH |

**Why OkHttp over Ktor:** OkHttp is simpler for a single use case (file download with progress). Ktor is overkill -- Dictus has no REST API, no serialization needs, no multiplatform requirements. OkHttp is already a transitive dependency of many Android libraries.

### Logging & Debugging

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Timber | 5.0.1 | Structured logging | Industry standard Android logging. Extensible Tree API for custom file appender. Auto-tags with class name. PRD specifies Timber. | HIGH |
| Custom FileTree | N/A | Log-to-file for debug export | Implement a Timber Tree that writes to a rotating log file in internal storage. Used for Settings > Export Logs feature. | HIGH |

### IPC (IME <-> DictationService)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Bound Service (AIDL) | (platform API) | Type-safe IME-to-Service communication | PRD decision: Bound Service for IPC. Type-safe interface, lifecycle-aware binding. IME binds to DictationService to send start/stop commands and receive transcription results + waveform data. | HIGH |
| Foreground Service | (platform API) | Long-running audio recording | Android-standard pattern. Shows notification during recording. Prevents system from killing the service during transcription. Required for RECORD_AUDIO in background on API 29+. | HIGH |

### Haptics & Feedback

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| HapticFeedbackConstants | (Compose API) | Key press haptic feedback | `Modifier.hapticFeedback()` or `LocalHapticFeedback.current`. No extra library needed. | HIGH |
| Vibrator / VibrationEffect | (platform API) | Custom vibration patterns | For recording start/stop feedback beyond standard Compose haptics. API 29+ supports VibrationEffect. | HIGH |

### Testing

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| JUnit 5 | 5.11.x | Unit testing | Standard JVM testing. Test state machine, model manager logic. | HIGH |
| Compose UI Test | (via BOM) | UI testing | `createComposeRule()` for keyboard layout tests. | HIGH |
| Mockk | 1.13.x | Kotlin mocking | Idiomatic Kotlin mocking. Better coroutine support than Mockito. | HIGH |

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| STT Engine | whisper.cpp (JNI) | Sherpa-ONNX | Adds ONNX Runtime dependency, different model format than iOS, more complex build. Consider as fallback only if whisper.cpp JNI proves too painful. |
| STT Engine | whisper.cpp (JNI) | whisper-jni (Maven) | Third-party wrapper (`io.github.givimad:whisper-jni`). Adds indirection. Better to control the JNI bridge directly for performance tuning (thread count, model loading). |
| UI in IME | Jetpack Compose | Android Views (XML) | Views are more battle-tested in IMEs but lose SwiftUI parity, are harder to maintain, and require more boilerplate. Compose in IME is viable with AbstractComposeView. |
| DI | Hilt | Koin | Koin is simpler but no compile-time verification. Hilt catches DI errors at build time, critical for a multi-module project (app, ime, core). |
| Networking | OkHttp | Ktor | Ktor is overkill for single-purpose file downloads. OkHttp is lighter, already a transitive dep. |
| Networking | OkHttp | Retrofit | Retrofit is for REST APIs. Dictus just downloads binary files from static URLs. |
| Persistence | DataStore Preferences | Room | No relational data. Models are files, settings are key-value. Room adds unnecessary complexity. |
| Persistence | DataStore Preferences | Proto DataStore | Proto requires protobuf schema definition. Settings are simple key-value pairs, Preferences DataStore is sufficient. |
| Logging | Timber | Logcat only | No file logging, no structured output, no production-safe logging. |
| State Management | StateFlow | LiveData | LiveData is lifecycle-aware but less flexible than Flow. StateFlow integrates better with coroutines and Compose (`collectAsStateWithLifecycle()`). |

## Project Module Structure

```
dictus-android/
├── app/              # Main app: onboarding, settings, model manager, DictationService
├── ime/              # IME module: InputMethodService, keyboard UI, emoji picker
├── core/             # Shared: state machine, DataStore keys, design tokens, logging
└── whisper/          # Native module: whisper.cpp source, JNI bridge, CMakeLists.txt
```

Each module is a Gradle module. `app` and `ime` depend on `core`. `app` depends on `whisper`. The IME communicates with whisper indirectly via the Bound Service in `app`.

## Installation

```kotlin
// build.gradle.kts (project-level)
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("com.android.library") version "9.1.0" apply false
    // Kotlin plugin no longer needed separately with AGP 9+
    id("com.google.dagger.hilt.android") version "2.57.1" apply false
}

// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.pivi.dictus"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        targetSdk = 35
    }
    // NDK for whisper.cpp
    externalNativeBuild {
        cmake {
            path = file("../whisper/cpp/CMakeLists.txt")
        }
    }
    ndkVersion = "28.0.13004108"
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // DI
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")

    // Networking (model download only)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.14")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

## Version Verification Notes

| Technology | Version Source | Verification |
|------------|--------------|-------------|
| Kotlin 2.3.20 | JetBrains blog (March 2026) | HIGH -- official announcement |
| AGP 9.1.0 | developer.android.com release notes | HIGH -- official docs |
| Compose BOM 2026.03.00 | developer.android.com | HIGH -- official docs |
| whisper.cpp v1.8.4 | GitHub releases (ggml-org/whisper.cpp) | HIGH -- official repo |
| NDK r28 | developer.android.com + GitHub releases | HIGH -- official |
| Hilt 2.57.1 | developer.android.com + Maven | HIGH -- official docs |
| Lifecycle 2.10.0 | developer.android.com | HIGH -- official docs |
| DataStore 1.2.1 | developer.android.com | HIGH -- official docs |
| Timber 5.0.1 | Maven Central | HIGH -- verified |
| OkHttp 4.12.0 | Maven Central | MEDIUM -- may have newer patch |

## Sources

- [Kotlin 2.3.20 Release](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/) -- JetBrains official blog
- [AGP 9.1.0 Release Notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) -- Android Developers
- [Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom) -- Android Developers
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp) -- Official repository
- [whisper.cpp Releases](https://github.com/ggml-org/whisper.cpp/releases) -- v1.8.4 latest
- [Compose IME Example](https://github.com/THEAccess/compose-keyboard-ime) -- AbstractComposeView pattern
- [Custom Soft Keyboard with Compose](https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd) -- Implementation guide
- [Hilt on Android Developers](https://developer.android.com/training/dependency-injection/hilt-android) -- Official setup
- [Lifecycle Releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) -- v2.10.0 stable
- [DataStore Documentation](https://developer.android.com/topic/libraries/architecture/datastore) -- Official guide
- [NDK Releases](https://github.com/android/ndk/releases) -- r28 latest
- [AGP 9.0 Kotlin Migration](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) -- JetBrains migration guide
- [Timber GitHub](https://github.com/JakeWharton/timber) -- v5.0.1
- [LocalMind: Whisper on Android via JNI](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc) -- Real-world implementation reference
