# Architecture Research

**Domain:** Android IME — v1.1 feature integration into existing 4-module Kotlin codebase
**Researched:** 2026-03-30
**Confidence:** HIGH (analysis based on actual source code + stack research)

> This file supersedes the v1.0 ARCHITECTURE.md (2026-03-21).
> It focuses on how v1.1 features integrate with existing architecture.
> Existing patterns (Bound Service IPC, Hilt EntryPointAccessors, DataStore,
> DictationController interface) are referenced as fixed constraints, not reopened.

---

## Existing Architecture Snapshot (v1.0 Constraints)

Before addressing integration questions, the key v1.0 constraints that shape every v1.1 decision:

```
┌──────────────────────────────────────────────────────────────────┐
│  app/                                                             │
│  ┌──────────────────┐   LocalBinder   ┌───────────────────────┐  │
│  │  DictationService│◄───────────────►│  DictusImeService     │  │
│  │  (ForegroundSvc) │                 │  (InputMethodService) │  │
│  │                  │                 │                       │  │
│  │  transcriptionEngine               │  suggestionEngine     │  │
│  │  = WhisperTranscriptionEngine      │  = StubSuggestionEngine│  │
│  └────────┬─────────┘                 └───────────┬───────────┘  │
│           │                                       │              │
├───────────┼───────────────────────────────────────┼──────────────┤
│  core/    │                                       │              │
│  ┌────────▼─────────┐   ┌──────────────────────┐  │              │
│  │DictationController│  │PreferenceKeys (DS)    │  │              │
│  │DictationState    │   │DictationState        │  │              │
│  └──────────────────┘   └──────────────────────┘  │              │
│                          ┌──────────────────────┐  │              │
│                          │SuggestionEngine iface │◄─┘              │
│                          └──────────────────────┘                │
├──────────────────────────────────────────────────────────────────┤
│  whisper/                                                         │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  WhisperContext (JNI) — CMake/NDK, libwhisper.so            │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

Key fixed constraints:
- `SuggestionEngine` interface lives in `ime/` (not `core/`) — IME-only domain
- `DictationService` constructs `WhisperTranscriptionEngine` directly as a private field
- `ModelCatalog` and `ModelManager` live in `app/` — app-level, not shared
- `TranscriptionEngine` interface already exists in `core/whisper/` — ready to extend
- `DictusImeService` uses `EntryPointAccessors` (not `@AndroidEntryPoint`) — pattern is locked

---

## Q1: BinaryDictRanker — Where Does It Live?

### Answer: `ime/` module, same package as `SuggestionEngine`

**Rationale:**

`SuggestionEngine` is already declared in `ime/suggestion/SuggestionEngine.kt`. It belongs in `ime/` because:

1. Only `DictusImeService` consumes suggestions — no other module uses them
2. `DictationService` in `app/` produces transcription text, not suggestions
3. Moving it to `core/` would violate the principle that `core/` holds shared contracts only. Suggestions are IME-internal.

`BinaryDictRanker` implements `SuggestionEngine`, so it belongs in the same package:

```
ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/
├── SuggestionEngine.kt          ← EXISTING (interface + StubSuggestionEngine)
├── BinaryDictRanker.kt          ← NEW — implements SuggestionEngine
└── DictWorkerThread.kt          ← NEW — single-threaded executor (see Pitfall 11)
```

**Dictionary assets** go in `ime/src/main/assets/dicts/`:

```
ime/src/main/assets/dicts/
├── fr_FR.dict    ← Apache 2.0, from Helium314/aosp-dictionaries
└── en_US.dict    ← Apache 2.0, from Helium314/aosp-dictionaries
```

Placing assets in `ime/` ensures they are packaged only in builds that include the IME module, not duplicated into the `app/` or `core/` APK contributions.

### Wiring into SuggestionEngine

No changes to the `SuggestionEngine` interface — it is already the correct contract:

```kotlin
interface SuggestionEngine {
    fun getSuggestions(input: String, maxResults: Int = 3): List<String>
}
```

The only change is in `DictusImeService`: swap `StubSuggestionEngine()` for `BinaryDictRanker(context)`.

**Manual construction (recommended for v1.1 — no additional Hilt boilerplate):**
```kotlin
// DictusImeService.kt — in onCreate() or onCreateInputView()
private val suggestionEngine: SuggestionEngine by lazy {
    BinaryDictRanker(applicationContext)
}
```

### Threading Model (mandatory — addresses Pitfall 11)

`BinaryDictRanker.getSuggestions()` must never be called on the main thread or on `Dispatchers.Default`. Use a dedicated single-threaded executor:

```kotlin
// DictWorkerThread.kt in ime/suggestion/
object DictWorkerThread {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dict-worker")
    }
}

