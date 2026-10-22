# Onboarding

A first launch walks through seven steps with progress dots: welcome, microphone, adding and selecting the keyboard, default keyboard page, model download, a test dictation, and « C'est prêt ! ».

- **ID:** `onboarding`
- **Tier:** Gratuit
- **Statut Android:** partiel (no language step, no Pro scenes, no What's new)
- **Screens:** `app.onboarding.welcome`, `app.onboarding.microphone`, `app.onboarding.ime-setup`, `app.onboarding.default-page`, `app.onboarding.model-preparation`, `app.onboarding.first-dictation`, `app.onboarding.completion`, `sys.settings.ime`, `sys.ime-picker` — see [screens.md](screens.md)

## Sub-features

- `onboarding.welcome`: « Dictus — Dictation vocale, 100 % offline », « Commencer ». — présent
- `onboarding.language`: — absent: no language or layout step (layout defaults to AZERTY, changeable in Réglages).
- `onboarding.keyboard-setup`: — Spécifique iOS (Full Access); Android's own step is `onboarding.ime-setup`.
- `onboarding.apple-intelligence`: — Spécifique iOS.
- `onboarding.pro-scenes`: — absent.
- `onboarding.smart-mode-pick`: — absent.
- `onboarding.model-download`: « Modèle vocal »: the model recommended for the phone's RAM (« Recommandé pour votre appareil »), « Télécharger », progress, « Réessayer ». — présent
- `onboarding.microphone`: « Microphone », « Autoriser le micro » then the system prompt. — présent
- `onboarding.first-dictation`: « Testez la dictée »: tap the mic, speak, see « Résultat ». — présent
- `onboarding.stop-standby`: — Spécifique iOS.
- `onboarding.completion`: « C'est prêt ! », « Commencer » opens Accueil. — présent
- `onboarding.resume`: — partiel: the step survives the system killing the process (`SavedStateHandle`), not a force-stop or a reboot (back to step 1).
- `onboarding.whats-new`: — absent.
- `onboarding.ime-setup` *(Spécifique Android)*: « Ajouter le clavier »: « Ouvrir les paramètres » (system on-screen keyboards) then « Sélectionner Dictus » (system picker); « Continuer » only once Dictus is enabled and selected.
- `onboarding.default-page` *(Spécifique Android)*: « Choisissez votre clavier »: ABC (Lettres) or 123 (Chiffres).

## How to get to it (user POV)

1. Install Dictus and open it.
2. « Commencer » → micro → clavier → ABC/123 → modèle → test → « C'est prêt ! ».

## Driving it with Argent

Preconditions: a phone where Dictus has **never completed onboarding**. Pierre's Pixel has completed it, and the only way back is clearing the app's data (models, settings, notes' state): **not allowed** on this phone.

- **Reach step 1.** Not reachable on Pierre's Pixel without `pm clear`: report `onboarding` as not driven, with this precondition, unless a disposable device or AVD (`dictus_api35`, headless) is provided.
- **On a fresh install.** `launch.sh` → `READY`; `mcp__argent__await-ui-element {selector:{text:"Commencer"}}` → tap → `Microphone` → `Autoriser le micro` → system dialog → `Continuer` → `Ajouter le clavier` …
- **Proof.** One capture per step and the final `Accueil` tab in the tree; `has_completed_onboarding` true in `adb shell run-as dev.pivisolutions.dictus cat files/datastore/*.preferences_pb | strings`.

## Gotchas

- Clearing data to see onboarding again would delete Pierre's models and settings: never on the Pixel.
- The model step downloads for real (Parakeet 0.6B ~620 MB from 6 GB of RAM, so on the Pixel 4, or Small Q5 ~190 MB below; Parakeet's archive then takes ~3.5 min to extract on the Pixel 4).
- The keyboard step leaves Dictus for Android's settings and its picker; `MainActivity` re-reads the IME state on return.
- The test dictation records the room.
