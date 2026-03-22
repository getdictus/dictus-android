---
phase: 01-core-foundation-keyboard-shell
verified: 2026-03-22T14:00:00Z
status: passed
score: 15/15 must-haves verified
re_verification: false
human_verification:
  - test: "Enable Dictus keyboard in Settings and type in any app"
    expected: "AZERTY keyboard renders with dark Dictus theme, letter entry works, shift/caps, layer switching (?123/ABC), long-press accent popup, delete repeat all functional"
    why_human: "End-to-end IME behavior requires installing APK on device/emulator and interacting with system settings — not verifiable via static code analysis. The 01-03 SUMMARY confirms a human checkpoint was completed and the keyboard was verified on emulator."
---

# Phase 1: Core Foundation & Keyboard Shell Verification Report

**Phase Goal:** Multi-module project skeleton with Dictus dark theme, IME service with Compose lifecycle, keyboard data models (AZERTY/QWERTY, numbers, symbols, accents), and full Compose keyboard UI.
**Verified:** 2026-03-22
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Multi-module Gradle project (app/ime/core) builds successfully | VERIFIED | `settings.gradle.kts` includes `:app`, `:ime`, `:core`; all three `build.gradle.kts` files present; 6 commits confirm `./gradlew assembleDebug` succeeded |
| 2 | Dictus dark theme defines exact iOS color tokens as Material 3 ColorScheme | VERIFIED | `DictusColors.kt` contains `Color(0xFF0A1628)`, `Color(0xFF3D7EFF)`, `Color(0xFF161C2C)` etc.; `DictusTheme.kt` uses `darkColorScheme(...)` wired to those tokens |
| 3 | Timber is initialized in Application.onCreate() and logs with auto-tags | VERIFIED | `DictusApplication.kt` calls `TimberSetup.init(BuildConfig.DEBUG)` in `onCreate()`; `TimberSetup.kt` calls `Timber.plant(Timber.DebugTree())` |
| 4 | AZERTY layout has 4 rows with correct key sequences (A-Z-E-R-T-Y row 1) | VERIFIED | `KeyboardLayouts.kt` `azertyLetters` row 1: A, Z, E, R, T, Y, U, I, O, P |
| 5 | QWERTY layout has 4 rows with correct key sequences (Q-W-E-R-T-Y row 1) | VERIFIED | `KeyboardLayouts.kt` `qwertyLetters` row 1: Q, W, E, R, T, Y, U, I, O, P |
| 6 | Numbers and symbols layers have correct key data | VERIFIED | `numbersRows` contains digits 1-0 and punctuation; `symbolsRows` contains brackets, currency, math symbols |
| 7 | AccentMap returns correct French accented variants | VERIFIED | `AccentMap.kt`: `'e'` maps to `listOf("è","é","ê","ë","ē")`; lowercase and uppercase both covered |
| 8 | IME service is declared in manifest with BIND_INPUT_METHOD permission | VERIFIED | `ime/src/main/AndroidManifest.xml` declares service with `android:permission="android.permission.BIND_INPUT_METHOD"` and `android.view.InputMethod` intent-filter |
| 9 | LifecycleInputMethodService wires Compose lifecycle owners on decorView | VERIFIED | `LifecycleInputMethodService.kt` implements `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` and calls `setViewTreeLifecycleOwner`, `setViewTreeViewModelStoreOwner`, `setViewTreeSavedStateRegistryOwner` on `decorView` |
| 10 | User can see Dictus keyboard with letter keys rendered in Compose | VERIFIED (human checkpoint confirmed) | `KeyboardScreen.kt` + `KeyboardView.kt` + `KeyRow.kt` + `KeyButton.kt` all substantive; DictusImeService overrides `KeyboardContent()` with `KeyboardScreen(...)`; 01-03 SUMMARY documents human emulator verification passed |
| 11 | User can tap a letter key and character appears in text field | VERIFIED | `KeyButton.kt` uses `detectTapGestures(onTap = ...)` calling `onPress()`; `KeyboardScreen.kt` routes `KeyType.CHARACTER` to `onCommitText(output)`; `DictusImeService.commitText()` calls `currentInputConnection?.commitText(text, 1)` |
| 12 | User can tap shift to toggle uppercase letters | VERIFIED | `KeyboardScreen.handleKeyPress()` handles `KeyType.SHIFT` with single-tap (toggle shift) and double-tap (caps lock); `KeyButton.kt` renders uppercase labels when `isShifted = true` |
| 13 | User can tap ?123/ABC to switch keyboard layers | VERIFIED | `KeyboardScreen.handleKeyPress()` handles `KeyType.LAYER_SWITCH` switching `currentLayer` between LETTERS/NUMBERS/SYMBOLS; `KeyboardView.kt` selects rows based on layer |
| 14 | User can long-press a letter with accents to see popup | VERIFIED | `KeyboardScreen.kt` `onKeyLongPress` checks `AccentMap.hasAccents(char)` then sets `showAccentPopup = true`; `AccentPopup.kt` renders popup with `Popup(focusable = false)` and calls `AccentMap.accentsFor(char)` |
| 15 | User can tap delete to remove characters (including key repeat) | VERIFIED | `KeyButton.kt` DELETE key uses custom `awaitPointerEventScope` with 400ms initial delay then 50ms repeat loop; routes to `DictusImeService.deleteBackward()` calling `currentInputConnection?.deleteSurroundingText(1, 0)` |

