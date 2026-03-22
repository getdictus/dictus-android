---
status: complete
phase: 01-core-foundation-keyboard-shell
source: [01-01-SUMMARY.md, 01-02-SUMMARY.md, 01-03-SUMMARY.md]
started: 2026-03-22T14:00:00Z
updated: 2026-03-22T14:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Install and Enable Dictus Keyboard
expected: Install the app via ./gradlew installDebug. Go to Settings > System > Languages & Input > On-screen keyboard > Manage on-screen keyboards. Dictus should appear in the list. Enable it. Dictus keyboard should now be selectable when tapping a text field.
result: pass

### 2. Basic Letter Entry (AZERTY Layout)
expected: Open any text field and switch to Dictus keyboard. The keyboard should display in dark theme (dark background with blue accent). First letter row should show AZERTY layout (A, Z, E, R, T, Y...). Tap several keys — corresponding letters should appear in the text field.
result: pass

### 3. Shift and Caps Lock
expected: Tap the shift key once — next letter typed should be uppercase, then shift auto-disengages back to lowercase. Double-tap shift — caps lock engages (visual indicator), all subsequent letters are uppercase until you tap shift again to disengage.
result: issue
reported: "Il n'y a aucune différence visuelle entre shift simple et caps lock. Pas de différence visuelle entre les deux états."
severity: minor

### 4. Layer Switching (Numbers and Symbols)
expected: Tap the "?123" key — keyboard switches to number/punctuation layer. Tap "#+=" — keyboard switches to symbols layer. Tap "ABC" — keyboard returns to letter layout. All transitions should be smooth with correct keys displayed.
result: pass

### 5. Delete Key with Repeat
expected: Type some text, then tap delete — removes one character. Long-press delete — continuously deletes characters at an accelerating rate until you release.
result: pass

### 6. Long-Press Accent Popup
expected: Long-press a vowel key (e.g., "e" or "a") — a popup appears above the key showing French accented variants (e.g., e → è, é, ê, ë). Slide to or tap an accent — the accented character is inserted in the text field. The popup dismisses and the keyboard remains visible.
result: pass

### 7. Mic Button Row
expected: Below or above the keyboard rows, a row with a microphone icon should be visible. Tapping it should not crash the app (it's a placeholder for Phase 2).
result: issue
reported: "Le bouton mic est en dessous du clavier au lieu d'être au-dessus. Le design ne correspond pas du tout aux maquettes. Mais c'est peut-être prévu pour les phases suivantes quand les mockups seront appliqués."
severity: cosmetic

## Summary

total: 7
passed: 5
issues: 2
pending: 0
skipped: 0

## Gaps

- truth: "Shift key should have a distinct visual indicator differentiating single-shift from caps lock state"
  status: failed
  reason: "User reported: Il n'y a aucune différence visuelle entre shift simple et caps lock. Pas de différence visuelle entre les deux états."
  severity: minor
  test: 3
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""

- truth: "Mic button row should be positioned above the keyboard and match the design mockups"
  status: failed
  reason: "User reported: Le bouton mic est en dessous du clavier au lieu d'être au-dessus. Le design ne correspond pas du tout aux maquettes. Mais c'est peut-être prévu pour les phases suivantes quand les mockups seront appliqués."
  severity: cosmetic
  test: 7
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