// In DictusImeService — suggestion invocation pattern
DictWorkerThread.executor.submit {
    val results = suggestionEngine.getSuggestions(currentWord, maxResults = 3)
    Handler(Looper.getMainLooper()).post { updateSuggestionBar(results) }
}
```

Verify the thread is running during development:
```bash
adb shell ps -T | grep dict-worker
# Must appear during active typing. Absence means lookup is on the wrong thread.
```

---

## Q2: New `asr/` Module — DictationService Interaction and IPC Changes

### Answer: `asr/` is a provider implementation only — DictationService IPC does NOT change

**Module role:**

`asr/` contains exactly one class: `ParakeetProvider`, which implements the `SttProvider` interface (see Q3). It has no awareness of `DictationService`, `DictusImeService`, or IPC. It is a pure inference module.

```
asr/
├── build.gradle.kts
├── libs/
│   └── sherpa-onnx-1.12.34.aar    ← local AAR, not on Maven Central
└── src/main/java/dev/pivisolutions/dictus/asr/
    └── ParakeetProvider.kt        ← implements SttProvider (from core/)
```

**Does the Bound Service IPC need changes for multi-provider?**

No. The `DictationController` interface and `LocalBinder` pattern are unchanged. The IME still calls `confirmAndTranscribe()` on `DictationService` through the same binder. Provider selection is internal to `DictationService` — the IME does not know which STT engine is active.

The only change in `DictationService` is replacing the hardcoded `WhisperTranscriptionEngine` field with a provider-selected engine chosen at transcription time:

```kotlin
// BEFORE (v1.0)
private val transcriptionEngine = WhisperTranscriptionEngine()

// AFTER (v1.1) — lazily selected per transcription call
private var activeEngine: SttProvider? = null
```

The `LocalBinder`, `startRecording()`, `stopRecording()`, `cancelRecording()`, and `confirmAndTranscribe()` signatures are all unchanged. No IPC protocol changes, no AIDL, no new broadcast.

**Module dependency graph for `asr/`:**

```
asr/ → core/    (for SttProvider interface)
app/ → asr/     (DictationService instantiates ParakeetProvider)
app/ → whisper/ (DictationService instantiates WhisperProvider)
```

`ime/` does NOT depend on `asr/`. `asr/` does NOT depend on `app/` or `ime/`. These constraints prevent circular dependencies.

---

## Q3: SttProvider/SttEngine Interface — Module Graph and Selection Flow

### Interface Placement: `core/` module

`SttProvider` must live in `core/` because:
- `DictationService` (in `app/`) needs to call it — `app/` depends on `core/`
- `whisper/` needs to implement it — `whisper/` depends on `core/`
- `asr/` needs to implement it — `asr/` depends on `core/`

```kotlin
// NEW: core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt
interface SttProvider {
    val id: String                    // "whisper" | "parakeet"
    val displayName: String           // shown in Settings UI
    suspend fun initialize(modelPath: String): Boolean
    suspend fun transcribe(samples: FloatArray, language: String): String
    fun isReady(): Boolean
    suspend fun release()
}
```

Note on `TranscriptionEngine`: This interface already exists in `core/whisper/`. For v1.1, `WhisperProvider` should implement `SttProvider` (not `TranscriptionEngine`), and `WhisperTranscriptionEngine` becomes a private implementation detail of `WhisperProvider`. The `TranscriptionEngine` interface can be kept for existing tests or removed if tests are updated to use `SttProvider`.

### Provider Selection Flow

```
SettingsScreen (app/ui/settings/)
    │  user selects "Parakeet" or "Whisper"
    ▼
SettingsViewModel.setActiveProvider("parakeet")
    │  writes to DataStore
    ▼
DataStore<Preferences>
    key: PreferenceKeys.ACTIVE_STT_PROVIDER  ← NEW key in core/PreferenceKeys.kt
    value: "whisper" | "parakeet"
    │  read at transcription time
    ▼
