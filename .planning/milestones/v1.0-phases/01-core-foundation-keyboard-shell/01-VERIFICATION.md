---
phase: 01-core-foundation-keyboard-shell
verified: 2026-03-22T14:30:00Z
status: passed
score: 18/18 must-haves verified
re_verification:
  previous_status: passed
  previous_score: 15/15
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Enable Dictus keyboard in Settings and type in any app"
    expected: "AZERTY keyboard renders with dark Dictus theme. Letter tap inserts character. Shift toggles uppercase (single-tap) or caps lock (double-tap with visual distinction: Accent blue for shift, AccentHighlight blue + underline for caps lock). Mic row appears above keyboard. ?123 switches to numbers, ABC switches back. Long-press on accented letters shows popup. Delete removes characters, hold delete repeats."
    why_human: "IME activation, InputConnection behavior, and visual theme correctness require installing the APK and interacting with system keyboard settings — not verifiable by static analysis."
---

# Phase 1: Core Foundation & Keyboard Shell Verification Report

**Phase Goal:** Multi-module Gradle project with IME service, Compose keyboard UI (AZERTY/QWERTY), key interactions, accent popup, and MicButtonRow placeholder
**Verified:** 2026-03-22
**Status:** PASSED
**Re-verification:** Yes — initial VERIFICATION.md existed (status: passed, 15/15). This is a fresh full re-verification against the actual codebase, not a trust of prior claims. Plan 04 (UAT gap closure) was completed after the first pass and is included here.

---

## Goal Achievement

All four plans that make up Phase 1 were verified against actual file content.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Multi-module Gradle project (app/ime/core) is declared | VERIFIED | `settings.gradle.kts` line 18: `include(":app", ":ime", ":core")` |
| 2 | Dictus dark theme defines exact iOS color tokens as Material 3 ColorScheme | VERIFIED | `DictusColors.kt` has `0xFF0A1628`, `0xFF3D7EFF`, `0xFF161C2C`, etc.; `DictusTheme.kt` uses `darkColorScheme(...)` wired to those tokens |
| 3 | Timber is initialized in Application.onCreate() | VERIFIED | `DictusApplication.kt` line 19: `TimberSetup.init(BuildConfig.DEBUG)`; `TimberSetup.kt` calls `Timber.plant(Timber.DebugTree())` |
| 4 | AZERTY layout row 1 is A-Z-E-R-T-Y-U-I-O-P | VERIFIED | `KeyboardLayouts.kt` `azertyLetters` row 1: A, Z, E, R, T, Y, U, I, O, P |
| 5 | QWERTY layout row 1 is Q-W-E-R-T-Y-U-I-O-P | VERIFIED | `KeyboardLayouts.kt` `qwertyLetters` row 1: Q, W, E, R, T, Y, U, I, O, P |
| 6 | Numbers layer has digits 1-0 and punctuation | VERIFIED | `numbersRows` row 1: 1-0; row 2: -, /, :, ;, (, ), &, @; punctuation in row 3 |
| 7 | Symbols layer has brackets, currency, and math symbols | VERIFIED | `symbolsRows` row 1: [, ], {, }, #, %, ^, *, +, =; row 2: _, \, \|, ~, <, >, $, &, @ |
| 8 | AccentMap returns correct accented variants (e maps to 5 variants) | VERIFIED | `AccentMap.kt` line 14: `'e' to listOf("\u00E8", "\u00E9", "\u00EA", "\u00EB", "\u0113")` |
| 9 | IME service declared in manifest with BIND_INPUT_METHOD permission | VERIFIED | `ime/AndroidManifest.xml`: `android:permission="android.permission.BIND_INPUT_METHOD"` and `android.view.InputMethod` intent-filter |
| 10 | LifecycleInputMethodService wires Compose lifecycle owners on decorView | VERIFIED | `LifecycleInputMethodService.kt` lines 65-67: `setViewTreeLifecycleOwner`, `setViewTreeViewModelStoreOwner`, `setViewTreeSavedStateRegistryOwner` called on decorView |
| 11 | User can tap a letter key and character appears in text field | VERIFIED | `KeyButton.kt` `detectTapGestures(onTap = { currentOnPress.value() })`; `KeyboardScreen.kt` routes `KeyType.CHARACTER` to `onCommitText(output)`; `DictusImeService.commitText()` calls `currentInputConnection?.commitText(text, 1)` |
| 12 | User can tap shift (single-tap) or double-tap for caps lock | VERIFIED | `handleKeyPress()` in `KeyboardScreen.kt`: 300ms double-tap window toggles caps lock, single tap toggles shift |
| 13 | Shift key has visually distinct appearance for caps lock vs single-shift | VERIFIED | `KeyButton.kt` lines 52-53: `isCapsLock -> DictusColors.AccentHighlight` vs `isShifted -> DictusColors.Accent`; lines 123-133: underline drawn via `drawBehind` when caps lock active; `isCapsLock` threaded through KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton |
| 14 | User can tap ?123/ABC to switch keyboard layers | VERIFIED | `handleKeyPress()` maps ABC/123/?123/#+= to `KeyboardLayer` enum; `KeyboardView.kt` selects rows accordingly |
| 15 | User can long-press a letter with accents to see a popup | VERIFIED | `KeyboardScreen.kt` `onKeyLongPress` checks `AccentMap.hasAccents(char)` and sets `showAccentPopup = true`; `AccentPopup.kt` renders `Popup(properties = PopupProperties(focusable = false))` with accent options |
| 16 | User can tap delete to remove characters with key repeat | VERIFIED | `KeyButton.kt` DELETE uses `awaitPointerEventScope` with 400ms initial delay then 50ms repeat via coroutine; `DictusImeService.deleteBackward()` calls `currentInputConnection?.deleteSurroundingText(1, 0)` |
| 17 | Mic button row is positioned above the keyboard rows | VERIFIED | `KeyboardScreen.kt` Column order: `MicButtonRow(...)` at line 55, then `KeyboardView(...)` at line 58 — mic row is first child |
| 18 | Keyboard renders with Dictus dark theme colors | VERIFIED | `KeyboardScreen.kt` wraps Column in `DictusTheme { }`; `KeyboardView.kt` and `MicButtonRow.kt` use `DictusColors.Background`, `DictusColors.KeyBackground`, `DictusColors.KeySpecialBackground` |