**Score:** 15/15 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Status | Evidence |
|----------|--------|----------|
| `gradle/libs.versions.toml` | VERIFIED | Exists; contains `timber = "5.0.1"`, `hilt = "2.51.1"`, `agp = "8.7.3"` (version adjusted from plan — 2.57.1 not in Maven, 2.51.1 used instead) |
| `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt` | VERIFIED | Exists; contains `0xFF0A1628`; 11 color tokens defined |
| `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt` | VERIFIED | Exists; contains `darkColorScheme` and `fun DictusTheme` |
| `core/src/main/java/dev/pivisolutions/dictus/core/logging/TimberSetup.kt` | VERIFIED | Exists; contains `Timber.plant(Timber.DebugTree())` |
| `app/src/main/java/dev/pivisolutions/dictus/DictusApplication.kt` | VERIFIED | Exists; contains `@HiltAndroidApp` |

### Plan 02 Artifacts

| Artifact | Status | Evidence |
|----------|--------|----------|
| `ime/src/main/java/dev/pivisolutions/dictus/ime/LifecycleInputMethodService.kt` | VERIFIED | Exists; contains `LifecycleOwner` implementation with full lifecycle event handling |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyboardLayouts.kt` | VERIFIED | Exists; contains `numbersRows`, `symbolsRows`, `azertyLetters`, `qwertyLetters`, `lettersForLayout()` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/model/AccentMap.kt` | VERIFIED | Exists; contains `accentsFor()` and `hasAccents()` with full French accent coverage |
| `ime/src/main/AndroidManifest.xml` | VERIFIED | Exists; contains `BIND_INPUT_METHOD` and `android.view.InputMethod` |

### Plan 03 Artifacts

