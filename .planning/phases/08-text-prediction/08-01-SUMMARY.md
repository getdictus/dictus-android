---
phase: 08-text-prediction
plan: 01
subsystem: ime/suggestion
tags: [dictionary, tdd, suggestion-engine, personal-dictionary, aosp]
requires:
  - core/preferences/PreferenceKeys.kt
  - ime/suggestion/SuggestionEngine.kt
provides:
  - ime/suggestion/DictionaryEngine.kt
  - ime/suggestion/PersonalDictionary.kt
  - ime/suggestion/WordEntry.kt
  - ime/src/main/assets/dict_fr.txt
  - ime/src/main/assets/dict_en.txt
affects:
  - ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/
  - core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt
tech-stack:
  added:
    - "AOSP FR+EN word lists (Apache 2.0) from Helium314/aosp-dictionaries, top 50k words each"
    - "java.text.Normalizer NFD for accent-insensitive matching (JDK stdlib)"
    - "stringSetPreferencesKey for PERSONAL_DICTIONARY in DataStore"
  patterns:
    - "Injectable ioDispatcher in DictionaryEngine for testability"
    - "Reactive DataStore collect in PersonalDictionary init (no blocking reads)"
    - "MutableStateFlow<Boolean> _isReady guard before dictionary load completes"
key-files:
  created:
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/WordEntry.kt"
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/PersonalDictionary.kt"
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngine.kt"
    - "ime/src/main/assets/dict_fr.txt"
    - "ime/src/main/assets/dict_en.txt"
    - "ime/src/test/assets/dict_test.txt"
    - "ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngineTest.kt"
    - "ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/PersonalDictionaryTest.kt"
    - "ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryBenchmarkTest.kt"
  modified:
    - "core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt"
decisions:
  - "Injectable ioDispatcher in DictionaryEngine: Dispatchers.IO by default in production, overrideable in tests so advanceUntilIdle() drains loading coroutine reliably"
  - "Top 50k words from AOSP corpus: original corpus has 190k FR / 642k EN words; 50k covers normal usage at 2MB/file vs 8-15MB for full set"
  - "DictionaryEngine.kt uses AOSP .combined pre-decoded format (word=X,f=Y per line) from Helium314/aosp-dictionaries wordlists/, not .dict binary files — no NDK needed"
metrics:
  duration: "17 min"
  completed: "2026-03-30"
  tasks: 2
  files_created: 9
  files_modified: 1
---

# Phase 08 Plan 01: DictionaryEngine + PersonalDictionary Summary

**One-liner:** Accent-insensitive prefix suggestion engine backed by AOSP FR+EN dictionaries (50k words each) with DataStore-persisted personal dictionary boosting after 2 types.

## What Was Built

### Task 1: TDD — Data classes, PersonalDictionary, DictionaryEngine, test infrastructure

**RED phase:** Created 3 test files (DictionaryEngineTest, PersonalDictionaryTest, DictionaryBenchmarkTest) that failed to compile because DictionaryEngine and PersonalDictionary didn't exist.

**GREEN phase:** Implemented all production code. 15 tests pass.

Key files created:
- `/ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/WordEntry.kt` — `data class WordEntry(val word: String, val frequency: Int)`
- `/ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/PersonalDictionary.kt` — DataStore-backed word learning with 2-tap threshold
- `/ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngine.kt` — Production `SuggestionEngine` implementation
- `/core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — Added `PERSONAL_DICTIONARY = stringSetPreferencesKey("personal_dictionary")`
- `/ime/src/test/assets/dict_test.txt` — 50-word test dictionary in AOSP format

### Task 2: AOSP FR+EN dictionary assets

Downloaded `main_fr.combined` and `main_en_US.combined` from Helium314/aosp-dictionaries (codeberg.org, Apache 2.0). Processed: stripped leading spaces from lines (the `.combined` format prefixes each `word=` line with a space), filtered to top 50k words by frequency, output as `word=X,f=Y,flags=,originalFreq=Y` format.

- `ime/src/main/assets/dict_fr.txt` — 50,000 French words, ~2.1MB
- `ime/src/main/assets/dict_en.txt` — 50,000 English US words, ~2.0MB

## Test Results

| Suite | Tests | Failures | Notes |
|-------|-------|----------|-------|
| DictionaryEngineTest | 9 | 0 | Prefix match, accent-insensitive, apostrophe words, frequency ranking, personal boost, maxResults, not-ready guard |
| PersonalDictionaryTest | 5 | 0 | Threshold (1 type = no learn, 2 types = learn), blank no-op, DataStore persistence, cross-instance load |
| DictionaryBenchmarkTest | 1 | 0 | 1000 sequential getSuggestions calls < 100ms |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Injected ioDispatcher for coroutine testability**
- **Found during:** Task 1 GREEN phase — tests returned 0 suggestions after `advanceUntilIdle()`
- **Issue:** `coroutineScope.launch(Dispatchers.IO)` in `DictionaryEngine.init` runs on a real IO thread, not on the TestDispatcher. `advanceUntilIdle()` only drains work scheduled on the TestDispatcher, so the dictionary never loaded in tests.
- **Fix:** Added `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` parameter to `DictionaryEngine`. Tests pass `testDispatcher`. Production code uses default `Dispatchers.IO` — no change to the service wiring.
- **Files modified:** `DictionaryEngine.kt`, `DictionaryEngineTest.kt`, `DictionaryBenchmarkTest.kt`
- **Commit:** 5d0ce5d

**2. [Rule 1 - Bug] Dictionary .combined format has leading space before word= lines**
- **Found during:** Task 2 — inspecting downloaded `main_fr.combined` file
- **Issue:** Helium314/aosp-dictionaries `.combined` files use ` word=bonjour,...` (leading space), not `word=bonjour,...`. The DictionaryEngine parser checks `line.startsWith("word=")` which would skip all entries.
- **Fix:** Python processing script stripped the leading space when generating the output `dict_fr.txt` and `dict_en.txt` files. The asset files in the repo have no leading space — the parser works as designed.
- **Files modified:** Only affects the generation script (not committed — one-time processing)
- **Commit:** a58197e

## Commits

| Hash | Description |
|------|-------------|
| 5d0ce5d | feat(08-01): implement DictionaryEngine, PersonalDictionary, WordEntry with TDD |
| a58197e | feat(08-01): bundle AOSP FR+EN dictionary assets (top 50k words each) |

## Self-Check: PASSED

All created files exist on disk. Both task commits verified in git history. All 15 tests pass green.
