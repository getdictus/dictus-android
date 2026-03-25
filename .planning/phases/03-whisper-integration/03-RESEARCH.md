# Phase 3: Whisper Integration - Research

**Researched:** 2026-03-25
**Domain:** whisper.cpp native integration via NDK/CMake/JNI on Android, on-device speech-to-text
**Confidence:** HIGH

## Summary

Phase 3 integrates whisper.cpp into the Dictus Android app as a native C/C++ library compiled via NDK/CMake, bridged to Kotlin through JNI. The official whisper.cpp repository provides a complete Android example (`examples/whisper.android`) with a `lib` module containing the JNI bridge (`jni.c`), CMake configuration, and a Kotlin wrapper (`LibWhisper.kt`). This reference implementation is the foundation -- it provides working JNI bindings, CPU-optimized builds with ARM NEON fp16 support, and a thread-safe Kotlin wrapper using single-threaded coroutine dispatchers.

The key technical challenges are: (1) adapting the reference JNI bridge to accept a language parameter (the reference hard-codes "en"), (2) choosing model delivery strategy (asset bundling vs auto-download from HuggingFace), (3) wiring the transcription result into the existing DictationService -> DictusImeService -> InputConnection pipeline, and (4) ensuring the 8-second performance target on Pixel 4 with the small quantized model. The existing codebase is well-prepared: `DictationService.stopRecording()` already returns a `FloatArray` of 16kHz mono Float32 samples -- exactly what whisper.cpp expects.

**Primary recommendation:** Clone the whisper.cpp `lib` module pattern (separate Android library module with CMake/JNI), adapt JNI to pass language parameter from Kotlin, use `initContext(filePath)` for model loading from internal storage, and download models from HuggingFace on first launch. This approach maximizes Phase 4 code reuse since the model manager will also download/delete from the same storage location.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- Bundle **two models** for Phase 3: tiny (fast iteration) and small-q5_0 (perf validation target)
- **Tiny is the default** model for development -- small-q5_0 available but opt-in
- Model delivery method: Claude's discretion between asset bundling and auto-download from HuggingFace -- choose whichever approach reuses the most code in Phase 4
- Phase 4 replaces this with full model management (download/delete UI, storage indicators)
- **All at once** text insertion after transcription completes (no progressive/streaming)
- **Post-processing matches iOS** (`DictationCoordinator.swift:368-372`): if ends with `.!?...` append space, otherwise append `. `
- Trim leading/trailing whitespace before post-processing
- Insert via `InputConnection.commitText()` at current cursor position
- **French ("fr") hard-coded** as transcription language for Phase 3
- **No cancel during transcription** -- user waits
- **Error handling**: silent return to keyboard idle state, error logged via Timber
- **Timeout**: 30 seconds -- if exceeded, treat as failure, return to idle
- **After insertion**: immediate return to keyboard (no success animation)
- Add `Transcribing` state to `DictationState` sealed class
- Full flow: Idle -> Recording -> Transcribing -> Idle

### Claude's Discretion
- whisper.cpp native build approach (NDK/CMake configuration, JNI bridge implementation)
- Model file format and storage location on device
- whisper.cpp threading configuration (number of threads for Pixel 4)
- Exact JNI method signatures and native wrapper design
- Coroutine dispatcher choice for transcription (Default vs IO)
- Model delivery approach (asset bundle vs auto-download) -- pick what reuses most in Phase 4

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DICT-03 | Transcription runs on-device via whisper.cpp with GGML models | whisper.cpp reference Android example provides complete JNI bridge, CMake build, Kotlin wrapper; models from HuggingFace `ggerganov/whisper.cpp` |
| DICT-04 | Transcribed text is inserted at cursor position in the active text field | `DictusImeService.commitText()` already implemented; post-processing logic ported from iOS `DictationCoordinator.swift:368-372` |

