---
phase: 04-model-management-onboarding
plan: 04
subsystem: settings
tags: [settings, datastore, logging, file-provider, viewmodel, compose, tdd]
dependency_graph:
  requires: [04-01, 04-02]
  provides: [settings-screen, file-logging, log-export, datastore-driven-dictation]
  affects: [DictationService, AppNavHost, DictusApplication]
tech_stack:
  added: [FileLoggingTree, LogExporter, SettingsViewModel, PickerBottomSheet, DictusToggle]
  patterns: [HiltViewModel+DataStore, EntryPointAccessors, stateIn(Eagerly), ModalBottomSheet]
key_files:
  created:
    - core/src/main/java/dev/pivisolutions/dictus/core/logging/FileLoggingTree.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/LogExporter.kt
    - app/src/main/res/xml/file_paths.xml
    - app/src/test/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModelTest.kt
    - app/src/test/java/dev/pivisolutions/dictus/ui/settings/LogExportTest.kt
  modified:
    - core/src/main/java/dev/pivisolutions/dictus/core/logging/TimberSetup.kt
    - app/src/main/java/dev/pivisolutions/dictus/DictusApplication.kt
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
    - core/src/test/java/dev/pivisolutions/dictus/core/logging/TimberSetupTest.kt
decisions:
  - "Used SharingStarted.Eagerly for SettingsViewModel StateFlows — WhileSubscribed requires active subscribers which makes unit tests with .value assertions fail without a subscriber setup"
  - "Used DictationServiceEntryPoint (EntryPointAccessors) instead of @AndroidEntryPoint for DataStore access in DictationService — preserves LocalBinder pattern, same approach as existing Hilt patterns in codebase"
  - "Split LogExporter.exportLogs() into createZip() + FileProvider URI call — FileProvider.getUriForFile() cannot be tested in Robolectric unit tests without a registered authority; createZip() is testable independently"
  - "Used UnconfinedTestDispatcher + Dispatchers.setMain for SettingsViewModelTest — required for viewModelScope coroutines to run eagerly and make .value assertions synchronous"
metrics:
  duration: 10 min
  completed_date: "2026-03-26"
  tasks: 2
  files: 13
---

# Phase 04 Plan 04: Settings Screen + Logging Summary

**One-liner:** Settings screen with 3-section DataStore-backed UI, file-based Timber logging to dictus.log, ZIP log export via FileProvider share sheet, and DataStore-driven model/language selection in DictationService replacing hard-coded constants.

## What Was Built

### Task 1 (TDD): Backend — FileLoggingTree, LogExporter, SettingsViewModel, DataStore Wiring

**FileLoggingTree** (`core/.../logging/FileLoggingTree.kt`):
- Extends `Timber.Tree`, writes `timestamp [LEVEL/tag] message` lines to a `File`
- Thread-safe via `synchronized(logFile)` — coroutine log calls from multiple threads cannot interleave

**TimberSetup** (updated `core/.../logging/TimberSetup.kt`):
- New signature: `init(isDebug: Boolean, filesDir: File)` — plants `DebugTree` (debug builds) + `FileLoggingTree` (always)
- `getLogFile(): File?` — used by `SettingsScreen` to locate the log for export

**DictusApplication** (updated): passes `filesDir` to `TimberSetup.init()`.

**LogExporter** (`app/.../ui/settings/LogExporter.kt`):
- `createZip(context, logFile): File?` — bundles `dictus.log` into `cacheDir/dictus-logs.zip`
- `exportLogs(context, logFile): Uri?` — calls `createZip()` then `FileProvider.getUriForFile()`
- `createShareIntent(uri)` — builds `ACTION_SEND` intent with `FLAG_GRANT_READ_URI_PERMISSION`
- `file_paths.xml` + `FileProvider` manifest entry enable `content://` URI sharing

**SettingsViewModel** (updated `app/.../ui/settings/SettingsViewModel.kt`):
- Hilt-injected `DataStore<Preferences>` and `ModelManager`
- 5 `StateFlow`s: `language`, `activeModel`, `hapticsEnabled`, `soundEnabled`, `keyboardLayout`
- `downloadedModels: StateFlow<List<String>>` — IO-dispatched scan via `ModelManager.getDownloadedModels()`
- Write functions: `setLanguage`, `setActiveModel`, `toggleHaptics`, `toggleSound`, `setKeyboardLayout`
- Uses `SharingStarted.Eagerly` so `.value` is correct without active subscribers (testability)

**DictationService** (updated):
- Removed `private const val LANGUAGE = "fr"` hard-coded constant
- Added `DictationServiceEntryPoint` (@EntryPoint) to access Hilt's `DataStore` singleton
- `confirmAndTranscribe()` now reads `ACTIVE_MODEL` and `TRANSCRIPTION_LANGUAGE` from DataStore at transcription time
- "auto" language maps to `null` for whisper.cpp (auto-detect), falls back to "fr"

