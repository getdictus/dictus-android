---
status: complete
phase: 04-model-management-onboarding
source: [04-07-SUMMARY.md, 04-08-SUMMARY.md]
started: 2026-03-27T21:00:00Z
updated: 2026-03-27T21:00:00Z
---

## Current Test

[testing complete]

## Tests

### 1. FakeSettingsCard Android-native style
expected: Onboarding step 3 (keyboard setup) shows a card titled "Clavier a l'ecran" with two entries: "Gboard" (subtitle "Google") and "Dictus" (subtitle "PIVI Solutions"). Each entry has a toggle that animates ON sequentially (Gboard at 800ms, Dictus at 1400ms). No iOS-style breadcrumb, no "Autoriser l'acces complet".
result: issue
reported: "Subtitles wrong — Gboard should say 'Multilingual typing', Dictus should be 'Dictus Keyboard' / 'QWERTY (English)'. Toggle color should be green not purple. Need round logos (Google G for Gboard, Dictus icon for Dictus). Match real Android 'Manage keyboards' screen. Also set Dictus app icon."
severity: cosmetic
fix_applied: "Updated FakeSettingsCard: correct texts, green toggle (#4CAF50), Google G circle icon, Dictus logo from iOS repo, 'Manage keyboards' title. Added app icon to AndroidManifest + generated mipmap assets."

### 2. Nouvelle dictee launches recording
expected: Home screen > tap "Nouvelle dictee" button > navigates to a full-screen RecordingScreen (no bottom nav). Recording auto-starts immediately. Waveform + timer visible. Stop button stops recording and shows "Transcription en cours..." with sine-wave animation.
result: pass

### 3. Recording result and re-record
expected: After transcription completes, result text appears in a GlassCard with a copy button. A blue mic button is visible to re-record. Tapping blue mic resets and starts a new recording. Back button (top-left arrow) returns to Home.
result: pass

### 4. Model download persistence
expected: Go to Models tab > tap download on an available model (e.g. base) > progress bar fills > on completion, the model moves from "Disponibles" to "Telecharges" section. Download button does NOT reappear.
result: pass

### 5. Model card redesign — description and bars
expected: Model cards show: model name + quality label, a description text (e.g. "Precis et equilibre" for base), two labeled progress bars ("Precision" in blue, "Vitesse" in light blue) with variable fill per model. No mic icon on cards. Size in Mo shown below bars.
result: pass

### 6. Tap-to-select active model
expected: On Models screen, tap a downloaded model card that is NOT currently active. The card gets a blue accent border (2dp) indicating it's now active. Previously active card loses its border. Selection persists (leave and return to Models screen).
result: pass

### 7. Settings — model picker removed
expected: Settings > TRANSCRIPTION section shows only "Langue" picker. No "Modele actif" picker row. Model selection now happens on Models screen only.
result: pass

### 8. Settings — Licences section
expected: Settings shows a "LICENCES" section between APPARENCE and A PROPOS. Two entries: "WhisperKit — MIT, Argmax Inc." and "Dictus — MIT, PIVI Solutions 2026". Tapping each opens the GitHub repo in browser.
result: issue
reported: "Label 'Dictus — MIT, PIVI Solutions 2026' trop long, prefere juste 'Dictus — MIT'. Le lien GitHub donne un 404 (repo n'existe pas encore)."
severity: cosmetic
fix_applied: "Label changed to 'Dictus — MIT', URL changed to pivisolutions.dev"

### 9. Model delete after download fix
expected: With 2+ models downloaded, swipe left on model card > red delete button (rounded corners) > tap > confirmation bottom sheet > confirm > model moves back to "Disponibles". Cannot delete last remaining model.
result: pass

## Summary

total: 9
passed: 7
issues: 2
pending: 0
skipped: 0

## Gaps

[none yet]