</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| whisper.cpp | master (via git submodule) | On-device speech-to-text inference engine | Official C/C++ port of OpenAI Whisper; reference Android example available; same engine as iOS via WhisperKit |
| Android NDK | r25+ (25.2.9519653) | C/C++ compiler toolchain for native code | Required for compiling whisper.cpp; version matches reference example |
| CMake | 3.10+ (bundled with AGP) | Native build system | Standard for Android NDK projects; used by whisper.cpp reference |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| OkHttp | 4.12.0 | HTTP client for model download | Download GGML models from HuggingFace; already standard for Android |
| Timber | 5.0.1 (already in project) | Structured logging | All whisper operations: init, transcribe, timing, errors |
| Kotlin Coroutines | 1.8.1 (already in project) | Async transcription execution | Offload blocking whisper_full() call from main thread |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| whisper.cpp submodule | whisper-jni Maven artifact | Maven artifact wraps whisper.cpp but adds indirection; submodule gives full control over version and build flags -- prefer submodule for this project |
| OkHttp for download | HttpURLConnection | OkHttp is more robust (retry, progress callbacks), worth the small dependency for Phase 4 reuse |
| Asset bundling models | Auto-download from HuggingFace | Assets inflate APK size (77MB+ for tiny alone); auto-download keeps APK small and matches Phase 4 model manager pattern -- **recommend auto-download** |

## Architecture Patterns

### Recommended Project Structure
```
whisper/                          # New module (Android library)
  src/main/
    jni/
      whisper/
        CMakeLists.txt            # CMake build config pointing to whisper.cpp submodule
        jni.c                     # JNI bridge (adapted from reference)
    java/dev/pivisolutions/dictus/whisper/
      WhisperContext.kt           # Kotlin wrapper (thread-safe, coroutine-based)
      WhisperLib.kt               # JNI external function declarations
      WhisperCpuConfig.kt         # Thread count detection (from reference)
  build.gradle.kts                # NDK + CMake configuration

core/src/main/.../
  service/
    DictationState.kt             # Add Transcribing state
    DictationController.kt        # Add transcribe method or extend stopRecording flow
  whisper/
    TranscriptionEngine.kt        # Interface for transcription (abstracts whisper module)
    TextPostProcessor.kt          # Post-processing: trim, punctuation, spacing

app/src/main/.../
  service/
    DictationService.kt           # Orchestrate: stopRecording -> transcribe -> emit result
  model/
    ModelManager.kt               # Download models from HuggingFace, store in internal storage

third_party/
  whisper.cpp/                    # Git submodule of ggml-org/whisper.cpp
```

### Pattern 1: Separate Native Library Module
**What:** whisper.cpp lives in its own Android library module (`whisper/`) with CMake/JNI, separate from `app/`, `core/`, and `ime/`.
**When to use:** Always for native code -- isolates NDK build complexity from the rest of the project.
**Why:** The reference example uses this exact pattern (`lib/` module). It keeps native build configuration contained, allows the `app` module to depend on `whisper` without polluting its build.gradle, and enables independent testing of the native layer.

### Pattern 2: Single-Threaded Coroutine Dispatcher for Whisper
**What:** WhisperContext uses `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` for all whisper operations.
**When to use:** All whisper API calls (init, transcribe, free).
**Why:** whisper.cpp is NOT thread-safe for concurrent access to the same context. The reference implementation enforces this with a single-thread executor. Internally, `whisper_full()` uses multiple threads for inference (controlled by `n_threads` param), but the JNI entry point must be called from one thread at a time.
**Example:**
```kotlin
// Source: whisper.cpp examples/whisper.android/lib/src/main/java/.../LibWhisper.kt
class WhisperContext private constructor(private var ptr: Long) {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(data: FloatArray): String = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        val numThreads = WhisperCpuConfig.preferredThreadCount
        WhisperLib.fullTranscribe(ptr, numThreads, data)
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }
}
```