DictationService.confirmAndTranscribe()
    │  reads ACTIVE_STT_PROVIDER from DataStore
    │  reads ACTIVE_MODEL from DataStore
    │  calls getOrInitEngine(providerKey, modelPath)
    │    → mutual exclusivity enforced (release old, load new)
    │    → memory check before Parakeet load
    ▼
SttProvider.transcribe(samples, language)
    ▼
TextPostProcessor.process(rawText)  ← unchanged
    ▼
return processedText to DictusImeService via confirmAndTranscribe()
```

**New DataStore key** (add to `core/PreferenceKeys.kt`):
```kotlin
val ACTIVE_STT_PROVIDER = stringPreferencesKey("active_stt_provider")  // default: "whisper"
```

**Engine selection in DictationService:**

```kotlin
private var activeEngine: SttProvider? = null

private suspend fun getOrInitEngine(providerKey: String, modelPath: String): SttProvider? {
    val current = activeEngine
    if (current != null && current.id == providerKey && current.isReady()) return current

    // Mutual exclusivity: release previous engine before loading new one
    current?.release()
    activeEngine = null

    // Memory guard before loading Parakeet (addresses Pitfall 14)
    if (providerKey == "parakeet") {
        val am = getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        if (memInfo.availMem < 1_500_000_000L) {
            Timber.w("Insufficient memory for Parakeet: %d MB free", memInfo.availMem / 1_000_000)
            return null  // caller handles null → show user-facing error
        }
    }

    val engine: SttProvider = when (providerKey) {
        "parakeet" -> ParakeetProvider()
        else -> WhisperProvider()  // "whisper" and unknown values fall back to Whisper
    }
    val initialized = engine.initialize(modelPath)
    if (!initialized) return null
    activeEngine = engine
    return engine
}
```

**WhisperProvider** is a thin wrapper in `app/service/` (or `whisper/`) that delegates to the existing `WhisperContext` JNI layer. No changes to the JNI layer or `WhisperContext`:

```kotlin
// app/src/main/java/.../service/WhisperProvider.kt (NEW — thin adapter)
class WhisperProvider : SttProvider {
    override val id = "whisper"
    override val displayName = "Whisper (whisper.cpp)"
    private val engine = WhisperTranscriptionEngine()
    override suspend fun initialize(modelPath: String) = engine.initialize(modelPath)
    override suspend fun transcribe(samples: FloatArray, language: String) =
        engine.transcribe(samples, language)
    override fun isReady() = engine.isReady
    override suspend fun release() = engine.release()
}
```

---

## Q4: ModelCatalog Extension for ONNX Models

### Answer: Extend `ModelInfo` in-place — one catalog, no second class

**Current issue:** GGML models are single `.bin` files. ONNX/Parakeet models are `.tar.bz2` archives that extract to a directory containing multiple files (encoder ONNX, tokens, config). The current `ModelInfo.fileName` + `ModelManager.getModelPath()` pattern assumes a single file.

#### Change 1: Extend `ModelInfo` with ONNX-specific fields

```kotlin
// MODIFIED: app/src/main/java/.../model/ModelManager.kt
data class ModelInfo(
    val key: String,
    val fileName: String,           // GGML: "ggml-tiny.bin" | ONNX: archive "*.tar.bz2"
    val displayName: String,
    val expectedSizeBytes: Long,
    val qualityLabel: String,
    val description: String = "",
    val precision: Float = 0f,
    val speed: Float = 0f,
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
    // NEW: directory name after archive extraction (null for GGML single-file models)
    val extractedDirName: String? = null,
    // NEW: language constraint (Parakeet 110M is English-only)
    val supportedLanguages: List<String> = listOf("fr", "en", "auto"),
)
```

#### Change 2: Fix `ModelCatalog.downloadUrl()` Parakeet stub

```kotlin
// MODIFIED: ModelCatalog object
private const val SHERPA_ONNX_RELEASE_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

