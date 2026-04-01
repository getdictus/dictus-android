# Phase 9: Parakeet Integration - Research

**Researched:** 2026-03-31
**Domain:** sherpa-onnx Android integration, NeMo CTC Parakeet models, NDK native lib coexistence
**Confidence:** MEDIUM — sherpa-onnx Kotlin API verified via official source; download URLs verified; AAR integration approach triangulated from official docs + confirmed by STATE.md prior decision (sherpa-onnx 1.12.34 local asr/libs/)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Provider follows the model automatically — no separate "STT Engine" setting in Settings
- User picks a model in the model manager; the app determines the provider from `ModelInfo.provider`
- Selecting a Parakeet model unloads the Whisper engine immediately (`release()` before `initialize()`) — no dual-engine overlap
- Home screen shows model name only (no provider badge) — the model manager already shows the badge
- All models (Whisper + Parakeet) appear in a single scrollable list with a small provider badge per card
- **Parakeet 110M CTC INT8** (~126 MB) — primary Parakeet model for v1.1
- **Parakeet 110M CTC FP16** (~220 MB) — full precision variant, slower on Pixel 4 but available
- **Parakeet 0.6B TDT v3** (~640 MB) — added to catalog with RAM guard, visible with warning "Necessite 8+ GB RAM"
- Strict single-engine policy: only one engine loaded at any time — release() then initialize()
- `libc++_shared.so` collision must be validated FIRST before any feature code
- Brief snackbar ("Chargement du modele...") during engine swap — non-blocking
- Persistent amber warning banner on Parakeet model cards: "Anglais uniquement — la transcription francaise n'est pas supportee"
- Confirmation dialog when activating a Parakeet model while transcription language is FR or auto
- Warning logic checks `PreferenceKeys.TRANSCRIPTION_LANGUAGE` (not app locale)
- No warning on recording/keyboard screen — model card warning + activation dialog are sufficient

### Claude's Discretion
- Download URL scheme (HuggingFace vs sherpa-onnx GitHub Releases)
- sherpa-onnx AAR integration approach (local libs/ vs Gradle dependency)
- ParakeetProvider implementation details (sherpa-onnx API usage)
- RAM check threshold and mechanism for 0.6B model guard
- Snackbar styling and duration
- NDK libc++_shared.so resolution strategy (pickFirst, repackaging, etc.)
- Exact precision/speed scores for Parakeet models in ModelInfo

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. (Parakeet 0.6B was added to Phase 9 catalog with guard, not deferred.)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| STT-02 | L'utilisateur peut telecharger et utiliser un modele Parakeet/Nvidia via sherpa-onnx | sherpa-onnx Kotlin API + NeMo CTC model download URL documented |
| STT-03 | L'utilisateur peut selectionner le fournisseur STT actif dans les parametres | Provider selection via ModelInfo.provider + DictationService dynamic dispatch |
| STT-04 | L'ecran modeles affiche des notes descriptives par fournisseur (Whisper, Parakeet) | ModelCard provider badge + description pattern; "WK" badge already exists, needs extension |
| STT-05 | L'utilisateur est averti des limitations linguistiques du modele selectionne | Warning banner + activation dialog pattern; TRANSCRIPTION_LANGUAGE check in ModelsViewModel |
</phase_requirements>

---

## Summary

Phase 9 wires sherpa-onnx (the Android/cross-platform ONNX-based speech toolkit from k2-fsa) as a second STT backend behind the existing `SttProvider` interface. The Parakeet models available are NVIDIA NeMo-exported CTC models, distributed as ONNX files from the sherpa-onnx GitHub Releases page. The INT8 model (`model.int8.onnx`, ~126 MB) is the feasible model for Pixel 4 class devices.

The biggest technical risk is the `libc++_shared.so` collision: whisper.cpp (compiled via NDK CMake in the `whisper/` module) and sherpa-onnx (pre-built `.so` files) both ship their own `libc++_shared.so`. This must be resolved via `packagingOptions.pickFirst` before any feature code compiles. A new Gradle module (`asr/`) is the cleanest home for the sherpa-onnx native libs and Kotlin wrapper, mirroring the `whisper/` module pattern.

The `DictationService.sttProvider` field (currently a hardcoded `val WhisperProvider()` at line 97) must become a dynamically-dispatched property that reads `ACTIVE_MODEL` from DataStore and instantiates the correct provider. The engine-swap cycle is: `currentProvider.release()` → snackbar → `newProvider.initialize(modelPath)`.

