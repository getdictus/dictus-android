---
status: diagnosed
phase: 04-model-management-onboarding
source: [04-01-SUMMARY.md, 04-02-SUMMARY.md, 04-03-SUMMARY.md, 04-04-SUMMARY.md, 04-05-SUMMARY.md]
started: 2026-03-26T22:00:00Z
updated: 2026-03-27T18:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Onboarding Welcome Screen
expected: On first launch, the app shows the onboarding welcome screen with an animated waveform (traveling sine-wave) and the "Dictus" wordmark in DM Sans font. CTA button and 6 progress dots visible at the bottom.
result: pass

### 2. Onboarding Mic Permission
expected: Tapping the CTA on step 1 advances to step 2 — microphone permission screen. Tapping "Continuer" shows the system RECORD_AUDIO permission dialog. The app stays in the foreground. Granting permission advances to next step. Process death preserves step via SavedStateHandle.
result: pass

### 3. Onboarding Keyboard Setup
expected: Step 3 shows keyboard setup instructions with a decorative settings card. Tapping the CTA opens Android system Input Method Settings. Returning to the app continues the flow.
result: issue
reported: "La FakeSettingsCard ressemble à iOS (avec 'Autoriser l'accès complet') et pas à Android natif. Sur Android on a une liste de claviers avec nom + toggle. Il faut se rapprocher du style Android natif (comme l'écran LineageOS/AOSP settings)."
severity: cosmetic

### 4. Onboarding Mode Selection
expected: Step 4 shows two layout option cards — ABC (AZERTY) and 123 (QWERTY). Selecting one highlights it with a border. Press animation visible on tap. CTA advances to next step.
result: pass

### 5. Onboarding Model Download
expected: Step 5 shows a model download screen. Download starts with a visible progress bar inside a card. CTA is disabled/blocked until download completes. If download fails, an error state with retry option appears.
result: pass

### 6. Onboarding Completion
expected: Step 6 shows a green check circle and a success-styled CTA button (green gradient). Tapping the CTA writes the onboarding flag and transitions to the main app with the bottom navigation bar. Progress dots show green on this step.
result: pass

### 7. Bottom Navigation Bar
expected: After onboarding, the main app shows a pill-style bottom navigation bar with 3 tabs (Home, Models, Settings). Active tab has an accent dot indicator. Tapping each tab switches content.
result: pass

### 8. Home Screen
expected: Home tab shows a GlassCard with the active model name. An empty transcription state is visible with a "Nouvelle dictee" CTA button that launches the recording flow.
result: issue
reported: "Le bouton 'Nouvelle dictée' ne lance pas d'enregistrement. Il devrait envoyer sur le flow d'enregistrement (auto-start recording → stop → résultat → micro bleu pour relancer), comme sur iOS."
severity: major

### 9. Models Screen - Downloaded Section
expected: Models tab shows a "Telecharges" section listing downloaded models. Each model card shows the model name, quality label, and a downloaded state indicator.
result: pass

### 10. Models Screen - Available Section
expected: Below the downloaded section, a "Disponibles" section shows models not yet downloaded. Each card has a download button. A storage usage footer is visible at the bottom.
result: issue
reported: "Les cartes modèles ne correspondent pas au design iOS: il manque le texte descriptif par modèle (ex: 'Précis et équilibré' pour Small), il manque les barres visuelles Précision/Vitesse en bleus Dictus avec remplissage variable selon le modèle, et il faut retirer le petit logo micro en haut à gauche de la carte."
severity: cosmetic

### 11. Model Download with Progress
expected: Tapping download on an available model shows a progress bar animating on the model card. The card shows the downloading state. On completion, the model moves to the "Telecharges" section.
result: issue
reported: "La barre de téléchargement va jusqu'à la fin mais le modèle ne passe pas dans 'Téléchargés'. Le bouton télécharger réapparaît comme si rien ne s'était passé. On ne sait pas si le modèle a été téléchargé ou non."
severity: major

