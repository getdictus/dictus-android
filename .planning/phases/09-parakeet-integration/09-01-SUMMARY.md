---
phase: 09-parakeet-integration
plan: 01
subsystem: asr
tags: [sherpa-onnx, onnxruntime, parakeet, nemo-ctc, android-jni, native-libs]

# Dependency graph
requires:
  - phase: 07-foundation
    provides: SttProvider interface in core/stt/ used by ParakeetProvider
provides:
  - asr/ Gradle module with sherpa-onnx v1.12.34 native libs (arm64-v8a, armeabi-v7a)
  - ParakeetProvider implementing SttProvider via sherpa-onnx OfflineRecognizer (NeMo CTC)
  - Native library coexistence validated: whisper.cpp + sherpa-onnx build without conflicts
affects:
  - 09-02 (Parakeet model catalog, dynamic provider wiring to DictationService)
  - 09-03 (Model download flow for tar.bz2 Parakeet archives)

# Tech tracking
tech-stack:
  added:
    - sherpa-onnx v1.12.34 (JNI: libsherpa-onnx-jni.so + libonnxruntime.so)
    - commons-compress 1.26.1 (BZip2+TAR extraction for Parakeet model archives)
  patterns:
    - asr/ module pattern: Android library with jniLibs/ + Kotlin API source + SttProvider impl
    - null AssetManager pattern: OfflineRecognizer(assetManager = null) for file-path model loading
    - pickFirst packaging: "**/libc++_shared.so" resolves multi-ABI native lib merge conflicts

key-files:
  created:
    - asr/build.gradle.kts
    - asr/src/main/AndroidManifest.xml
    - asr/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so
    - asr/src/main/jniLibs/arm64-v8a/libonnxruntime.so
    - asr/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so
    - asr/src/main/jniLibs/armeabi-v7a/libonnxruntime.so
    - asr/src/main/java/com/k2fsa/sherpa/onnx/OfflineRecognizer.kt
    - asr/src/main/java/com/k2fsa/sherpa/onnx/OfflineStream.kt
    - asr/src/main/java/com/k2fsa/sherpa/onnx/FeatureConfig.kt
    - asr/src/main/java/com/k2fsa/sherpa/onnx/QnnConfig.kt
    - asr/src/main/java/com/k2fsa/sherpa/onnx/HomophoneReplacerConfig.kt
    - asr/src/main/java/dev/pivisolutions/dictus/asr/ParakeetProvider.kt
  modified:
    - settings.gradle.kts (added :asr)
    - app/build.gradle.kts (added :asr dependency + pickFirsts for libc++_shared.so)

key-decisions:
  - "sherpa-onnx Kotlin API vendored as source (not AAR) into asr/src/main/java/com/k2fsa/sherpa/onnx/ — avoids Maven Central availability gap for v1.12.34"
  - "null AssetManager in OfflineRecognizer routes to newFromFile() JNI path — models stored in app files dir, not assets"
  - "OfflineNemoEncDecCtcModelConfig used for Parakeet CTC models — tokens.txt co-located with model.onnx file"
  - "pickFirsts libc++_shared.so in app packaging block — resolves potential conflict if both whisper.cpp and sherpa-onnx bundled libc++"
  - "Task 1 and catalog/downloader work (eb92fa2, 89879d2) were already committed in a prior session — this plan execution resumed from Task 2 only"

patterns-established:
  - "SttProvider impls live in dedicated modules (whisper/ for WhisperProvider, asr/ for ParakeetProvider)"
  - "sherpa-onnx JNI libs placed in jniLibs/ alongside Kotlin API source in same module"

requirements-completed: [STT-02]

# Metrics
duration: 6min
completed: 2026-03-31
---

# Phase 9 Plan 01: Native Library Gate Summary

**sherpa-onnx v1.12.34 JNI integrated into asr/ Gradle module with ParakeetProvider implementing SttProvider via OfflineRecognizer NeMo CTC path — full assembleDebug succeeds**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-31T16:17:42Z
- **Completed:** 2026-03-31T16:23:00Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments

- Created asr/ Android library module with sherpa-onnx v1.12.34 native libs for arm64-v8a and armeabi-v7a
- Validated native library coexistence: whisper.cpp + sherpa-onnx build cleanly in one APK
- Implemented ParakeetProvider with all 7 SttProvider interface members (English-only, NeMo CTC)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create asr/ Gradle module with sherpa-onnx native libs and Kotlin API** - `89879d2` (feat) — *committed in prior session*
2. **Task 2: Implement ParakeetProvider using sherpa-onnx OfflineRecognizer** - `89cb7e1` (feat)

*Note: Task 1 artifacts (asr module, native libs, Kotlin API, settings.gradle.kts) were committed in commits `eb92fa2` and `89879d2` by a prior session. This execution session committed Task 2 only.*

## Files Created/Modified

- `asr/build.gradle.kts` — Android library module definition (namespace dev.pivisolutions.dictus.asr)
- `asr/src/main/AndroidManifest.xml` — Minimal manifest for library module
- `asr/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` — sherpa-onnx JNI bridge (18 MB)
- `asr/src/main/jniLibs/arm64-v8a/libonnxruntime.so` — ONNX Runtime (18 MB)
- `asr/src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so` — sherpa-onnx JNI bridge (3.5 MB)
- `asr/src/main/jniLibs/armeabi-v7a/libonnxruntime.so` — ONNX Runtime (13 MB)
- `asr/src/main/java/com/k2fsa/sherpa/onnx/OfflineRecognizer.kt` — Main recognizer class + data classes
- `asr/src/main/java/com/k2fsa/sherpa/onnx/OfflineStream.kt` — Audio stream class
- `asr/src/main/java/com/k2fsa/sherpa/onnx/FeatureConfig.kt` — Feature extractor config
- `asr/src/main/java/com/k2fsa/sherpa/onnx/QnnConfig.kt` — QNN config (required by data classes)
- `asr/src/main/java/com/k2fsa/sherpa/onnx/HomophoneReplacerConfig.kt` — HR config (required by OfflineRecognizerConfig)
- `asr/src/main/java/dev/pivisolutions/dictus/asr/ParakeetProvider.kt` — SttProvider implementation
- `settings.gradle.kts` — Added :asr module registration
- `app/build.gradle.kts` — Added :asr dependency + pickFirsts for libc++_shared.so

## Decisions Made

- Vendored sherpa-onnx Kotlin API source directly into asr/ (not as AAR) because v1.12.34 is not on Maven Central
- Used `null` AssetManager in OfflineRecognizer to route to file-path JNI path (models in files dir, not assets)
- `pickFirsts += setOf("**/libc++_shared.so")` added preemptively in app packaging to handle potential merge conflict
- Copied only 5 Kotlin API files needed for offline CTC: OfflineRecognizer, OfflineStream, FeatureConfig, QnnConfig, HomophoneReplacerConfig (excluded streaming, TTS, speaker ID)

## Deviations from Plan

None — plan executed exactly as written. Task 1 artifacts were discovered as already committed in a prior partial session; no rework needed. The pre-existing commons-compress dependency and :asr dependency in app/build.gradle.kts were already committed by that session.

## Issues Encountered

Discovered that a prior session had already committed Task 1 work (asr module creation, native libs, Kotlin API, settings.gradle.kts changes) under commits `eb92fa2` and `89879d2`. The working tree was clean with all Task 1 artifacts in place. Execution resumed from Task 2 (ParakeetProvider) only.

## Next Phase Readiness

- asr/ module is ready for wiring into DictationService (Plan 09-03)
- ParakeetProvider is instantiable — no crash on construction
- Model catalog entries (Plan 09-02) already committed alongside this work
- Remaining gate: runtime initialization test with actual model file on device

---
*Phase: 09-parakeet-integration*
*Completed: 2026-03-31*

## Self-Check: PASSED

- asr/build.gradle.kts: FOUND
- ParakeetProvider.kt: FOUND
- libsherpa-onnx-jni.so (arm64-v8a): FOUND
- libonnxruntime.so (arm64-v8a): FOUND
- 09-01-SUMMARY.md: FOUND
- Task 2 commit 89cb7e1: FOUND
- Task 1 commit 89879d2: FOUND