**Primary recommendation:** Create a new `asr/` Gradle module for sherpa-onnx integration, resolve `libc++_shared.so` with `pickFirst` in `app/build.gradle.kts`, then wire `ParakeetProvider` through `DictationService` using the exact same lifecycle pattern as `WhisperProvider`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| sherpa-onnx (native .so) | 1.12.34 | NeMo CTC offline ASR runtime | Only viable option: whisper.cpp uses GGML; Parakeet models are ONNX. sherpa-onnx is the k2-fsa reference implementation for Android |
| OfflineRecognizer (Kotlin API) | bundled in sherpa-onnx AAR/JNI | Kotlin facade over JNI for offline CTC recognition | Official Kotlin API in sherpa-onnx, same file ships with .so |
| ActivityManager.MemoryInfo | Android platform | RAM guard check for 0.6B model | Standard Android API, no dependency needed |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Snackbar (Material 3) | existing | Engine swap feedback | Already in project via compose.material3 |
| AlertDialog / ConfirmationDialog | existing Compose | Language mismatch activation dialog | Already in project patterns |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| sherpa-onnx pre-built .so | Build from source | Faster setup with pre-built; source build gives more control but requires NDK + CMake build time (~30 min) |
| GitHub Releases download URL | HuggingFace direct | GitHub Releases URLs are stable and versioned; HF direct requires resolving LFS/CDN. STATE.md decision: GitHub Releases |
| `pickFirst` for libc++_shared.so | Repackage AAR without libc++_shared.so | `pickFirst` is simpler; repackaging requires `zip` manipulation. pickFirst is the documented Android community solution |

**Installation (new asr/ module):**
```bash
# Download sherpa-onnx pre-built Android libs
# Unzip to asr/libs/arm64-v8a/ and asr/libs/armeabi-v7a/
# Files needed: libsherpa-onnx-jni.so, libonnxruntime.so, libc++_shared.so
# Also: sherpa-onnx Kotlin API source files (from sherpa-onnx/kotlin-api/)
```

---

## Architecture Patterns

### Recommended Project Structure
```
asr/
├── build.gradle.kts         # new module; depends on core/
├── libs/
│   ├── arm64-v8a/
│   │   ├── libsherpa-onnx-jni.so
│   │   ├── libonnxruntime.so
│   │   └── libc++_shared.so
│   └── armeabi-v7a/
│       ├── libsherpa-onnx-jni.so
│       ├── libonnxruntime.so
│       └── libc++_shared.so
└── src/main/java/dev/pivisolutions/dictus/asr/
    ├── ParakeetProvider.kt  # SttProvider impl using sherpa-onnx
    └── SherpaOnnxLoader.kt  # System.loadLibrary() bootstrap (if needed)

app/
├── build.gradle.kts         # add: implementation(project(":asr"))
│                            # add: packagingOptions.jniLibs.pickFirsts += "**/libc++_shared.so"
└── src/main/java/.../
    ├── service/
    │   └── DictationService.kt   # line 97: val sttProvider → dynamic dispatch
    └── model/
        └── ModelManager.kt       # ModelCatalog.ALL += 3 Parakeet entries
                                  # ModelCatalog.downloadUrl() PARAKEET branch → real URLs
```

### Pattern 1: Dynamic Provider Dispatch in DictationService
**What:** Replace the hardcoded `val sttProvider: SttProvider = WhisperProvider()` with a function that reads `ACTIVE_MODEL` from DataStore, looks up the `ModelInfo.provider`, and instantiates the correct provider.
**When to use:** On every `confirmAndTranscribe()` call — already reads `ACTIVE_MODEL` from DataStore at step 2.

```kotlin
// Source: DictationService.kt confirmAndTranscribe() pattern
// Current (line 97):
private val sttProvider: SttProvider = WhisperProvider()

// Phase 9 replacement — provider is decided per-transcription:
private var currentProvider: SttProvider? = null

private suspend fun getOrInitProvider(modelKey: String, modelPath: String): SttProvider? {
    val info = ModelCatalog.findByKey(modelKey) ?: return null
    val neededProvider = when (info.provider) {
        AiProvider.WHISPER -> WhisperProvider()
        AiProvider.PARAKEET -> ParakeetProvider()
    }
    // Strict single-engine policy: release current if provider type changed
    val current = currentProvider
    if (current != null && current::class != neededProvider::class) {
        current.release()
        currentProvider = null
        // TODO: show snackbar "Chargement du modele..." (non-blocking)
    }
    val active = currentProvider ?: neededProvider.also { currentProvider = it }
    if (!active.isReady) {
        val ok = active.initialize(modelPath)
        if (!ok) return null
    }
    return active
}
```