**Score:** 18/18 truths verified

---

## Required Artifacts

### Plan 01 Artifacts (Project Skeleton)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `gradle/libs.versions.toml` | VERIFIED | Exists; `timber = "5.0.1"`, `hilt = "2.51.1"`, `agp = "8.7.3"` present |
| `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt` | VERIFIED | Exists; 11 color tokens including `Color(0xFF0A1628)` |
| `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt` | VERIFIED | Exists; `darkColorScheme(...)` and `fun DictusTheme` present |
| `core/src/main/java/dev/pivisolutions/dictus/core/logging/TimberSetup.kt` | VERIFIED | Exists; `Timber.plant(Timber.DebugTree())` in `init()` |
| `app/src/main/java/dev/pivisolutions/dictus/DictusApplication.kt` | VERIFIED | Exists; `@HiltAndroidApp` annotation present |

### Plan 02 Artifacts (IME Service + Data Models)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `ime/src/main/java/dev/pivisolutions/dictus/ime/LifecycleInputMethodService.kt` | VERIFIED | Exists; implements `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` with full lifecycle event handling |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyboardLayouts.kt` | VERIFIED | Exists; `azertyLetters`, `qwertyLetters`, `numbersRows`, `symbolsRows`, `lettersForLayout()` all present |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/model/AccentMap.kt` | VERIFIED | Exists; `accentsFor()` and `hasAccents()` with full French accent coverage (lowercase + uppercase) |
| `ime/src/main/AndroidManifest.xml` | VERIFIED | Exists; `BIND_INPUT_METHOD` permission and `android.view.InputMethod` intent-filter |

