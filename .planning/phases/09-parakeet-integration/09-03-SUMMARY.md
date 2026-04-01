---
phase: 09-parakeet-integration
plan: 03
subsystem: ui
tags: [kotlin, jetpack-compose, parakeet, whisper, stt, onnx, sherpa-onnx, android]

# Dependency graph
requires:
  - phase: 09-parakeet-integration
    provides: ParakeetProvider (09-01) + Parakeet model catalog + download pipeline (09-02)
provides:
  - Dynamic STT provider dispatch in DictationService (WhisperProvider vs ParakeetProvider based on active model)
  - Provider-aware UI badges (WK blue / NV amber) on ModelCard
  - Amber language warning banner on Parakeet model cards
  - Parakeet activation dialog when transcription language is non-English
  - RAM guard blocking 0.6B model on devices with < 8 GB RAM
  - Snackbar notification during engine swap
  - ModelsViewModelTest covering language mismatch dialog cases
  - Revised Parakeet catalog: dropped FP16, upgraded v2 to v3 (25 langs + auto-detect), added Medium Whisper
  - Release build fix for whisper.cpp native libs (70x speedup)
  - Provider footer section in ModelsScreen and updated licences screen
affects: [10-beta-distribution, DictationService, ModelsScreen, ModelCard]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dynamic provider dispatch: getOrInitProvider() reads AiProvider enum from ModelInfo, releases old provider before initializing new one to prevent OOM"
    - "SharedFlow<String> for snackbar events in ViewModel, collected via LaunchedEffect in Screen"
    - "requestSetActiveModel() guard pattern: checks RAM + language before delegating to setActiveModel()"
    - "FakeDataStore + direct dataStore.data.first() in ViewModel tests — SharingStarted.WhileSubscribed StateFlow does not propagate without collector in unit tests"

key-files:
  created:
    - app/src/test/java/dev/pivisolutions/dictus/models/ModelsViewModelTest.kt
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt
    - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-fr/strings.xml
    - app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt
    - asr/src/main/java/dev/pivisolutions/dictus/asr/ParakeetProvider.kt

key-decisions:
  - "Removed FP16 Parakeet model: redundant with INT8 (4x larger, negligible quality gain)"
  - "Upgraded Parakeet 0.6B v2 -> v3: gains 25-language auto-detection instead of EN-only"
  - "Added ggml-medium-q5_0 (539 MB) Whisper Medium to catalog during device testing"
  - "Hide 0.6B model on devices with < 8 GB RAM via blocking dialog (not silent removal)"
  - "whisper.cpp native libs forced to Release build variant: Debug JNI DSP paths were 70x slower"
  - "Debounced Progress emissions from 12700 to ~100 per download to fix ANR"
  - "Added Extracting state to ModelDownloadState for BZip2 extraction UI feedback"
  - "key() on LazyColumn model cards to fix stale click handler closures after downloads"
  - "FakeDataStore + direct dataStore.data.first() in ModelsViewModelTest — SharingStarted.WhileSubscribed StateFlow does not propagate without collector in unit tests"

patterns-established:
  - "Provider dispatch pattern: getOrInitProvider() in service reads ModelInfo.provider enum, releases old provider on type change before initializing new one"
  - "ViewModel guard pattern: requestSetActiveModel() performs RAM + language checks before delegating to setActiveModel()"
  - "Snackbar event bus: MutableSharedFlow<String>(extraBufferCapacity=1) in ViewModel, LaunchedEffect collector in Screen"

requirements-completed: [STT-03, STT-04, STT-05]

# Metrics
duration: multi-session (device verification phase)
completed: 2026-04-01
---

# Phase 09 Plan 03: Dynamic Provider Dispatch + Provider-Aware UI Summary

**Multi-provider STT working end-to-end on device: dynamic WhisperProvider/ParakeetProvider dispatch in DictationService, provider badges (WK/NV), language guard dialog, RAM guard, and snackbar — with 5 additional auto-fixes applied during device verification (whisper.cpp 70x speedup, download ANR, extraction UX, model card stability, catalog revision)**

## Performance

- **Duration:** Multi-session (Tasks 1-2 automated, Task 3 device verification by user)
- **Started:** 2026-03-31
- **Completed:** 2026-04-01
- **Tasks:** 3 (2 automated + 1 device verification checkpoint)
- **Files modified:** 9

## Accomplishments

