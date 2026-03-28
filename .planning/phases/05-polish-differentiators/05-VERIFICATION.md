---
phase: 05-polish-differentiators
verified: 2026-03-28T08:00:00Z
status: passed
score: 18/18 must-haves verified
---

# Phase 05: Polish & Differentiators Verification Report

**Phase Goal:** Users experience premium feedback, convenience features, and visual polish during dictation and typing
**Verified:** 2026-03-28
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Waveform bars animate smoothly with rise/decay interpolation | VERIFIED | `WaveformDriver.tickLevels()` implements smoothingFactor=0.3 (rise) and decayFactor=0.85 (fall) with snap-to-zero at <0.005 |
| 2 | User hears a sound when recording starts | VERIFIED | `DictationService`: `if (soundEnabled) soundPlayer.playStart()` on `startAudioCapture()` at line 377 |
| 3 | User hears a sound when recording stops | VERIFIED | `DictationService`: `if (soundEnabled) soundPlayer.playStop()` called in both `stopRecording()` and `confirmAndTranscribe()` (lines 202, 251) |
| 4 | Sounds are silent when toggle is off | VERIFIED | `soundEnabled` flag populated reactively from `SOUND_ENABLED` DataStore key; all play* calls gated on it |
| 5 | App renders with light colors when theme set to "light" | VERIFIED | `DictusTheme(ThemeMode.LIGHT)` uses `DictusLightColorScheme` (LightBackground=0xFFF2F2F7, LightSurface=0xFFFFFFFF) |
| 6 | App follows system dark/light when theme set to "auto" | VERIFIED | `ThemeMode.AUTO -> isSystemInDarkTheme()` delegates to Compose system API |
| 7 | Keyboard IME switches between light and dark themes | VERIFIED | `DictusImeService` reads `THEME` from DataStore, maps to `ThemeMode`, passes to `KeyboardScreen` which wraps in `DictusTheme(themeMode)` |
| 8 | Bottom nav pill indicator more visible with filled background | VERIFIED | `DictusBottomNavBar` uses filled translucent pill via `Box` with `clip(RoundedCornerShape) + background(DictusColors.Accent.copy(alpha=0.15f))` |
| 9 | Modeles tab uses chip/processor icon | VERIFIED | `Icons.Outlined.Memory` used for the Modeles nav item |
| 10 | Model card shows description + precision/vitesse bars side by side | VERIFIED | `ModelCard` uses `Row(weight(1f)/weight(1f))` layout with description LEFT, bars RIGHT, plus "WK" provider badge |
| 11 | User taps emoji key and sees emoji picker | VERIFIED | `_isEmojiPickerOpen` StateFlow toggled on `KeyType.EMOJI`; `KeyboardScreen` shows `EmojiPickerScreen` when `isEmojiPickerOpen=true` |
| 12 | User taps an emoji and it inserts into text field | VERIFIED | `onEmojiSelected` callback routes to `commitText(emoji)` in `DictusImeService` via `InputConnection` |
| 13 | User can return to keyboard from emoji picker | VERIFIED | Emoji key re-tap toggles `_isEmojiPickerOpen`; `onKeyDown(KEYCODE_BACK)` override dismisses picker |
| 14 | User sees 3 text suggestions above keyboard while typing | VERIFIED | `SuggestionBar` (36dp, 3 weighted slots, center bold) appears in `KeyboardScreen` when `suggestions.isNotEmpty()` |
| 15 | User taps suggestion and it replaces current word at cursor | VERIFIED | `onSuggestionSelected` in `DictusImeService` uses `ic.deleteSurroundingText(currentWord.length, 0)` then `ic.commitText("$suggestion ", 1)` |
| 16 | Settings sections are visually grouped in card-style rounded containers | VERIFIED | `SettingsCard` composable wraps each section with `clip(RoundedCornerShape(12.dp)) + background(Surface)` |
| 17 | Haptic toggle disables haptics in keyboard end-to-end | VERIFIED | `hapticsEnabled` propagated `DictusImeService -> KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton`; `HapticHelper.performKeyHaptic()` gated by `if (hapticsEnabled)` at all 3 call sites |
| 18 | Debug Logs viewer accessible from Settings with Clear/Copy | VERIFIED | `DebugLogsScreen` exists with `LazyColumn` + `DropdownMenu` ("Effacer" clears file + list, "Copier" copies to clipboard); navigated from Settings via `AppDestination.DebugLogs` route |

