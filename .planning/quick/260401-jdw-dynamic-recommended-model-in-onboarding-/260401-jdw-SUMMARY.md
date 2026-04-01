---
phase: quick
plan: 260401-jdw
subsystem: onboarding
tags: [model-recommendation, ram-detection, onboarding, parakeet, whisper]

requires:
  - phase: 09-parakeet-integration
    provides: ModelCatalog with Parakeet entries, DownloadProgress.Extracting state
provides:
  - RAM-based model recommendation in onboarding (>= 8GB -> Parakeet 0.6B, < 8GB -> Small Q5)
  - Dynamic model card in OnboardingModelDownloadScreen
  - Extraction UI for Parakeet archive downloads during onboarding
affects: [onboarding, model-download]

tech-stack:
  added: []
  patterns: [ActivityManager.MemoryInfo for RAM detection, dynamic composable parameters from ModelInfo]

key-files:
  created: []
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
    - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt
    - app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values-fr/strings.xml
    - app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt

key-decisions:
  - "ActivityManager.MemoryInfo.totalMem for RAM detection: stable across reboots, available since API 16"
  - "8 GB threshold for Parakeet 0.6B vs Small Q5: matches minRamGb in ModelCatalog"
  - "isExtracting as separate StateFlow (not collapsed into downloadProgress): cleaner UI state separation for extraction phase"

patterns-established:
  - "recommendedModelKey(context) in ModelCatalog: centralized device-aware model selection"
  - "Dynamic composable parameters from ModelInfo: no hardcoded model data in UI"

requirements-completed: [dynamic-recommended-model]

duration: 4min
completed: 2026-04-01
---

# Quick Task 260401-jdw: Dynamic Recommended Model in Onboarding Summary

**RAM-based onboarding model recommendation: >= 8GB devices get Parakeet 0.6B, < 8GB get Small Q5, with dynamic model card and extraction UI**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-01T12:01:12Z
- **Completed:** 2026-04-01T12:04:58Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- ModelCatalog.recommendedModelKey(context) selects model based on device RAM (>= 8GB -> parakeet-tdt-0.6b-v3, < 8GB -> small-q5_1)
- OnboardingViewModel uses recommended model for download and DataStore persistence instead of hardcoded DEFAULT_KEY
- OnboardingModelDownloadScreen dynamically displays model name, size, and quality from ModelInfo
- Extraction phase UI shows full progress bar with "Extracting model files..." for Parakeet archive downloads
- Dead hardcoded strings removed (onboarding_model_download_size, onboarding_model_download_fast)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add recommendedModelKey() and wire OnboardingViewModel** - `79c5a8a` (feat)
2. **Task 2: Make OnboardingModelDownloadScreen dynamic and handle extraction UI** - `02f00ae` (feat)
3. **Task 3: Run tests and verify build** - verification only, no commit needed

## Files Created/Modified
- `app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt` - Added recommendedModelKey(context) to ModelCatalog
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt` - Context injection, recommendedKey/recommendedModelInfo properties, isExtracting state
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt` - Dynamic model card params, extraction UI
- `app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt` - Pass model info and isExtracting to download screen
- `app/src/main/res/values/strings.xml` - Added extracting string, removed dead strings
- `app/src/main/res/values-fr/strings.xml` - Added extracting string, removed dead strings
- `app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt` - Updated constructor calls with context parameter

## Decisions Made
- Used ActivityManager.MemoryInfo.totalMem for RAM detection (stable, available since API 16)
- 8 GB threshold aligns with ModelInfo.minRamGb for Parakeet 0.6B
- isExtracting as separate StateFlow for clean extraction phase UI (progress stays at 99% during extraction, not 100%)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

---
*Plan: quick/260401-jdw*
*Completed: 2026-04-01*

## Self-Check: PASSED