fun downloadUrl(info: ModelInfo): String = when (info.provider) {
    AiProvider.WHISPER -> "$WHISPER_BASE_URL/${info.fileName}"
    AiProvider.PARAKEET -> "$SHERPA_ONNX_RELEASE_URL/${info.fileName}"
}
```

#### Change 3: Add Parakeet model entry to `ModelCatalog.ALL`

```kotlin
ModelInfo(
    key = "parakeet-110m",
    fileName = "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2",
    displayName = "Parakeet 110M",
    expectedSizeBytes = 132_000_000L,  // ~126 MB archive
    qualityLabel = "Rapide",
    description = "Nvidia Parakeet — anglais uniquement",
    precision = 0.75f,
    speed = 0.8f,
    provider = AiProvider.PARAKEET,
    extractedDirName = "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
    supportedLanguages = listOf("en"),  // English only — critical for UI warning
)
```

#### Change 4: Extend `ModelManager.getModelPath()` for ONNX directories

```kotlin
fun getModelPath(modelKey: String): String? {
    val info = ModelCatalog.findByKey(modelKey) ?: return null
    return if (info.extractedDirName != null) {
        // ONNX: verify extracted directory exists with the encoder file
        val dir = File(modelsDir, info.extractedDirName)
        val encoderFile = File(dir, "model.int8.onnx")
        if (dir.isDirectory && encoderFile.exists()) dir.absolutePath else null
    } else {
        // GGML: existing single-file check (unchanged)
        val file = File(modelsDir, info.fileName)
        if (file.exists() && file.length() >= info.expectedSizeBytes * 95 / 100) {
            file.absolutePath
        } else null
    }
}
```

#### Change 5: ONNX archive extraction in ModelDownloader

`ModelDownloader` needs a new branch for `.tar.bz2` archives. Add Apache Commons Compress (Apache 2.0, compatible with Dictus MIT license):

```kotlin
// app/build.gradle.kts
implementation("org.apache.commons:commons-compress:1.26.2")
```

After download, extract the archive to `modelsDir/`:
```kotlin
// ModelDownloader — pseudocode for ONNX extraction branch
if (info.extractedDirName != null) {
    val archiveFile = File(modelsDir, info.fileName)
    BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bz2 ->
        TarArchiveInputStream(bz2).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(modelsDir, entry.name)
                if (entry.isDirectory) outFile.mkdirs()
                else outFile.outputStream().use { tar.copyTo(it) }
                entry = tar.nextEntry
            }
        }
    }
    archiveFile.delete()  // remove archive after extraction to reclaim space
}
```

#### Change 6: Language guard in Settings UI

When the user selects a provider/model that only supports specific languages, `SettingsViewModel` must warn:

```kotlin
// SettingsViewModel — add provider+language validation
fun setActiveModel(modelKey: String) {
    val model = ModelCatalog.findByKey(modelKey) ?: return
    val currentLanguage = /* read from DataStore */
    if (currentLanguage != "auto" && currentLanguage !in model.supportedLanguages) {
        // Emit a UI event: "Parakeet requires English. Switch language to English or choose Whisper."
    }
    // proceed with write to DataStore
}
```

---

## Q5: Suggested Build Order

Build order respects the dependency graph: `core/` changes first, then provider implementations, then integration in `app/`, then UI surface.

### Phase 1: Foundation — `core/` interface and DataStore key

**What:** Add `SttProvider` interface in `core/stt/`. Add `ACTIVE_STT_PROVIDER` key to `PreferenceKeys`.
**Why first:** Both `whisper/` and `asr/` implement this interface. Both `DictationService` and `SettingsViewModel` depend on this key. No downstream work can proceed without it.
**Modified files:**
- `core/src/main/java/.../core/stt/SttProvider.kt` — NEW
- `core/src/main/java/.../core/preferences/PreferenceKeys.kt` — MODIFIED (add key)
**Risk:** Low. Pure interface addition, no behavior change.

### Phase 2: Text Prediction — `ime/` module

**What:** `BinaryDictRanker` implementing `SuggestionEngine`. FR/EN `.dict` assets in `ime/assets/dicts/`. `DictWorkerThread` for off-main-thread calls. Swap `StubSuggestionEngine` in `DictusImeService`.
**Why second:** Fully independent of Parakeet. No libc++ risk. Validates the `SuggestionEngine` interface remains correct before adding a second STT provider. Delivers user-visible improvement immediately.
**Modified/added files:**
- `ime/src/main/java/.../ime/suggestion/BinaryDictRanker.kt` — NEW
- `ime/src/main/java/.../ime/suggestion/DictWorkerThread.kt` — NEW
- `ime/src/main/java/.../ime/DictusImeService.kt` — MODIFIED (swap engine)
- `ime/src/main/assets/dicts/fr_FR.dict` — NEW static asset
- `ime/src/main/assets/dicts/en_US.dict` — NEW static asset
**Risk:** Medium. Validate threading model with a microbenchmark (1000 sequential lookups) before IME integration. Confirm `dict-worker` thread appears in `adb shell ps` during typing.

### Phase 3: WhisperProvider adapter — `app/` or `whisper/`

**What:** Wrap `WhisperTranscriptionEngine` in `WhisperProvider : SttProvider`. Update `DictationService` to use `WhisperProvider` via the `SttProvider` interface instead of the hardcoded concrete type.
**Why third:** Must validate the existing Whisper pipeline is functionally identical after the refactor — before adding Parakeet. This is a pure refactor with no feature change. Regressions are detectable immediately with existing tests.
**Modified/added files:**
- `app/src/main/java/.../service/WhisperProvider.kt` — NEW (thin adapter)
- `app/src/main/java/.../service/DictationService.kt` — MODIFIED (use `SttProvider`, add `getOrInitEngine()`)
**Risk:** Low-medium. Run a full transcription integration test on Pixel 4 device before proceeding. Regression in transcription = blocked Phase 5.

### Phase 4: ModelCatalog extension — `app/`

**What:** Extend `ModelInfo` with `extractedDirName` and `supportedLanguages`. Add Parakeet entry to `ModelCatalog.ALL`. Fix `downloadUrl()` stub. Extend `ModelManager.getModelPath()` for directory-based ONNX models. Add `.tar.bz2` extraction logic to `ModelDownloader`. Add `commons-compress` dependency.
**Why fourth:** Validates the download/storage plumbing for ONNX independently before connecting inference. The model manager test can verify correct directory detection without sherpa-onnx being present.
**Modified files:**
- `app/src/main/java/.../model/ModelManager.kt` — MODIFIED (`ModelInfo`, `ModelCatalog`, `ModelManager`)
- `app/src/main/java/.../service/ModelDownloader.kt` — MODIFIED (extraction branch)
- `app/build.gradle.kts` — MODIFIED (add `commons-compress`)
**Risk:** Medium. Tar/bzip2 extraction is new code. Test with the actual Parakeet archive before marking done. Verify the extracted directory structure matches `getModelPath()` expectations.

### Phase 5: Parakeet/ONNX — new `asr/` module

**What:** Create `asr/` Gradle module. Add sherpa-onnx AAR to `asr/libs/`. Implement `ParakeetProvider : SttProvider`. Wire into `DictationService.getOrInitEngine()`. Resolve libc++ symbol conflict (whisper.cpp `c++_static` + symbol visibility).
**Why fifth:** Most risky phase. Depends on all previous phases. The libc++ conflict (Pitfall 12) must be verified before any ONNX inference call is executed in-process.
**Added/modified files:**
- `asr/build.gradle.kts` — NEW
- `asr/libs/sherpa-onnx-1.12.34.aar` — NEW
- `asr/src/main/java/.../asr/ParakeetProvider.kt` — NEW
- `settings.gradle.kts` — MODIFIED (include `:asr`)
- `app/build.gradle.kts` — MODIFIED (add `implementation(project(":asr"))`)
- `app/src/main/java/.../service/DictationService.kt` — MODIFIED (add `"parakeet"` branch + memory guard)
- `whisper/CMakeLists.txt` — MODIFIED (change `ANDROID_STL` to `c++_static`, add symbol visibility flags)
**Risk:** HIGH. Plan a dedicated validation session: load both engines sequentially in the same process, run 20 inference iterations each, verify no crash and no OOM on Pixel 4. Use `adb shell dumpsys meminfo` to monitor memory before/after each load/unload.

**libc++ conflict validation command:**
```bash
readelf -d build/intermediates/merged_native_libs/debug/out/lib/arm64-v8a/libwhisper.so \
    | grep NEEDED
