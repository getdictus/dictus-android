# Phase 7: Foundation - Research

**Researched:** 2026-03-30
**Domain:** Android Kotlin — interface extraction, DataStore Flow observation, runtime permissions, JUnit/Robolectric test fixes
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Rename `TranscriptionEngine` to `SttProvider`, `WhisperTranscriptionEngine` to `WhisperProvider`
- Add minimal metadata: `providerId` (String), `displayName` (String), `supportedLanguages` (List<String>)
- Keep existing method signatures (`initialize`, `transcribe`, `release`) — they already cover the contract
- `DictationService` continues to manage provider lifecycle (no separate SttManager yet — Phase 9 can revisit if needed)
- IME must observe the keyboard layout preference from DataStore via Flow and update the layout without IME restart
- Declare `POST_NOTIFICATIONS` in AndroidManifest.xml
- Add runtime permission request flow for API 33+ (e.g., during first recording or app launch) so notifications actually work end-to-end on modern devices
- Fix all 3 stale test files: AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest
- Update assertions to match current code, do not remove tests
- Full test suite must pass (`./gradlew test`) before phase is considered complete

### Claude's Discretion
- Package organization for SttProvider (core/stt/ vs core/whisper/)
- Exact timing of POST_NOTIFICATIONS runtime request (app launch vs first recording)
- How to wire DataStore Flow observation in DictusImeService (LaunchedEffect, coroutine scope, etc.)
- Test assertion fixes — whatever makes them match current behavior

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DEBT-01 | AZERTY/QWERTY toggle propagates immediately to IME without restart | DataStore Flow observation pattern in DictusImeService; existing `entryPoint.dataStore()` mechanism is the right hook |
| DEBT-02 | POST_NOTIFICATIONS declared in AndroidManifest for API 33+ | One-line manifest addition; runtime code already exists in PermissionHelper |
| DEBT-03 | Stale tests fixed (AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest) | Exact byte-size mismatches and step-count mismatch identified below |
| STT-01 | SttProvider interface exists in core/ with WhisperProvider wrapping whisper.cpp | Simple rename + metadata addition; all callers identified |
</phase_requirements>

---

## Summary

Phase 7 is a focused cleanup + interface extraction pass with four fully-scoped tasks. The codebase is well-organized and the changes are low-risk: no new dependencies, no new modules, and no behavioral change visible to users.

Three of the four requirements (DEBT-02, DEBT-03, STT-01) are mechanical. DEBT-01 (keyboard layout propagation) is the only requirement that needs a new reactive wiring path — but the pattern is already established in DictusImeService for `THEME`, `KEYBOARD_MODE`, and `HAPTICS_ENABLED`, so it is a straight copy.

The stale tests have been diagnosed in detail (see Common Pitfalls below). The exact mismatched values are known; no investigation is needed during planning.

**Primary recommendation:** Execute in wave order: (1) DEBT-02 manifest fix, (2) DEBT-03 test fixes, (3) DEBT-01 DataStore flow wiring, (4) STT-01 rename. Each task is independently verifiable. Complete DEBT-02 first because it is a one-line change that lets `./gradlew test` pass cleanly before more complex changes land.

---

## Standard Stack

### Core (already in project — no new dependencies)
| Library | Purpose | Status |
|---------|---------|--------|
| AndroidX DataStore Preferences | Persistent key-value preferences, `Flow<Preferences>` API | Already used throughout |
| Hilt / EntryPointAccessors | DI for non-standard Android components (IME, Service) | Already used in DictusImeService |
| Kotlin Coroutines + Flow | Reactive observation of DataStore values | Already used |
| JUnit 4 + Robolectric | Unit tests with Android context simulation | Already used |
| kotlinx-coroutines-test | `StandardTestDispatcher`, `runTest`, `advanceUntilIdle` | Already used |

No new libraries or Gradle dependencies are needed for any task in this phase.

### Installation
No installation steps required — all dependencies are already declared.

---

## Architecture Patterns

### Recommended Structure After Phase 7

```
core/src/main/java/dev/pivisolutions/dictus/core/
├── stt/                          # NEW — provider-neutral STT contract
│   └── SttProvider.kt            # (renamed from whisper/TranscriptionEngine.kt)
├── whisper/
│   └── TextPostProcessor.kt      # Unchanged
└── preferences/
    └── PreferenceKeys.kt         # Unchanged

app/src/main/java/dev/pivisolutions/dictus/service/
├── WhisperProvider.kt            # (renamed from WhisperTranscriptionEngine.kt)
└── DictationService.kt           # Updated: uses SttProvider, WhisperProvider
```

