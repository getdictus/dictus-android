# Dictation inside the app

Dictus's Home tab starts a full-screen recording without any keyboard; the result can be copied, and the last transcription stays on Home with a copy button.

- **ID:** `dictation.in-app`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.home`, `app.recording` — see [screens.md](screens.md)

## Sub-features

- `dictation.start-in-app`: « Nouvelle dictée » on Accueil opens the recording screen **and starts recording at once**; « Toucher pour arrêter » stops and transcribes. A dictation that produces no text (silence, too short, blank transcription, or only non-speech tags and punctuation such as « [Musique]. » or « ... », #161) shows « Aucun mot détecté » in place of « Nouvelle dictée », clears « Dernière transcription » and saves nothing, like iOS (#153). — présent
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
- **Open the recorder (this records).** Tap `Nouvelle dictée`. → `Retour`, a running timer (`0:01`), `Arrêter`, `Toucher pour arrêter`; log `ACTION_START` (`screen.sh app.recording`).
- **Stop and transcribe (records the room).** Tap `Arrêter`. → `Transcription en cours…` then `Résultat` with `Copier`; log `Recording stopped: samples=N`; « Dernière transcription » appears on Accueil.
- **Empty dictation (no words).** On the emulator, play `/home/pierre/Dev/dictus-proofs/153/silence-5s.wav` (5 s of digital silence, `ffmpeg -f lavfi -i anullsrc=r=16000:cl=mono -t 5`) right after « Nouvelle dictée », then `Arrêter`. → log `Transcription processed: rawLength=0` then `No words detected notice shown: surface=app`; the screen reads « Aucun mot détecté » above the mic, no `Résultat` card; Accueil has no « Dernière transcription ». Whisper often writes a tag on silence instead (« [Musique]. » with Tiny, « ... » with Small Q5): since #161 that is the same path, with `rawLength` above 0 and `processedLength=0` (seen 2026-10-21 with Tiny and `/home/pierre/Dev/dictus-proofs/161/silence-4s.wav`: `rawLength=10, processedLength=0`). A result made only of `[...]` / `(...)` / `*...*` spans and punctuation is no words, whatever the span says (`*...*`: no line break inside, no space right inside the asterisks, so « 2 * 3 * 4 » stays text; Whisper Small writes « *musique* », « *Bruit de chute* »). In text with words, only the closed list of iOS is dropped (musique, rires, applaudissements, silence, bruit, inaudible, music, laughter, applause, noise, BLANK_AUDIO, musik, lachen, applaus, stille, geräusch, música, risas, aplausos, silencio, ruido), case-insensitive, accent required, with whitespace and punctuation allowed inside the brackets (« [ Rires... ] ») (« Bonjour [Musique] ça va » → « Bonjour ça va. »); any other bracket or parenthesis and an unclosed « [Musique » stay, like iOS (dictus-ios #768).
- **Leave without a transcript.** `Retour` does **not** stop the recording (see Gotchas). Stop it from Quick Settings: swipe down twice, tap `1 appli est active`, Dictus `Arrêter`; the green mic chip disappears.
- **Proof.** Screens before/after and, for a real dictation, the `Résultat` text in the tree plus the log line; the « Dernière transcription » card on Accueil shows the same text afterwards.

## Gotchas

- **Bug seen 2026-10-20:** leaving the recording screen with `Retour` (or system back) keeps recording: `DictationService` stays foreground (`dumpsys activity services` → `isForeground=true`), the mic chip stays green, no `Recording cancelled` line. Only `Arrêter`, the (invisible here) notification's Stop, or « Applis actives → Arrêter » ends it. Evidence: `dictus-proofs/verify-dictus-android/20261020-214307-0f15824/90-bug-recording-continues-after-retour.png`.
- Pierre's Accueil shows no « Dernière transcription » card at the time of writing; it appears after the first in-app or keyboard dictation.

- Copying writes to Pierre's clipboard; avoid it unless the task needs it.
- « Dernière transcription » is Pierre's last real dictation: it is private, do not paste it into evidence or a PR.
- History (`history.*`) is compiled but hidden (`PremiumFlags.HISTORY_VISIBLE = false`): no swipe-up, no « Historique » on Accueil.