### Pattern 2: ParakeetProvider via sherpa-onnx OfflineRecognizer
**What:** Implement `SttProvider` using `OfflineRecognizer` with `OfflineNemoEncDecCtcModelConfig`.
**When to use:** When `ModelInfo.provider == AiProvider.PARAKEET`.

```kotlin
// Source: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt
class ParakeetProvider : SttProvider {
    override val providerId = "parakeet"
    override val displayName = "Parakeet (sherpa-onnx)"
    override val supportedLanguages = listOf("en")  // English-only

    private var recognizer: OfflineRecognizer? = null

    override val isReady: Boolean get() = recognizer != null

    override suspend fun initialize(modelPath: String): Boolean {
        // modelPath points to the directory containing model.int8.onnx + tokens.txt
        val tokensPath = modelPath.substringBeforeLast("/") + "/tokens.txt"
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = 2,
            )
        )
        return try {
            recognizer = OfflineRecognizer(null, config)
            true
        } catch (e: Exception) {
            Timber.e(e, "ParakeetProvider: init failed")
            false
        }
    }

    override suspend fun transcribe(samples: FloatArray, language: String): String {
        val r = recognizer ?: throw IllegalStateException("ParakeetProvider not initialized")
        val stream = r.createStream()
        stream.acceptWaveform(samples, sampleRate = 16000)
        r.decode(stream)
        return r.getResult(stream).text
    }

    override suspend fun release() {
        recognizer = null  // GC handles ONNX Runtime cleanup
        Timber.d("ParakeetProvider released")
    }
}
```

### Pattern 3: Provider Badge in ModelCard
**What:** The existing "WK" badge in `ModelCard.kt` (line 233–245) must become provider-aware. For Parakeet models, show "NV" (Nvidia/NeMo) in amber; for Whisper, keep "WK" in blue.
**When to use:** Always — replace the hardcoded "WK" text with `when (model.provider)` dispatch.

```kotlin
// In ModelCard.kt — replace hardcoded "WK" block:
val (badgeText, badgeColor) = when (model.provider) {
    AiProvider.WHISPER  -> "WK" to DictusColors.Accent
    AiProvider.PARAKEET -> "NV" to Color(0xFFF59E0B)  // amber
}
```

### Pattern 4: Language Warning Banner
**What:** Amber warning `Box` rendered below the provider badge row on Parakeet model cards.
**When to use:** `model.provider == AiProvider.PARAKEET`.

```kotlin
// In ModelCard.kt, after the header Row:
if (model.provider == AiProvider.PARAKEET) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.model_parakeet_language_warning),
            color = Color(0xFFF59E0B),
            fontSize = 12.sp,
        )
    }
}
```

### Pattern 5: Activation Dialog for Language Mismatch
**What:** In `ModelsViewModel.setActiveModel()`, before writing to DataStore, check if the new model is Parakeet AND `TRANSCRIPTION_LANGUAGE != "en"`. If so, expose a state flag to trigger a confirmation dialog in the UI.
**When to use:** `setActiveModel()` for any Parakeet model when language is "fr" or "auto".

```kotlin
// In ModelsViewModel — add:
private val _showParakeetLanguageDialog = MutableStateFlow<String?>(null) // holds pending key
val showParakeetLanguageDialog: StateFlow<String?> = _showParakeetLanguageDialog.asStateFlow()

fun requestSetActiveModel(key: String) {
    viewModelScope.launch {
        val info = ModelCatalog.findByKey(key) ?: return@launch
        if (info.provider == AiProvider.PARAKEET) {
            val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "auto"
            if (lang != "en") {
                _showParakeetLanguageDialog.value = key
                return@launch
            }
        }
        setActiveModel(key)
    }
}

fun confirmParakeetActivation() {
    val key = _showParakeetLanguageDialog.value ?: return
    _showParakeetLanguageDialog.value = null
    setActiveModel(key)
}

fun dismissParakeetDialog() { _showParakeetLanguageDialog.value = null }
```

