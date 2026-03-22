---
status: resolved
phase: 01-core-foundation-keyboard-shell
source: [01-01-SUMMARY.md, 01-02-SUMMARY.md, 01-03-SUMMARY.md]
started: 2026-03-22T14:00:00Z
updated: 2026-03-22T14:35:00Z
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
  status: resolved
  reason: "User reported: Il n'y a aucune différence visuelle entre shift simple et caps lock. Pas de différence visuelle entre les deux états."
  severity: minor
  test: 3
  root_cause: "isCapsLock state in KeyboardScreen is never propagated to KeyButton. The rendering chain (KeyboardScreen > KeyboardView > KeyRow > KeyButton) only passes isShifted: Boolean. Both single-shift and caps-lock set isShifted=true, so the shift key renders identically. KeyButton.kt line 49 has only one visual branch for shift."
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt"
      issue: "isCapsLock state exists (line 40) but is not passed to KeyboardView (line 57)"
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardView.kt"
      issue: "Missing isCapsLock parameter in signature"
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyButton.kt"
      issue: "Missing isCapsLock parameter; line 49 only checks isShifted for shift key color"
  missing:
    - "Add isCapsLock: Boolean parameter to KeyboardView, KeyRow, and KeyButton"
    - "Pass isCapsLock from KeyboardScreen through the composable chain"
    - "Add distinct visual for caps lock in KeyButton (e.g., different icon or underline indicator)"
  debug_session: ".planning/debug/shift-caps-no-visual-diff.md"

- truth: "Mic button row should be positioned above the keyboard and match the design mockups"
  status: resolved
  reason: "User reported: Le bouton mic est en dessous du clavier au lieu d'être au-dessus. Le design ne correspond pas du tout aux maquettes. Mais c'est peut-être prévu pour les phases suivantes quand les mockups seront appliqués."
  severity: cosmetic
  test: 7
  root_cause: "In KeyboardScreen.kt Column composable, MicButtonRow (line 126) is placed after KeyboardView (line 55). Compose Column lays out children top-to-bottom, so MicButtonRow renders below the keyboard. MicButtonRow.kt KDoc (line 23) explicitly says 'below the keyboard'."
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt"
      issue: "MicButtonRow at line 126 placed after KeyboardView at line 55 in Column"
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/MicButtonRow.kt"
      issue: "KDoc says 'below the keyboard' instead of 'above'"
  missing:
    - "Move MicButtonRow call before KeyboardView in the Column"
    - "Update KDoc comments to reflect 'above' positioning"
  debug_session: ".planning/debug/mic-button-row-position.md"