### 12. Model Delete with Confirmation
expected: On a downloaded model (when more than one is downloaded), a delete action is available. Tapping it shows a ModalBottomSheet confirmation dialog. Confirming deletes the model and moves it back to "Disponibles". Cannot delete the last remaining model.
result: skipped
reason: blocked by test 11 (can't download additional models)

### 13. Settings - Transcription Section
expected: Settings tab shows a TRANSCRIPTION section with Language picker (Automatique/fr/en via bottom sheet) and Active Model picker (shows downloaded models via bottom sheet).
result: pass
note: UX change requested — remove Active Model picker from Settings. Model selection should happen on Models screen by tapping downloaded model card (blue border highlight), matching iOS behavior.

### 14. Settings - Keyboard Section
expected: Settings shows a CLAVIER section with keyboard layout picker (AZERTY/QWERTY), haptics toggle (custom green toggle), and sound toggle. Toggles animate between on/off states.
result: pass

### 15. Settings - Appearance Section
expected: Settings shows an APPARENCE section with a "Theme" picker. Opening it shows a bottom sheet with "Sombre" (dark) option selected.
result: pass

### 16. Settings - About Section
expected: Settings shows an A PROPOS section with version info (version name + code), Export Logs action, GitHub link, and Legal mentions link. Export Logs creates a ZIP and opens the share sheet. Links open in browser.
result: pass
note: Add a Licences section showing WhisperKit (MIT, Argmax Inc.) and Dictus (MIT, PIVI Solutions 2026) with GitHub links, matching iOS.

## Summary

total: 16
passed: 10
issues: 4
pending: 0
skipped: 1

## Gaps

- truth: "Onboarding keyboard setup card should look like Android native keyboard settings"
  status: failed
  reason: "User reported: FakeSettingsCard uses iOS style ('Autoriser l'accès complet' + toggles). Should show Android-native style: keyboard list with name, subtitle, and toggle — similar to AOSP/LineageOS input method settings."
  severity: cosmetic
  test: 3
  root_cause: "FakeSettingsCard copies iOS keyboard settings pattern ('Allow Full Access' toggle, iOS breadcrumb navigation) instead of Android input method settings (keyboard list with name, subtitle, toggle)."
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/ui/onboarding/FakeSettingsCard.kt"
      issue: "iOS-style breadcrumb header, 'Autoriser l'acces complet' toggle (iOS-only concept), missing keyboard list with subtitles"
  missing:
    - "Replace header from iOS breadcrumb to Android-style section header ('Clavier a l'ecran')"
    - "Replace toggle rows with keyboard list entries (system default + Dictus) with subtitles"
    - "Remove 'Autoriser l'acces complet' (no Android equivalent)"
  debug_session: ".planning/debug/ios-style-fake-settings-card.md"

- truth: "'Nouvelle dictée' button launches recording flow (auto-start → stop → result → re-record)"
  status: failed
  reason: "User reported: button does nothing. Should navigate to recording/transcription flow same as onboarding test recording."
  severity: major
  test: 8
  root_cause: "onNewDictation callback in AppNavHost.kt line 251 is an empty lambda placeholder. No Recording destination exists in the navigation graph."
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt"
      issue: "Line 251: onNewDictation = empty lambda placeholder"
    - path: "app/src/main/java/dev/pivisolutions/dictus/navigation/AppDestination.kt"
      issue: "Missing Recording destination"
  missing:
    - "Add Recording destination to AppDestination.kt"
    - "Create standalone RecordingScreen (extract from OnboardingTestRecordingScreen, remove onboarding UI)"
    - "Register recording route in MainTabsScreen NavHost"
    - "Wire onNewDictation to navigate to Recording route"
  debug_session: ".planning/debug/home-nouvelle-dictee-noop.md"

- truth: "Model cards show description text and Precision/Vitesse progress bars in Dictus blue"
  status: failed
  reason: "User reported: cards missing descriptive text per model, missing Precision/Vitesse bars with variable fill, mic icon should be removed from card top-left. Should match iOS design."
  severity: cosmetic
  test: 10
  root_cause: "ModelInfo data class lacks description, precision, speed fields. ModelCard composable has no description text or Precision/Vitesse progress bars. Mic icon is a placeholder."
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt"
      issue: "ModelInfo missing description, precision, speed fields"
    - path: "app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt"
      issue: "Mic icon placeholder, no description text, no Precision/Vitesse bars"
  missing:
    - "Add description, precision, speed to ModelInfo + ModelCatalog entries"
    - "Remove mic icon, add description text, add two labeled progress bars in Dictus blue"
  debug_session: ".planning/debug/model-cards-ui-gap.md"

- truth: "Model download completes and model moves to Telecharges section"
  status: failed
  reason: "User reported: progress bar fills to 100% but model stays in Disponibles with download button reappearing. Model doesn't move to Telecharges section."
  severity: major
  test: 11
  root_cause: "ModelCatalog.expectedSizeBytes for base/small/small-q5_1 are rounded approximations, not actual file sizes. isDownloaded() uses exact byte-length equality so downloaded files never pass the check."
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt"
      issue: "Wrong expectedSizeBytes for 3/4 models + fragile exact-size equality check"
  missing:
    - "Fix expectedSizeBytes: base=147951465, small=487601967, small-q5_1=190085487"
    - "Replace exact-size check with minimum-size threshold"
    - "Update unit tests with corrected sizes"
  debug_session: ".planning/debug/model-download-not-persisting.md"
