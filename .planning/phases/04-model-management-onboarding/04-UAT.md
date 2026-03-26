---
status: diagnosed
phase: 04-model-management-onboarding
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md, 04-05-SUMMARY.md]
started: 2026-03-26T22:00:00Z
updated: 2026-03-26T22:15:00Z
---

## Current Test

[testing complete — stopped early due to blocker on test 2]

## Tests

### 1. Onboarding Welcome Screen
expected: On first launch, the app shows the onboarding welcome screen with a static waveform and the "Dictus" wordmark in DM Sans font. CTA button and 6 progress dots visible at the bottom.
result: issue
reported: "waveform statique sur Android, sur iOS c'est une sinusoide en mouvement. Le concept waveform est correct mais il manque l'animation."
severity: minor

### 2. Onboarding Mic Permission
expected: Tapping the CTA on step 1 advances to step 2 — microphone permission screen. The system RECORD_AUDIO permission dialog appears when you tap the CTA. Granting permission advances to next step.
result: issue
reported: "quand je clique sur commencer ca me fait bien passer à la step 2 par contre ensuite quand je clique sur continuer pour donner acces au micro ca me sort de l'application dictus. Ensuite, si je retourne sur l'application, je retourne sur la step 1 et ensuite je peux recommencer la même chose."
severity: blocker

### 3. Onboarding Keyboard Setup
expected: Step 3 shows keyboard setup instructions with a decorative iOS-style settings card. Tapping the CTA opens Android system Input Method Settings (ACTION_INPUT_METHOD_SETTINGS). Returning to the app continues the flow.
result: skipped
reason: blocked by test 2 bug (app exits on mic permission)

### 4. Onboarding Mode Selection
expected: Step 4 shows two layout option cards — ABC (AZERTY) and 123 (QWERTY). Selecting one highlights it with a border. Press animation visible on tap. CTA advances to next step.
result: skipped
reason: blocked by test 2 blocker (app exits on mic permission)

### 5. Onboarding Model Download
expected: Step 5 shows a model download screen. Download starts with a visible progress bar inside a card. CTA is disabled/blocked until download completes. If download fails, an error state with retry option appears.
result: skipped
reason: blocked by test 2 blocker

### 6. Onboarding Completion
expected: Step 6 shows a green check circle and a success-styled CTA button (green gradient). Tapping the CTA writes the onboarding flag and transitions to the main app with the bottom navigation bar. Progress dots show green on this step.
result: skipped
reason: blocked by test 2 blocker

### 7. Bottom Navigation Bar
expected: After onboarding, the main app shows a pill-style bottom navigation bar with 3 tabs (Home, Models, Settings). Active tab has an accent dot indicator. Tapping each tab switches content.
result: skipped
reason: blocked by test 2 blocker (can't complete onboarding to reach main tabs)

### 8. Home Screen
expected: Home tab shows a GlassCard with the active model name. An empty transcription state is visible with a "Nouvelle dictee" CTA button.
result: skipped
reason: blocked by test 2 blocker

### 9. Models Screen - Downloaded Section
expected: Models tab shows a "Telecharges" section listing downloaded models. Each model card shows the model name, quality label, and a downloaded state indicator.
result: skipped
reason: blocked by test 2 blocker

### 10. Models Screen - Available Section
expected: Below the downloaded section, a "Disponibles" section shows models not yet downloaded. Each card has a download button. A storage usage footer is visible at the bottom.
result: skipped
reason: blocked by test 2 blocker

### 11. Model Download with Progress
expected: Tapping download on an available model shows a progress bar animating on the model card. The card shows the downloading state. On completion, the model moves to the "Telecharges" section.
result: skipped
reason: blocked by test 2 blocker

### 12. Model Delete with Confirmation
expected: On a downloaded model (when more than one is downloaded), a delete action is available. Tapping it shows a ModalBottomSheet confirmation dialog. Confirming deletes the model and moves it back to "Disponibles". Cannot delete the last remaining model.
result: skipped
reason: blocked by test 2 blocker

### 13. Settings - Transcription Section
expected: Settings tab shows a TRANSCRIPTION section with Language picker (Automatique/fr/en via bottom sheet) and Active Model picker (shows downloaded models via bottom sheet).
result: skipped
reason: blocked by test 2 blocker

### 14. Settings - Keyboard Section
expected: Settings shows a CLAVIER section with keyboard layout picker (AZERTY/QWERTY), haptics toggle (custom green toggle), and sound toggle. Toggles animate between on/off states.
result: skipped
reason: blocked by test 2 blocker

### 15. Settings - Appearance Section
expected: Settings shows an APPARENCE section with a "Theme" picker. Opening it shows a bottom sheet with "Sombre" (dark) option selected.
result: skipped
reason: blocked by test 2 blocker

### 16. Settings - About Section
expected: Settings shows an A PROPOS section with version info (version name + code), Export Logs action, GitHub link, and Legal mentions link. Export Logs creates a ZIP and opens the share sheet. Links open in browser.
result: skipped
reason: blocked by test 2 blocker

## Summary

total: 16
passed: 0
issues: 2
pending: 0
skipped: 14

## Gaps

- truth: "Welcome screen waveform should be animated (moving), using the transcription waveform style (not recording waveform)"
  status: failed
  reason: "User reported: waveform is static on Android. iOS reference shows an animated transcription waveform in movement on the welcome screen. Important: this is the transcription wave, not the recording wave."
  severity: minor
  test: 1
  root_cause: "OnboardingWelcomeScreen uses a private static WaveformDecoration with Random(42) fixed bars. The project already has the exact animated transcription waveform: WaveformBars in core/ui + sine-wave driver pattern in TranscribingScreen.kt"
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingWelcomeScreen.kt"
      issue: "Private WaveformDecoration uses 15 static bars from Random(42), no animation"
  missing:
    - "Replace static WaveformDecoration with WaveformBars (core/ui) + sine-wave infiniteTransition driver from TranscribingScreen pattern"
  debug_session: ".planning/debug/onboarding-static-waveform.md"

- truth: "Mic permission request stays in-app, granting advances to step 3"
  status: failed
  reason: "User reported: clicking Continuer on step 2 exits the app entirely. Returning to app resets to step 1 instead of advancing. Permission dialog causes app exit and onboarding state is lost."
  severity: blocker
  test: 2
  root_cause: "Two issues: (1) OnboardingViewModel._currentStep is volatile MutableStateFlow with no SavedStateHandle persistence — process death resets to step 1. (2) MainActivity.onCreate() eagerly binds DictationService (BIND_AUTO_CREATE) even during onboarding, increasing memory pressure and process death likelihood when permission dialog Activity takes foreground."
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt"
      issue: "_currentStep, _micPermissionGranted, _imeActivated are all volatile MutableStateFlow with no SavedStateHandle"
    - path: "app/src/main/java/dev/pivisolutions/dictus/MainActivity.kt"
      issue: "bindDictationService() called eagerly in onCreate() before onboarding gate check"
  missing:
    - "Add SavedStateHandle to OnboardingViewModel constructor, persist currentStep/micPermissionGranted/imeActivated"
    - "Defer DictationService binding until after onboarding completes (hasCompletedOnboarding == true)"
  debug_session: ".planning/debug/onboarding-mic-permission-crash.md"
