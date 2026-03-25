---
status: resolved
phase: 02-audio-recording-service-architecture
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md, 02-03-SUMMARY.md]
started: 2026-03-25T22:00:00Z
updated: 2026-03-25T15:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Mic button starts recording overlay
expected: Open any text field, bring up Dictus keyboard. Tap the mic pill button on the right side of the bottom row. The keyboard should transform into a recording overlay showing: waveform bars animating, a timer counting up (00:00, 00:01...), a cancel (X) button, and a confirm (checkmark) button.
result: pass
fix: "Root cause was SecurityException in HapticHelper.performMicHaptic() crashing IME process. Fixed by using View.performHapticFeedback(LONG_PRESS)."

### 2. Cancel discards recording and returns to keyboard
expected: While recording (waveform animating), tap the X (cancel) button. Recording should stop, waveform disappears, and the full keyboard layout returns immediately. No text should be inserted.
result: pass

### 3. Confirm stops recording and returns to keyboard
expected: While recording, tap the checkmark (confirm) button. Recording should stop and the keyboard layout returns. (Transcription is not yet implemented — placeholder behavior is acceptable, but the UI should return to keyboard state.)
result: pass

### 4. Foreground notification during recording
expected: While recording via the mic button, pull down the notification shade. A persistent "Dictus - Recording" notification should be visible with a Stop action button. Tapping the Stop action should end the recording.
result: not-retested
reason: Not re-tested during gap closure UAT (focus was on 3 reported issues)

### 5. Haptic feedback on key press
expected: Type a few characters on the Dictus keyboard. Each key press should produce a haptic tick on touch-down (not on release). The mic button should produce a stronger/heavier haptic than regular character keys.
result: pass
fix: "Same root cause as test 1 — HapticHelper crash. Fixed with View.performHapticFeedback."

### 6. Key preview popup on character press
expected: Press and hold a character key. A preview popup should appear above the key showing the character being pressed. Release the key — the popup disappears.
result: pass

### 7. Accent popup with slide-to-select
expected: Long-press a vowel key (e.g., "e"). An accent popup should appear showing accent variants (e, e, e, etc.). Slide finger to select an accent character. The selected accent should be inserted into the text field.
result: pass
fix: "Added onGloballyPositioned to clamp popup X offset within screen bounds."

### 8. Keyboard height matches Gboard proportions
expected: Switch between Gboard and Dictus keyboard. The overall keyboard height should be similar — Dictus should not feel noticeably taller or shorter than Gboard.
result: pass

## Summary

total: 8
passed: 7
issues: 0
pending: 0
not-retested: 1

## Gaps

- truth: "User can tap the mic button to start recording and the keyboard transforms into a recording overlay with waveform, timer, cancel and confirm buttons"
  status: resolved
  reason: "User reported: Quand je clique sur le micro, ca me ferme le clavier. Je ne peux pas voir si ca lance l'enregistrement."
  severity: blocker
  test: 1
  root_cause: "DictusImeService.handleMicTap() at line 165 calls startForegroundService() directly instead of using the already-bound dictationController.startRecording(). Starting a foreground service from an IME context causes Android to dismiss the keyboard window."
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt"
      issue: "handleMicTap() calls startForegroundService() instead of controller.startRecording()"
  missing:
    - "Replace startForegroundService(startIntent) with dictationController.startRecording() — the controller already handles foreground promotion internally"
  debug_session: ".planning/debug/mic-tap-closes-keyboard.md"

- truth: "Mic button produces stronger haptic feedback than regular keys"
  status: resolved
  reason: "User reported: Les touches marchent bien sauf le micro : pas d'haptic et le clavier disparait"
  severity: major
  test: 5
  root_cause: "Same root cause as test 1 — handleMicTap() crashes the keyboard before haptic fires. The haptic call is likely after the startForegroundService() call, or the keyboard dismissal prevents it from executing."
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt"
      issue: "handleMicTap() flow breaks before haptic feedback executes"
  missing:
    - "Fix test 1 (use controller.startRecording()), then ensure haptic fires before recording starts"
  debug_session: ".planning/debug/mic-tap-closes-keyboard.md"

- truth: "Accent popup for edge keys (like 'a') stays within screen bounds showing all accent variants"
  status: resolved
  reason: "User reported: Pour la lettre a, on ne voit pas tous les caracteres car le popup deborde de l'ecran sur la droite"
  severity: minor
  test: 7
  root_cause: "KeyButton.kt Popup at line 279 uses Alignment.TopCenter with x-offset hardcoded to 0. No screen-edge clamping. For leftmost key 'a', the popup (6 accents x 44dp = 264dp) extends past the left screen edge. resolveAccentIndex() at line 107 also uses unclamped centered math."
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt"
      issue: "Popup positioning at lines 279-281 has no edge clamping; resolveAccentIndex() at line 107 uses naive centered math"
  missing:
    - "Get key absolute X via onGloballyPositioned, clamp popup offset to screen bounds, sync resolveAccentIndex() with clamped position"
  debug_session: ".planning/debug/accent-popup-left-overflow.md"