### Pattern 3: Model Auto-Download with Internal Storage
**What:** Models are downloaded from HuggingFace to `context.filesDir/models/` on first use, not bundled as APK assets.
**When to use:** Phase 3 initial setup and Phase 4 model manager.
**Why:** Asset bundling would inflate the APK by 77MB (tiny) or 190MB (small-q5_1). Auto-download keeps the APK under 10MB. Phase 4's model manager will also download/delete models from the same directory -- this approach gives maximum code reuse.
**Download URLs:**
```
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin      (77.7 MB)
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin (190 MB)
```

### Pattern 4: State Machine Extension
**What:** Add `Transcribing` to the `DictationState` sealed class; DictationService transitions Recording -> Transcribing -> Idle.
**When to use:** After stopRecording() returns audio samples.
**Flow:**
```
User taps confirm -> DictationService.stopRecording() returns FloatArray
  -> state = Transcribing -> IME shows transcribing UI
  -> WhisperContext.transcribeData(samples) on background thread
  -> post-process text (trim, punctuation)
  -> InputConnection.commitText(text, 1)
  -> state = Idle -> IME shows keyboard
```

### Anti-Patterns to Avoid
- **Accessing WhisperContext from multiple threads:** whisper.cpp context is NOT thread-safe. Always use the single-thread dispatcher pattern.
- **Loading model from assets at runtime:** AAsset streaming is slower than file I/O and cannot seek. Use `initContext(filePath)` with a file on internal storage, not `initContextFromAsset()`.
- **Blocking the main thread during transcription:** `whisper_full()` is CPU-intensive (seconds). Must run on a background dispatcher. The existing `serviceScope` with `Dispatchers.Main` is NOT suitable -- transcription needs `Dispatchers.Default` or the single-thread executor.
- **Creating a new WhisperContext per transcription:** Context initialization loads the entire model into memory (takes seconds). Create once, reuse for all transcriptions.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Speech-to-text inference | Custom ML pipeline | whisper.cpp via JNI | Thousands of hours of optimization; NEON/fp16 support; proven accuracy |
| JNI bridge | Custom JNI from scratch | Adapt reference `jni.c` | Reference handles memory management, context lifecycle, segment extraction correctly |
| CPU thread detection | Hard-code thread count | `WhisperCpuConfig` from reference | Reads `/proc/cpuinfo` and CPU frequencies to detect high-perf cores; handles big.LITTLE correctly |
| Model file download | Manual HTTP with streams | OkHttp with progress callback | Handles redirects, retries, partial downloads; Phase 4 needs this too |
| Audio format conversion | PCM16 to Float32 converter | Direct Float32 capture (already done) | AudioCaptureManager already captures ENCODING_PCM_FLOAT at 16kHz mono |

**Key insight:** The whisper.cpp reference Android example provides a complete, tested JNI bridge and Kotlin wrapper. The only modifications needed are: (1) add language parameter to `fullTranscribe` JNI call, and (2) adapt package names. Do NOT rewrite from scratch.

## Common Pitfalls

### Pitfall 1: whisper.cpp Build Fails with NDK
**What goes wrong:** CMake cannot find whisper.cpp sources because the path is relative to the example's location within the whisper.cpp repo.
**Why it happens:** The reference CMakeLists.txt uses `${CMAKE_SOURCE_DIR}/../../../../../../..` to navigate to the whisper.cpp root. When whisper.cpp is a git submodule at a different relative path, this breaks.
**How to avoid:** Adjust `WHISPER_LIB_DIR` in CMakeLists.txt to point to the actual submodule location: `set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../../../third_party/whisper.cpp)` or use a variable passed from build.gradle.
**Warning signs:** CMake errors about missing `whisper.h`, `ggml.h`, or `whisper.cpp` source files.

