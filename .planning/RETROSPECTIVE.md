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

## Milestone: v1.1 — Public Beta

**Shipped:** 2026-04-01
**Phases:** 5 | **Plans:** 15 | **Timeline:** 3 days

### What Was Built
- Production text prediction engine: AOSP FR+EN dictionaries (50k words each), accent-insensitive prefix matching, DataStore-persisted personal dictionary with 2-tap learning
- Parakeet/Nvidia STT via sherpa-onnx: SttProvider interface, ParakeetProvider, safe engine switching (mutual exclusion), directory-model layout, commons-compress tar.bz2 extraction
- GitHub Actions CI/CD: lint + tests + licensee + assembleDebug on PR, signed release APK on v* tag via softprops/action-gh-release
- License audit: cashapp/licensee 1.14.1, auto-generated LicencesScreen from artifacts.json
- OSS repository: MIT LICENSE, CONTRIBUTING.md (5-module map), issue templates (bug/feature YAML), PR template, enhanced README with badges

### What Worked
- SttProvider interface extracted first (Phase 7) before any Parakeet code — Phase 9 implementation was clean, no refactoring needed
- v1.0 tech debt cleared in Phase 7 (AZERTY toggle, POST_NOTIFICATIONS, stale tests) — no legacy issues leaked into feature phases
- Phase 10 (CI) + Phase 11 (OSS) were mostly documentation/infra — fast execution after heavier Phases 8-9
- AOSP pre-decoded dictionary format avoided NDK dependency — pure Kotlin suggestion engine, no native code
- Milestone audit before completion caught stale REQUIREMENTS.md traceability (OSS-01, OSS-04 marked Pending when actually Complete)

### What Was Inefficient
- Phase 9 Plan 03 took 422 min (7h) — Parakeet model download, tar.bz2 extraction, provider dispatch UI, and device verification all in one plan. Should have been split.
- sherpa-onnx not on Maven Central forced vendoring the Kotlin API as source — ongoing maintenance burden
- whisper.cpp Release build discovery (70x Debug→Release speedup) was found late in Phase 9 instead of Phase 3
- CODE_OF_CONDUCT.md (OSS-03) blocked by content filtering — should have been handled with a pre-written template file

### Patterns Established
- SttProvider interface pattern: new STT engines implement core/stt/SttProvider, wired in DictationService
- Injectable ioDispatcher for coroutine testability (DictionaryEngine pattern)
- by lazy for DictionaryEngine in IME service (applicationContext not available at field-init)
- pickFirst "**/libc++_shared.so" for multi-native-lib APK packaging
- FakeDataStore + direct dataStore.data.first() for StateFlow testing (WhileSubscribed doesn't propagate without collector)

### Key Lessons
1. **Large plans (>2h estimated) should be split** — 09-03 accumulated too much scope (UI + download + extraction + device verify)
2. **Native build variants matter early** — Release vs Debug for whisper.cpp JNI was a 70x difference, discovered in Phase 9 instead of Phase 3
3. **Vendored dependencies need explicit update strategy** — sherpa-onnx source in asr/ has no automated update path
4. **Content filtering can block legitimate OSS files** — have fallback templates for standard community documents

### Cost Observations
- Model mix: ~70% opus, ~25% sonnet, ~5% haiku
- Sessions: ~10 across 3 days
- Notable: Phases 10-11 averaged ~2min/plan (documentation-heavy); Phase 9 Plan 03 was an outlier at 7h

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 9 days | 7 | Risk-first ordering, UAT gap closure pattern established |
| v1.1 | 3 days | 5 | Interface-first extraction, milestone audit before completion |

### Cumulative Quality

| Milestone | Plans | Known Debt | Requirements Met |
|-----------|-------|------------|-----------------|
| v1.0 | 29 | 4 stale tests + 2 wiring gaps | 23/23 |
| v1.1 | 15 | 1 known deviation (OSS-03) + 8 housekeeping items | 19/20 |

### Top Lessons (Verified Across Milestones)

1. Front-load technical risk in early phases — later phases become predictable
2. Test maintenance must happen in the same plan that changes behavior
3. Extract interfaces before implementing new providers — keeps implementation phases clean
4. Large plans (>2h) should be split — scope accumulation causes outlier execution times
5. Native build configuration (Release vs Debug) should be verified in the first phase that uses native code
