---
phase: 03-whisper-integration
plan: 01
subsystem: whisper
tags: [whisper.cpp, ndk, cmake, jni, native, ggml, arm-neon, fp16]

# Dependency graph
requires:
  - phase: 02-audio-recording
    provides: AudioCaptureManager with 16kHz mono Float32 capture
provides:
  - whisper/ Android library module with CMake/NDK native build
  - JNI bridge with Dictus package names and language parameter
  - WhisperContext thread-safe Kotlin wrapper for transcription
  - WhisperLib JNI declarations with multi-variant native library loading
  - WhisperCpuConfig for optimal thread count detection
  - whisper.cpp git submodule at third_party/whisper.cpp
affects: [03-02, 03-03, 04-model-management]

# Tech tracking
tech-stack:
  added: [whisper.cpp v1.8.4, NDK 27.2, CMake 3.22.1, kotlinx-coroutines-android 1.8.1]
  patterns: [separate native library module, single-thread coroutine dispatcher for JNI, multi-variant .so loading with fallback chain]

key-files:
  created:
    - whisper/build.gradle.kts
    - whisper/src/main/jni/whisper/CMakeLists.txt
    - whisper/src/main/jni/whisper/jni.c
    - whisper/src/main/java/dev/pivisolutions/dictus/whisper/WhisperLib.kt
    - whisper/src/main/java/dev/pivisolutions/dictus/whisper/WhisperContext.kt
    - whisper/src/main/java/dev/pivisolutions/dictus/whisper/WhisperCpuConfig.kt
    - whisper/src/main/AndroidManifest.xml
  modified:
    - settings.gradle.kts
    - gradle/libs.versions.toml

key-decisions:
  - "Used NDK 27.2.12479018 instead of planned 25.2.9519653 (corrupted install, download failures)"
  - "Added kotlinx-coroutines-android as explicit whisper module dependency (not transitively provided)"
  - "Kept user-added okhttp version catalog entry for future Phase 3 plans"
  - "Adapted reference CMakeLists.txt using FetchContent for ggml (matches upstream pattern)"

patterns-established:
  - "Native module isolation: whisper/ module contains all NDK/CMake/JNI code, app depends on it as library"
  - "Single-thread dispatcher: WhisperContext uses Executors.newSingleThreadExecutor() for thread-safe JNI access"
  - "Multi-variant .so loading: fp16 > vfpv4 > default fallback chain in WhisperLib init block"

requirements-completed: []

# Metrics
duration: 15min
completed: 2026-03-25
---

# Phase 03 Plan 01: Whisper Native Module Summary

**whisper.cpp native library module with NDK/CMake build, JNI bridge (language-aware), and thread-safe Kotlin wrapper producing libwhisper*.so for arm64-v8a and armeabi-v7a**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-25T21:30:53Z
- **Completed:** 2026-03-25T21:46:00Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- whisper.cpp added as git submodule (v1.8.4) and compiles to native .so libraries via NDK/CMake
- JNI bridge adapted from reference with Dictus package names and language parameter for multilingual transcription
- Thread-safe WhisperContext Kotlin wrapper with single-thread dispatcher, ready for use by DictationService
- Three native library variants built: libwhisper_v8fp16_va.so (ARM fp16), libwhisper_vfpv4.so (ARMv7 NEON), libwhisper.so (default)

## Task Commits

Each task was committed atomically:

1. **Task 1: whisper.cpp git submodule + whisper module scaffold + CMake/NDK build** - `4c26b6f` (feat)
2. **Task 2: Kotlin wrapper (WhisperLib, WhisperContext, WhisperCpuConfig) + build verification** - `68edd20` (feat)

## Files Created/Modified
- `whisper/build.gradle.kts` - Android library module with NDK/CMake configuration
- `whisper/src/main/jni/whisper/CMakeLists.txt` - CMake build pointing to whisper.cpp submodule with fp16/vfpv4 variants
- `whisper/src/main/jni/whisper/jni.c` - JNI bridge with Dictus package names and language parameter
- `whisper/src/main/java/.../WhisperLib.kt` - JNI declarations with multi-variant native library loading
- `whisper/src/main/java/.../WhisperContext.kt` - Thread-safe Kotlin wrapper with single-thread dispatcher
- `whisper/src/main/java/.../WhisperCpuConfig.kt` - Optimal thread count detection from /proc/cpuinfo
- `whisper/src/main/AndroidManifest.xml` - Minimal library manifest
- `settings.gradle.kts` - Added :whisper module
- `gradle/libs.versions.toml` - Added NDK, CMake versions, coroutines-android, okhttp entries
- `.gitmodules` - whisper.cpp submodule reference

## Decisions Made
- **NDK 27.2 instead of 25.2:** NDK 25.2.9519653 had a corrupted install (missing source.properties) and re-download consistently failed with zip corruption. NDK 27.2.12479018 installed successfully and is fully compatible with whisper.cpp. Updated libs.versions.toml accordingly.
- **Explicit coroutines dependency:** WhisperContext uses kotlinx.coroutines (CoroutineScope, withContext). Unlike app/core modules that get coroutines transitively via lifecycle-runtime-ktx, the whisper module needs it explicitly. Added coroutines-android to the version catalog.
- **Kept okhttp catalog entry:** The user added okhttp = "4.12.0" to libs.versions.toml during the session (likely preparing for Plan 03-02 model download). Preserved this addition.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NDK 25.2.9519653 corrupted, switched to 27.2.12479018**
- **Found during:** Task 2 (build verification)
- **Issue:** NDK directory existed but was empty (only .installer stub). Download attempts failed with "Error reading Zip content from a SeekableByteChannel"
- **Fix:** Installed NDK 27.2.12479018 which downloaded and extracted successfully. Updated libs.versions.toml ndk version.
- **Files modified:** gradle/libs.versions.toml
- **Verification:** ./gradlew :whisper:assembleDebug BUILD SUCCESSFUL
- **Committed in:** 68edd20 (Task 2 commit)

**2. [Rule 3 - Blocking] Missing kotlinx-coroutines-android dependency**
- **Found during:** Task 2 (build verification)
- **Issue:** WhisperContext.kt uses CoroutineScope/withContext but whisper module had no coroutines dependency
- **Fix:** Added coroutines version and coroutines-android library to version catalog, added implementation dependency to whisper/build.gradle.kts
- **Files modified:** gradle/libs.versions.toml, whisper/build.gradle.kts
- **Verification:** ./gradlew :whisper:assembleDebug BUILD SUCCESSFUL
- **Committed in:** 68edd20 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes necessary for compilation. NDK version change has no functional impact (27.2 is newer and compatible). No scope creep.

## Issues Encountered
- NDK 25.2.9519653 zip download consistently corrupted on this machine. Root cause unclear (possibly incomplete previous install that left a stub directory). Resolved by using NDK 27.2 instead.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- whisper/ module compiles and produces native .so libraries for both ARM ABIs
- WhisperContext.createFromFile() + transcribeData() API ready for integration in Plan 03-02/03-03
- Model download and DictationService wiring are next (Plans 02 and 03)
- On-device validation with actual model file needed in Plan 03-02

---
*Phase: 03-whisper-integration*
*Completed: 2026-03-25*