**Package decision (Claude's discretion):** Move `SttProvider` to `core/stt/` rather than keeping it in `core/whisper/`. Rationale: the interface is provider-neutral by design (STT-01 purpose is to be engine-agnostic). `core/stt/` signals this neutrality clearly and avoids Phase 9 confusion when `WhisperProvider` and `ParakeetProvider` coexist.

### Pattern 1: SttProvider Interface (STT-01)

**What:** Rename `TranscriptionEngine` → `SttProvider`, add three metadata fields.

**Example — new SttProvider.kt in `core/stt/`:**
```kotlin
package dev.pivisolutions.dictus.core.stt

/**
 * Contract for all speech-to-text engines in Dictus.
 *
 * WHY an interface: core/ cannot depend on whisper/ or any specific engine.
 * DictationService in the app module wires the concrete implementation.
 * This interface is the only coupling point — adding Parakeet in Phase 9
 * requires only a new implementation, no changes to DictationService.
 */
interface SttProvider {
    /** Stable machine-readable identifier (e.g., "whisper", "parakeet"). */
    val providerId: String

    /** Human-readable name shown in settings (e.g., "Whisper (whisper.cpp)"). */
    val displayName: String

    /**
     * BCP-47 language codes this provider supports.
     * Whisper supports any language; Parakeet is English-only.
     * Used in Phase 9 to warn users when their language is unsupported.
     */
    val supportedLanguages: List<String>

    /** Whether the engine is ready (model loaded). */
    val isReady: Boolean

    suspend fun initialize(modelPath: String): Boolean
    suspend fun transcribe(samples: FloatArray, language: String): String
    suspend fun release()
}
```

**Example — WhisperProvider (renamed + metadata added):**
```kotlin
package dev.pivisolutions.dictus.service

import dev.pivisolutions.dictus.core.stt.SttProvider
// ... existing imports

class WhisperProvider : SttProvider {
    override val providerId: String = "whisper"
    override val displayName: String = "Whisper (whisper.cpp)"
    override val supportedLanguages: List<String> = emptyList() // empty = all languages

    // ... all existing method bodies unchanged
}
```

**DictationService change:** Line 96 changes from:
```kotlin
private val transcriptionEngine = WhisperTranscriptionEngine()
```
to:
```kotlin
private val sttProvider: SttProvider = WhisperProvider()
```
All references to `transcriptionEngine` become `sttProvider`. The import changes from `core.whisper.TranscriptionEngine` to `core.stt.SttProvider`.

### Pattern 2: DataStore Flow Observation in IME (DEBT-01)

**What:** DictusImeService must react to `KEYBOARD_LAYOUT` DataStore changes without restart.

**Why it's broken now:** `KeyboardLayouts.AZERTY` / `KeyboardLayouts.QWERTY` in `KeyboardScreen` receive an `initialLayer` parameter computed once at `KeyboardContent()` composition from `KEYBOARD_MODE`, but `KEYBOARD_LAYOUT` is not observed at all in the IME. The Settings screen writes to DataStore via `SettingsViewModel.setKeyboardLayout()`, but the IME has no subscription to that key.

**Existing pattern to copy (from DictusImeService.kt, line 259-262):**
```kotlin
val keyboardModeKey by entryPoint.dataStore().data
    .map { it[PreferenceKeys.KEYBOARD_MODE] ?: "abc" }
    .collectAsState(initial = "abc")
```

**New pattern to add (same location, same style):**
```kotlin
// Source: existing DictusImeService pattern
val keyboardLayout by entryPoint.dataStore().data
    .map { it[PreferenceKeys.KEYBOARD_LAYOUT] ?: "azerty" }
    .collectAsState(initial = "azerty")
```

`keyboardLayout` is then passed to `KeyboardScreen` so it can select the correct layout definition from `KeyboardLayouts`. Check how `KeyboardScreen` currently receives layout info — if `initialLayer` is the only entry point, `keyboardLayout` needs to route through to the layout selection logic.

**Key constraint:** `collectAsState` with `DataStore.data` in `@Composable` scope is the established pattern in this codebase. Do not introduce a coroutine-scope-level subscription because the `bindingScope` (MainScope) is not tied to the Compose recomposition lifecycle.

### Pattern 3: POST_NOTIFICATIONS Manifest Declaration (DEBT-02)

**What:** One line missing from `app/src/main/AndroidManifest.xml`.

**Current state:** `PermissionHelper.kt` already requests `POST_NOTIFICATIONS` at runtime on API 33+ (line 25-27). The `buildPermissionsToRequest()` function is already called from onboarding. The manifest simply lacks the `<uses-permission>` declaration, which causes Android to silently ignore the runtime request.

**Fix:**
```xml
<!-- In app/src/main/AndroidManifest.xml, after FOREGROUND_SERVICE_MICROPHONE -->
<uses-permission
    android:name="android.permission.POST_NOTIFICATIONS"
    android:maxSdkVersion="32"
    tools:ignore="PermissionImpliesUnsupportedChromeOSHardware" />
```

**Important note on `maxSdkVersion="32"`:** This is optional but correct practice. On API 33+ the permission is real; on API 32 and below it is a no-op (Android ignores it). Many projects declare it without `maxSdkVersion` — either approach is valid. The simpler form is:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Runtime request is already handled:** `PermissionHelper.buildPermissionsToRequest()` conditionally adds `POST_NOTIFICATIONS` for API >= 33. The `rememberPermissionState` composable is used in onboarding. No new runtime request code is needed — the missing piece was only the manifest declaration.

**Claude's discretion on timing:** The existing onboarding flow already requests both RECORD_AUDIO and POST_NOTIFICATIONS together at step 2. No additional request is needed at first recording. Verify the onboarding step 2 flow actually invokes `buildPermissionsToRequest()` and passes it to the launcher.

### Anti-Patterns to Avoid
- **Creating a SttManager layer now:** The context says DictationService continues to own provider lifecycle. SttManager is deferred to Phase 9. Do not add it.
- **Removing stale tests:** CONTEXT.md explicitly says "update assertions to match current code, do not remove tests." Fix the expected values; do not `@Ignore` or delete tests.
- **Using `collectLatest` instead of `collectAsState`:** In Compose, `DataStore.data.collectAsState()` is the correct API. `collectLatest` is for coroutine scopes, not Compose.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Reactive preferences in IME | Custom broadcast/listener | `DataStore.data.map{}.collectAsState()` | Already established pattern in this exact file |
| Runtime permission UI | Custom dialog | Existing `PermissionHelper` + `rememberPermissionState` | Already implemented correctly |
| Test coroutine execution | `Thread.sleep()` or `runBlocking` | `runTest(StandardTestDispatcher)` + `advanceUntilIdle()` | Already used in OnboardingViewModelTest |

---

## Common Pitfalls

### Pitfall 1: Stale Test Values in ModelCatalogTest

**What goes wrong:** Tests assert hardcoded byte sizes that no longer match `ModelManager.kt`.

**Exact mismatches (test value → actual value in ModelManager.kt):**
```
base:       142_000_000L → 147_951_465L
small:      466_000_000L → 487_601_967L
small-q5_1: 190_031_232L → 190_085_487L
```
`tiny` (77_691_713L) and `DEFAULT_KEY = "tiny"` are correct.

**Fix:** Update the three `assertEquals` calls in `ModelCatalogTest` to the correct values from `ModelManager.kt`.

### Pitfall 2: Stale Step Count in OnboardingViewModelTest

**What goes wrong:** Tests assume 6 is the final onboarding step, but `OnboardingViewModel.advanceStep()` uses `step == 7` as the completion trigger.

**Root cause:** The ViewModel was updated to 7 steps (comment says "6-step" but logic says step < 7). Tests were not updated.

**Affected tests and fixes:**
- `advanceStep from step 5 advances when download is complete` — expects step 6 as final landing. Actually advances to step 6, which is correct, but `advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING` must advance to step 7 (not step 6+final-tap), then step 7 triggers `completeOnboarding()`. Trace the actual state machine:
  - Steps 1–4: free advance
  - Step 5: blocked until download
  - Step 5 → 6: unblocked after download
  - Step 6 → 7: triggers `completeOnboarding()` (DataStore write)
  - Step 7 is the "done" state
- Test `advanceStep from step 6 writes HAS_COMPLETED_ONBOARDING to DataStore` currently reaches step 6, then calls `advanceStep()` once more expecting the DataStore write. But since `step == 7` triggers `completeOnboarding()`, not `step == 6`, the test must reach step 7 first. The test sequence needs to advance to step 6 and then call `advanceStep()` again to trigger step 7 / `completeOnboarding()`.

**Warning signs:** Test passes but `editedPrefs[PreferenceKeys.HAS_COMPLETED_ONBOARDING]` is null — means `completeOnboarding()` was never called.

### Pitfall 3: AudioCaptureManager Test Methods Must Be Internal/Public

**What goes wrong:** `calculateRmsEnergy`, `normalizeEnergy`, `addEnergyToHistory` are called directly in tests. If these methods are `private` in the production class, tests will not compile.

**Research finding:** The existing test file already calls these methods, so they are currently accessible (either `internal` or package-private). This pitfall is pre-empted — no change needed. Verify before touching `AudioCaptureManager`.

### Pitfall 4: Import Update Scope for SttProvider Rename

**What goes wrong:** Renaming `TranscriptionEngine` without updating all import sites causes compilation failures.

**All callers to update:**
- `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt` — delete (replaced by `core/stt/SttProvider.kt`)
- `app/src/main/java/dev/pivisolutions/dictus/service/WhisperTranscriptionEngine.kt` — rename file to `WhisperProvider.kt`, update class name and `implements` clause
- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` — update import + field type + field name

Search for any additional callers:
```bash
grep -r "TranscriptionEngine\|WhisperTranscriptionEngine" app/src/ core/src/ --include="*.kt"
```

### Pitfall 5: KeyboardLayout Key is KEYBOARD_LAYOUT, Not KEYBOARD_MODE

**What goes wrong:** `KEYBOARD_LAYOUT` (the AZERTY/QWERTY toggle) and `KEYBOARD_MODE` (ABC/123 default mode) are distinct preference keys. The IME already observes `KEYBOARD_MODE`. DEBT-01 requires adding observation of `KEYBOARD_LAYOUT` — a separate key.

**PreferenceKeys confirmation:**
- `KEYBOARD_LAYOUT = stringPreferencesKey("keyboard_layout")` — controls AZERTY vs QWERTY
- `KEYBOARD_MODE = stringPreferencesKey("keyboard_mode")` — controls ABC vs 123

---

## Code Examples

### DEBT-01: Adding keyboard layout observation in DictusImeService.kt

```kotlin
// Source: existing pattern in DictusImeService.KeyboardContent(), line 259
// Add alongside the existing keyboardModeKey collection:

val keyboardLayout by entryPoint.dataStore().data
    .map { it[PreferenceKeys.KEYBOARD_LAYOUT] ?: "azerty" }
    .collectAsState(initial = "azerty")

// Then pass to KeyboardScreen:
KeyboardScreen(
    // ... existing params
    keyboardLayout = keyboardLayout,
    // ...
)
```

### DEBT-02: AndroidManifest.xml permission addition

```xml
<!-- After line 7 (FOREGROUND_SERVICE_MICROPHONE permission) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### STT-01: SttProvider interface

```kotlin
// File: core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt
package dev.pivisolutions.dictus.core.stt

interface SttProvider {
    val providerId: String
    val displayName: String
    val supportedLanguages: List<String>
    val isReady: Boolean
    suspend fun initialize(modelPath: String): Boolean
    suspend fun transcribe(samples: FloatArray, language: String): String
    suspend fun release()
}
```

### DEBT-03: ModelCatalogTest corrected byte sizes

```kotlin
// Fix for `second model is base with correct size`
assertEquals(147_951_465L, base.expectedSizeBytes)

// Fix for `third model is small with correct size`
assertEquals(487_601_967L, small.expectedSizeBytes)

// Fix for `fourth model is small-q5_1 with correct size`
assertEquals(190_085_487L, smallQ5.expectedSizeBytes)
```

---

## State of the Art

| Old Approach | Current Approach | Impact on This Phase |
|---|---|---|
| `SharedPreferences` | AndroidX DataStore | DataStore already in use; `collectAsState()` is the correct reactive API |
| `BroadcastReceiver` for IME preferences | DataStore `Flow` + `collectAsState` | Existing pattern in DictusImeService — extend it for `KEYBOARD_LAYOUT` |
| `@AndroidEntryPoint` on all components | `EntryPointAccessors` for IME/Service | Already in place; no change needed |

---

## Open Questions

1. **Does KeyboardScreen accept a `keyboardLayout` parameter today?**
   - What we know: `KeyboardScreen` receives `initialLayer` (a `KeyboardLayer` enum). Layout selection probably happens inside `KeyboardLayouts` definitions.
   - What's unclear: Whether `KeyboardScreen` has a direct layout parameter or whether layout is derived from `initialLayer` or a separate parameter.
   - Recommendation: Read `KeyboardScreen.kt` and `KeyboardLayouts.kt` before implementing DEBT-01. The IME change may require adding a parameter to `KeyboardScreen` in addition to observing the DataStore key.

2. **Does AudioCaptureManagerTest.kt have compilation issues today?**
   - What we know: Tests call `calculateRmsEnergy`, `normalizeEnergy`, `addEnergyToHistory` directly. These must be non-private.
   - What's unclear: Whether `./gradlew test` currently compiles cleanly or fails.
   - Recommendation: Run `./gradlew test` as the first action in the implementation plan to establish baseline failure mode.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric (existing) |
| Config file | `app/build.gradle` — `testOptions { unitTests { includeAndroidResources = true } }` |
| Quick run command | `./gradlew :app:test --tests "dev.pivisolutions.dictus.*" -x lint` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DEBT-01 | KEYBOARD_LAYOUT DataStore change propagates to IME layout | manual smoke | manual — IME UI can't be unit-tested without instrumentation | N/A |
| DEBT-02 | POST_NOTIFICATIONS in manifest | unit (manifest parse) | `./gradlew :app:lint` detects missing permissions | ✅ via lint |
| DEBT-03 | ModelCatalogTest passes | unit | `./gradlew :app:test --tests "dev.pivisolutions.dictus.model.ModelCatalogTest"` | ✅ exists |
| DEBT-03 | OnboardingViewModelTest passes | unit | `./gradlew :app:test --tests "dev.pivisolutions.dictus.onboarding.OnboardingViewModelTest"` | ✅ exists |
| DEBT-03 | AudioCaptureManagerTest passes | unit | `./gradlew :app:test --tests "dev.pivisolutions.dictus.service.AudioCaptureManagerTest"` | ✅ exists |
| STT-01 | SttProvider interface exists, WhisperProvider compiles, DictationService uses it | compilation | `./gradlew :app:compileDebugKotlin :core:compileDebugKotlin` | ❌ Wave 0: create SttProvider.kt + rename files |

### Sampling Rate
- **Per task commit:** `./gradlew :app:test -x lint`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt` — STT-01 interface (new file, not a test)
- [ ] `app/src/main/java/dev/pivisolutions/dictus/service/WhisperProvider.kt` — STT-01 renamed implementation

*(No new test files needed — all three DEBT-03 test files exist. STT-01 has no unit test requirement in this phase; compilation is the validation.)*

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection — `DictationService.kt`, `DictusImeService.kt`, `PreferenceKeys.kt`, `ModelManager.kt`, `PermissionHelper.kt`, `AndroidManifest.xml` — all canonical source files read
- Direct test file inspection — `ModelCatalogTest.kt`, `OnboardingViewModelTest.kt`, `AudioCaptureManagerTest.kt` — exact mismatches identified from diff between test assertions and production values

### Secondary (MEDIUM confidence)
- Android documentation pattern: `DataStore.data.collectAsState()` in Compose — verified by existing usage in DictusImeService lines 248-267
- `POST_NOTIFICATIONS` API 33+ requirement — verified by `Build.VERSION_CODES.TIRAMISU` check already present in `PermissionHelper.buildPermissionsToRequest()`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all patterns already in codebase
- Architecture: HIGH — SttProvider shape confirmed by reading TranscriptionEngine; rename scope confirmed by grepping callers
- Pitfalls: HIGH — stale test values confirmed by direct comparison of test assertions vs ModelManager.kt; step-count mismatch confirmed by reading OnboardingViewModel.advanceStep()
- DEBT-01 IME wiring: MEDIUM — pattern is clear, but KeyboardScreen parameter surface needs verification before implementation (noted in Open Questions)

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (stable codebase, no fast-moving dependencies)
