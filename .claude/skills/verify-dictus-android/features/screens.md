# Dictus Android screen map

One stable ID per screen, `<surface>.<area>[.<sub>]`, **the same IDs as the iOS screen map** when the screen exists on both: `app` (the Dictus app), `kbd` (the Dictus keyboard, drawn over any app), `sys` (Android system screens Dictus depends on), `share` and `widget` (iOS only today). Android-only screens get new IDs in the same format and are listed last.

Captures are in [`../screens/`](../screens/), one `<screen-id>.png` each: Pixel 4, LineageOS, French, with Pierre's own theme and settings, taken with `helpers/screen.sh` (adb screencap, resized to 1140 px high, quantized). Re-take one with `helpers/screen.sh <screen-id>` (see `../SKILL.md`, Evidence). Recipes use the labels of `../SKILL.md` → Drive.

| Screen ID | Screen | How to reach it | Features | States | Capture |
| --- | --- | --- | --- | --- | --- |
| `app.onboarding.welcome` | Onboarding 1/7: « Dictus — Dictation vocale, 100 % offline », « Commencer »; no progress bar | fresh install only | onboarding | — | no — onboarding completed on the Pixel; seeing it again requires wiping the data (forbidden) |
| `app.onboarding.language` | — | — | onboarding | absent on Android | — |
| `app.onboarding.keyboard-setup` | — | — | onboarding | iOS-only; see app.onboarding.ime-setup | — |
| `app.onboarding.apple-intelligence` | — | — | onboarding | iOS-only | — |
| `app.onboarding.smart-modes-scene` | — | — | onboarding, smart-modes | absent | — |
| `app.onboarding.smart-mode-pick` | — | — | onboarding, smart-modes | absent | — |
| `app.onboarding.voice-notes-scene` | — | — | onboarding, voice-notes | absent | — |
| `app.onboarding.vocabulary-scene` | — | — | onboarding, vocabulary | absent | — |
| `app.onboarding.history-scene` | — | — | onboarding, history | absent | — |
| `app.onboarding.model-preparation` | Onboarding 5/7: bar segment 4/5, full-screen « On prépare votre modèle » (#181), starts the install on entry | fresh install, after step 4 | onboarding, models.transcription | « Téléchargement N % » + « X Mo sur Y Mo » + « Le téléchargement continue si vous quittez l'app. » / « Préparation pour ce téléphone » / « Chargement du modèle » / « Votre modèle est prêt » (then step 6 by itself) / « Le modèle vocal n'a pas pu être préparé » + « Réessayer » | no — onboarding completed; besides, the step downloads a model |
| `app.onboarding.microphone` | Onboarding 2/7: back chevron, bar segment 1/5, « Microphone », « Autoriser le micro » | fresh install, after step 1 | onboarding | not granted / granted (« Continuer ») | no — onboarding completed on the Pixel |
| `app.onboarding.first-dictation` | Onboarding 6/7: bar segment 5/5, « Testez la dictée », mic, « Résultat », « Passer » | fresh install, after step 5 | onboarding, dictation.in-app | idle / recording « Toucher pour arrêter » / « Transcription en cours… » / result | no — onboarding completed on the Pixel |
| `app.onboarding.stop-standby` | — | — | onboarding | iOS-only | — |
| `app.onboarding.completion` | Onboarding 7/7: « C'est prêt ! », « Commencer »; no progress bar | fresh install, after step 6 | onboarding | — | no — onboarding completed on the Pixel |
| `app.whats-new` | — | — | onboarding | absent | — |
| `app.home` | Home tab: logo, « Modèle actif », « Dernière transcription » + « Copier », « Nouvelle dictée » | tap `Accueil` | dictation.in-app | without a last transcription (captured) / with one | [yes](../screens/app.home.png) |
| `app.recording` | Full-screen dictation: timer, « Arrêter », « Toucher pour arrêter » | Accueil → `Nouvelle dictée` (starts recording at once; `Retour` does not stop it) | dictation.in-app | idle / recording / transcription / « Résultat » + « Copier » / « Aucun mot détecté » (empty dictation) / loading overlay or « Aucun modèle vocal » | [yes](../screens/app.recording.png) |
| `app.history` | — | route not registered (`PremiumFlags.HISTORY_VISIBLE = false`) | history | absent (code present) | — |
| `app.history.detail` | — | same | history | absent (code present) | — |
| `app.models` | Models tab: « Téléchargés », « Disponibles », storage | tap `Modèles` | models.transcription | active card « Actif » / download and switch: see app.models.loading / failure « Réessayer » (after « Fermer ») / greyed out « Nécessite N Go de RAM ou plus » / dialogs « Supprimer … ? », « Modèle anglais uniquement », « RAM insuffisante » | [yes](../screens/app.models.png) |
| `app.models.languages` | — | — | models.transcription | absent | — |
| `app.models.loading` | Full-screen model preparation (#181): « On prépare votre modèle », download then « Préparation pour ce téléphone » / « Chargement du modèle » | Modèles → « Télécharger » on a card, or tap a downloaded model | models.transcription | download « N % » / preparing / loading / « Votre modèle est prêt » / failure « Réessayer » + « Fermer » | no — downloading or switching the active model would change Pierre's setting; emulator captures in the #181 PR |
| `app.settings` | Settings tab: TRANSCRIPTION, CLAVIER, APPARENCE, À PROPOS | tap `Réglages`; also long-press the keyboard's language code (no bottom tabs) | keyboard.languages, typing.*, feedback.sounds-haptics, appearance, diagnostics.logs | top / bottom of the page (scroll) / opened from the keyboard (no tabs) | [yes](../screens/app.settings.png) |
| `app.settings.sounds` | Settings → Sounds | Réglages → `Sons` | feedback.sounds-haptics | sound on / off | [yes](../screens/app.settings.sounds.png) |
| `app.settings.diagnostic` | — | — | diagnostics.logs | iOS-only | — |
| `app.settings.debug-logs` | Settings → Debug Logs, « Effacer » / « Copier » menu | Réglages, scroll → `Debug Logs` | diagnostics.logs | logs / « Aucun log disponible » / menu open | [yes](../screens/app.settings.debug-logs.png) |
| `app.settings.licences` | Settings → Licences | Réglages, scroll → `Licences` | diagnostics.logs | — | [yes](../screens/app.settings.licences.png) |
| `app.settings.polish-debug` | — | — | polish | iOS-only | — |
| `app.pro.hub` | — | — | pro.subscription | absent | — |
| `app.pro.smart-modes` | — | — | smart-modes | absent | — |
| `app.pro.history` | — | — | history | absent | — |
| `app.pro.vocabulary` | — | — | vocabulary | absent | — |
| `app.pro.vocabulary.term-editor` | — | — | vocabulary | absent | — |
| `app.pro.voice-notes` | — | — | voice-notes | absent | — |
| `app.paywall` | — | — | pro.subscription | absent | — |
| `app.pro.trial-announcement` | — | — | pro.subscription | absent | — |
| `app.voice-note.result` | — | — | voice-notes | absent | — |
| `kbd.keys` | Dictus keyboard: suggestion bar, language code + mic row, AZERTY keys | focus a text field in another app (scratch note) | dictation.keyboard, typing.*, keyboard.languages | letters / 123 / #+= / shift (manual or automatic at a sentence start) / accent strip / trackpad (key labels hidden) / FR EN ES DE | [yes](../screens/kbd.keys.png) |
| `kbd.no-full-access` | — | — | dictation.keyboard | iOS-only | — |
| `kbd.dictation-error` | Keyboard overlay: « Aucun modèle vocal » or « Impossible de démarrer la reconnaissance vocale » + « Réessayer » / « Annuler »; after a failed transcription, the suggestion bar reads « La dictée a échoué. Touchez le micro pour réessayer. » in grey for 3 s (#182) | mic tap with no model / engine failure / a dictation ending in `Failed` (model file removed mid-recording, timeout) | dictation.keyboard | no model / loading failure / failed transcription | no — Pierre has a model: triggering the error would require deleting his model |
| `kbd.recording` | Keyboard while dictating: ✕ / ✓, waveform, timer, « En écoute… », gear | mic tap | dictation.keyboard | recording / « Transcription… » | [yes](../screens/kbd.recording.png) |
| `kbd.panel` | — | — | keyboard.languages | absent (tap on the language code instead) | — |
| `kbd.emoji` | Emoji picker, ABC and ⌫ at the bottom | tap 😊 | keyboard.emoji | categories / recents | [yes](../screens/kbd.emoji.png) |
| `kbd.voice-note-reader` | — | — | voice-notes | absent | — |
| `kbd.smart-mode-fan` | — | — | smart-modes | absent | — |
| `kbd.suggestions` | Suggestion bar (3 slots); « Annuler » chip after a dictation | type in a scratch note | typing.autocorrect, dictation.keyboard | completions / predictions / dictation undo chip / « Aucun mot détecté » or « La dictée a échoué… » notice (3 s) | [yes](../screens/kbd.suggestions.png) |
| `sys.settings.full-access` | — | — | dictation.keyboard | iOS-only | — |
| `share.voice-note` | — | — | voice-notes | absent | — |
| `widget.live-activity` | — | — | live-activity | iOS-only | — |
| **Android-only** | | | | | |
| `app.onboarding.ime-setup` | Onboarding 3/7: « Ajouter le clavier », « Ouvrir les paramètres » / « Sélectionner Dictus » / « Continuer » | fresh install, after step 2 | onboarding | not enabled / enabled but not selected / ready | no — onboarding completed on the Pixel |
| `app.onboarding.default-page` | Onboarding 4/7: « Choisissez votre clavier », ABC / 123 | fresh install, after step 3 | onboarding | ABC / 123 | no — onboarding completed on the Pixel |
| `app.settings.sound-picker` | Sound list (« Début » / « Fin » / « Annuler ») | Réglages → Sons → `Début` | feedback.sounds-haptics | selected sound checked | [yes](../screens/app.settings.sound-picker.png) |
| `app.settings.picker` | Choice sheets: « Langue », « Langue du clavier », « Disposition du clavier », « Mode par défaut », « Thème », « Langue de l'interface » | Réglages → tap a row | keyboard.languages, appearance | one sheet per setting | [yes](../screens/app.settings.picker.png) |
| `kbd.model-loading` | Keyboard overlay: « Préparation de la reconnaissance vocale », mic greyed out | mic tap while the model loads (first use after the process starts) | dictation.keyboard, models.transcription | loading / failure | no — the loading window is too brief to capture reliably without killing the process |
| `sys.ime-picker` | Android keyboard picker: « Clavier Android (AOSP) », « Clavier Dictus » with « AZERTY (Français) » / « QWERTY (Anglais) » | long-press the nav bar's keyboard button (never a tap), onboarding « Sélectionner Dictus », or the gear during a keyboard recording | dictation.keyboard, onboarding | — | [yes](../screens/sys.ime-picker.png) |
| `sys.settings.ime` | Android settings → « Clavier à l'écran » | onboarding « Ouvrir les paramètres » | onboarding | — | no — outside the app; a system screen, not needed for the map |
| `sys.notification.recording` | « Dictus – Enregistrement » notification + « Arrêter » | pull down the shade during a recording | dictation.keyboard | — | no — notifications denied for Dictus on the Pixel (Pierre's setting): the notification does not appear; only « 1 appli est active » in quick settings |

14 of the 26 screens that exist on Android are captured (the 31 others are absent on Android or iOS-only, marked "—"). Each "no" gives its reason: a step that needs a fresh install or a data wipe, a model download, deletion or switch, a permission Pierre refused, or a state too brief to capture reliably.
