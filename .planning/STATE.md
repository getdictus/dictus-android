---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Public Beta
status: completed
stopped_at: Completed 10-beta-distribution-license-audit-03-PLAN.md
last_updated: "2026-04-01T12:45:03.248Z"
last_activity: 2026-03-31 — PersonalDictionary wiring verified on device, keyboard learns words after 2 interactions
progress:
  total_phases: 5
  completed_phases: 3
  total_plans: 12
  completed_plans: 10
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-30)

**Core value:** La dictee vocale on-device qui fonctionne comme clavier systeme -- gratuite, privee, sans cloud.
**Current focus:** Phase 8 — Text Prediction complete, ready for Phase 9

## Current Position

Phase: 8 of 11 (Phase 8: Text Prediction)
Plan: 3 of 3 (Phase 8 complete)
Status: Phase 8 complete — all 3 plans executed, SUGG-03 gap closed
Last activity: 2026-03-31 — PersonalDictionary wiring verified on device, keyboard learns words after 2 interactions

Progress: [██████████] 100%

## Performance Metrics

**Velocity (v1.0 reference):**
- v1.0 total plans completed: 29
- v1.0 average duration: ~25 min/plan
- v1.0 total execution time: ~12 hours over 9 days

**v1.1 By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 7. Foundation | TBD | - | - |
| 8. Text Prediction | TBD | - | - |
| 9. Parakeet Integration | TBD | - | - |
| 10. Beta Distribution + License Audit | TBD | - | - |
| 11. OSS Repository | TBD | - | - |
| Phase 07-foundation P01 | 22 min | 2 tasks | 5 files |
| Phase 07-foundation P02 | ~15 min | 2 tasks | 3 files |
| Phase 07-foundation P03 | ~10 min | 2 tasks | 2 files |
| Phase 07-foundation P02 | 8 | 2 tasks | 4 files |
| Phase 08-text-prediction P01 | 17 | 2 tasks | 10 files |
| Phase 08-text-prediction P02 | 10 | 1 tasks | 1 files |
| Phase 08-text-prediction P03 | 8 | 1 tasks | 1 files |
| Phase 09-parakeet-integration P02 | 6 | 2 tasks | 4 files |
| Phase 09-parakeet-integration P01 | 6 | 2 tasks | 14 files |
| Phase 09-parakeet-integration P03 | 422 | 2 tasks | 7 files |
| Phase 10-beta-distribution-license-audit P03 | 2 | 1 tasks | 1 files |

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table (v1.0 outcomes recorded).