**Score:** 18/18 truths verified

---

## Required Artifacts

| Artifact | Provides | Status | Notes |
|----------|----------|--------|-------|
| `core/src/main/java/.../core/ui/WaveformDriver.kt` | Smoothing/decay animation driver | VERIFIED | Full implementation: `runLoop()`, `tickLevels()`, `interpolateToBarTargets()` |
| `app/src/main/java/.../audio/DictationSoundPlayer.kt` | SoundPool wrapper for recording sounds | VERIFIED | `loadSounds()`, `playStart()`, `playStop()`, `playCancel()`, `release()` |
| `app/src/main/res/raw/` (29 WAV files) | Sound assets | VERIFIED | 29 files found: `electronic_01a.wav` through `electronic_03c.wav` |
| `core/src/main/java/.../core/theme/DictusTheme.kt` | ThemeMode enum + DictusTheme(themeMode) | VERIFIED | `enum class ThemeMode { DARK, LIGHT, AUTO }` + both color schemes |
| `core/src/main/java/.../core/theme/DictusColors.kt` | Light palette colors | VERIFIED | 8 light tokens added: `LightBackground`, `LightSurface`, etc. |
| `ime/src/main/java/.../ime/ui/EmojiPickerScreen.kt` | AndroidView wrapping EmojiPickerView | VERIFIED | Fixed 310.dp height, `onEmojiSelected` callback |
| `ime/src/main/java/.../ime/suggestion/SuggestionEngine.kt` | Interface + StubSuggestionEngine MVP | VERIFIED | Interface with KDoc contract; `StubSuggestionEngine` prefix-matches 40-word FR/EN dict |
| `ime/src/main/java/.../ime/ui/SuggestionBar.kt` | 3-slot suggestion row composable | VERIFIED | Center slot bold, vertical separators, clickable slots |
| `app/src/main/java/.../ui/settings/DebugLogsScreen.kt` | Full-screen log viewer | VERIFIED | `LazyColumn` + monospace text + "Effacer"/"Copier" dropdown |
| `app/src/main/java/.../navigation/AppDestination.kt` | DebugLogs destination | VERIFIED | `data object DebugLogs : AppDestination("debug_logs")` |

---

## Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `ime/DictusImeService.kt` | `core/ui/WaveformDriver` | `LaunchedEffect(Unit) { waveformDriver.runLoop() }` in Recording state | WIRED |
| `app/service/DictationService.kt` | `app/audio/DictationSoundPlayer` | `soundPlayer.playStart()/.playStop()/.playCancel()` gated on `soundEnabled` | WIRED |
| `app/MainActivity.kt` | `core/theme/DictusTheme` | `DictusTheme(themeMode = themeMode)` reading THEME from DataStore | WIRED |
| `ime/DictusImeService.kt` | `core/theme/DictusTheme` | `KeyboardScreen(themeMode = themeMode)` — `KeyboardScreen` wraps in `DictusTheme(themeMode)` | WIRED |
| `ime/ui/KeyboardScreen.kt` | `ime/ui/EmojiPickerScreen.kt` | `if (isEmojiPickerOpen) EmojiPickerScreen(...)` — state hoisted to `DictusImeService` | WIRED |
| `ime/ui/EmojiPickerScreen.kt` | `InputConnection.commitText` | `onEmojiSelected` callback → `commitText(emoji)` in `DictusImeService` | WIRED |
| `ime/ui/KeyboardScreen.kt` | `ime/ui/SuggestionBar.kt` | `if (suggestions.isNotEmpty()) SuggestionBar(...)` above `MicButtonRow` | WIRED |
| `ime/ui/SuggestionBar.kt` | `InputConnection.commitText` | `onSuggestionSelected` callback routes to `deleteSurroundingText + commitText` in `DictusImeService` | WIRED |
| `app/ui/settings/SettingsScreen.kt` | `app/ui/settings/DebugLogsScreen.kt` | `SettingNavRow(onClick = onNavigateToDebugLogs)` → `navController.navigate(DebugLogs.route)` | WIRED |
| `core/preferences/PreferenceKeys.kt` | `ime/DictusImeService.kt` | `KEYBOARD_MODE` read via `entryPoint.dataStore()` in `KeyboardContent()`, passed as `initialLayer` | WIRED |
| `hapticsEnabled` parameter chain | `KeyButton.kt` | `KeyboardScreen -> KeyboardView -> KeyRow -> KeyButton`; `if (hapticsEnabled) HapticHelper.performKeyHaptic()` | WIRED |

