---
phase: 09-parakeet-integration
plan: 03
subsystem: stt-dispatch, models-ui, models-viewmodel
tags: [parakeet, provider-dispatch, model-cards, dialogs, snackbar, tests]
dependency_graph:
  requires: [09-01, 09-02]
  provides: [dynamic-stt-dispatch, parakeet-activation-ux]
  affects: [DictationService, ModelsViewModel, ModelsScreen, ModelCard]
tech_stack:
  added: []
  patterns:
    - Dynamic provider dispatch via AiProvider enum in DictationService
    - Strict single-engine policy: release() before switching provider type
    - SharedFlow<String> for snackbar event bus from ViewModel to Screen
    - StateFlow<String?> for dialog state (null = hidden, key = pending activation)
    - RAM guard via ActivityManager.MemoryInfo for 0.6B model threshold
key_files:
  created:
    - app/src/test/java/dev/pivisolutions/dictus/models/ModelsViewModelTest.kt
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-fr/strings.xml
decisions:
  - "FakeDataStore + direct dataStore.data.first() check in confirmParakeetActivation test — activeModelKey uses SharingStarted.WhileSubscribed which does not propagate without a collector in unit tests"
metrics:
  duration_seconds: 422
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_modified: 6
  files_created: 1
---

# Phase 09 Plan 03: Dynamic Provider Dispatch + Provider-Aware UI Summary

**One-liner:** Dynamic STT provider dispatch (WhisperProvider / ParakeetProvider) based on active model's AiProvider enum, with Parakeet activation guards and provider-aware model card badges.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Dynamic provider dispatch + activation dialog + snackbar | 1428ce2 | DictationService.kt, ModelsViewModel.kt, ModelsScreen.kt, strings.xml x2 |
| 2 | Provider-aware badge/banner in ModelCard + ModelsViewModelTest | 8164b95 | ModelCard.kt, ModelsViewModelTest.kt |
| 3 | Device verification — checkpoint pending user | — | — |

## What Was Built

### Task 1 — Dynamic Provider Dispatch

**DictationService.kt:**
- Replaced `private val sttProvider: SttProvider = WhisperProvider()` with `private var currentProvider: SttProvider? = null`
- Added `getOrInitProvider(modelKey, modelPath)`: reads `AiProvider` enum from ModelCatalog, releases current provider when type changes (prevents OOM), reuses existing provider if same type and already ready
- Updated `confirmAndTranscribe()` steps 4-5 to use dynamic dispatch
- Updated `onDestroy()` to `currentProvider?.release()`

**ModelsViewModel.kt:**
- Added `@ApplicationContext Context` parameter to Hilt constructor
- Added `snackbarEvent: SharedFlow<String>` — emits "Loading model..." when provider type switches
- Added `showParakeetLanguageDialog: StateFlow<String?>` — holds pending model key when language mismatch detected
- Added `showRamWarningDialog: StateFlow<String?>` — holds pending model key when device has < 8 GB RAM
- Added `requestSetActiveModel(key)`: checks RAM (parakeet-tdt-0.6b-v2 requires 8+ GB), checks language (Parakeet requires "en"), shows appropriate dialog or proceeds
- Added `confirmParakeetActivation()`, `dismissParakeetDialog()`, `dismissRamWarningDialog()`
- Updated `setActiveModel()` to emit snackbar when provider type changes

**ModelsScreen.kt:**
- Added `SnackbarHostState` + `LaunchedEffect` collecting `viewModel.snackbarEvent`
- Added two `AlertDialog` composables for language mismatch and RAM warning
- Wrapped content in `Scaffold` with `snackbarHost` parameter
- Changed `onSelect = { viewModel.setActiveModel(...) }` to `requestSetActiveModel(...)` in downloaded model cards

**String resources (EN + FR):** `model_parakeet_language_warning`, `parakeet_lang_dialog_title`, `parakeet_lang_dialog_message`, `ram_warning_dialog_title`, `ram_warning_dialog_message`, `model_loading_snackbar`, `dialog_continue`, `dialog_cancel`, `dialog_ok`

### Task 2 — Provider-Aware ModelCard UI

**ModelCard.kt:**
- Provider badge: `when (model.provider)` — "WK" (blue `DictusColors.Accent`) for Whisper, "NV" (amber `#F59E0B`) for Parakeet
- Amber language warning banner for all Parakeet cards: `stringResource(R.string.model_parakeet_language_warning)`

**ModelsViewModelTest.kt (6 tests):**
- `requestSetActiveModel` shows dialog for Parakeet when language is "fr"
- `requestSetActiveModel` shows dialog for Parakeet when language is "auto"
- `requestSetActiveModel` does NOT show dialog for Parakeet when language is "en"
- `requestSetActiveModel` does NOT show dialog for Whisper models
- `confirmParakeetActivation` sets active model in DataStore and clears dialog
- `dismissParakeetDialog` clears dialog without writing model to DataStore

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Scaffold wrapper needed for SnackbarHost**
- **Found during:** Task 1
- **Issue:** ModelsScreen had no Scaffold — SnackbarHost requires a Scaffold to position correctly
- **Fix:** Wrapped the screen content in a `Scaffold` with `snackbarHost` parameter and used `innerPadding`
- **Files modified:** ModelsScreen.kt

**2. [Rule 1 - Bug] Test assertion using SharingStarted.WhileSubscribed StateFlow**
- **Found during:** Task 2 (test run)
- **Issue:** `viewModel.activeModelKey.value` returned "tiny" (initial value) in `confirmParakeetActivation` test because no collector was active — `SharingStarted.WhileSubscribed(5000)` doesn't push upstream without a subscriber
- **Fix:** Check `fakeDataStore.data.first()[PreferenceKeys.ACTIVE_MODEL]` directly instead of `activeModelKey.value`
- **Files modified:** ModelsViewModelTest.kt

## Verification Results

- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "*.ModelsViewModelTest" -x lint` — 6/6 passed
- `./gradlew :app:testDebugUnitTest --tests "*.ModelCatalogTest" -x lint` — passed
- `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest -x lint` — full suite passed

## Pending

Task 3 (device verification) is a `checkpoint:human-verify` gate — awaiting manual verification on Pixel 4.