# After fix: must NOT list libc++_shared.so
```

### Phase 6: Settings UI — provider picker and language guard

**What:** Add STT provider picker to `SettingsScreen`. Enforce language warning when Parakeet is selected with a non-English transcription language. Show memory guard error toast when `getOrInitEngine()` returns null due to low memory.
**Why sixth:** UI should come after the backend is validated. Do not ship the provider picker before Phase 5 is confirmed stable on device.
**Modified files:**
- `app/src/main/java/.../ui/settings/SettingsScreen.kt` — MODIFIED
- `app/src/main/java/.../ui/settings/SettingsViewModel.kt` — MODIFIED
**Risk:** Low. UI change only.

### Phase 7: Tech debt, CI/CD, OSS prep

**What:** Fix settings AZERTY/QWERTY toggle propagation. Add `POST_NOTIFICATIONS` to manifest. Fix 4 stale test assertions. GitHub Actions CI + release workflow. OSS repo files (CONTRIBUTING, CODE_OF_CONDUCT, issue templates, PR template, README). License audit (`gradle-license-plugin`). Signing key setup.
**Why last:** Does not block any feature. Can be parallelized with Phase 6 if bandwidth allows.
**Risk:** Low for most items. Signing key setup (Pitfall 15) must happen before the first signed release — irreversible once users install.

---

## System Overview: v1.1 Target Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  app/                                                                 │
│  ┌────────────────────┐   LocalBinder   ┌──────────────────────────┐ │
│  │  DictationService  │◄───────────────►│  DictusImeService        │ │
│  │                    │                 │  (InputMethodService)    │ │
│  │  getOrInitEngine() │                 │                          │ │
│  │  ┌──────────────┐  │                 │  BinaryDictRanker        │ │
│  │  │WhisperProvider│  │                 │  (SuggestionEngine impl) │ │
│  │  └──────────────┘  │                 └──────────────────────────┘ │
│  │  ┌──────────────┐  │                                               │
│  │  │ParakeetProvider│ │  ← mutual exclusivity enforced               │
│  │  └──────────────┘  │                                               │
│  └────────┬───────────┘                                               │
├───────────┼───────────────────────────────────────────────────────────┤
│  core/    │                                                            │
│  ┌────────▼──────────┐  ┌─────────────────┐  ┌────────────────────┐  │
│  │DictationController│  │SttProvider iface│  │PreferenceKeys      │  │
│  │DictationState     │  │(NEW)            │  │+ACTIVE_STT_PROVIDER│  │
│  └───────────────────┘  └─────────────────┘  └────────────────────┘  │
├────────────────────────────────────────────────────────────────────── │
│  whisper/                         asr/ (NEW)                          │
│  ┌──────────────────────┐         ┌──────────────────────────────┐    │
│  │WhisperContext (JNI)  │         │sherpa-onnx AAR               │    │
│  │(libwhisper.so —      │         │(libonnxruntime.so —          │    │
│  │ c++_static after fix)│         │ bundled, no extra CMake)     │    │
│  │WhisperProvider       │         │ParakeetProvider              │    │
│  │(implements SttProvider)│       │(implements SttProvider)      │    │
│  └──────────────────────┘         └──────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────── ┘
```