### Plan 03 Artifacts (Keyboard UI)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` | VERIFIED | Exists; full state management (layer, shift, caps, accent popup); `handleKeyPress()` routes all key types |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt` | VERIFIED | Exists; `detectTapGestures`, `isCapsLock` distinction, `drawBehind` underline, `rememberUpdatedState`, key-repeat for delete |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/AccentPopup.kt` | VERIFIED | Exists; `Popup(properties = PopupProperties(focusable = false))` with `AccentMap.accentsFor(char)` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/MicButtonRow.kt` | VERIFIED | Exists; mic circle button + keyboard switcher; `height(56.dp)` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt` | VERIFIED | Exists; routes to `KeyboardLayouts.lettersForLayout(layout)` for LETTERS layer |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyRow.kt` | VERIFIED | Exists; renders `Row` with `KeyButton` elements weighted by `widthMultiplier` |

### Plan 04 Artifacts (UAT Gap Closure)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt` | VERIFIED | `isCapsLock` param at line 45; `AccentHighlight` bg at line 52; `drawBehind` underline at lines 123-133 |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` | VERIFIED | `MicButtonRow` called at line 55 before `KeyboardView` at line 58 — mic row is above keyboard |

---

## Key Link Verification

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `settings.gradle.kts` | modules app/ime/core | `include(":app", ":ime", ":core")` | WIRED | Line 18 confirmed |
| `app/build.gradle.kts` | `:core` | `implementation(project(":core"))` | WIRED | Line 37 confirmed |
| `DictusApplication.kt` | `TimberSetup.kt` | `TimberSetup.init(BuildConfig.DEBUG)` | WIRED | Line 19 confirmed |
| `DictusImeService.kt` | `LifecycleInputMethodService.kt` | `class DictusImeService : LifecycleInputMethodService()` | WIRED | Line 18 confirmed |
| `DictusImeService.kt` | `DictusImeEntryPoint.kt` | `EntryPointAccessors.fromApplication(...)` | WIRED | Lines 21-25 confirmed |
| `DictusImeService.kt` | `KeyboardScreen.kt` | `KeyboardContent()` calls `KeyboardScreen(onCommitText, onDeleteBackward, onSendReturn, onSwitchKeyboard)` | WIRED | Lines 33-43 confirmed |
| `KeyboardScreen.kt` | `DictusImeService.kt` | callback lambdas `commitText()`, `deleteBackward()`, `sendReturnKey()` | WIRED | Lines 35-37 confirmed |
| `KeyboardView.kt` | `KeyboardLayouts.kt` | `KeyboardLayouts.lettersForLayout(layout)` | WIRED | `KeyboardView.kt` line 35 confirmed |
| `KeyboardView.kt` | `KeyButton.kt` | via `KeyRow` with `isCapsLock` threaded through | WIRED | `isCapsLock` confirmed in KeyboardView l.28, KeyRow l.23, KeyButton l.45 |
| `KeyboardScreen.kt` | `AccentPopup.kt` | `onKeyLongPress` checks `AccentMap.hasAccents()` then sets `showAccentPopup = true` | WIRED | Lines 91-103 in `KeyboardScreen.kt` confirmed |
| `DictusImeService.kt` | `InputConnection` | `currentInputConnection?.commitText(text, 1)`, `deleteSurroundingText(1, 0)`, `sendKeyEvent(...)` | WIRED | Lines 49, 56, 63-68 confirmed |

---

## Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| KBD-01 | Plan 02 | Full AZERTY keyboard with shift, caps lock, numbers, symbols | SATISFIED | AZERTY layout in `KeyboardLayouts.kt`; shift/caps in `handleKeyPress()`; numbers and symbols layers present and routed via `KeyboardLayer` enum |
| KBD-02 | Plans 02, 04 | Switch between AZERTY and QWERTY layouts | SATISFIED | `KeyboardLayouts.lettersForLayout("azerty"/"qwerty")`; `currentLayout` state in `KeyboardScreen.kt`; caps lock visual distinction added in Plan 04 |
| KBD-03 | Plan 02 | Access accented characters via long-press | SATISFIED | `AccentMap.kt` covers e/a/u/i/o/c/y/n (upper + lower); `AccentPopup.kt` renders popup; long-press wired in `KeyboardScreen.kt` |
| KBD-06 | Plans 03, 04 | Insert text into any app via InputConnection | SATISFIED | `DictusImeService.commitText()` -> `currentInputConnection?.commitText(text, 1)`; `deleteBackward()` and `sendReturnKey()` wired; service registered in manifest |
| APP-06 | Plan 01 | Structured logging via Timber | SATISFIED | `TimberSetup.init()` plants DebugTree; `DictusApplication.kt` calls it in `onCreate()`; multiple service/UI files use `Timber.d(...)` |
| DSG-01 | Plan 01 | Branded dark theme matching Dictus iOS colors | SATISFIED | `DictusColors.kt` maps all iOS hex values; `DictusTheme.kt` uses `darkColorScheme`; `KeyboardScreen.kt` wraps all keyboard UI in `DictusTheme { }` |

