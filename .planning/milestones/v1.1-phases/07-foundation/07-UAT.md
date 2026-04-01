---
status: complete
phase: 07-foundation
source: [07-01-SUMMARY.md, 07-02-SUMMARY.md, 07-03-SUMMARY.md]
started: 2026-03-30T14:30:00Z
updated: 2026-03-30T14:30:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. AZERTY/QWERTY toggle takes effect immediately
expected: Switch the keyboard layout in Settings (AZERTY to QWERTY or vice versa). Open the Dictus IME keyboard — the layout should reflect the new setting immediately without restarting the app or IME service.
result: pass

### 2. Dictation still works after SttProvider refactoring
expected: Open a text field, activate the Dictus IME, tap the microphone button to start dictation. Speech-to-text transcription should work exactly as before — the SttProvider refactoring is transparent to the user.
result: pass

### 3. Notification permission request on Android 13+
expected: If on Android 13+ (API 33+), the app should request POST_NOTIFICATIONS permission at the appropriate time. If on an older Android version, skip this test. The permission request dialog should appear when the app needs to show notifications.
result: skipped
reason: Pas d'accès à Android 13+ (Pixel 4 sous Android 10/11)

## Summary

total: 3
passed: 2
issues: 0
pending: 0
skipped: 1

## Gaps

[none yet]
