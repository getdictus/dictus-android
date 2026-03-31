---
phase: 09-parakeet-integration
plan: 02
subsystem: model-catalog
tags: [parakeet, sherpa-onnx, onnx, model-catalog, tar-extraction, commons-compress]

# Dependency graph
requires:
  - phase: 09-parakeet-integration
    provides: AiProvider.PARAKEET enum value in ModelManager.kt; SttProvider interface in core/stt/
provides:
  - "ModelCatalog.ALL with 7 entries (4 Whisper + 3 Parakeet)"
  - "ModelCatalog.downloadUrl() PARAKEET branch returning sherpa-onnx GitHub Releases URLs"
  - "ModelCatalog.isDirectoryModel() extension for provider-aware path layout"
  - "ModelManager.getModelPath/getTargetPath/deleteModel/storageUsedBytes/modelFileSize handling directory models"
  - "ModelDownloader.extractTarBz2() for commons-compress BZip2+TAR extraction"
  - "ModelDownloader.ensureModelAvailable() + downloadWithProgress() Parakeet archive flow"
affects: [09-03-parakeet-provider, 09-04-model-cards-ui, 09-05-engine-swap]

# Tech tracking
tech-stack:
  added:
    - "commons-compress:1.26.1 (Apache Commons, BZip2+TAR extraction without system tar binary)"
  patterns:
    - "Directory-model layout: models/{key}/{fileName} for Parakeet vs models/{fileName} for Whisper"
    - "isDirectoryModel() extension function on ModelInfo gated by AiProvider enum"
    - "Archive download + extract pattern in ModelDownloader: download to .archive.tmp, extract via commons-compress, delete archive"

key-files:
  created: []
  modified:
    - "app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt"
    - "app/src/main/java/dev/pivisolutions/dictus/service/ModelDownloader.kt"
    - "app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt"
    - "app/build.gradle.kts"

key-decisions:
  - "commons-compress:1.26.1 chosen over system tar command: pure-Java, works on all Android API levels, no toybox dependency"
  - "isDirectoryModel() defined as extension inside ModelCatalog object: avoids adding Android-unaware logic to ModelInfo data class itself"
  - "Download URLs use sherpa-onnx GitHub Releases (asr-models tag): stable versioned URLs, confirmed by research document"
  - "extractTarBz2 strips parent directory component from archive entries using File(entry.name).name: handles arbitrary archive root directory names"

patterns-established:
  - "Pattern: isDirectoryModel() used as gate for all path-construction branches in ModelManager and ModelDownloader"
  - "Pattern: Archive download uses .archive.tmp suffix (distinct from .tmp for direct file downloads)"

requirements-completed: [STT-02, STT-04]

# Metrics
duration: 6min
completed: 2026-03-31
---

# Phase 9 Plan 02: Model Catalog + Downloader Summary

**3 Parakeet models added to ModelCatalog with sherpa-onnx GitHub Releases download URLs, directory-based path layout in ModelManager, and tar.bz2 extraction via commons-compress in ModelDownloader**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-03-31T16:17:49Z
- **Completed:** 2026-03-31T16:23:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- ModelCatalog.ALL extended from 4 Whisper entries to 7 (4 Whisper + 3 Parakeet: 110M INT8, 110M FP16, 0.6B TDT v2)
- downloadUrl() PARAKEET branch implemented with real sherpa-onnx GitHub Releases tar.bz2 URLs
- ModelManager updated to resolve models/{key}/{fileName} paths for Parakeet (vs flat models/{fileName} for Whisper)
- ModelDownloader gains extractTarBz2() using commons-compress; ensureModelAvailable() and downloadWithProgress() both handle the archive download + extract flow
- ModelCatalogTest updated: 2 conflicting tests fixed, 7 new Parakeet-specific assertions added; all 14 tests green

## Task Commits

1. **Task 1: Add Parakeet catalog entries + directory-based model support** - `eb92fa2` (feat)
2. **Task 2: Extend ModelCatalogTest with Parakeet assertions** - `89879d2` (test)

## Files Created/Modified

- `app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt` - 3 Parakeet ModelInfo entries, PARAKEET downloadUrl branch, isDirectoryModel() extension, directory-aware path methods
- `app/src/main/java/dev/pivisolutions/dictus/service/ModelDownloader.kt` - commons-compress imports, extractTarBz2() method, archive-aware ensureModelAvailable() and downloadWithProgress()
- `app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt` - fixed 2 stale tests, added 7 Parakeet assertions
- `app/build.gradle.kts` - added commons-compress:1.26.1 dependency

## Decisions Made

- Used commons-compress over system `tar` command: pure-Java implementation works on all Android versions without relying on Android's toybox tar binary, which may not be available on all devices.
- `isDirectoryModel()` declared as extension inside `ModelCatalog` object: keeps the provider-layout decision colocated with catalog logic, avoids adding Android-unaware methods to the `ModelInfo` data class.
- Download URLs use sherpa-onnx GitHub Releases `asr-models` tag: stable, versioned, immutable once published.
- `extractTarBz2` uses `File(entry.name).name` to strip the archive's root directory prefix (e.g., `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8/model.int8.onnx` → `model.int8.onnx`), tolerating any archive directory name.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

The existing `ModelCatalogTest` had two tests that directly contradicted the new data: `catalog has exactly 4 entries` and `all models have WHISPER provider`. These were updated as part of Task 2 (expected work - the plan's Task 2 was to extend the test file).

## Next Phase Readiness

- Data layer complete: Parakeet models are in the catalog with real download URLs and directory-aware path resolution
- Downloader can handle tar.bz2 archives — ready to be exercised once sherpa-onnx `asr/` module is wired
- Next: Plan 09-03 can implement ParakeetProvider using the model paths resolved by this plan's ModelManager changes

---
*Phase: 09-parakeet-integration*
*Completed: 2026-03-31*

## Self-Check: PASSED

- FOUND: ModelManager.kt
- FOUND: ModelDownloader.kt
- FOUND: ModelCatalogTest.kt
- FOUND: 09-02-SUMMARY.md
- FOUND commit: eb92fa2
- FOUND commit: 89879d2
