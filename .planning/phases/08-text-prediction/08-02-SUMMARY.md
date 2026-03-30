---
phase: 08-text-prediction
plan: 02
subsystem: ime/suggestion
tags: [dictionary-engine, suggestion, personal-dictionary, ime-service, wiring]

# Dependency graph
requires:
  - phase: 08-text-prediction-01
    provides: "DictionaryEngine and PersonalDictionary with AOSP FR+EN dictionaries"
provides:
  - "DictusImeService wired with production DictionaryEngine (replaces StubSuggestionEngine)"
  - "Personal dictionary word counting on suggestion tap and raw word commit"
  - "Post-dictation suggestion bar clearing"
affects:
  - "ime/DictusImeService.kt - live keyboard suggestion pipeline"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "by lazy for DictionaryEngine: defers initialization past field-init time when applicationContext and entryPoint are available"
    - "Safe cast (as? DictionaryEngine) for optional personalDictionary access without interface coupling"

key-files:
  created: []
  modified:
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt"

key-decisions:
  - "by lazy initialization for DictionaryEngine: applicationContext and entryPoint are not available at field-init time in InputMethodService — lazy defers to first access (after onCreate)"
  - "Safe cast (as? DictionaryEngine) for personalDictionary access: avoids adding recordWordTyped to SuggestionEngine interface which would force StubSuggestionEngine to implement it"

patterns-established:
  - "Post-dictation state reset: always clear _suggestions and _currentWord after commitText in voice transcription flow"

requirements-completed:
  - SUGG-02

# Metrics
duration: ~10min
completed: 2026-03-30
---

# Phase 08 Plan 02: Wire DictionaryEngine into DictusImeService Summary

**DictusImeService now uses the production AOSP-backed DictionaryEngine instead of StubSuggestionEngine, with personal dictionary counting on suggestion taps and post-dictation bar clearing.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-30T17:15:00Z
- **Completed:** 2026-03-30T17:25:00Z
- **Tasks:** 1 complete, 1 awaiting human verification
- **Files modified:** 1

## Accomplishments
- Replaced `StubSuggestionEngine` with `DictionaryEngine` using `by lazy` initialization
- Engine receives `applicationContext`, `entryPoint.dataStore()`, and `bindingScope` — all dependencies wired correctly
- Suggestion tap (`onSuggestionSelected`) now calls `personalDictionary.recordWordTyped(suggestion)`
- Raw word commit (`onCurrentWordSelected`) now calls `personalDictionary.recordWordTyped(word)` before committing space
- Voice transcription confirm (`onConfirm`) now clears `_suggestions` and `_currentWord` after `commitText(text)`
- All 106 existing tests pass; `compileDebugKotlin` exits 0

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace StubSuggestionEngine with DictionaryEngine** - `6db1747` (feat)
2. **Task 2: Verify live suggestion behavior on device** - awaiting human verification

## Files Created/Modified
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` - Engine swap + personal dict counting + post-dictation clear

## Decisions Made
- `by lazy` for `DictionaryEngine`: In an `InputMethodService`, field initializers run during object construction before `onCreate()`. `applicationContext` and `entryPoint` are not ready at that point. `by lazy` defers initialization to first access (triggered from `onUpdateSelection` after `onCreate`). Without `lazy`, the service would crash on startup.
- Safe cast `(suggestionEngine as? DictionaryEngine)` for `personalDictionary` access: Adding `recordWordTyped` to the `SuggestionEngine` interface would force `StubSuggestionEngine` to implement it (dead code) and couple the interface to personal dictionary concerns. The safe cast keeps the interface clean and is appropriate since the caller knows the runtime type in production.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- DictionaryEngine is live in the keyboard. Human verification on device needed to confirm suggestions appear, taps work, personal dictionary accumulates, and post-dictation clearing works.
- Once Task 2 is approved: Phase 08 is complete, Phase 09 (Parakeet Integration) can begin.

---
*Phase: 08-text-prediction*
*Completed: 2026-03-30*
