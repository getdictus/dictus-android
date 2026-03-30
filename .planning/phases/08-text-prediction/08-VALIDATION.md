---
phase: 8
slug: text-prediction
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 + Robolectric 4.14.1 |
| **Config file** | `ime/src/test/resources/robolectric.properties` (sdk=35) |
| **Quick run command** | `./gradlew :ime:test --tests "*.suggestion.*" -x lint` |
| **Full suite command** | `./gradlew :ime:test -x lint` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :ime:test --tests "*.suggestion.*" -x lint`
- **After every plan wave:** Run `./gradlew :ime:test -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | SUGG-01 | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 0 | SUGG-03 | unit | `./gradlew :ime:test --tests "*.PersonalDictionaryTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-01-03 | 01 | 0 | SUGG-01 | benchmark | `./gradlew :ime:test --tests "*.DictionaryBenchmarkTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | SUGG-01 | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-02-02 | 02 | 1 | SUGG-01 | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-03-01 | 03 | 1 | SUGG-03 | unit | `./gradlew :ime:test --tests "*.PersonalDictionaryTest" -x lint` | ❌ W0 | ⬜ pending |
| 08-04-01 | 04 | 2 | SUGG-02 | integration | `./gradlew :ime:test --tests "*.SuggestionBarTest" -x lint` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngineTest.kt` — stubs for SUGG-01 (prefix match, accent, apostrophe, frequency ranking, maxResults, not-ready guard)
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/PersonalDictionaryTest.kt` — stubs for SUGG-03 (threshold, suggestion boost, DataStore persistence)
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryBenchmarkTest.kt` — stubs for SUGG-01 microbenchmark (1000 sequential lookups)
- [ ] `ime/src/test/assets/dict_fr.txt` — small subset (~50 words) for unit tests
- [ ] `ime/src/main/assets/` directory creation

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Suggestion bar shows candidates while typing on real device | SUGG-01 | Requires IME visual rendering | 1. Open any text field 2. Type "bon" 3. Verify bar shows "bonjour" etc. |
| Post-dictation suggestion clear | SUGG-01 | Requires STT + IME integration | 1. Dictate text 2. Verify suggestion bar clears 3. Type to verify suggestions resume |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