**Tests — 11 total, all green:**
- `SettingsViewModelTest`: 8 tests covering defaults and write operations
- `LogExportTest`: 3 tests covering ZIP creation and null-when-missing
- `TimberSetupTest`: updated for new 2-tree (debug) / 1-tree (non-debug) behavior

### Task 2: SettingsScreen UI

Full 3-section settings screen replacing the placeholder composable.

**Section TRANSCRIPTION:**
- Language picker row — ModalBottomSheet with "Automatique/fr/en" radio options
- Active model picker row — ModalBottomSheet with downloaded models; disabled when empty

**Section CLAVIER:**
- Keyboard layout picker — ModalBottomSheet with AZERTY/QWERTY options
- Haptics toggle — custom `DictusToggle` (51x31dp, on=#22C55E, off=#6B6B70, animated knob)
- Sound toggle — same custom toggle, defaults off

**Section A PROPOS:**
- Version info row — `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE`
- Export logs action — calls `LogExporter.exportLogs()` then `startActivity(createChooser(...))`; shows Toast on failure
- GitHub link — opens browser via `ACTION_VIEW`
- Legal mentions link — opens `pivisolutions.dev/legal`

**Components:**
- `DictusToggle` — custom Composable, 51x31dp rounded rect, animated knob position via `animateDpAsState`
- `PickerBottomSheet` — reusable generic sheet accepting `List<Pair<String,String>>` options
- `SectionHeader`, `SettingPickerRow`, `SettingToggleRow`, `SettingInfoRow`, `SettingActionRow`, `SettingLinkRow`

**AppNavHost** updated: `SettingsScreen()` is called with no parameters (all state via `hiltViewModel()`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Deprecated `Divider` API**
- **Found during:** Task 2 build
- **Issue:** Material 3 renamed `Divider` to `HorizontalDivider`; compiler warning
- **Fix:** Replaced import and call with `HorizontalDivider`
- **Files modified:** `SettingsScreen.kt`
- **Commit:** b5e85ab

**2. [Rule 2 - Critical] LogExporter.exportLogs() split into createZip() + URI**
- **Found during:** Task 1 TDD — LogExportTest
- **Issue:** `FileProvider.getUriForFile()` throws `IllegalArgumentException` in Robolectric unit tests (authority not registered in test profile)
- **Fix:** Extracted `createZip()` as a separately testable method; `exportLogs()` delegates to it
- **Files modified:** `LogExporter.kt`, `LogExportTest.kt`
- **Commit:** 29051ca

**3. [Rule 2 - Critical] SharingStarted.Eagerly for SettingsViewModel**
- **Found during:** Task 1 TDD — SettingsViewModelTest
- **Issue:** `WhileSubscribed(5000)` does not start the upstream flow unless there's an active subscriber. Tests checking `.value` directly had no subscriber, so writes were not reflected.
- **Fix:** Changed to `SharingStarted.Eagerly` — correct for a screen ViewModel that should track preferences immediately on creation
- **Files modified:** `SettingsViewModel.kt`
- **Commit:** 29051ca

**4. [Rule 3 - Missing] FakeDataStore test helper added**
- **Found during:** Task 1 TDD
- **Issue:** No DataStore test double existed; Hilt injection not available in unit tests
- **Fix:** Implemented `FakeDataStore` (in `SettingsViewModelTest.kt`) backed by `MutableStateFlow<Preferences>`
- **Files modified:** `SettingsViewModelTest.kt`
- **Commit:** 29051ca

**5. [Rule 2 - Critical] File location: `ui/settings` package instead of `settings`**
- **Found during:** Task 1 start — pre-existing `SettingsScreen.kt` and `SettingsViewModel.kt` were in `ui/settings`, not `settings`
- **Fix:** Created all new files in `ui/settings` to match existing package structure and avoid breaking `AppNavHost` imports
- **Impact:** No functional impact; consistent with Phase 04 Plans 02+03

## Self-Check: PASSED

| Item | Status |
|------|--------|
| FileLoggingTree.kt | FOUND |
| LogExporter.kt | FOUND |
| SettingsScreen.kt | FOUND |
| SettingsViewModel.kt | FOUND |
| file_paths.xml | FOUND |
| SettingsViewModelTest.kt | FOUND |
| LogExportTest.kt | FOUND |
| Commit 29051ca (Task 1) | FOUND |
| Commit b5e85ab (Task 2) | FOUND |
| assembleDebug | BUILD SUCCESSFUL |
| All unit tests | 11/11 PASS |