---

## Component Responsibilities

| Component | Module | v1.1 Status | Responsibility |
|-----------|--------|-------------|---------------|
| `SttProvider` | `core/` | NEW interface | Contract for all STT engines: initialize, transcribe, release |
| `PreferenceKeys.ACTIVE_STT_PROVIDER` | `core/` | NEW key | Persists provider selection ("whisper" / "parakeet") |
| `WhisperProvider` | `app/` | NEW adapter | Wraps WhisperTranscriptionEngine as SttProvider |
| `ParakeetProvider` | `asr/` (NEW module) | NEW | sherpa-onnx inference, implements SttProvider |
| `DictationService` | `app/` | MODIFIED | Provider selection, mutual exclusivity, memory guard |
| `ModelInfo.extractedDirName` | `app/` | NEW field | Enables directory-based ONNX model storage |
| `ModelInfo.supportedLanguages` | `app/` | NEW field | Language constraint for UI guard (Parakeet = EN only) |
| `ModelCatalog.downloadUrl()` | `app/` | MODIFIED | Adds Parakeet ONNX download URL (removes error() stub) |
| `ModelCatalog.ALL` | `app/` | MODIFIED | Adds parakeet-110m entry |
| `ModelManager.getModelPath()` | `app/` | MODIFIED | Returns directory path for ONNX models |
| `ModelDownloader` | `app/` | MODIFIED | Adds tar.bz2 extraction for ONNX archives |
| `BinaryDictRanker` | `ime/` | NEW | SuggestionEngine impl using AOSP binary .dict files |
| `DictWorkerThread` | `ime/` | NEW | Single-threaded executor for dict lookups |
| `StubSuggestionEngine` | `ime/` | REPLACED | Keep in test sources only; BinaryDictRanker replaces it in production |
| `SettingsScreen` | `app/` | MODIFIED | Provider picker UI, language warning |
| `SettingsViewModel` | `app/` | MODIFIED | Writes ACTIVE_STT_PROVIDER, enforces language guard |

---

## Data Flows

