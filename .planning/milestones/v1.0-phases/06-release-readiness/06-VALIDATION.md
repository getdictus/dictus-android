---
phase: 6
slug: release-readiness
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-29
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Robolectric 4.x (existing) |
| **Config file** | None — configured via `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])` |
| **Quick run command** | `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | DSG-03 | build | `./gradlew :app:assembleDebug` (string resources compile) | ✅ | ⬜ pending |
| 06-01-02 | 01 | 1 | DSG-03 | build | `./gradlew :app:assembleDebug` (French overlay compiles) | ✅ | ⬜ pending |
| 06-01-03 | 01 | 1 | DSG-03 | lint | `./gradlew :app:lintDebug` (missing translations check) | ✅ automatic | ⬜ pending |
| 06-02-01 | 02 | 1 | DSG-03 | unit | `./gradlew :app:testDebugUnitTest --tests "*.LanguagePickerTest"` | ❌ W0 | ⬜ pending |
| 06-02-02 | 02 | 1 | DSG-02 | unit | `./gradlew :core:testDebugUnitTest --tests "*.DictusThemeTest"` | ✅ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/dev/pivisolutions/dictus/ui/settings/LanguagePickerTest.kt` — stubs for DSG-03 in-app language picker logic
- [ ] `app/src/main/resources.properties` — needed for `generateLocaleConfig = true`

*Existing infrastructure covers most phase requirements. Wave 0 adds language picker test stub only.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| commitText inserts text in WhatsApp/Gmail/Chrome | DSG-03 | Requires physical device + real apps | 1. Open WhatsApp chat 2. Switch to Dictus keyboard 3. Tap mic, dictate, verify text appears 4. Repeat in Gmail compose and Chrome search bar |
| Cold start IME dictation without main app | DSG-03 | Requires physical device scenario | 1. Force stop Dictus app 2. Open any text field 3. Switch to Dictus keyboard 4. Tap mic → record → transcribe 5. Verify foreground service starts and text inserts |
| No memory leak in long keyboard sessions | Perf | Requires Android Studio profiler | 1. Open text field with Dictus keyboard 2. Perform 20+ dictation cycles over 10min 3. Check Logcat for GC pressure or growing heap |
| Backspace + Enter behavior across apps | DSG-03 | Requires physical device + app-specific behavior | 1. In WhatsApp: verify Enter sends message 2. In Gmail: verify Enter creates new line 3. Verify backspace deletes correctly |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