### Pitfall 2: JNI Package Name Mismatch
**What goes wrong:** Native methods fail to link at runtime with `UnsatisfiedLinkError`.
**Why it happens:** JNI function names in `jni.c` encode the full Java package + class name. The reference uses `Java_com_whispercpp_whisper_WhisperLib_*` but Dictus uses `dev.pivisolutions.dictus.whisper`.
**How to avoid:** Rename ALL JNI functions in `jni.c` to match the actual Kotlin package: `Java_dev_pivisolutions_dictus_whisper_WhisperLib_*`. Every dot in the package becomes underscore.
**Warning signs:** App crash on first `WhisperLib.initContext()` call with `java.lang.UnsatisfiedLinkError: No implementation found`.

### Pitfall 3: Hard-Coded Language in JNI
**What goes wrong:** All transcription returns English text regardless of input language.
**Why it happens:** The reference `jni.c` hard-codes `params.language = "en"`. Phase 3 needs French.
**How to avoid:** Add a `jstring language` parameter to the `fullTranscribe` JNI function. Convert to C string, set `params.language`. For Phase 3, Kotlin always passes `"fr"`.
**Warning signs:** French speech transcribed as nonsensical English words.

### Pitfall 4: Model File Not Found After Download
**What goes wrong:** `whisper_init_from_file_with_params()` returns NULL.
**Why it happens:** The file path passed to JNI is wrong, or the download was incomplete/corrupt.
**How to avoid:** Use `context.filesDir.absolutePath + "/models/ggml-tiny.bin"` for the path. Verify file size after download matches expected size. Log the exact path before passing to JNI.
**Warning signs:** `Couldn't create context with path` exception from WhisperContext.

### Pitfall 5: Out-of-Memory on Model Load
**What goes wrong:** App crashes or model initialization fails silently.
**Why it happens:** whisper.cpp loads the full model into memory. Tiny = ~77MB RAM, small = ~500MB+ RAM (or ~200MB for quantized). On Pixel 4 (6GB), this is fine, but if other apps are consuming memory, it can fail.
**How to avoid:** Use quantized models (q5_1 = 190MB for small). Log available memory before model load. Handle NULL context gracefully.
**Warning signs:** App killed by OOM killer during model init, or `whisper_init_from_file_with_params` returns NULL.

### Pitfall 6: Model Name Discrepancy (q5_0 vs q5_1)
**What goes wrong:** Cannot find `ggml-small-q5_0.bin` on HuggingFace.
**Why it happens:** The HuggingFace `ggerganov/whisper.cpp` repo provides pre-built `ggml-small-q5_1.bin` (190 MB) but NOT `q5_0`. The `q5_0` variant must be generated manually using the `quantize` tool.
**How to avoid:** Use `ggml-small-q5_1.bin` instead of `q5_0`. The q5_1 quantization is slightly larger but slightly more accurate than q5_0. Performance is comparable. The CONTEXT.md mentions "small-q5_0" but the closest available pre-built model is q5_1.
**Warning signs:** 404 error when downloading from HuggingFace.

## Code Examples

### JNI Bridge with Language Parameter (adapted from reference)
```c
// Source: Adapted from whisper.cpp/examples/whisper.android/lib/src/main/jni/whisper/jni.c
JNIEXPORT void JNICALL
Java_dev_pivisolutions_dictus_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);
    const char *language = (*env)->GetStringUTFChars(env, language_str, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language;       // "fr" for Phase 3
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);

    if (whisper_full(context, params, audio_data_arr, audio_data_length) != 0) {
        LOGI("Failed to run the model");
    } else {
        whisper_print_timings(context);
    }

    (*env)->ReleaseStringUTFChars(env, language_str, language);
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
}
```

### Kotlin JNI Declaration with Language
```kotlin
// WhisperLib.kt
private class WhisperLib {
    companion object {
        init {
            System.loadLibrary("whisper")
        }
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
    }
}
```

