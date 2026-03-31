---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Public Beta
status: completed
stopped_at: Completed 09-02-PLAN.md
last_updated: "2026-03-31T16:24:21.187Z"
last_activity: 2026-03-31 — PersonalDictionary wiring verified on device, keyboard learns words after 2 interactions
progress:
  total_phases: 5
  completed_phases: 2
  total_plans: 9
  completed_plans: 7
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

### Research Flags (Phase 9)

- libc++_shared.so collision between whisper.cpp and sherpa-onnx must be validated FIRST in Phase 9
- ONNX Runtime memory release timing (sherpa-onnx #1939) needs empirical validation with `dumpsys meminfo`

### Pending Todos

None.

### Blockers/Concerns

None at roadmap stage.

## Session Continuity

Last session: 2026-03-31T16:24:21.185Z
Stopped at: Completed 09-02-PLAN.md
Resume file: None
