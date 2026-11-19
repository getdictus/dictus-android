# Debug logs and diagnostics

Réglages → À propos shows the version, « Envoyer un retour » (#373), the app's own log (without any dictated or typed text), an export of that log, the licences, GitHub, the privacy policy and the terms of use.

- **ID:** `diagnostics.logs`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.settings.debug-logs`, `app.settings.polish-debug`, `app.settings.licences`, `app.settings`, `app.settings.feedback`, `app.settings.feedback.ask-log`, `app.settings.feedback.log`, `app.settings.feedback.failed`, `app.settings.feedback.rate-limited`, `app.settings.feedback.rate-limited-elapsed`, `app.settings.feedback.sent` — see [screens.md](screens.md)

## Sub-features

- `diagnostics.log-view`: « Debug Logs »: the log, with a menu to « Effacer » or « Copier ». — présent
- `diagnostics.log-export`: « Exporter les logs de débogage »: a ZIP (`dictus-logs.zip`) through the share sheet. — présent
- `diagnostics.app-group-check`: — Spécifique iOS.
- `diagnostics.feedback`: « Envoyer un retour » (#373, iOS #565 and #981): name (required), optional email, Bug / Idée / Autre, message, « Joindre le journal technique » (off by default for every type; « Voir ce qui sera envoyé » appears once it is on), « Également envoyé » (Dictus, Android, Appareil, Langue et région, Révision du code). A Bug sent with the box off asks « Joindre le journal technique ? » first: « Joindre » and « Envoyer sans » both send. Posted to `POST https://www.getdictus.com/api/feedback` with `platform: "android"`. On failure: one sentence, « Réessayer » (not after a refusal), « Partager à la place ». — présent
- `diagnostics.about`: « Version », « Licences », « GitHub », « Politique de confidentialité » and « Conditions d’utilisation » (EN « Privacy policy », « Terms of use »), which open getdictus.com/fr/privacy and /fr/terms, /en/ in English (#220; « Mentions légales » pointed to a dead page). — présent
- `polish.debug` (iOS ID): « Transcription debug », opened by tapping « Version » five times quickly, in release builds too. One entry per dictation the engine returned text for, app (`<APP>`) or keyboard (`<KBD>`), kept 7 days in `files/polish_events.jsonl` with the dictated text in plain text; menu « Exporter le JSON… » (`dictus-polish-debug-<yyyyMMdd-HHmmss>.json`, the iOS schema, through the share sheet) and « Effacer ». — présent (#295)

## How to get to it (user POV)

1. Dictus → Réglages, scroll to À propos.
2. « Debug Logs » to read; « Exporter les logs de débogage » to send.

## Driving it with Argent

Preconditions: Launch and Doctor OK.

- **Scroll.** Réglages, `gesture-swipe` (0.5, 0.8) → (0.5, 0.2) twice. → `À propos`, `Version`, `Debug Logs` (`evidence.sh about`).
- **Read the log.** Tap `Debug Logs`. → title `Debug Logs`, the menu `⋮`, log lines oldest first (`screen.sh app.settings.debug-logs`); `Retour`.
- **Export (sheet only).** Tap `Exporter les logs de débogage`. → the share sheet `Partage de 1 fichier`, `dictus-logs.zip` (seen 2026-10-20); system back, never pick a destination.
- **Transcription debug.** Tap `Version` five times quickly (`gesture-tap` with `clickCount: 5`). → title `Transcription debug`, `Résultats (N affichés)`, `Événements récents`, one row per dictation (`polish off`, the raw text, `<KBD>  none  stt=<model>  det=-  <ms>ms  <n>→<n>`); tap a row → `Événement de transcription` with `tx mode`, `keyboard`, `stt lang` (`(inert)` on Parakeet). Menu `Menu` → `Exporter le JSON…` opens the share sheet on `dictus-polish-debug-….json`: system back, never send. From the host: `adb shell run-as dev.pivisolutions.dictus cat files/polish_events.jsonl` (one JSON per line, `metrics.languageResolution.sttLanguageCode` is the language the engine got).
- **Licences.** Tap `Licences`. → the list (`screen.sh app.settings.licences`); `Retour`.
- **Proof.** The tree of each screen. The same log is readable from the host: `adb shell run-as dev.pivisolutions.dictus cat files/dictus.log`. The exported `dictus.log` inside the ZIP is that file with the context header on top; to pull it without a share target, `adb exec-out run-as dev.pivisolutions.dictus cat cache/dictus-logs.zip > dictus-logs.zip` after tapping export.

## Send feedback (#373)

Never post to www.getdictus.com from a test. A debug build takes the route from `-Pdictus.feedbackEndpoint`, and `helpers/feedback-stub.mjs` stands in for it with the website's own handler (validation, status codes, `Retry-After`), Telegram and the rate limit faked:

```bash
git clone -q --depth 1 -b develop https://github.com/getdictus/dictus-website.git /tmp/dictus-website
node .claude/skills/verify-dictus-android/helpers/feedback-stub.mjs /tmp/dictus-website 8787 <run-dir>/feedback.jsonl &
./gradlew assembleDebug -Pdictus.feedbackEndpoint=http://10.0.2.2:8787/api/feedback   # emulator
curl -s localhost:8787/__mode/429     # next POSTs: ok | 429 (Retry-After 600) | 429-bare (no header) | 502 | 503
```

On the Pixel, `adb reverse tcp:8787 tcp:8787` and `-Pdictus.feedbackEndpoint=http://localhost:8787/api/feedback`. The debug build allows plain HTTP to `10.0.2.2` and `localhost` only (`app/src/debug`). Each report the stub gets is a line of `feedback.jsonl`, the log replaced by its size and first line.

- **Open.** Réglages, scroll to À propos, tap `Envoyer un retour`. → title `Envoyer un retour`, `Envoyer` disabled, the box `Joindre le journal technique` off (`screen.sh app.settings.feedback`).
- **Bug question.** Type a name and a message, leave `Bug` and the box off, tap `Envoyer`. → dialog `Joindre le journal technique ?` with `Voir ce qui sera envoyé`, `Envoyer sans`, `Joindre` (`app.settings.feedback.ask-log`). `Joindre` sends with the log and turns the box on; `Envoyer sans` sends without. `Idée` or `Autre`, or the box on: no dialog.
- **Preview.** Turn the box on, tap `Voir ce qui sera envoyé`. → title `Ce qui sera envoyé`, the export header then the newest log lines (`app.settings.feedback.log`). It is read once when the form opens; coming back from it does not read it again.
- **Failure.** Airplane mode (`adb shell cmd connectivity airplane-mode enable`), or stub mode `502`/`503`. → the sentence in red on top, `Réessayer`, `Partager à la place` (`app.settings.feedback.failed`); the text stays after `Retour` and a new opening. `Partager à la place` opens the chooser with the text and, if the box is on, `cache/feedback-share/<uuid>/dictus-logs.txt`, deleted when the chooser closes: `adb shell run-as dev.pivisolutions.dictus ls -R cache/feedback-share`.
- **429.** Stub mode `429` → « Réessayez dans 10 minutes », `Réessayer` and `Envoyer` greyed for 600 s; mode `429-bare` → « Réessayez dans une minute », greyed 60 s, then enabled by themselves (`app.settings.feedback.rate-limited`, `…-elapsed`).
- **Sent.** Stub mode `ok` → `Envoyé. Merci.` and `OK` (`app.settings.feedback.sent`); the next opening keeps the name and email only.

## What the export contains (#112)

- **Files**: `files/dictus.log` rotates past 2 MB into `files/dictus.previous.log` (one previous file only). The ZIP holds `dictus.previous.log` first (when it exists), then `dictus.log`.
- **Header**, at the top of `dictus.log`, before `---`: export time and zone; app version, versionCode, build type, `rev <sha>@<branch>`; manufacturer, model, Android version and API, build id, ABI; total and available RAM; active model, engine, transcription language; keyboard enabled and default IME; `RECORD_AUDIO` and `POST_NOTIFICATIONS`; the retained window, from the oldest line of the previous file to the newest of the current one.
- **Lines**: `2026-10-23T00:12:03.456+02:00 INFO    [IME] DictusImeService d=7f3a01: …` — zoned time, level, category (`LIFECYCLE`, `UI`, `IME`, `KEYBOARD`, `SERVICE`, `AUDIO`, `STT`, `MODEL`, `INSERT`, `APP`), calling class, and the dictation id while one is in flight.
- **One keyboard dictation**, `grep 'd=<id>'`: `Mic tap` → `Mic gate: command=START_RECORDING` → `Recording requested: source=keyboard` → `DictationService started foreground` → `AudioRecord started` → `Recording stopped: samples=…, audioMs=…` → `confirmAndTranscribe: model=…, engine=…` → `Transcription started` / `Transcription finished: engine=…, durationMs=…, rawLength=…` → `Dictation tally: …` (a take with words) → `Transcription pipeline done` → `[INSERT] Dictation inserted: d=…, length=…, committed=…`. Lengths and timings only, never text.
- **One in-app dictation, Stop to result (#145)**: every stage carries the `t+=` of the dictation, measured from the start request: `Stop tapped: surface=app` → `Recording stopped` → `confirmAndTranscribe: model=…` → `Transcription started` (inference start) → `Transcription finished: …, durationMs=…` (inference end) → `Transcription processed` (post-processing end, DEBUG) → `Transcription outcome` → `Dictation tally` → `Transcription history step done: retention=…` (`EPHEMERAL` while history is off: nothing is written) → `Transcription pipeline done` → `Dictation result shown: surface=app` (the screen has left « Transcription en cours… »). Stop to result = `t+` of the last line minus `t+` of the first.
- **Dictation tally (#201)**, the debug readout of the words and audio seconds counted per local day for every user: after each take with words, app or keyboard, `[STT] DictationTranscriber d=…: Dictation tally: added audioSeconds=3; today words=22, audioSeconds=21, timeSavedSeconds=4`. Today's totals since local midnight, the iOS time-saved estimate (52.24 WPM) included; numbers only. From the host: `adb logcat -d -s DictationTranscriber:I | grep 'Dictation tally'` (debug build), or `adb shell "run-as dev.pivisolutions.dictus grep 'Dictation tally' files/dictus.log"`. In the app: Réglages → Debug Logs, among the other lines; no screen of its own yet.
- **Suggestion bar (#374)**, one DEBUG line per change of the keyboard bar's centre slot or of its suggestions: `Suggestion bar: slot=Suggestions mode=COMPLETION computed=3 received=3 shown=3`. `computed` is what the engine returned for the latest request (`-` before it answers), `received` what the bar got, `shown` the slots with a word; on an empty bar `reason=` says why (`pending`, `typing`, `cleared`, `no word`, `editor not eligible`, `suggestions off`, `word changed`, `context changed`, `no candidates`), and `reason=hidden by <slot>` when an error, the dictation notice or the undo pill holds the slot. Counts only, never a word. About three lines per key while typing.

## Gotchas

- Do not tap « Effacer » in the menu: it erases Pierre's log. The same goes for « Effacer » in Transcription debug: it erases his dictation journal.
- The export opens the share sheet with Pierre's apps and accounts: prove the sheet opens, then system back; never send. The same for « Partager à la place ».
- A debug build without `-Pdictus.feedbackEndpoint` posts feedback to production, as a release does: always build with the stub's URL before tapping `Envoyer`.
- Coming back from Debug Logs restores Réglages at another scroll offset: re-read the frames before the next tap (a stale frame opened the export sheet once).
- « GitHub », « Politique de confidentialité » and « Conditions d’utilisation » open the browser: GitHub on `github.com/getdictus/dictus-android` (the repo the Play listing names), the two legal pages in the interface language.
