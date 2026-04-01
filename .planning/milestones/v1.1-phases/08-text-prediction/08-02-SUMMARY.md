---
phase: 08-text-prediction
plan: 02
subsystem: ime/service
tags: [dictionary-engine, suggestion, ime-service, wiring, perf-fix, settings]

# Dependency graph
requires:
  - phase: 08-text-prediction-01
    provides: "DictionaryEngine and PersonalDictionary with AOSP FR+EN dictionaries"
provides:
  - "DictusImeService wired with production DictionaryEngine (replaces StubSuggestionEngine)"
  - "DictionaryEngine performance optimization (pre-computed prefix index)"
  - "SUGGESTIONS_ENABLED toggle in Settings > Keyboard"
  - "Post-dictation suggestion bar clearing"
affects:
  - "ime/DictusImeService.kt - live keyboard suggestion pipeline"
  - "ime/suggestion/DictionaryEngine.kt - perf optimization"
  - "ime/suggestion/WordEntry.kt - strippedLower field"
  - "core/preferences/PreferenceKeys.kt - SUGGESTIONS_ENABLED key"
  - "app/ui/settings/ - suggestions toggle"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "by lazy for DictionaryEngine: defers initialization past field-init time when applicationContext and entryPoint are available"
    - "Pre-computed strippedLower in WordEntry for O(1) accent comparison"
    - "Prefix index Map<Char, List<WordEntry>> reduces scan from 50k to ~2k per keystroke"
    - "SUGGESTIONS_ENABLED DataStore preference with reactive observation in IME"

key-files:
  created: []
  modified:
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt"
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngine.kt"
    - "ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/WordEntry.kt"
    - "core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt"
    - "app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt"
    - "app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt"

key-decisions:
  - "by lazy initialization for DictionaryEngine: applicationContext and entryPoint are not available at field-init time in InputMethodService"
  - "Personal dictionary counting deferred to future autocorrect phase: works in unit tests but never fires in live IME runtime"
  - "Added suggestions toggle as user-facing safety valve for performance concerns"

requirements-completed:
  - SUGG-02

# Metrics
duration: ~45min (including perf debugging + device verification)
completed: 2026-03-31
---

# Phase 08 Plan 02: Wire DictionaryEngine into DictusImeService — Summary

**DictusImeService now uses the production AOSP-backed DictionaryEngine. Critical perf fix applied (50ms→<1ms per keystroke). Suggestions toggle added in Settings.**

## Tasks

| Task | Status | Commit |
|------|--------|--------|
| 1: Replace StubSuggestionEngine with DictionaryEngine | Done | `6db1747` |
| 2: Device verification | Approved | N/A (human checkpoint) |
| Post-plan: Perf fix + toggle + cleanup | Done | `3f5a568` |

## What was built

1. **DictionaryEngine wiring**: Replaced StubSuggestionEngine with lazy-initialized DictionaryEngine using applicationContext, dataStore, and bindingScope.
2. **Performance fix**: Pre-computed `strippedLower` in WordEntry at load time + prefix index by first char. Reduced per-keystroke work from ~50ms (50k NFD normalizations) to <1ms.
3. **Suggestions toggle**: SUGGESTIONS_ENABLED preference in Settings > Keyboard, observed reactively in IME service.
4. **Exact-match fix**: Compare original words (not stripped) so "deja" input no longer excludes "deja" from suggestions.
5. **Post-dictation clear**: Suggestions and current word cleared after voice transcription.

## Deviations

- **Personal dictionary counting removed**: `recordWordTyped` works in unit tests but never executes in the live IME service (confirmed via logcat). Root cause undiagnosed. Deferred to future autocorrect phase alongside auto-capitalization.
- **Suggestions toggle added**: Not in original plan, added at user request as safety valve.
- **Perf fix required**: Original DictionaryEngine design caused severe input lag on Pixel 4.

## Self-Check: PASSED

- [x] StubSuggestionEngine replaced by DictionaryEngine
- [x] DictionaryEngine wired with applicationContext, dataStore, bindingScope
- [x] Post-dictation clears suggestion bar
- [x] Suggestions work on device (prefix matching, accent-insensitive, apostrophe words)
- [x] Performance acceptable on Pixel 4
- [x] Suggestions toggle in settings
- [ ] Personal dictionary learning (deferred to future phase)

---
*Phase: 08-text-prediction*
*Completed: 2026-03-31*
