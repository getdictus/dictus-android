# Dictation inside the app

Dictus's Home tab starts a full-screen recording without any keyboard; the result can be copied, and the last transcription stays on Home with a copy button.

- **ID:** `dictation.in-app`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.home`, `app.recording` — see [screens.md](screens.md)

## Sub-features

- `dictation.start-in-app`: « Nouvelle dictée » on Accueil opens the recording screen **and starts recording at once**, with the same 30-bar live waveform as the keyboard (120 dp, half opacity, #184); « Toucher pour arrêter » stops and transcribes. A dictation that produces no text (silence, too short, blank transcription, or only non-speech tags and punctuation such as « [Musique]. » or « ... », #161) shows « Aucun mot détecté » in place of « Nouvelle dictée », clears « Dernière transcription » and saves nothing, like iOS (#153). — présent
- Keyboard dictation running (#237, #243, iOS `MainTabView` / `RecordingView`): while a dictation records, Dictus always shows the recording screen when it comes to the front, and « Nouvelle dictée » leads there too; nothing starts a second recording. The screen has `Arrêter` and a single ✕ `Fermer` top left, no `Retour`. `Arrêter` transcribes and shows `Résultat` + `Copier` in the app (nothing kept for a keyboard dictation: no history, no « Dernière transcription »). ✕ and system back cancel the dictation, whoever started it, and return to Accueil; after a result they only return. A refused start logs `Recording start refused` or `App start skipped`. — présent
- App dictation in the background (#248, iOS `DictationCoordinator.stopDictation`): Home, an app switch or a screen lock keep an app dictation recording; coming back shows the recording screen, `Arrêter` transcribes as usual (log `ON_STOP ignored, dictation continues`). Only ✕ and system back discard. A stop without `Arrêter` (« Arrêter » in the notification, Dictus swiped from Recents, the service torn down) transcribes the take and keeps it in « Dernière transcription » (log `Stop without confirm: reason=notification_stop|task_removed|service_destroyed`, then `Stop without confirm done: … saved=true`). A process kill loses the take. A start still pending when the app leaves the front is cancelled (`ON_STOP: pending app start cancelled`). — présent
- `dictation.copy-last`: The « Dernière transcription » card on Accueil copies its text (`Copier`). — présent
- `dictation.download-prompt`: — partiel: Accueil never asks to download a model (it always shows « Modèle actif »); only the mic answers « Aucun modèle vocal » once tapped.
- `dictation.from-widget`: — absent: no home-screen widget.

## How to get to it (user POV)

1. Open Dictus, tab Accueil.
2. Tap « Nouvelle dictée », speak (recording has started), tap the stop button (« Toucher pour arrêter »).
3. Copy the result (`Copier`) on the recording screen, or later from the « Dernière transcription » card on Accueil.

## Driving it with Argent

Preconditions: Launch and Doctor OK, onboarding completed, a model downloaded.

- **Home.** Tap `Accueil`. → `Modèle actif`, `Nouvelle dictée` and, if any, `Dernière transcription` in the tree (`screen.sh app.home`).
- **Open the recorder (this records).** Tap `Nouvelle dictée`. → `Fermer` (✕ top left), a running timer (`0:01`), `Arrêter`, `Toucher pour arrêter`; log `ACTION_START` (`screen.sh app.recording`).
- **Stop and transcribe (records the room).** Tap `Arrêter`. → `Transcription en cours…` then `Résultat` with `Copier`; log `Recording stopped: samples=N`; « Dernière transcription » appears on Accueil.
- **Empty dictation (no words).** On the emulator, play `/home/pierre/Dev/dictus-proofs/153/silence-5s.wav` (5 s of digital silence, `ffmpeg -f lavfi -i anullsrc=r=16000:cl=mono -t 5`) right after « Nouvelle dictée », then `Arrêter`. → log `Transcription processed: rawLength=0` then `No words detected notice shown: surface=app`; the screen reads « Aucun mot détecté » above the mic, no `Résultat` card; Accueil has no « Dernière transcription ». Whisper often writes a tag on silence instead (« [Musique]. » with Tiny, « ... » with Small Q5): since #161 that is the same path, with `rawLength` above 0 and `processedLength=0` (seen 2026-10-21 with Tiny and `/home/pierre/Dev/dictus-proofs/161/silence-4s.wav`: `rawLength=10, processedLength=0`). A result made only of `[...]` / `(...)` / `*...*` spans and punctuation is no words, whatever the span says (`*...*`: no line break inside, no space right inside the asterisks, so « 2 * 3 * 4 » stays text; Whisper Small writes « *musique* », « *Bruit de chute* »). In text with words, only the closed list of iOS is dropped (musique, rires, applaudissements, silence, bruit, inaudible, music, laughter, applause, noise, BLANK_AUDIO, musik, lachen, applaus, stille, geräusch, música, risas, aplausos, silencio, ruido), case-insensitive, accent required, with whitespace and punctuation allowed inside the brackets (« [ Rires... ] ») (« Bonjour [Musique] ça va » → « Bonjour ça va. »); any other bracket or parenthesis and an unclosed « [Musique » stay, like iOS (dictus-ios #768).
- **Keyboard dictation seen from the app (#237, #243).** Start a dictation from the Dictus keyboard in any field, open Dictus. → the recording screen with `Arrêter` and `Fermer` (✕ top left), no `Retour` / `Enregistrer` / `Nouvelle dictée`; no second `Recording requested` line. Leave Dictus (Home key) and open it again → the same screen. `Fermer` → log `Recording cancelled, samples discarded`, back on Accueil, the keyboard is idle. `Arrêter` → `Résultat` (or « Aucun mot détecté » on emulator silence).
- **Leave without a transcript.** `Fermer` (✕) or system back cancels the recording and returns to Accueil (#243); nothing is transcribed.
- **Proof.** Screens before/after and, for a real dictation, the `Résultat` text in the tree plus the log line; the « Dernière transcription » card on Accueil shows the same text afterwards.

## Gotchas

- The live waveform logs `waveformHeartbeat surface=app` every 2 s (`avgLevel`, `maxGapMs`) and `waveformStall` above 500 ms; the onboarding test logs `surface=onboarding`.
- **Fixed bug (seen 2026-10-20, #142, then #243):** leaving the recording screen used to keep recording. Since #243 the screen has no `Retour`; ✕ and system back cancel. Evidence of the old bug: `dictus-proofs/verify-dictus-android/20261020-214307-0f15824/90-bug-recording-continues-after-retour.png`.
- Pierre's Accueil shows no « Dernière transcription » card at the time of writing; it appears after the first in-app or keyboard dictation.

- Copying writes to Pierre's clipboard; avoid it unless the task needs it.
- « Dernière transcription » is Pierre's last real dictation: it is private, do not paste it into evidence or a PR.
- History (`history.*`) is compiled but hidden (`PremiumFlags.HISTORY_VISIBLE = false`): no swipe-up, no « Historique » on Accueil.
