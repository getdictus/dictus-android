---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Public Beta
status: completed
stopped_at: Phase 8 context gathered
last_updated: "2026-03-30T14:14:29.404Z"
last_activity: "2026-03-30 — DEBT-01 resolved: KEYBOARD_LAYOUT preference now flows reactively from Settings to IME keyboard"
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 3
  completed_plans: 3
  percent: 10
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-30)

**Core value:** La dictee vocale on-device qui fonctionne comme clavier systeme -- gratuite, privee, sans cloud.
**Current focus:** Phase 7 — Foundation (SttProvider interface + tech debt)

## Current Position

Phase: 7 of 11 (Phase 7: Foundation)
Plan: 3 of 3 (Phase 7 complete)
Status: Phase 7 complete — all 3 plans executed
Last activity: 2026-03-30 — DEBT-01 resolved: KEYBOARD_LAYOUT preference now flows reactively from Settings to IME keyboard

Progress: [█░░░░░░░░░] 10%

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

### Research Flags (Phase 9)

- libc++_shared.so collision between whisper.cpp and sherpa-onnx must be validated FIRST in Phase 9
- ONNX Runtime memory release timing (sherpa-onnx #1939) needs empirical validation with `dumpsys meminfo`

### Pending Todos

None.

### Blockers/Concerns

None at roadmap stage.

## Session Continuity

Last session: 2026-03-30T14:14:29.401Z
Stopped at: Phase 8 context gathered
Resume file: .planning/phases/08-text-prediction/08-CONTEXT.md