### Pattern 6: RAM Guard for 0.6B Model
**What:** Before calling `ParakeetProvider.initialize()` for the 0.6B model, check `ActivityManager.MemoryInfo.totalMem`. Block initialization if < 8 GB.

```kotlin
// Standard Android API — no extra dependency
fun hasEnoughRamForLargeModel(context: Context): Boolean {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    val totalGb = memInfo.totalMem / (1024L * 1024L * 1024L)
    return totalGb >= 8
}
```

### Anti-Patterns to Avoid
- **Keeping both engines initialized simultaneously:** Always `release()` the current provider before `initialize()` on the new one. The Pixel 4 has ~6 GB RAM; loading both models at once (~126 MB + model weights in ONNX Runtime) will cause OOM in the IME process.
- **Hardcoding "en" in transcribe() for Parakeet:** The `language` parameter passed to `transcribe()` is ignored by Parakeet (NeMo CTC is English-only and doesn't accept a language hint) — but the `SttProvider` interface passes it anyway. Document this as a no-op in ParakeetProvider.
- **Downloading model.onnx + tokens.txt separately via current downloader:** The model must be downloaded as a directory (model.int8.onnx + tokens.txt). The current `ModelDownloader` downloads a single file. Phase 9 must handle the multi-file model structure — either download the tar.bz2 and extract, or download the two files individually.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ONNX inference runtime | Custom ONNX loader | sherpa-onnx (`libonnxruntime.so` + `libsherpa-onnx-jni.so`) | ONNX Runtime for Android has complex memory management, quantization, thread pools — sherpa-onnx handles all of this |
| CTC beam search decoding | Custom decoder | `OfflineRecognizer.decode()` | CTC decoding requires handling blank tokens, repeated characters, and language model integration — sherpa-onnx has this tested |
| RAM check | `/proc/meminfo` parsing | `ActivityManager.MemoryInfo` | Platform API, works correctly across all Android versions |
| Native library loading | Manual `dlopen` | Android's automatic JNI loading via `jniLibs/` directory | Android loads .so files from jniLibs automatically when the AAR/module is in the dependency graph |

**Key insight:** The sherpa-onnx Kotlin API is thin: `OfflineRecognizer` → `createStream()` → `acceptWaveform()` → `decode()` → `getResult().text`. All complexity is in the native layer. Don't replicate it.

---

## Common Pitfalls

### Pitfall 1: libc++_shared.so Duplicate Merge Conflict
**What goes wrong:** Gradle merge fails with `More than one file was found with OS independent path 'lib/arm64-v8a/libc++_shared.so'` because both `whisper/` (NDK CMake build) and `asr/` (pre-built sherpa-onnx .so) ship their own copy.
**Why it happens:** NDK requires `libc++_shared.so` be bundled with each native library. Multiple modules each include it.
**How to avoid:** Add to `app/build.gradle.kts`:
```kotlin
android {
    packagingOptions {
        jniLibs.pickFirsts += setOf("**/libc++_shared.so")
    }
}
```
**Warning signs:** Build error mentioning `libc++_shared.so` during `mergeDebugNativeLibs` task. Validate this BEFORE any other Phase 9 code.

### Pitfall 2: Model File Layout — Directory vs Single File
**What goes wrong:** `ModelDownloader` downloads a single file. Parakeet requires a directory with `model.int8.onnx` + `tokens.txt`. Passing the wrong path to `OfflineNemoEncDecCtcModelConfig.model` causes a JNI crash (not a Kotlin exception).
**Why it happens:** Current download infrastructure expects one file per model key; Parakeet models from sherpa-onnx ship as an archive with two files.
**How to avoid:** Either (a) download both files individually to `models/parakeet-ctc-110m-int8/` and track the directory, or (b) download the `.tar.bz2` and extract. Option (a) is simpler given the existing OkHttp downloader.
**Warning signs:** `ParakeetProvider.initialize()` returns false; logcat shows JNI error about missing file.

### Pitfall 3: ONNX Runtime Memory Release Timing (sherpa-onnx #1939)
**What goes wrong:** After `recognizer = null` in `release()`, ONNX Runtime may hold native memory for several GC cycles. On a Pixel 4, this can cause apparent high memory usage if the user immediately initializes a new model.
**Why it happens:** Java GC doesn't guarantee immediate finalization of native-backed objects.
**How to avoid:** Validate with `adb shell dumpsys meminfo dev.pivisolutions.dictus` after model switches. If memory doesn't drop after switch, call `System.gc()` after `release()` (not ideal, but effective for a foreground service with a clear boundary).
**Warning signs:** OOM crash or slowdown after model switch sequence.

### Pitfall 4: Engine Swap During Active Transcription
**What goes wrong:** User somehow triggers model switch while `confirmAndTranscribe()` is running. `release()` is called on the active provider mid-transcription.
**Why it happens:** Model switch is triggered from `ModelsViewModel.setActiveModel()` which writes to DataStore, and `DictationService.confirmAndTranscribe()` reads from DataStore at step 2. If the write happens between steps 2 and 4, the wrong provider is used.
**How to avoid:** `DictationService` reads `ACTIVE_MODEL` once at the start of `confirmAndTranscribe()` and uses that snapshot for the entire transcription. Provider switch only takes effect on the NEXT transcription. This is already the current pattern.

### Pitfall 5: `TRANSCRIPTION_LANGUAGE == "auto"` Not Treated as Non-English
**What goes wrong:** Warning dialog is NOT shown because code checks `lang != "en"` but "auto" means the STT engine auto-detects — on Parakeet (English-only), auto will produce English output regardless.
**Why it happens:** "auto" is neither "en" nor "fr" — a naive check might miss it.
**How to avoid:** Warning condition should be `lang != "en"` which correctly catches "auto", "fr", and any other non-EN value.

---

## Code Examples

Verified patterns from official sources:

### OfflineRecognizer for NeMo CTC (Parakeet)
```kotlin
// Source: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt
val config = OfflineRecognizerConfig(
    modelConfig = OfflineModelConfig(
        nemo = OfflineNemoEncDecCtcModelConfig(
            model = "/path/to/model.int8.onnx"  // absolute path on device storage
        ),
        tokens = "/path/to/tokens.txt",
        numThreads = 2,
        provider = "cpu",
    ),
    decodingMethod = "greedy_search",
)
val recognizer = OfflineRecognizer(null, config)  // first arg is optional Asset manager (null = file path mode)
val stream = recognizer.createStream()
stream.acceptWaveform(floatArraySamples, sampleRate = 16000)
recognizer.decode(stream)
val result = recognizer.getResult(stream)
println(result.text)
```

### libc++_shared.so pickFirst fix
```kotlin
// Source: Android NDK docs + community consensus
// In app/build.gradle.kts:
android {
    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}
```

### RAM check for 0.6B model guard
```kotlin
// Source: Android developer.android.com/reference/kotlin/android/app/ActivityManager.MemoryInfo
val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
val memInfo = ActivityManager.MemoryInfo()
actManager.getMemoryInfo(memInfo)
val totalRamGb = memInfo.totalMem / (1024L * 1024L * 1024L)
val canLoad0_6B = totalRamGb >= 8
```

### ActivityManager.MemoryInfo.totalMem
```kotlin
// totalMem — total installed physical RAM (does NOT decrease with use)
// availMem — currently available RAM (fluctuates)
// Use totalMem for "does this device support this model" guard
// Use availMem for "is it safe to load right now" check
```

### Model catalog entry format for Parakeet
```kotlin
// Source: existing ModelInfo data class pattern in ModelManager.kt
ModelInfo(
    key = "parakeet-ctc-110m-int8",
    fileName = "model.int8.onnx",  // inside models/parakeet-ctc-110m-int8/ directory
    displayName = "Parakeet 110M INT8",
    expectedSizeBytes = 131_628_000L,  // ~126 MB — verify against actual download
    qualityLabel = "Rapide",
    description = "Anglais uniquement - Rapide et precis",
    precision = 0.75f,
    speed = 0.85f,
    provider = AiProvider.PARAKEET,
)
```

---

## Model Download URLs

**Source:** sherpa-onnx GitHub Releases `asr-models` tag (MEDIUM confidence — verified via official docs page)

| Model | Key | Download URL | Size | File in archive |
|-------|-----|-------------|------|-----------------|
| Parakeet 110M CTC INT8 | `parakeet-ctc-110m-int8` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2` | ~126 MB | `model.int8.onnx` + `tokens.txt` |
| Parakeet 110M CTC FP16 | `parakeet-ctc-110m-fp16` | Likely same release, FP16 variant — verify on GitHub Releases page | ~220 MB | `model.onnx` + `tokens.txt` |
| Parakeet 0.6B TDT v3 | `parakeet-tdt-0.6b-v3` | sherpa-onnx releases — search for `parakeet-tdt-0.6b` | ~640 MB | TDT model files |

**IMPORTANT:** The existing `ModelDownloader` downloads a single file to `models/{fileName}`. Parakeet models are archives with two files. The download implementation must be adapted to:
1. Download the `.tar.bz2` archive to a temp file
2. Extract `model.int8.onnx` and `tokens.txt` into `models/parakeet-ctc-110m-int8/`
3. `ModelManager.getModelPath()` returns the directory path (not a single file)

**Alternative simpler approach** (recommended): Download the two files separately using direct HuggingFace LFS URLs. The sherpa-onnx project publishes ONNX exports on HuggingFace. Check `huggingface.co/k2-fsa/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8` for direct file URLs. This avoids the tar.bz2 extraction complexity.

---

## sherpa-onnx Integration Approach

**Decision (from STATE.md):** sherpa-onnx 1.12.34 AAR in local `asr/libs/` directory.

### Integration steps:
1. Download `sherpa-onnx-v1.12.34-android.tar.bz2` from GitHub Releases
2. Extract `libsherpa-onnx-jni.so` and `libonnxruntime.so` for `arm64-v8a` and `armeabi-v7a`
3. Place in new `asr/` module under `src/main/jniLibs/{abi}/`
4. Copy sherpa-onnx Kotlin API source files from `sherpa-onnx/kotlin-api/` (specifically `OfflineRecognizer.kt`, `OfflineStream.kt`, and supporting data classes) into `asr/src/main/java/`
5. New `asr/build.gradle.kts`:
```kotlin
android {
    sourceSets {
        main { jniLibs.srcDirs("src/main/jniLibs") }
    }
}
```
6. `app/build.gradle.kts`: add `implementation(project(":asr"))`
7. `settings.gradle.kts`: add `include(":asr")`

**Note:** The Kotlin API source files are NOT compiled into the AAR automatically — they are distributed as source in the sherpa-onnx repo under `sherpa-onnx/kotlin-api/`. Include only the files needed for offline recognition (not streaming, TTS, etc.).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single-engine hardcoded `WhisperProvider` | Dynamic provider dispatch via `ModelInfo.provider` | Phase 9 | DictationService must become provider-agnostic |
| ModelCatalog only Whisper entries | Mixed Whisper + Parakeet entries | Phase 9 | `downloadUrl()` switch must cover both cases |
| ModelCard hardcoded "WK" badge | Provider-aware badge (WK/NV) | Phase 9 | ModelCard gets conditional rendering |

**Deprecated/outdated:**
- Line 97 of `DictationService.kt`: `private val sttProvider: SttProvider = WhisperProvider()` — becomes a mutable field or function per Phase 9.
- `ModelCatalog.downloadUrl()` PARAKEET branch: `error("Parakeet downloads not yet supported")` — replaced with real URL.

---

## Open Questions

1. **FP16 model download URL**
   - What we know: INT8 model URL verified (`sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2`)
   - What's unclear: FP16 variant may not be published on sherpa-onnx releases; may need direct HuggingFace URL from nvidia/parakeet-tdt_ctc-110m
   - Recommendation: Implement INT8 first (primary model). Verify FP16 URL on GitHub Releases page before adding that catalog entry. If not available as pre-exported ONNX, defer FP16 to a later plan within Phase 9.

2. **0.6B TDT v3 exact download URL**
   - What we know: Support was added to sherpa-onnx; `parakeet-tdt-0.6b-v2` was merged (PR #2181)
   - What's unclear: v3 specifically — may be `parakeet-tdt-0.6b-v2` with v3 being the NeMo model version. Verify on releases page.
   - Recommendation: Add 0.6B to catalog with RAM guard but mark as LOW priority within phase. Do not block STT-02 delivery on this.

3. **Model file download strategy: tar.bz2 extraction vs direct file download**
   - What we know: Current `ModelDownloader` uses OkHttp to stream a single file. Parakeet needs two files.
   - What's unclear: Whether sherpa-onnx provides direct (non-archive) download URLs for individual ONNX files
   - Recommendation: Check if HuggingFace (`huggingface.co/k2-fsa/`) provides direct `.onnx` and `tokens.txt` URLs. If yes, download both files individually — simpler than tar.bz2 extraction. If no, add tar.bz2 extraction logic.

4. **ONNX Runtime memory after `release()`**
   - What we know: Issue #1939 in sherpa-onnx documents delayed memory release
   - What's unclear: Whether this is still an issue in v1.12.34
   - Recommendation: Empirical validation required. After first implementation, test: download Parakeet → activate → transcribe → switch back to Whisper → run `dumpsys meminfo`. If memory > 200 MB above baseline, add `System.gc()` after `release()`.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric (existing project setup) |
| Config file | Via `testOptions.unitTests.isIncludeAndroidResources = true` in `app/build.gradle.kts` |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` |
| Full suite command | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| STT-02 | Parakeet models appear in ModelCatalog.ALL with correct provider | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ ModelCatalogTest exists |
| STT-02 | Parakeet download URL returns non-error string | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ |
| STT-03 | setActiveModel writes correct key; getOrInitProvider creates correct provider type | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` | ❌ Wave 0 — ModelsViewModelTest needs Parakeet cases |
| STT-04 | ModelInfo.description and provider field set correctly for all 3 Parakeet entries | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` | ✅ extend existing |
| STT-05 | requestSetActiveModel triggers dialog for Parakeet when lang != "en" | unit | `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` | ❌ Wave 0 |
| STT-05 | requestSetActiveModel does NOT trigger dialog when lang == "en" | unit | same | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/.../models/ModelsViewModelTest.kt` — Parakeet language dialog trigger tests (STT-05). Note: existing SettingsViewModelTest.kt shows the Robolectric + DataStore fake pattern to follow.

*(Note: `libc++_shared.so` build validation and ONNX memory validation are device/build tests — not unit-testable. Verified manually via `./gradlew assembleDebug` and `dumpsys meminfo`.)*

---

## Sources

### Primary (HIGH confidence)
- `github.com/k2-fsa/sherpa-onnx` `sherpa-onnx/kotlin-api/OfflineRecognizer.kt` — Kotlin API contract for NeMo CTC models
- `k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/english.html` — Parakeet INT8 download URL and model file names (model.int8.onnx + tokens.txt, 126 MB)
- `developer.android.com/reference/kotlin/android/app/ActivityManager.MemoryInfo` — RAM check API
- Codebase: `SttProvider.kt`, `WhisperProvider.kt`, `DictationService.kt`, `ModelManager.kt`, `ModelCard.kt`, `ModelsViewModel.kt`, `PreferenceKeys.kt` — all read and analyzed

### Secondary (MEDIUM confidence)
- `k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html` — Integration via jniLibs pre-built .so; no Maven Central; local libs approach confirmed
- `.planning/STATE.md` "Research Flags (Phase 9)" and "Key v1.1 architectural decisions" — confirms sherpa-onnx 1.12.34 AAR in `asr/libs/` approach; confirms Parakeet 110M CTC INT8 as only feasible model for IME
- Android NDK `packagingOptions.jniLibs.pickFirsts` — `libc++_shared.so` conflict resolution community consensus (multiple verified sources)

### Tertiary (LOW confidence)
- FP16 variant download URL — not directly verified; should be on same asr-models releases page but not confirmed
- 0.6B TDT v3 exact URL — PR #2181 adds parakeet-tdt-0.6b-v2 support; v3 naming unclear

---

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — sherpa-onnx integration approach confirmed by STATE.md prior decision and official docs; Kotlin API verified via source
- Architecture: HIGH — all integration points identified via direct code read; provider dispatch pattern is a clear extension of existing WhisperProvider pattern
- Pitfalls: HIGH — libc++_shared.so collision confirmed by multiple sources; ONNX memory issue documented in sherpa-onnx issue tracker; multi-file model layout confirmed from official model docs
- Download URLs: MEDIUM — INT8 URL confirmed; FP16 and 0.6B need page-level verification before implementation

**Research date:** 2026-03-31
**Valid until:** 2026-04-30 (sherpa-onnx releases frequently but Kotlin API is stable; model URLs are immutable once published)
