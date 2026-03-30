# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — MVP

**Shipped:** 2026-03-30
**Phases:** 7 | **Plans:** 29 | **Timeline:** 9 days

### What Was Built
- Compose-based IME keyboard with AZERTY/QWERTY, accented characters, emoji picker, text suggestions
- On-device voice dictation via whisper.cpp (NDK/CMake/JNI) with end-to-end record→transcribe→insert
- Model management: download/delete Whisper models from HuggingFace with progress and storage indicators
- 7-step onboarding flow with animated welcome, permissions, IME activation, model download, test dictation
- Waveform animation, sound feedback, light/dark/auto theme, bilingual FR/EN UI with in-app language picker

### What Worked
- Risk-first phase ordering: Compose-in-IME (Phase 1), foreground service (Phase 2), whisper.cpp native (Phase 3) de-risked early — later phases were smooth
- iOS app as functional reference eliminated ambiguity — clear target for every feature
- Decimal phase insertion (02.1) handled urgent testability need without disrupting numbering
- UAT gap closure plans (01-04, 02-03, 04-06/07/08) caught real issues before they compounded
- Parallel execution of Phases 4 and 5 (independent after Phase 3) compressed timeline

### What Was Inefficient
- Phase 1 Plan 03 took ~22h across 2 sessions — Compose-in-IME lifecycle was poorly documented, required extensive debugging
- Test maintenance neglected: 4 stale test assertions accumulated across Phases 2, 4 — should fix tests in same plan that changes behavior
- Settings↔IME DataStore wiring gap (KBD-02) not caught until audit — integration flows need earlier cross-module verification
- POST_NOTIFICATIONS permission omission — manifest review should be part of service architecture phase

### Patterns Established
- EntryPointAccessors.fromApplication() for Hilt DI in InputMethodService (not @AndroidEntryPoint)
- START_NOT_STICKY for transient recording service
- when() dispatch for one-time sequential flows (onboarding) instead of NavHost
- remember(initialLayer) pattern for keyboard state that resets on preference change
- SoundPool pre-loaded in Service.onCreate() for sub-10ms playback latency

### Key Lessons
1. **Compose-in-IME is viable but undocumented** — plan extra debugging time for lifecycle wiring, BackHandler doesn't work (use onKeyDown interception)
2. **DataStore keys written in one module must be verified as read in consuming modules** — the KBD-02 gap would have been caught by a cross-module integration checklist
3. **Fix tests in the same plan that changes behavior** — deferring test updates creates compounding debt that's harder to fix later
4. **NDK version matters** — corrupted NDK 25.2 install wasted time, always verify source.properties exists before building

### Cost Observations
- Model mix: ~60% opus, ~30% sonnet, ~10% haiku
- Sessions: ~15-20 across 9 days
- Notable: Plans averaged ~12min execution; Phase 4 (8 plans) was most complex but each plan stayed focused

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 9 days | 7 | Risk-first ordering, UAT gap closure pattern established |

### Cumulative Quality

| Milestone | Plans | Known Debt | Requirements Met |
|-----------|-------|------------|-----------------|
| v1.0 | 29 | 4 stale tests + 2 wiring gaps | 23/23 |

### Top Lessons (Verified Across Milestones)

1. Front-load technical risk in early phases — later phases become predictable
2. Test maintenance must happen in the same plan that changes behavior