Key v1.1 architectural decisions (from research):
- sherpa-onnx 1.12.34 AAR (local `asr/libs/`) — not on Maven Central
- Parakeet 110M CTC INT8 (126 MB) — only feasible model for IME process (640 MB OOMs)
- Binary AOSP `.dict` files (Apache 2.0) for suggestion engine — no NDK, no LatinIME dependency
- SttProvider interface in `core/` is a hard gate before any Parakeet code
- [Phase 07-foundation]: AudioCaptureManagerTest assertions updated to match actual implementation (20x+sqrt curve, pre-filled history) — production code is correct, tests were stale
- [Phase 07-foundation]: Release variant Robolectric test failures (ImeStatusCardTest, RecordingTestAreaTest) are pre-existing and deferred — debug baseline is green 91/91
- [Phase 07-foundation P03]: remember(keyboardLayout) used to trigger KeyboardScreen recomposition on KEYBOARD_LAYOUT DataStore change — same pattern as remember(initialLayer) for KEYBOARD_MODE
- [Phase 07-foundation]: SttProvider placed in core/stt/ (not core/whisper/) to make engine-neutral nature explicit for Phase 9 Parakeet
- [Phase 07-foundation]: Empty supportedLanguages list means all languages supported (Whisper); non-empty means restriction (Parakeet will be listOf(en))
- [Phase 08-text-prediction]: Injectable ioDispatcher in DictionaryEngine: Dispatchers.IO by default, overrideable in tests for advanceUntilIdle() compatibility
- [Phase 08-text-prediction]: Top 50k words from AOSP corpus: covers normal usage at ~2MB/file vs 8-15MB for full decoded set
- [Phase 08-text-prediction]: AOSP .combined pre-decoded format from Helium314/aosp-dictionaries wordlists/ — no NDK needed, strips leading space during processing
- [Phase 08-text-prediction]: by lazy for DictionaryEngine in DictusImeService: applicationContext not available at field-init time in InputMethodService
- [Phase 08-text-prediction]: Safe cast (as? DictionaryEngine) for personalDictionary access: avoids coupling SuggestionEngine interface to personal dictionary concerns
- [Phase 08-text-prediction]: recordWordTyped called after commitText in onSuggestionSelected (commit first, then learn)
- [Phase 08-text-prediction]: Safe cast (as? DictionaryEngine) for personalDictionary access from onSuggestionSelected and onCurrentWordSelected in DictusImeService
- [Phase 09-parakeet-integration]: commons-compress:1.26.1 chosen over system tar command for Parakeet archive extraction: pure-Java, works on all Android API levels
- [Phase 09-parakeet-integration]: isDirectoryModel() extension defined inside ModelCatalog object: keeps provider-layout decision colocated with catalog logic
- [Phase 09-parakeet-integration]: Parakeet models use directory layout models/{key}/{fileName} vs flat models/{fileName} for Whisper
- [Phase 09-parakeet-integration]: sherpa-onnx Kotlin API vendored as source into asr/ module — not as AAR — because v1.12.34 not on Maven Central
- [Phase 09-parakeet-integration]: null AssetManager in OfflineRecognizer routes to newFromFile() JNI path for file-path model loading (not assets)
- [Phase 09-parakeet-integration]: pickFirsts libc++_shared.so in app packaging block resolves potential whisper.cpp + sherpa-onnx native lib merge conflict
- [Phase 09-parakeet-integration]: FakeDataStore + direct dataStore.data.first() check in ModelsViewModelTest — SharingStarted.WhileSubscribed StateFlow does not propagate without collector in unit tests
- [Phase 09-parakeet-integration]: Removed FP16 Parakeet model: redundant with INT8 (4x larger, negligible quality gain)
- [Phase 09-parakeet-integration]: Upgraded Parakeet 0.6B v2 -> v3: gains 25-language auto-detection instead of EN-only
- [Phase 09-parakeet-integration]: whisper.cpp native libs forced to Release build variant: Debug JNI DSP paths were 70x slower
- [Phase 09-parakeet-integration]: key() on LazyColumn model cards to fix stale click handler closures after downloads (Compose reuse bug)
- [Phase 09-parakeet-integration]: FakeDataStore + direct dataStore.data.first() in ModelsViewModelTest: SharingStarted.WhileSubscribed StateFlow does not propagate without collector in unit tests
- [Phase 10-beta-distribution-license-audit]: Text-only README with sideloading steps; no screenshots per user decision; getdictus/dictus-android repo slug; API 29 minSdk; feedback links to GitHub Issues (templates in Phase 11)

### Research Flags (Phase 9)

- libc++_shared.so collision between whisper.cpp and sherpa-onnx must be validated FIRST in Phase 9
- ONNX Runtime memory release timing (sherpa-onnx #1939) needs empirical validation with `dumpsys meminfo`

### Pending Todos

None.

### Blockers/Concerns

None at roadmap stage.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260401-jdw | Dynamic recommended model in onboarding based on device RAM | 2026-04-01 | 27791d9 | [260401-jdw-dynamic-recommended-model-in-onboarding-](./quick/260401-jdw-dynamic-recommended-model-in-onboarding-/) |

## Session Continuity

Last session: 2026-04-01T12:45:03.246Z
Stopped at: Completed 10-beta-distribution-license-audit-03-PLAN.md
Resume file: None