| Artifact | Status | Evidence |
|----------|--------|----------|
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` | VERIFIED | Exists; contains `fun KeyboardScreen(` with full state management (layer, shift, caps, accent popup) |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt` | VERIFIED | Exists; contains `DictusColors.KeyBackground`, `DictusColors.KeySpecialBackground`, `detectTapGestures`, `RoundedCornerShape(8.dp)`, `rememberUpdatedState` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/AccentPopup.kt` | VERIFIED | Exists; contains `AccentMap.accentsFor` and `Popup` with `focusable = false` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/MicButtonRow.kt` | VERIFIED | Exists; contains `height(56.dp)`, mic circle button, keyboard switcher |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt` | VERIFIED | Exists; contains `KeyboardLayouts.lettersForLayout` |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyRow.kt` | VERIFIED | Exists; renders `Row` composable with `KeyButton` elements |

---

## Key Link Verification

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `app/build.gradle.kts` | `core/build.gradle.kts` | `implementation(project(":core"))` | WIRED | Line 37: `implementation(project(":core"))` |
| `DictusApplication.kt` | `TimberSetup.kt` | `TimberSetup.init(BuildConfig.DEBUG)` | WIRED | Line 19: `TimberSetup.init(BuildConfig.DEBUG)` |
| `DictusImeService.kt` | `LifecycleInputMethodService.kt` | extends base class | WIRED | `class DictusImeService : LifecycleInputMethodService()` |
| `DictusImeService.kt` | `DictusImeEntryPoint.kt` | `EntryPointAccessors.fromApplication` | WIRED | Line 4-5: import + lazy `EntryPointAccessors.fromApplication(...)` |
| `KeyboardLayouts.kt` | `KeyDefinition.kt` | uses data class | WIRED | All layout rows constructed with `KeyDefinition(...)` |
| `KeyboardScreen.kt` | `DictusImeService.kt` | callback lambdas for commitText/deleteBackward | WIRED | `onCommitText`, `onDeleteBackward`, `onSendReturn` passed from service to screen |
| `KeyButton.kt` | `AccentPopup.kt` (via `KeyboardScreen`) | long-press triggers popup display | WIRED | `onKeyLongPress` in `KeyboardScreen.kt` checks `AccentMap.hasAccents()` and sets `showAccentPopup = true` |
| `KeyboardScreen.kt` | `KeyboardLayouts.kt` | reads layout data for current layer | WIRED | `KeyboardView.kt` calls `KeyboardLayouts.lettersForLayout(layout)` |
| `DictusImeService.kt` | `InputConnection` | `currentInputConnection.commitText()` | WIRED | `currentInputConnection?.commitText(text, 1)`, `deleteSurroundingText(1, 0)`, `sendKeyEvent(...)` all present |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| APP-06 | Plan 01 | Structured logging via Timber throughout the app | SATISFIED | `TimberSetup.kt` plants DebugTree; `DictusApplication.kt` initializes it; `DictusImeService.kt`, `KeyboardScreen.kt`, `MicButtonRow.kt` all use `Timber.d(...)` |
| DSG-01 | Plan 01 | App uses branded dark theme matching Dictus iOS colors | SATISFIED | `DictusColors.kt` defines all iOS color tokens; `DictusTheme.kt` uses `darkColorScheme` with those tokens; `KeyboardScreen.kt` wraps in `DictusTheme` |
| KBD-01 | Plan 02 | User can type on full AZERTY keyboard with shift, caps lock, numbers, symbols | SATISFIED | `KeyboardLayouts.kt` has AZERTY + numbers + symbols; `KeyboardScreen.kt` handles SHIFT (single/double-tap for caps), `LAYER_SWITCH` for numbers/symbols |
| KBD-02 | Plan 02 | User can switch between AZERTY and QWERTY layouts | SATISFIED | `KeyboardLayouts.kt` has both layouts; `KeyboardView.kt` calls `lettersForLayout(layout)` selecting the correct one based on `currentLayout` state |
| KBD-03 | Plan 02 | User can access accented characters via long-press | SATISFIED | `AccentMap.kt` maps e/a/u/i/o/c/y/n (upper and lower) to French accented variants; `AccentPopup.kt` renders popup; `KeyboardScreen.kt` wires long-press to popup display |
| KBD-06 | Plan 03 | User can insert text into any app via InputConnection | SATISFIED | `DictusImeService.commitText()` calls `currentInputConnection?.commitText(text, 1)`; `deleteBackward()` and `sendReturnKey()` also wired; `DictusImeService.kt` registered in manifest as an IME service |

All 6 requirements claimed by phase plans are accounted for. No orphaned requirements found — REQUIREMENTS.md marks all 6 as Phase 1 / Complete.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `DictusImeService.kt` | 16 | Stale doc comment: "Currently renders a placeholder UI" | Info | The comment predates the Plan 03 update. Actual implementation calls `KeyboardScreen(...)` — not a placeholder. Documentation issue only, no functional impact. |
| `KeyboardScreen.kt` | 188-200 | `KeyType.EMOJI` and `KeyType.MIC` log "not yet implemented" | Info (intentional) | These are Phase 2 placeholders explicitly designed per plan spec. Non-blocking — the plan called for these as non-functional stubs in Phase 1. |

No blockers or warnings found.

---

## Human Verification Required

### 1. Full keyboard end-to-end on device/emulator

**Test:** Install APK via `./gradlew installDebug`, enable Dictus keyboard in system settings, select it as active keyboard in any app text field
**Expected:** AZERTY keyboard renders with dark Dictus background (#0A1628), letter tap inserts character, shift toggles uppercase, ?123 switches to numbers, long-press E shows accented variants, delete removes characters, hold delete repeats deletion, mic row visible below keyboard
**Why human:** IME lifecycle, input connection, and system keyboard switching cannot be verified by static analysis. Visual theme correctness requires rendering.

**Note:** The 01-03 SUMMARY documents that this human checkpoint was completed by the user during Plan 03 Task 2, who confirmed the keyboard works on emulator. Both runtime bugs found (accent popup focus theft, stale callbacks) were fixed in commit `6024c34`.

---

## Commit Verification

All 6 commits documented in SUMMARYs are present in git history:

| Commit | Plan | Description |
|--------|------|-------------|
| `623aaae` | 01-01 | feat: multi-module Gradle project |
| `fb6d340` | 01-01 | feat: Dictus dark theme and unit tests |
| `718e731` | 01-02 | feat: IME service with Compose lifecycle |
| `336dc14` | 01-02 | feat: keyboard data models |
| `eb72391` | 01-03 | feat: complete keyboard UI composables |
| `6024c34` | 01-03 | fix: accent popup focus + stale callbacks |

---

## Gaps Summary

No gaps found. All 15 observable truths are VERIFIED, all 6 requirements are SATISFIED, all key links are WIRED, all documented commits exist in git history.

The only human verification item (end-to-end on device) was completed during the Plan 03 checkpoint and documented in 01-03-SUMMARY.md. The stale doc comment and intentional Phase 2 stubs are not blockers.

---

*Verified: 2026-03-22*
*Verifier: Claude (gsd-verifier)*
