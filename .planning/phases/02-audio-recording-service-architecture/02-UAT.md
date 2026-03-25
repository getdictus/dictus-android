---
status: complete
phase: 02-audio-recording-service-architecture
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md]
started: 2026-03-25T22:00:00Z
updated: 2026-03-25T22:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Mic button starts recording overlay
expected: Open any text field, bring up Dictus keyboard. Tap the mic pill button on the right side of the bottom row. The keyboard should transform into a recording overlay showing: waveform bars animating, a timer counting up (00:00, 00:01...), a cancel (X) button, and a confirm (checkmark) button.
result: issue
reported: "Quand je clique sur le micro, ca me ferme le clavier. Je ne peux pas voir si ca lance l'enregistrement."
severity: blocker

### 2. Cancel discards recording and returns to keyboard
expected: While recording (waveform animating), tap the X (cancel) button. Recording should stop, waveform disappears, and the full keyboard layout returns immediately. No text should be inserted.
result: skipped
reason: Untestable due to test 1 blocker (keyboard closes on mic tap)

### 3. Confirm stops recording and returns to keyboard
expected: While recording, tap the checkmark (confirm) button. Recording should stop and the keyboard layout returns. (Transcription is not yet implemented — placeholder behavior is acceptable, but the UI should return to keyboard state.)
result: skipped
reason: Untestable due to test 1 blocker (keyboard closes on mic tap)

### 4. Foreground notification during recording
expected: While recording via the mic button, pull down the notification shade. A persistent "Dictus - Recording" notification should be visible with a Stop action button. Tapping the Stop action should end the recording.
result: skipped
reason: Untestable due to test 1 blocker (keyboard closes on mic tap)

### 5. Haptic feedback on key press
expected: Type a few characters on the Dictus keyboard. Each key press should produce a haptic tick on touch-down (not on release). The mic button should produce a stronger/heavier haptic than regular character keys.
result: issue
reported: "Les touches marchent bien sauf le micro : pas d'haptic et le clavier disparait (meme probleme que test 1)"
severity: major

### 6. Key preview popup on character press
expected: Press and hold a character key. A preview popup should appear above the key showing the character being pressed. Release the key — the popup disappears.
result: pass

### 7. Accent popup with slide-to-select
expected: Long-press a vowel key (e.g., "e"). An accent popup should appear showing accent variants (e, e, e, etc.). Slide finger to select an accent character. The selected accent should be inserted into the text field.
result: issue
reported: "OK sauf pour la lettre a : on ne voit pas tous les caracteres car deborde de l'ecran sur la droite. Il faut repositionner le popup quand la touche est proche du bord."
severity: minor

### 8. Keyboard height matches Gboard proportions
expected: Switch between Gboard and Dictus keyboard. The overall keyboard height should be similar — Dictus should not feel noticeably taller or shorter than Gboard.
result: pass

## Summary

total: 8
passed: 2
issues: 3
pending: 0
skipped: 3

## Gaps

- truth: "User can tap the mic button to start recording and the keyboard transforms into a recording overlay with waveform, timer, cancel and confirm buttons"
  status: failed
  reason: "User reported: Quand je clique sur le micro, ca me ferme le clavier. Je ne peux pas voir si ca lance l'enregistrement."
  severity: blocker
  test: 1
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "Mic button produces stronger haptic feedback than regular keys"
  status: failed
  reason: "User reported: Les touches marchent bien sauf le micro : pas d'haptic et le clavier disparait"
  severity: major
  test: 5
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "Accent popup for edge keys (like 'a') stays within screen bounds showing all accent variants"
  status: failed
  reason: "User reported: Pour la lettre a, on ne voit pas tous les caracteres car le popup deborde de l'ecran sur la droite"
  severity: minor
  test: 7
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
