---
phase: 03-whisper-integration
plan: 02
subsystem: audio
tags: [whisper, transcription, state-machine, model-manager, tdd]

# Dependency graph
requires:
  - phase: 02-audio-recording-service-architecture
    provides: DictationState sealed class, DictationController interface, DictationService
provides:
  - DictationState.Transcribing variant for transcription pipeline
  - TranscriptionEngine interface abstracting whisper.cpp
  - TextPostProcessor matching iOS punctuation rules
  - ModelManager for HuggingFace model path resolution and download URLs
  - confirmAndTranscribe() method on DictationController
affects: [03-whisper-integration Plan 03 (wiring), 04-model-management]

# Tech tracking
tech-stack:
  added: [okhttp 4.12.0 (catalog only, not wired)]
  patterns: [TDD red-green for utility classes, object singleton for stateless processors]

key-files:
  created:
    - core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/whisper/TextPostProcessor.kt
    - core/src/test/java/dev/pivisolutions/dictus/core/whisper/TextPostProcessorTest.kt
    - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    - app/src/test/java/dev/pivisolutions/dictus/model/ModelManagerTest.kt
  modified:
    - core/src/main/java/dev/pivisolutions/dictus/core/service/DictationState.kt
    - core/src/main/java/dev/pivisolutions/dictus/core/service/DictationController.kt
    - core/src/test/java/dev/pivisolutions/dictus/core/service/FakeDictationController.kt
    - app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
    - app/src/main/java/dev/pivisolutions/dictus/ui/RecordingTestArea.kt
    - gradle/libs.versions.toml

key-decisions:
  - "Used q5_1 instead of q5_0 for small model -- HuggingFace provides pre-built q5_1, slightly more accurate"
  - "NDK updated from 25.2.9519653 to 27.2.12479018 to fix missing source.properties"

patterns-established:
  - "TDD red-green for utility/model classes: write tests first, then implement"
  - "TextPostProcessor as object singleton: stateless processor, no DI needed"
  - "ModelManager size-validated path resolution: detect corrupt/partial downloads"

requirements-completed: [DICT-03, DICT-04]

# Metrics
duration: 14min
completed: 2026-03-25
---

# Phase 03 Plan 02: Whisper Contracts Summary

**Kotlin-side transcription contracts: DictationState.Transcribing, TranscriptionEngine interface, TextPostProcessor with iOS-matching punctuation, ModelManager with HuggingFace URL resolution -- 17 TDD tests green**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-25T21:30:51Z
- **Completed:** 2026-03-25T21:45:26Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- DictationState extended with Transcribing variant and updated state transition docs
- TranscriptionEngine interface provides clean abstraction for whisper.cpp (core module, no native dependency)
- TextPostProcessor matches iOS DictationCoordinator.swift:368-372 punctuation rules (8 tests)
- ModelManager resolves paths and HuggingFace URLs for tiny and small-q5_1 models (9 tests)
- DictationController gains confirmAndTranscribe() suspend method for full Recording->Transcribing->Idle flow

## Task Commits

Each task was committed atomically (TDD: RED then GREEN):

1. **Task 1 RED: TextPostProcessor tests** - `5a0ddff` (test)
2. **Task 1 GREEN: DictationState + TranscriptionEngine + TextPostProcessor** - `4133671` (feat)
3. **Task 2 RED: ModelManager tests** - `2993154` (test)
4. **Task 2 GREEN: ModelManager + OkHttp catalog** - `e704470` (feat)