### Post-Processing (ported from iOS DictationCoordinator.swift:368-372)
```kotlin
// TextPostProcessor.kt in core module
object TextPostProcessor {
    /**
     * Apply post-processing to transcribed text before insertion.
     *
     * Matches iOS behavior from DictationCoordinator.swift:368-372:
     * - Trim whitespace (Whisper sometimes adds extra spaces)
     * - If text ends with sentence-ending punctuation (.!?...), append just a space
     * - Otherwise, append ". " (period + space)
     * This prevents chained dictations from sticking together.
     */
    fun process(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ""

        val lastChar = trimmed.last()
        return if (lastChar in setOf('.', '!', '?') || trimmed.endsWith("...")) {
            "$trimmed "
        } else {
            "$trimmed. "
        }
    }
}
```

### Model Download from HuggingFace
```kotlin
// ModelManager.kt in app module
class ModelManager(private val context: Context) {
    companion object {
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
        val MODELS = mapOf(
            "tiny" to ModelInfo("ggml-tiny.bin", 77_700_000L),
            "small-q5_1" to ModelInfo("ggml-small-q5_1.bin", 190_000_000L),
        )
    }

    private val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

    fun getModelPath(modelKey: String): String? {
        val info = MODELS[modelKey] ?: return null
        val file = File(modelsDir, info.fileName)
        return if (file.exists() && file.length() == info.expectedSize) file.absolutePath else null
    }

    fun downloadUrl(modelKey: String): String? {
        val info = MODELS[modelKey] ?: return null
        return "$BASE_URL/${info.fileName}"
    }

    data class ModelInfo(val fileName: String, val expectedSize: Long)
}
```

