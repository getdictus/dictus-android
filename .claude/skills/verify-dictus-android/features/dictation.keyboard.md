# Dictation from the keyboard

In any app, the Dictus keyboard's blue mic pill records the voice on the phone, transcribes it offline with the active model and inserts the text at the cursor. ✕ cancels without inserting anything; after an insertion, the « Annuler » chip removes what was just inserted.

- **ID:** `dictation.keyboard`
- **Tier:** Gratuit (nothing is gated on Android)
- **Statut Android:** présent
- **Screens:** `kbd.keys`, `kbd.recording`, `kbd.dictation-error`, `kbd.model-loading`, `sys.notification.recording`, `sys.ime-picker` — see [screens.md](screens.md)

## Sub-features

- `dictation.start`: Tap the mic pill (top right of the keyboard) to start recording. — présent
- `dictation.stop`: ✓ stops, transcribes (« Transcription… ») and inserts the text at the cursor. When the dictation produces no text (including only non-speech tags or punctuation, #161), nothing is inserted and the suggestion bar reads « Aucun mot détecté » in grey for 3 s, like the iOS toolbar (#153). When the transcription fails (`Failed`: model missing, engine failure, a timeout before any segment), nothing is inserted and the bar reads « La dictée a échoué. Touchez le micro pour réessayer. » in grey, on up to two lines, for 3 s, like the iOS toolbar (`kbd.dictation-error`, #182). A timeout after some segments is `Partial`: those words are inserted and no failure line shows. — présent
- `dictation.cancel`: ✕ stops and discards the audio; nothing is inserted. — présent
- `dictation.status`: Recording panel with a 30-bar waveform that follows the voice (iOS pipeline and smoothing, #184; flat thin bars in silence), MM:SS timer and « En écoute… », then « Transcription… » with a travelling sine; while the model loads the mic is dimmed and an overlay reads « Préparation de la reconnaissance vocale ». — présent
- `dictation.undo-insert`: After an insertion, the suggestion bar shows « Annuler » (`Annuler la dernière dictée`): it deletes the inserted text if it is still right before the cursor. — présent (#137)
- `dictation.handoff`: — Spécifique iOS (Android's IME binds to the app's `DictationService` directly; no app switch).
- `dictation.requires-full-access`: — Spécifique iOS (no Full Access on Android; only the `RECORD_AUDIO` permission).
- `dictation.device-gating`: — absent: no device is refused as a whole; only heavy models are refused per RAM (`models.device-gating`).
- `dictation.notification` *(Spécifique Android)*: while recording, a foreground-service notification « Dictus - Recording » with a « Stop » action (required by Android for microphone use in the background).
- `dictation.switch-keyboard` *(Spécifique Android)*: during a recording the gear (`Settings`) under the waveform opens the system keyboard picker.

## How to get to it (user POV)

1. Dictus → onboarding, or Android Paramètres → Système → Langues et saisie → Clavier à l'écran: enable « Clavier Dictus » and select it.
2. In any text field (Notes, Messages…), the Dictus keyboard opens with the mic pill at the top right.
3. Tap the mic, speak, tap ✓ to insert or ✕ to cancel. Tap « Annuler » in the bar right after to remove the insertion.

## Driving it with Argent

Proven on the Pixel on 2026-10-20 (runs `20261020-215152-0f15824` and `20261020-223327-b2ecc45`): `dictation.start` + `dictation.cancel`, end to end.

Preconditions: this session holds the lock, Launch and Doctor OK (Dictus is the default IME, `RECORD_AUDIO` granted, at least one model in `files/models`); Argent device `99071FFAZ00EBN`.

- **Open a scratch field.** `mcp__argent__launch-app {bundleId:"dev.dettmer.simplenotes"}`, tap `Nouvelle note` then `Note textuelle` (`run-sequence` with `delayMs: 1000`). → the tree lists `TextField "Titre"` with no value and the keyboard's nodes: `Keyboard language DE`, `Microphone`, the keys (`helpers/evidence.sh 01-dictation.cancel-keyboard-before`).
- **Mark the log.** `M=$(helpers/log.sh mark)`.
- **Start.** Tap the centre of `Microphone`. → `helpers/log.sh wait 'ACTION_START' 15 "$M"` prints `DictationService: DictationService started foreground with ACTION_START`; the tree shows `Annuler la dictée` (✕), `Terminer la dictée` (✓), `00:01`, `En écoute…`, `Settings`; the status bar shows the green mic chip (`helpers/evidence.sh 02-dictation.cancel-recording "$M"`).
- **Cancel.** Tap the centre of `Annuler la dictée` (✕). → `helpers/log.sh wait 'Recording cancelled' 10 "$M"` prints `DictationService: Recording cancelled, samples discarded` (preceded by `AudioCaptureManager: AudioRecord cancelled, samples discarded`, followed by the IME's `Recording cancelled`); the keys are back.
- **Check nothing was inserted.** `mcp__argent__describe`: `TextField "Titre"` still has no value, the app shows `0 character`; `helpers/log.sh since "$M" | grep -cE 'confirmAndTranscribe|Transcribed text inserted'` prints `0`; `dumpsys activity services dev.pivisolutions.dictus` lists `DictationService` without `isForeground=true` (`helpers/evidence.sh 03-dictation.cancel-after "$M"`).
- **Delete the scratch note.** `Plus d'options` → `Supprimer`; if it reached the bin, `Paramètres` → `Corbeille` → `Supprimer la note définitivement` on it. An empty note is not kept at all.
- **Stop instead (records the room, only if the task needs a transcript).** Tap `Terminer la dictée` (✓). → `Recording stopped: samples=N`, then `Dictation inserted: d=…, length=N, committed=true`; `Titre` holds the text and the bar shows `Annuler la dernière dictée`; tap it → `Dictation undone: length=N` and the field is back as before (`dictation.undo-insert`).
- **Proof.** Before / during / after screenshots, the `ACTION_START` and `Recording cancelled, samples discarded` lines after the mark, zero transcription lines, and the empty field in the tree.

## Gotchas

- The keyboard's keys are all in Argent's tree as `StaticText` (labels follow the keyboard language: `Leertaste` / `Eingabe` in DE); tap the centre of their frames.
- On this Pixel the recording notification is invisible (notifications denied for Dictus): `dictation.notification` cannot be shown without changing Pierre's permission. The running service shows only as « 1 appli est active » in Quick Settings.
- The recording panel's `Settings` gear sits in the bottom-left corner, in the band of the system's keyboard nav bar (`⌄`, keyboard switcher): aim at its frame, not at the nav icons.
- Never tap the nav bar's keyboard switcher (`Changer le mode de saisie`): a tap switches to the AOSP keyboard at once.
- With no model downloaded, a mic tap shows « Aucun modèle vocal — Téléchargez un modèle vocal pour commencer à dicter. » (`kbd.dictation-error`) and nothing records: that is the expected end state, not a failure of the recipe.
- The first tap after the app process was killed can land while the model loads: the mic is dimmed (`Microphone indisponible pendant le chargement…`) and the recording starts by itself once the engine is ready (pending mic gate). Wait for `Engine prewarm complete` before asserting.
- Clips under 0.5 s are dropped before transcription: no insertion, a warning in the log and « Aucun mot détecté » in the suggestion bar (log `No words detected notice shown: surface=keyboard`), the same notice as a blank transcription.
- To force a failed transcription on the emulator (debug build): start a dictation, remove the active model file with `run-as dev.pivisolutions.dictus` while it records, then ✓. → log `Model not available, cannot transcribe` then `Transcription failed, nothing inserted`; the bar shows the failure line for 3 s. Reinstall the model afterwards.
- Fields marked password, e-mail, URL or search still accept dictation, but nothing is kept for learning (`typing.private-fields`).
- The waveform logs `waveformHeartbeat surface=keyboard` every 2 s (`avgLevel`, `maxGapMs` = worst frame gap) and `waveformStall` above 500 ms. `avgLevel=0.0` on every heartbeat of a take while a clip plays means the guest recorded silence: the take is lost (#156).
- A `✓` inserts what the room said. Use `✕` unless the task needs a transcript.