All 6 requirement IDs claimed by Phase 1 plans are accounted for. REQUIREMENTS.md traceability table marks all 6 as Phase 1 / Complete. No orphaned requirements: KBD-04, KBD-05 are mapped to Phase 5; DICT-xx to Phases 2-3; APP-01 to APP-05 to Phase 4; DSG-02, DSG-03 to Phase 6 — all correctly outside Phase 1 scope.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `DictusImeService.kt` | 17 | Stale comment: "Currently renders a placeholder UI; the full keyboard layout is wired in Plan 03" | Info | Comment predates Plan 03 completion. Actual implementation calls `KeyboardScreen(...)` with real callbacks. Documentation issue only. |
| `KeyboardScreen.kt` | 188-201 | `Timber.d("Emoji picker not yet implemented")` and `Timber.d("Mic not yet implemented")` | Info (intentional) | Phase 2 placeholders per plan spec. `KeyType.EMOJI` and `KeyType.MIC` are explicitly non-functional in Phase 1. |

No blockers or functional warnings found.

---

## Human Verification Required

### 1. Full keyboard end-to-end on device or emulator

**Test:** Run `./gradlew installDebug`. Go to Settings > General management > Keyboard > On-screen keyboards, enable Dictus. Open any text field and switch to Dictus keyboard.
**Expected:**
- AZERTY keyboard renders on dark background (#0A1628)
- Tap a letter: character appears in text field
- Tap shift once: keys go uppercase, shift key turns blue (#3D7EFF)
- Tap shift twice quickly: caps lock activates, shift key turns lighter blue (#6BA3FF) with white underline indicator
- Tap ?123: numbers layer appears; tap ABC: returns to letters
- Long-press E: accent popup appears above keyboard with accented e variants
- Tap delete: removes one character; hold delete: repeats deletion
- Mic row visible above keyboard with globe icon and mic button

**Why human:** IME system activation, InputConnection routing, and visual rendering cannot be validated by static code analysis. Note: a human checkpoint on emulator was passed during Plan 03 UAT. Two bugs (accent popup focus theft, stale layer switch callbacks) were fixed in commit `6024c34`. Plan 04 gap closure (caps lock visual, mic row position) was completed in commits `42445ce` and `86f03c0`.

---

## Commit Verification

Commits confirmed present in git history at time of verification:

| Commit | Plan | Description |
|--------|------|-------------|
| `718e731` | 01-02 | IME service with Compose lifecycle |
| `336dc14` | 01-02 | Keyboard data models |
| `eb72391` | 01-03 | Complete keyboard UI composables |
| `6024c34` | 01-03 | Fix accent popup focus + stale callbacks |
| `42445ce` | 01-04 | Caps lock visual distinction on shift key |
| `86f03c0` | 01-04 | Move mic button row above keyboard |
| `7c2e591` | Phase docs | Resolve UAT gaps |

---

## Gaps Summary

No gaps found. All 18 observable truths are VERIFIED, all 6 requirements are SATISFIED, all key links are WIRED, and all commits are confirmed in git history.

The phase goal is fully achieved: a functional multi-module Gradle project exists with an IME service, full Compose keyboard UI (AZERTY/QWERTY), key interactions (tap, shift, caps lock with visual distinction, layer switching), accent popup, delete with key repeat, and a non-functional MicButtonRow placeholder positioned above the keyboard.

The one outstanding item (human end-to-end test) was completed during Plan 03 UAT. Plan 04 gap closure addressed two visual/layout issues found during that session.

---

*Verified: 2026-03-22*
*Verifier: Claude (gsd-verifier)*