## Files Created/Modified
- `core/.../whisper/TranscriptionEngine.kt` - Interface abstracting whisper.cpp (initialize, transcribe, release)
- `core/.../whisper/TextPostProcessor.kt` - Stateless post-processor matching iOS punctuation rules
- `core/.../whisper/TextPostProcessorTest.kt` - 8 test cases for empty/whitespace/punctuation handling
- `core/.../service/DictationState.kt` - Added Transcribing data object, updated KDoc
- `core/.../service/DictationController.kt` - Added confirmAndTranscribe() suspend method
- `core/.../service/FakeDictationController.kt` - Updated test double with confirmAndTranscribe()
- `app/.../model/ModelManager.kt` - Model path resolution, size validation, HuggingFace URLs
- `app/.../model/ModelManagerTest.kt` - 9 Robolectric tests for path/URL/size logic
- `app/.../service/DictationService.kt` - Stub confirmAndTranscribe() for compilation
- `ime/.../DictusImeService.kt` - Transcribing branch in both when() expressions
- `app/.../ui/RecordingTestArea.kt` - Transcribing branch for sealed class exhaustiveness
- `gradle/libs.versions.toml` - OkHttp 4.12.0 added, NDK updated to 27.2.12479018

## Decisions Made
- Used q5_1 instead of q5_0 for the small model: HuggingFace hosts pre-built q5_1, which is slightly more accurate with negligible perf difference
- NDK version updated from 25.2.9519653 to 27.2.12479018: the old version's directory was empty/corrupt, preventing all Gradle builds

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] DictationService stub for confirmAndTranscribe()**
- **Found during:** Task 1 (GREEN phase)
- **Issue:** DictationService implements DictationController, adding confirmAndTranscribe() to the interface breaks compilation
- **Fix:** Added placeholder implementation that transitions through Transcribing state and returns null
- **Files modified:** app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt
- **Verification:** Project compiles, test suites pass
- **Committed in:** 4133671 (Task 1 GREEN commit)

**2. [Rule 3 - Blocking] Sealed class exhaustiveness in DictusImeService**
- **Found during:** Task 1/2 (GREEN phase)
- **Issue:** Two when() expressions on DictationState in IME module became non-exhaustive after adding Transcribing
- **Fix:** Added Transcribing branches: no-op for mic tap handler, static RecordingScreen for keyboard content
- **Files modified:** ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
- **Verification:** Project compiles
- **Committed in:** 4133671, 2993154

**3. [Rule 3 - Blocking] Sealed class exhaustiveness in RecordingTestArea**
- **Found during:** Task 2 (RED phase compilation)
- **Issue:** RecordingTestArea.kt when() on DictationState non-exhaustive after Transcribing added
- **Fix:** Added Transcribing branch showing "State: Transcribing..." text
- **Files modified:** app/src/main/java/dev/pivisolutions/dictus/ui/RecordingTestArea.kt
- **Verification:** Project compiles
- **Committed in:** 2993154

**4. [Rule 3 - Blocking] NDK 25.2.9519653 not installed / corrupt**
- **Found during:** Task 1 (GREEN phase - tests would not run)
- **Issue:** whisper module references NDK 25.2.9519653 but directory was empty (no source.properties)
- **Fix:** Temporarily excluded :whisper from settings.gradle.kts; user/linter subsequently updated NDK to 27.2.12479018
- **Files modified:** settings.gradle.kts, gradle/libs.versions.toml
- **Verification:** Gradle configuration succeeds, all tests pass
- **Committed in:** 4133671, e704470

---

**Total deviations:** 4 auto-fixed (all Rule 3 - blocking)
**Impact on plan:** All fixes necessary for compilation. No scope creep. Plan 03 will replace the DictationService stub with real transcription wiring.

## Issues Encountered
- NDK download from sdkmanager failed twice with "Error reading Zip content from a SeekableByteChannel" -- resolved by user updating to NDK 27.2.12479018
- Pre-existing warnings in DictationServiceTest.kt (instance checks always true) -- not addressed, out of scope

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All contracts defined: TranscriptionEngine, TextPostProcessor, ModelManager, confirmAndTranscribe()
- Plan 03 can now focus purely on wiring: JNI bridge, DictationService implementation, IME text insertion
- OkHttp version in catalog ready for Plan 03 to add as dependency for model download

---
*Phase: 03-whisper-integration*
*Completed: 2026-03-25*
