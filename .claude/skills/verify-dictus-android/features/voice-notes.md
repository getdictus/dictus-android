# Voice notes transcription

A voice message or an audio file shared to Dictus from another app is transcribed on the phone; « Messages vocaux » shows each note's transcript with « Copier ». Pro only, as on iOS.

- **ID:** `voice-notes`
- **Tier:** Pro (`ProStatus.isPro`; nothing is sold on Android yet, so in practice the Debug build's « Forcer Dictus Pro »)
- **Android status:** partial — no Smart Mode after the transcription, no voice-note language setting (the dictation language is used), no keyboard reader, no progress outside the app
- **Screens:** `share.voice-note`, `app.pro.voice-notes`, `app.voice-note.result` — see [screens.md](screens.md)

## Sub-features

- `voice-notes.share-in`: Partager → « Transcrire avec Dictus » from any app sharing `audio/*` or `application/ogg` (WhatsApp, Telegram, Signal, Fichiers), one file or up to ten. Dictus is in the share sheet only while the user has Pro (iOS lists it always and refuses inside). — present
- `voice-notes.transcribe`: the file is decoded to 16 kHz mono (Ogg / Opus, WebM, MPEG-4 / AAC, MP3, WAV, FLAC, AMR), cut into 45 s pieces, transcribed with the active model, oldest note first; refused over 10 minutes or as a video. — present
- `voice-notes.after-mode`: — absent (no Smart Mode on Android)
- `voice-notes.language`: — partial: the dictation language of Réglages → Langue is used (iOS has its own setting, automatic by default)
- `voice-notes.reader`: — absent (no keyboard reader)
- `voice-notes.insert`: — absent; « Copier » on the card instead
- `voice-notes.delete`: the bin on a card in « Messages vocaux ». — present (in the app, not the keyboard)
- `voice-notes.toggle`: « Messages vocaux » switch at the top of the screen. — present
- `voice-notes.progress-activity`: iOS-only (N/A)

## How to get to it (user POV)

1. With Pro, in WhatsApp (or Files), share a voice message → « Transcrire avec Dictus ».
2. Dictus opens on « Messages vocaux »: the note waits, transcribes with its progress, then shows its text and « Copier ».
3. Later: Réglages → « Dictus Pro » → « Messages vocaux », as on iOS. Réglages has no Messages vocaux row of its own.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a Debug build, a model on disk. Note whether « Forcer Dictus Pro » is on: it must be the same at the end.

- **Pro.** Réglages → « Développeur » → `Forcer Dictus Pro` on. → Réglages → `Dictus Pro` lists the `Messages vocaux` card (`pro_hub_feature_voice_notes`).
- **Share.** Push a clip and share it with an explicit intent (no messaging app needed):
  `adb push fr-beau-paris.ogg /sdcard/Download/` then `adb shell am start -a android.intent.action.SEND -t audio/ogg --eu android.intent.extra.STREAM file:///sdcard/Download/fr-beau-paris.ogg --grant-read-uri-permission` and pick « Transcrire avec Dictus » in the chooser. A `file://` URI needs the storage permission Dictus does not have: share from Files (`content://`) when that fails.
- **Result.** « Messages vocaux » opens; wait for the card's text. The log lines `Voice note started: … chunks=N` and `Voice note finished: … chars=N` (counts only) prove the run.
- **Clean up.** Delete the card with its bin, put « Forcer Dictus Pro » back.
- **Proof.** The card's text in the tree, the two log lines.

## Gotchas

- Without Pro, Dictus is absent from the share sheet: an empty chooser is the expected result, not a failure.
- The transcription runs in the app's process: leaving Dictus keeps it going, killing the app stops it, and the note starts again the next time « Messages vocaux » opens.
- A keyboard dictation started during a note waits for the current 45 s piece to finish.