### Transcribing State Extension
```kotlin
// DictationState.kt -- add Transcribing variant
sealed class DictationState {
    data object Idle : DictationState()
    data class Recording(
        val elapsedMs: Long = 0L,
        val energy: List<Float> = emptyList(),
    ) : DictationState()
    data object Transcribing : DictationState()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| whisper_init() with custom model_loader | whisper_init_from_file_with_params() | whisper.cpp 1.5+ | Simpler initialization from file path; use this for internal storage models |
| whisper_init_no_state() | whisper_init_from_file_with_params() with default params | whisper.cpp 1.6+ | Unified API; default params handle most cases |
| Manual quantization only | Pre-built quantized models on HuggingFace | 2024 | q5_1, q8_0 available pre-built; no need to quantize locally |
| NDK r21 | NDK r25+ (25.2.9519653) | 2024 | Better ARM optimizations, C++17 support required by ggml |

**Deprecated/outdated:**
- `whisper_init()` with custom `whisper_model_loader`: Still works but `whisper_init_from_file_with_params()` is simpler for file-based loading
- The `initContextFromAsset()` JNI path: Works but slower than file I/O; not recommended for production

## Open Questions

1. **Exact model file name: q5_0 vs q5_1**
   - What we know: HuggingFace `ggerganov/whisper.cpp` has `ggml-small-q5_1.bin` (190 MB) pre-built. No `q5_0` variant is pre-built.
   - What's unclear: Whether the CONTEXT.md "small-q5_0" refers to a self-quantized model or should be q5_1.
   - Recommendation: Use `ggml-small-q5_1.bin` (q5_1). Performance difference between q5_0 and q5_1 is negligible; q5_1 is pre-built and readily available. The 8-second target should still be achievable.

2. **Pixel 4 thread count optimization**
   - What we know: Pixel 4 has Snapdragon 855 = 4 high-perf Cortex-A76 cores + 4 efficiency Cortex-A55 cores. The `WhisperCpuConfig.preferredThreadCount` from the reference detects high-perf cores via `/proc/cpuinfo` frequency analysis.
   - What's unclear: Whether 4 threads (high-perf only) or 6 threads performs better for whisper.cpp on this specific SoC.
   - Recommendation: Use `WhisperCpuConfig.preferredThreadCount` (should auto-detect 4 high-perf cores). Benchmark during implementation; log thread count and inference duration.

3. **Whether fp16 NEON variant loads correctly on Pixel 4**
   - What we know: The reference builds 3 variants: `whisper` (default), `whisper_v8fp16_va` (ARM v8.2a fp16), `whisper_vfpv4` (ARMv7 NEON). Pixel 4's Cortex-A76 supports ARMv8.2-A with fp16.
   - What's unclear: Whether the runtime CPU feature detection in `WhisperLib` init block correctly detects fp16 on Pixel 4.
   - Recommendation: Include all 3 build variants. The runtime detection reads `/proc/cpuinfo` for "fphp" flag. Log which variant loads. If fp16 variant loads, inference should be faster.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.14.1 (already configured) |
| Config file | `app/build.gradle.kts` testOptions, existing test structure |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.whisper.*"` |
| Full suite command | `./gradlew testAll` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DICT-03 | WhisperContext transcribes audio to text | integration (device) | Manual: requires native lib + model on device | No - Wave 0 |
| DICT-03 | TextPostProcessor applies punctuation rules | unit | `./gradlew :core:testDebugUnitTest --tests "*.TextPostProcessorTest"` | No - Wave 0 |
| DICT-03 | ModelManager returns correct paths and URLs | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelManagerTest"` | No - Wave 0 |
| DICT-04 | DictationState transitions Idle->Recording->Transcribing->Idle | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest"` | Partial (existing test covers Idle/Recording) |
| DICT-04 | Text inserted via InputConnection.commitText after transcription | integration (device) | Manual: requires running IME | No - Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest`
- **Per wave merge:** `./gradlew testAll`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `core/src/test/.../whisper/TextPostProcessorTest.kt` -- covers DICT-03 post-processing
- [ ] `app/src/test/.../model/ModelManagerTest.kt` -- covers DICT-03 model path/URL logic
- [ ] `app/src/test/.../service/DictationStateTest.kt` -- extend existing test for Transcribing state (DICT-04)
- [ ] `whisper/` module build verification -- CMake compiles without errors (`./gradlew :whisper:assembleDebug`)

## Sources

### Primary (HIGH confidence)
- [whisper.cpp official repository](https://github.com/ggml-org/whisper.cpp) - Android example in `examples/whisper.android/`, JNI bridge, CMake config, Kotlin wrapper, CPU config
- [HuggingFace ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp/tree/main) - Pre-built GGML model files, sizes, download URLs
- Existing codebase: `DictationService.kt`, `DictusImeService.kt`, `DictationState.kt`, `DictationController.kt`, `AudioCaptureManager.kt`
- iOS reference: `TranscriptionService.swift`, `DictationCoordinator.swift:368-372`

### Secondary (MEDIUM confidence)
- [Android NDK CMake guide](https://developer.android.com/ndk/guides/cmake) - NDK build configuration
- [whisper.cpp Discussion #549](https://github.com/ggml-org/whisper.cpp/discussions/549) - Multilingual support in Android demo
- [whisper.cpp models README](https://github.com/ggml-org/whisper.cpp/blob/master/models/README.md) - Model sizes, quantization types

### Tertiary (LOW confidence)
- [atomglitch whisper Android example](https://www.atomglitch.com/whisper-android-example/) - Performance benchmark (4.3s for 11s audio with tiny on Amlogic A311D2) -- different hardware, not directly comparable to Pixel 4

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - whisper.cpp reference Android example is well-documented and actively maintained; JNI bridge code verified directly from repository
- Architecture: HIGH - Pattern follows reference implementation closely; DictationService/IME pipeline already established in Phase 2
- Pitfalls: HIGH - Build issues are well-documented in whisper.cpp GitHub issues; JNI naming is a known pain point; model availability verified on HuggingFace
- Performance target (8s on Pixel 4): MEDIUM - No direct Pixel 4 benchmarks found; tiny model should be well under target; small-q5_1 needs on-device validation

**Research date:** 2026-03-25
**Valid until:** 2026-04-25 (whisper.cpp is actively developed but Android example pattern is stable)