---

## Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DICT-05 | 05-01, 05-02 | User sees real-time waveform animation during recording | SATISFIED | `WaveformDriver` with rise/decay interpolation wired into `DictusImeService` Recording state; light/dark theme also covers visual polish |
| DICT-06 | 05-01, 05-05 | User hears a sound when recording starts/stops (configurable) | SATISFIED | `DictationSoundPlayer` with `playStart/playStop/playCancel`; `SOUND_ENABLED` DataStore read reactively; Settings "Son de dictée" toggle end-to-end |
| KBD-04 | 05-04 | User can see 3 text suggestions above the keyboard and tap to insert | SATISFIED | `SuggestionBar` 3-slot composable + `StubSuggestionEngine` + `onUpdateSelection` wiring in `DictusImeService` |
| KBD-05 | 05-03 | User can open an emoji picker with categories and recent emojis | SATISFIED | `EmojiPickerScreen` wrapping `EmojiPickerView` (provides categories/recents natively); state hoisted with back-key dismissal |

**All 4 required IDs satisfied. No orphaned requirements.**

---

## Anti-Patterns Found

None. No TODO/FIXME/placeholder comments, empty returns, or stub implementations found in phase-5 files. The `StubSuggestionEngine` is explicitly documented as a Phase 5 MVP (not production quality), which is by design per the plan's stub-first contract.

---

## Human Verification Required

### 1. Waveform Animation Visual Quality

**Test:** Open a text field, enable Dictus keyboard, tap the mic button to start recording.
**Expected:** Waveform bars animate smoothly with organic rise (fast) and decay (slow) rather than jumping. Bars feel fluid at ~60fps.
**Why human:** Frame-rate animation and visual smoothness cannot be verified statically.

### 2. Sound Feedback Audibility

**Test:** Toggle "Son de dictée" on in Settings. Start and stop a recording.
**Expected:** Distinct electronic click plays on start, different click on stop. No sound plays when toggle is off.
**Why human:** Audio playback and SoundPool latency require a real device.

### 3. Theme Switching Visual Correctness

**Test:** Go to Settings > Apparence > Thème. Switch between Sombre, Clair, Automatique.
**Expected:** App background transitions between dark navy (#0A1628) and light (#F2F2F7). IME keyboard also follows the theme.
**Why human:** Visual rendering of Material 3 color schemes requires a real device.

### 4. Emoji Picker Categories and Recent Emojis

**Test:** Open Dictus keyboard, tap the emoji key.
**Expected:** Full-height emoji picker appears with category tabs and a "Recent" section. Tap an emoji — it appears in the text field. Tap emoji key again or press back — keyboard returns.
**Why human:** EmojiPickerView category rendering and recent-emoji persistence require a real device.

### 5. Suggestion Bar Responsiveness

**Test:** Open Dictus keyboard, type "bon" in a text field.
**Expected:** "bonjour", "bonsoir" appear as suggestions above the keyboard. Tap "bonjour" — the partial word is replaced with "bonjour " (with trailing space).
**Why human:** `onUpdateSelection` callback behavior in an IME context requires a live keyboard session.

### 6. ABC/123 Keyboard Mode

**Test:** Go to Settings > Clavier > Mode par défaut > select "123". Open any text field with Dictus keyboard.
**Expected:** Keyboard opens on the numeric layer instead of the letter layer.
**Why human:** `KeyboardLayer` initial state set from `KEYBOARD_MODE` preference requires a live IME session to confirm.

---

## Gaps Summary

No gaps. All 18 must-have truths are verified, all 10 required artifacts exist and are substantive, all 11 key links are wired. All 4 requirement IDs (DICT-05, DICT-06, KBD-04, KBD-05) are fully satisfied by the implementation. Six items are flagged for human verification as they require a real Android device with the keyboard active.

---

_Verified: 2026-03-28_
_Verifier: Claude (gsd-verifier)_