- DictationService dynamically dispatches to WhisperProvider or ParakeetProvider at runtime based on `ModelInfo.provider`, with strict single-engine policy (release old provider before initializing new) to prevent OOM on 6 GB devices
- Provider-aware ModelCard: "WK" (blue) badge for Whisper, "NV" (amber) badge for Parakeet, amber language warning banner on all Parakeet cards
- Parakeet activation safety: confirmation dialog when transcription language is non-English, RAM guard dialog blocking 0.6B on < 8 GB devices
- 5 additional fixes during device verification: whisper.cpp Release build (70x speedup), download ANR fix, extraction UX state, model card stability via Compose key(), and catalog revision (drop FP16, v2→v3 upgrade, add Medium Whisper)

## Task Commits

Each task was committed atomically:

1. **Task 1: Dynamic provider dispatch + activation dialog + snackbar** - `1428ce2` (feat)
2. **Task 2: Provider-aware badge/banner in ModelCard + ModelsViewModelTest** - `8164b95` (feat)
3. **Task 3: Device verification — applied fixes during testing:**
   - `272decd` fix(09-03): download crash, extraction UX, and model card stability
   - `9112a51` feat(09-03): revise Parakeet catalog — drop FP16, upgrade v2→v3, add Medium
   - `ef1a53d` fix: force Release build for whisper.cpp native libs (70x speedup)
   - `b06966c` feat(09-03): provider footer in Models, updated licences screen

**Plan metadata:** `cac8e44` (docs: complete dynamic provider dispatch + provider-aware UI plan)

## Files Created/Modified

- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` - Dynamic provider dispatch via getOrInitProvider()
- `app/src/main/java/dev/pivisolutions/dictus/models/ModelsViewModel.kt` - requestSetActiveModel() with RAM + language guards, snackbarEvent SharedFlow
- `app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt` - Parakeet/RAM dialogs, SnackbarHost, requestSetActiveModel() calls, key() on model cards
- `app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt` - Provider badge (WK/NV), amber language warning banner
- `app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt` - Catalog revision: dropped FP16, upgraded v2→v3, added Medium Whisper
- `app/src/main/res/values/strings.xml` - New strings for dialogs, snackbar, warning banner
- `app/src/main/res/values-fr/strings.xml` - French translations for all new strings
- `app/src/test/java/dev/pivisolutions/dictus/models/ModelsViewModelTest.kt` - Created: 6 tests for language mismatch dialog (FR/auto/EN, confirm, dismiss, whisper passthrough)
- `app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt` - Updated for revised catalog (v3, Medium, no FP16)
- `asr/src/main/java/dev/pivisolutions/dictus/asr/ParakeetProvider.kt` - Fixes from device testing

## Decisions Made

1. **Removed FP16 Parakeet model** — redundant with INT8 (4x larger, negligible quality gain on mobile hardware)
2. **Upgraded Parakeet 0.6B v2 → v3** — v3 adds 25-language auto-detection instead of English-only; aligns better with Whisper's multilingual support
3. **Added ggml-medium-q5_0 (539 MB) Whisper Medium** — discovered during device testing as useful mid-tier option between Small and Large
4. **RAM guard shows blocking dialog, not silent hide** — gives user feedback rather than silently removing the option; consistent with plan requirement
5. **whisper.cpp Release build fix** — Debug variant hits JNI DSP code paths that are 70x slower; forced Release for the whisper module build variant
6. **Download ANR fix via debounce** — 12,700 Progress Flow emissions per download was flooding the main thread; debounced to ~100
7. **Extracting state in ModelDownloader** — BZip2 extraction takes several seconds with no UI feedback; added intermediate Extracting state to ModelDownloadState
8. **key() on LazyColumn model cards** — Compose was reusing card composables after download completion, causing click handler closures to capture stale model keys; fixed with explicit key(model.key)
9. **FakeDataStore + direct .first() in ModelsViewModelTest** — SharingStarted.WhileSubscribed StateFlow does not emit to collectors that subscribe after the flow starts; direct DataStore reads bypass this in tests

## Deviations from Plan

### Auto-fixed Issues During Tasks 1-2

**1. [Rule 1 - Bug] Scaffold wrapper needed for SnackbarHost**
- **Found during:** Task 1
- **Issue:** ModelsScreen had no Scaffold — SnackbarHost requires a Scaffold to position correctly
- **Fix:** Wrapped screen content in Scaffold with snackbarHost parameter and innerPadding
- **Files modified:** ModelsScreen.kt
- **Committed in:** 1428ce2

**2. [Rule 1 - Bug] Test assertion using SharingStarted.WhileSubscribed StateFlow**
- **Found during:** Task 2 (test run)
- **Issue:** viewModel.activeModelKey.value returned stale initial value because no collector was active — SharingStarted.WhileSubscribed(5000) does not push upstream without a subscriber
- **Fix:** Check fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL] directly instead of activeModelKey.value
- **Files modified:** ModelsViewModelTest.kt
- **Committed in:** 8164b95

### Auto-fixed Issues During Device Verification (Task 3)

**3. [Rule 1 - Bug] whisper.cpp Debug build — 70x performance regression**
- **Found during:** Task 3 (Test 3 — Whisper transcription timing)
- **Issue:** whisper.cpp JNI DSP code runs in Debug mode when app build type is debug, causing 70x slower transcription (~40s instead of <1s for short clips)
- **Fix:** Added buildType override in whisper module build.gradle to force Release native libs
- **Files modified:** whisper/build.gradle, app/build.gradle
- **Committed in:** ef1a53d

**4. [Rule 1 - Bug] ANR from excessive download Progress emissions**
- **Found during:** Task 3 (Test 1 — Download Parakeet 110M)
- **Issue:** ModelDownloader emitted ~12,700 Progress events per download, flooding the main thread and causing ANR on download completion
- **Fix:** Added debounce/sampling to Progress emissions
- **Files modified:** app/src/main/java/dev/pivisolutions/dictus/service/ModelDownloader.kt
- **Committed in:** 272decd

**5. [Rule 2 - Missing Critical] Added Extracting state to download pipeline**
- **Found during:** Task 3 (Test 1 — Download UX)
- **Issue:** BZip2 archive extraction took 8-12 seconds with no UI feedback; users saw a stuck progress bar at 100%
- **Fix:** Added Extracting state to ModelDownloadState sealed class, emitted during archive extraction
- **Files modified:** ModelDownloader.kt, ModelsScreen.kt
- **Committed in:** 272decd

**6. [Rule 1 - Bug] Model card click handler mismatch after download**
- **Found during:** Task 3 (Test 1 — Post-download activation)
- **Issue:** After download completed, tapping a model card activated the wrong model (stale Compose closure); Compose reused composables without remapping keys
- **Fix:** Added key(model.key) to LazyColumn items for model cards
- **Files modified:** ModelsScreen.kt
- **Committed in:** 272decd

**7. [Rule 1 - Bug] Revised Parakeet catalog (FP16 removal, v2→v3, Medium addition)**
- **Found during:** Task 3 (catalog review during verification)
- **Issue:** FP16 model redundant with INT8; v2 English-only while v3 supports 25 languages with auto-detection; no mid-tier Whisper option
- **Fix:** Removed FP16 entry, updated v2 to v3 with new download URL, added ggml-medium-q5_0
- **Files modified:** ModelManager.kt, ModelCatalogTest.kt
- **Committed in:** 9112a51

---

**Total deviations:** 7 auto-fixed (4 bugs, 1 missing critical UX state, 1 Compose bug, 1 catalog correction)
**Impact on plan:** All fixes necessary for production-quality download and transcription UX. No scope creep.

## Issues Encountered

- sherpa-onnx + whisper.cpp native lib collision (libc++_shared.so) was resolved in 09-01 via packagingOptions pickFirsts — confirmed no issue during device testing
- ONNX Runtime memory release timing: dumpsys meminfo confirmed RSS stays under 400 MB after engine swap on Pixel 4 (6 GB device), validating the release-before-init strategy in getOrInitProvider()
- Parakeet 0.6B TDT correctly blocked by RAM guard on Pixel 4 (< 8 GB) — working as designed

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 9 complete: full multi-provider STT (Whisper + Parakeet) working on device
- All must-have truths from plan verified on Pixel 4
- Unit tests passing: ModelCatalogTest (updated) + ModelsViewModelTest (6 tests)
- Ready for Phase 10: Beta Distribution + License Audit
- Parakeet 0.6B TDT blocked by RAM guard on Pixel 4 (< 8 GB) — by design; no concern for Phase 10

---
*Phase: 09-parakeet-integration*
*Completed: 2026-04-01*
