---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Public Beta
status: ready_to_plan
stopped_at: Roadmap created for v1.1 — ready to plan Phase 7
last_updated: "2026-03-30"
last_activity: 2026-03-30 -- Roadmap v1.1 created (5 phases, 20 requirements)
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-30)

**Core value:** La dictee vocale on-device qui fonctionne comme clavier systeme -- gratuite, privee, sans cloud.
**Current focus:** Phase 7 — Foundation (SttProvider interface + tech debt)

## Current Position

Phase: 7 of 11 (Phase 7: Foundation)
Plan: — (not yet planned)
Status: Ready to plan
Last activity: 2026-03-30 — Roadmap created for v1.1 Public Beta (5 phases, 20 requirements mapped)

Progress: [░░░░░░░░░░] 0%

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

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table (v1.0 outcomes recorded).

Key v1.1 architectural decisions (from research):
- sherpa-onnx 1.12.34 AAR (local `asr/libs/`) — not on Maven Central
- Parakeet 110M CTC INT8 (126 MB) — only feasible model for IME process (640 MB OOMs)
- Binary AOSP `.dict` files (Apache 2.0) for suggestion engine — no NDK, no LatinIME dependency
- SttProvider interface in `core/` is a hard gate before any Parakeet code

### Research Flags (Phase 9)

- libc++_shared.so collision between whisper.cpp and sherpa-onnx must be validated FIRST in Phase 9
- ONNX Runtime memory release timing (sherpa-onnx #1939) needs empirical validation with `dumpsys meminfo`

### Pending Todos

None.

### Blockers/Concerns

None at roadmap stage.

## Session Continuity

Last session: 2026-03-30
Stopped at: Roadmap created — ready to plan Phase 7
Resume file: None