### Flow 1: Provider Selection

```
User taps "Parakeet" in SettingsScreen
    ↓
SettingsViewModel.setActiveModel("parakeet-110m")
    │ validates language constraint → warns if TRANSCRIPTION_LANGUAGE != "en"
    ↓ DataStore.edit { prefs[ACTIVE_MODEL] = "parakeet-110m" }
    ↓ DataStore.edit { prefs[ACTIVE_STT_PROVIDER] = "parakeet" }

[Later — user presses mic button in IME]
    ↓
DictusImeService → (LocalBinder) → DictationService.confirmAndTranscribe()
    ↓ reads ACTIVE_STT_PROVIDER = "parakeet", ACTIVE_MODEL = "parakeet-110m"
    ↓ modelPath = ModelManager.getModelPath("parakeet-110m")  → directory path
    ↓ getOrInitEngine("parakeet", modelPath)
      ├─ current engine released (whisper_free() if Whisper was loaded)
      ├─ ActivityManager memory check → abort if <1.5 GB free
      └─ ParakeetProvider().initialize(modelPath)
    ↓
ParakeetProvider.transcribe(samples, "en")   ← sherpa-onnx inference
    ↓
TextPostProcessor.process(rawText)
    ↓
DictusImeService.commitText(processedText) via confirmAndTranscribe() return value
```

### Flow 2: Text Suggestions

```
User completes dictation OR types a key
    ↓
DictusImeService detects partial word (currentWord)
    ↓
DictWorkerThread.executor.submit {
    results = binaryDictRanker.getSuggestions(currentWord, maxResults = 3)
    Handler(Looper.getMainLooper()).post { updateSuggestionBar(results) }
}
    ↓
SuggestionBar (Compose) shows up to 3 candidates
    ↓
User taps suggestion → DictusImeService.commitText(word)
```

---

## Anti-Patterns

### Anti-Pattern 1: Eager-load Both STT Engines on Service Start

**What people do:** Instantiate both `WhisperProvider` and `ParakeetProvider` in `DictationService.onCreate()` to eliminate initialization latency at transcription time.
**Why it's wrong:** ~1.7 GB combined ML memory (126 MB Parakeet + ~500 MB Whisper + working memory spikes) on a device where Android reserves ~2.5 GB for the OS. The low-memory killer terminates the process silently mid-recording. No crash log — only `adb shell cat /proc/last_kmsg` reveals an OOM kill.
**Do this instead:** Lazy initialization with mutual exclusivity. Load the selected engine on first `confirmAndTranscribe()` call, release the other. Show a `DictationState.Initializing` state in the IME during the ~1–3s Parakeet load time.

### Anti-Pattern 2: Calling BinaryDictRanker on Dispatchers.Default

**What people do:** Launch a coroutine on `Dispatchers.Default` to call `getSuggestions()`, reasoning that it's "off the main thread so it's fine."
**Why it's wrong:** `Dispatchers.Default` is a shared thread pool. Parallel dict lookups from rapid typing compete for pool threads, causing latency spikes. More critically, native dictionary operations (if the ranker ever calls JNI in a future extension) must run on a stable thread, not a pool thread that may be assigned and preempted unpredictably.
**Do this instead:** Dedicated `ExecutorService` with a single named thread (`dict-worker`). Serialized access, named for debuggability, immune to pool saturation.

### Anti-Pattern 3: Second ModelCatalog for Parakeet

**What people do:** Create a `ParakeetModelCatalog` class in the `asr/` module, separate from `ModelCatalog` in `app/`.
**Why it's wrong:** The model picker UI, `ModelManager`, download progress tracking, and the `canDelete` guard all reference `ModelCatalog.ALL` as the single source of truth. A second catalog breaks the picker, creates storage accounting gaps, and means the delete guard cannot protect Parakeet models.
**Do this instead:** Add the Parakeet `ModelInfo` entry directly to `ModelCatalog.ALL`. Extend `ModelInfo` with the fields needed for ONNX. One catalog to rule them all.

### Anti-Pattern 4: Moving SuggestionEngine to `core/`

**What people do:** Move `SuggestionEngine` to `core/` to "make it available to other modules" or for perceived cleanliness.
**Why it's wrong:** The only consumer is `DictusImeService`. `DictationService` does not use suggestions. `ModelManager` does not use suggestions. A `core/` interface with a single consumer is a misplaced abstraction that adds coupling without benefit. `core/` is for contracts shared between ≥2 modules.
**Do this instead:** Keep `SuggestionEngine` in `ime/`. Move it to `core/` only if a non-IME component requires it.

