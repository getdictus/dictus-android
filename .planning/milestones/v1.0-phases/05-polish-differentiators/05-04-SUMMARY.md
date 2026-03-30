---
phase: 05-polish-differentiators
plan: "04"
subsystem: ime/suggestion
tags: [suggestion-bar, ime, keyboard, tdd, stub-first]
dependency_graph:
  requires: []
  provides: [SuggestionEngine, StubSuggestionEngine, SuggestionBar]
  affects: [ime/ui/KeyboardScreen.kt, ime/DictusImeService.kt]
tech_stack:
  added: []
  patterns: [stub-first, drop-in-replacement-contract, onUpdateSelection]
key_files:
  created:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/SuggestionEngine.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/SuggestionBar.kt
    - ime/src/test/java/dev/pivisolutions/dictus/ime/ui/SuggestionBarTest.kt
  modified:
    - ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt
    - ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt
decisions:
  - "StubSuggestionEngine as Phase 5 MVP: prefix-matching against 40-word FR/EN dictionary; SuggestionEngine interface designed for zero-change drop-in replacement with AOSP LatinIME JNI"
  - "onUpdateSelection reads 50 chars before cursor and splits on whitespace to extract current word for suggestion seeding"
  - "SuggestionBar only renders when suggestions list is non-empty — keyboard height unchanged when no suggestions"
metrics:
  duration: ~15min
  completed: "2026-03-28"
  tasks_completed: 2
  files_changed: 5
---

# Phase 05 Plan 04: Text Suggestion Bar Summary

Gboard-style 3-slot suggestion bar with SuggestionEngine interface (drop-in AOSP LatinIME contract) and StubSuggestionEngine prefix-matching MVP, wired into KeyboardScreen and DictusImeService via onUpdateSelection.

## What Was Built

- **SuggestionEngine interface** — contract for word prediction with `getSuggestions(input, maxResults)`. KDoc explicitly documents this as the drop-in replacement point for AOSP LatinIME JNI (per CONTEXT.md target architecture). No UI changes required to swap implementations.
- **StubSuggestionEngine** — Phase 5 MVP. Prefix-matching against a 40-word hardcoded FR/EN dictionary. Intentionally not production quality. Swappable via one line change in DictusImeService.
- **SuggestionBar composable** — 36dp height row with 3 weighted slots, center slot bold (FontWeight.Bold), vertical separators (DictusColors.BorderSubtle), DictusColors.Surface background. Only renders when suggestions list is non-empty.
- **KeyboardScreen** — added `suggestions: List<String>` and `onSuggestionSelected` parameters. SuggestionBar placed above MicButtonRow inside the Column (not in emoji picker branch).
- **DictusImeService** — added `onUpdateSelection` override that reads 50 chars before cursor, splits to extract current word, feeds to `StubSuggestionEngine`. Suggestion selection replaces the current word fragment and appends a space. StateFlows `_currentWord` and `_suggestions` drive the UI reactively.
- **SuggestionBarTest** — 7 unit tests covering engine logic: prefix matching, empty input, blank input, maxResults, exact-word exclusion.

## Verification

- `./gradlew :ime:testDebugUnitTest` — BUILD SUCCESSFUL (all tests pass)
- `./gradlew assembleDebug` — BUILD SUCCESSFUL

## Task Commits

| Task | Description | Commit |
|------|-------------|--------|
| 1 | SuggestionBar + SuggestionEngine + tests (TDD) | a5ce69e |
| 2 | Wiring into KeyboardScreen + DictusImeService | included in a5ce69e / HEAD |

Note: Task 2 changes to `KeyboardScreen.kt` and `DictusImeService.kt` were present in the working tree at session start (from a prior partial session) and were completed and confirmed correct. The full build and tests verify correctness. HEAD (`6d6780e`) contains both Task 1 and Task 2 changes.

## Deviations from Plan

None — plan executed exactly as specified.

## Self-Check: PASSED

- SuggestionEngine.kt: FOUND
- SuggestionBar.kt: FOUND
- SuggestionBarTest.kt: FOUND
- KeyboardScreen.kt (with SuggestionBar wiring): FOUND
- DictusImeService.kt (with onUpdateSelection + suggestions): FOUND
- All tests: PASS
- Full build: SUCCESS
