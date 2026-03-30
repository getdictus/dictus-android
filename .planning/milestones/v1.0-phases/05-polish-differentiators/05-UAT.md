---
status: complete
phase: 05-polish-differentiators
source: [05-01-SUMMARY.md, 05-02-SUMMARY.md, 05-03-SUMMARY.md, 05-04-SUMMARY.md, 05-05-SUMMARY.md]
started: 2026-03-28T20:00:00Z
updated: 2026-03-28T20:00:00Z
---

## Current Test

[testing complete]
expected: |
  Open the Dictus keyboard in any text field, tap the mic button to start recording. You should see animated waveform bars that respond to your voice — bars rise smoothly when you speak and decay back down when you stop. The animation should feel fluid (not choppy).
awaiting: user response

## Tests

### 1. Waveform animation during recording
expected: Open the Dictus keyboard in any text field, tap the mic button to start recording. You should see animated waveform bars that respond to your voice — bars rise smoothly when you speak and decay back down when you stop. The animation should feel fluid (not choppy).
result: issue
reported: "Pas de différence visible, l'animation défile peut-être un peu moins rapidement mais encore loin de l'animation iOS pendant un enregistrement"
severity: major

### 2. Sound feedback on recording start/stop
expected: With sound enabled in Settings, start a recording — you should hear a short start sound. Stop the recording — you should hear a stop sound. Cancel a recording — you should hear a cancel sound. Then toggle sound OFF in Settings and repeat — no sounds should play.
result: pass

### 3. Theme switching (Dark / Light / Auto)
expected: Go to Settings > Apparence. You should see 3 theme options: Sombre, Clair, Automatique. Select "Clair" — the app should switch to a light theme (light background, dark text). Select "Automatique" — the app should follow your system dark/light mode. Select "Sombre" — back to dark theme.
result: issue
reported: "Le sélecteur est bien présent dans les settings, mais aucune différence visible entre thème sombre et clair, ça ne marche pas"
severity: major

### 4. Theme applies to keyboard
expected: After switching to Light theme in Settings, open the Dictus keyboard in any app. The keyboard should also appear in light colors (light background, dark key labels). Switch back to Dark — keyboard returns to dark colors.
result: pass

### 5. Bottom navigation bar polish
expected: Look at the bottom navigation bar in the app. The "Modeles" tab should have a chip/memory icon. The active tab should have a filled translucent pill indicator behind the icon and label (not just a small dot).
result: pass

### 6. Model card redesign
expected: Go to the Models screen. Each model card should show: model name with a "WK" provider badge next to it, description text on the LEFT side, and precision/speed bars on the RIGHT side in a side-by-side layout.
result: issue
reported: "Les barres precision/vitesse doivent etre sur la meme ligne avec 5 sous-barres segmentees comme iOS, pas des barres continues empilees"
severity: major

### 7. Emoji picker opens and inserts
expected: Open the Dictus keyboard, tap the emoji key (smiley face). The emoji picker should replace the keyboard area at the same height. Browse emoji categories, tap an emoji — it should be inserted into the text field. The picker stays open for rapid entry (doesn't auto-dismiss).
result: issue
reported: "Picker suit le theme systeme pas le theme app, deborde en bas, pas de boutons ABC/Suppr pour revenir au clavier rapidement"
severity: major

### 8. Emoji picker dismissal
expected: With the emoji picker open, tap the emoji key again — it should toggle back to the regular keyboard. Alternatively, press the back button — the picker should also dismiss and return to the keyboard.
result: [pending]

### 9. Suggestion bar appears while typing
expected: Open the Dictus keyboard and start typing a word (e.g., "bon"). A suggestion bar should appear above the keyboard with up to 3 word suggestions. The center suggestion should be bold. The bar only appears when there are suggestions (not on empty input).
result: issue
reported: "Barre apparait/disparait avec glitch, devrait etre toujours visible avec 3 slots. Gauche devrait montrer le mot en cours. Pas assez de suggestions."
severity: major

### 10. Suggestion bar inserts word
expected: While typing and seeing suggestions, tap one of the suggested words. It should replace what you've typed so far and add a space after. The suggestion bar should update for the next word.
result: pass

### 11. Settings card-style grouping
expected: Open Settings. The settings should be organized in rounded card sections: TRANSCRIPTION, CLAVIER, APPARENCE, A PROPOS. Each section should have a rounded card background grouping its rows together (iOS-style grouped settings).
result: pass

### 12. ABC/123 keyboard mode picker
expected: In Settings > CLAVIER, you should see an ABC/123 keyboard mode option. Select "123" (numbers). Close and reopen the keyboard — it should open on the numbers layer instead of letters. Switch back to "ABC" — keyboard opens on letters.
result: pass

### 13. Haptics toggle
expected: In Settings > CLAVIER, toggle haptics OFF. Open the keyboard and type — you should NOT feel vibration on key presses. Toggle haptics back ON — typing should produce haptic feedback again.
result: pass

### 14. Debug Logs screen
expected: In Settings > A PROPOS, tap "Debug Logs". A full-screen log viewer should open showing log lines in monospace font. There should be a 3-dot menu with "Effacer" (clear) and "Copier" (copy) options. The bottom nav should be hidden on this screen.
result: pass

## Summary

total: 14
passed: 8
issues: 5
pending: 0
skipped: 0

## Gaps

- truth: "Waveform animation matches iOS quality — smooth rise/decay responsive to voice"
  status: failed
  reason: "User reported: Pas de différence visible, l'animation défile peut-être un peu moins rapidement mais encore loin de l'animation iOS pendant un enregistrement"
  severity: major
  test: 1
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
- truth: "Theme switching between Sombre/Clair/Automatique visually changes the app appearance"
  status: failed
  reason: "User reported: Le sélecteur est bien présent dans les settings, mais aucune différence visible entre thème sombre et clair, ça ne marche pas"
  severity: major
  test: 3
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