### Anti-Pattern 5: Skipping libc++ Conflict Validation Before Parakeet UI Integration

**What people do:** Implement `ParakeetProvider`, wire it into `DictationService`, build the UI picker, and test end-to-end — only to discover the process crashes on first Parakeet inference call, which is now tangled with UI code.
**Why it's wrong:** The libc++ conflict (Pitfall 12) manifests only at runtime, only when both `.so` files are loaded, and only on some devices. Debugging it after UI integration means untangling crash logs from multiple moving parts.
**Do this instead:** Before connecting `ParakeetProvider` to `DictationService`, write a standalone test: load `WhisperContext` via JNI, then load sherpa-onnx via `ParakeetProvider`, run 20 inference iterations each, verify no crash. Fix the libc++ linkage issue in isolation before any UI work.

---

## Integration Points

### Module Dependency Graph (v1.1 Complete)

| Dependency | Direction | v1.1 Status | Notes |
|------------|-----------|-------------|-------|
| `app/` → `core/` | compile | Existing | Unchanged |
| `app/` → `whisper/` | compile | Existing | Unchanged |
| `app/` → `asr/` | compile | NEW | Add in `app/build.gradle.kts` |
| `whisper/` → `core/` | compile | EXTENDED | WhisperProvider implements SttProvider |
| `asr/` → `core/` | compile | NEW | ParakeetProvider implements SttProvider |
| `ime/` → `core/` | compile | Existing | DictationController, PreferenceKeys |
| `ime/` → `app/` | FORBIDDEN | — | Circular dependency. Never add. |
| `asr/` → `app/` | FORBIDDEN | — | Circular dependency. Never add. |
| `asr/` → `whisper/` | FORBIDDEN | — | Providers must not know each other. |

### Native Library Conflict Resolution (Pitfall 12)

| Library | Source | Current STL Linkage | Required Action |
|---------|--------|---------------------|----------------|
| `libwhisper.so` | `whisper/` CMake | `c++_shared` (current) | Change to `c++_static` + symbol visibility script |
| `libonnxruntime.so` | sherpa-onnx AAR | Internal/bundled | No action — verify NDK version matches |
| `libsherpa-onnx-jni.so` | sherpa-onnx AAR | Internal/bundled | Do NOT add `onnxruntime-android` as a separate Gradle dep |

CMake change required in `whisper/CMakeLists.txt`:
```cmake
# Change: target_link_libraries(whisper ... c++_shared)
# To:
set(CMAKE_ANDROID_STL_TYPE "c++_static")
target_link_options(whisper PRIVATE
    -Wl,--exclude-libs,libc++_static.a
    -Wl,--exclude-libs,libc++abi.a
)
```

---

## Sources

- Existing codebase (directly analyzed):
  - `app/src/main/java/.../service/DictationService.kt`
  - `app/src/main/java/.../service/WhisperTranscriptionEngine.kt`
  - `app/src/main/java/.../model/ModelManager.kt` (includes ModelCatalog, ModelInfo, AiProvider)
  - `ime/src/main/java/.../ime/suggestion/SuggestionEngine.kt`
  - `core/src/main/java/.../core/service/DictationController.kt`
  - `core/src/main/java/.../core/preferences/PreferenceKeys.kt`
  - `ime/src/main/java/.../ime/di/DictusImeEntryPoint.kt`
- `.planning/research/STACK.md` — sherpa-onnx AAR integration, BinaryDictRanker approach (MEDIUM-HIGH)
- `.planning/research/PITFALLS.md` — Pitfall 11 (threading), 12 (libc++ collision), 14 (OOM), 15 (signing key) (HIGH)
- [Android NDK C++ library support](https://developer.android.com/ndk/guides/cpp-support) — STL linkage requirements (HIGH)
- [Android NDK middleware vendor advice](https://developer.android.com/ndk/guides/middleware-vendors) — symbol collision mechanism (HIGH)
- [sherpa-onnx Android integration](https://k2-fsa.github.io/sherpa/onnx/android/index.html) — AAR usage pattern (MEDIUM)

---
*Architecture research for: Dictus Android v1.1 — integration of text prediction, Parakeet STT, and OSS prep into existing 4-module codebase*
*Researched: 2026-03-30*
