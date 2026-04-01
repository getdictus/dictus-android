---
phase: 07-foundation
plan: 02
subsystem: stt
tags: [kotlin, interface, stt, whisper, refactoring]

# Dependency graph
requires:
  - phase: 07-01
    provides: Cleaned-up tech debt baseline with green 91/91 debug test suite
provides:
  - SttProvider interface in core/stt/ with providerId, displayName, supportedLanguages metadata
  - WhisperProvider implementing SttProvider (wraps whisper.cpp)
  - DictationService decoupled from WhisperProvider via SttProvider abstraction
affects:
  - 09-parakeet-integration (will implement SttProvider as ParakeetProvider)
  - Any phase touching DictationService engine wiring

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Provider pattern: SttProvider interface in core/, concrete implementations in app/"
    - "Metadata on interface: providerId/displayName/supportedLanguages for engine selection UI"

key-files:
  created:
    - core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt
    - app/src/main/java/dev/pivisolutions/dictus/service/WhisperProvider.kt
  modified:
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt

key-decisions:
  - "SttProvider placed in core/stt/ (not core/whisper/) to make engine-neutral nature explicit"
  - "Empty supportedLanguages list means all languages supported — non-empty means restriction (Parakeet = English-only)"
  - "DictationService field renamed from transcriptionEngine to sttProvider for clarity"
  - "TranscriptionEngine.kt deleted — no longer referenced by any file; WhisperTranscriptionEngine.kt deleted — replaced by WhisperProvider"

patterns-established:
  - "SttProvider pattern: new STT engines implement SttProvider and are wired in DictationService"
  - "providerId='whisper' convention: stable machine-readable identifier for settings persistence"

requirements-completed: [STT-01]

# Metrics
duration: 8min
completed: 2026-03-30
---

# Phase 7 Plan 02: SttProvider Interface Summary

**SttProvider interface extracted to core/stt/ with providerId/displayName/supportedLanguages metadata; WhisperProvider replaces WhisperTranscriptionEngine; DictationService wired to SttProvider abstraction — ready for Phase 9 Parakeet implementation.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-30T12:54:23Z
- **Completed:** 2026-03-30T13:02:25Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 1 modified, 1 deleted)

## Accomplishments

- Created `SttProvider` interface in `core/stt/` with all required metadata fields for Phase 9 engine selection UI
- Created `WhisperProvider` implementing `SttProvider`, replacing `WhisperTranscriptionEngine` with updated log messages
- Updated `DictationService` to use `SttProvider` type and `sttProvider` field name (all 4 call sites updated)
- Deleted legacy `TranscriptionEngine.kt` and `WhisperTranscriptionEngine.kt` (both unreferenced after migration)
- Debug test suite: 91/91 passing; compilation: BUILD SUCCESSFUL

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SttProvider interface and WhisperProvider implementation** - `9aa59ba` (feat)
2. **Task 2: Remove legacy TranscriptionEngine.kt** - `7e4d632` (feat)

Note: DictationService.kt was already updated in a prior session (commit `5cd53a2`) as a partial forward-completion of this plan.

## Files Created/Modified

- `core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt` - Engine-neutral STT contract with providerId, displayName, supportedLanguages, isReady, initialize, transcribe, release
- `app/src/main/java/dev/pivisolutions/dictus/service/WhisperProvider.kt` - Whisper implementation of SttProvider (providerId="whisper", all languages supported)
- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` - sttProvider: SttProvider = WhisperProvider() replacing transcriptionEngine
- `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt` - DELETED (replaced by SttProvider)
- `app/src/main/java/dev/pivisolutions/dictus/service/WhisperTranscriptionEngine.kt` - DELETED (replaced by WhisperProvider)

## Decisions Made

- SttProvider placed in `core/stt/` (not `core/whisper/`) to make its engine-neutral nature explicit — a Parakeet implementation should not live under a "whisper" package
- Empty `supportedLanguages` list means "all languages supported" (Whisper), non-empty means restriction (Parakeet will be `listOf("en")`)
- `TranscriptionEngine.kt` was re-added by a previous 07-03 session for "build compatibility" but was confirmed unreferenced — safely deleted

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] KSP incremental build cache corruption after file deletion**
- **Found during:** Task 2 (compilation verification)
- **Issue:** `java.lang.IllegalStateException: Storage for file-to-id.tab is already registered` — KSP caches from prior builds conflicted with the changed file set
- **Fix:** Used `--rerun-tasks` flag to bypass stale KSP caches
- **Files modified:** None (build system cache only)
- **Verification:** `./gradlew :app:compileDebugKotlin :core:compileDebugKotlin` BUILD SUCCESSFUL
- **Committed in:** No separate commit needed (build-time issue only)

**2. [Rule 1 - Bug] TranscriptionEngine.kt re-added by 07-03 session as untracked file**
- **Found during:** Task 1 (post-deletion verification)
- **Issue:** Prior session restored TranscriptionEngine.kt claiming "build compatibility" but the file was untracked and is not referenced by any code
- **Fix:** Permanently deleted from disk and staged `git rm` for the tracked copy
- **Files modified:** `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt` (deleted)
- **Verification:** `grep -r "TranscriptionEngine" app/src/ core/src/` returns no matches; BUILD SUCCESSFUL
- **Committed in:** `7e4d632`

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes necessary to complete the plan goals. No scope creep.

## Issues Encountered

- `WhisperTranscriptionEngine.kt` was in an inconsistent state: not tracked by git index but present on disk (it was deleted from git tracking in commit `7fd2bc3` but the file persisted as untracked). Deleted from disk directly.
- Release variant tests (ImeStatusCardTest, RecordingTestAreaTest — 9 failures) are pre-existing as documented in STATE.md. Debug baseline 91/91 green is unchanged.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- STT-01 complete: SttProvider interface is the extensibility gate for Phase 9 Parakeet integration
- Phase 9 (Parakeet) can now implement `ParakeetProvider : SttProvider` and wire it in DictationService with a one-line change
- No blockers for remaining Phase 7 plans

---
*Phase: 07-foundation*
*Completed: 2026-03-30*
