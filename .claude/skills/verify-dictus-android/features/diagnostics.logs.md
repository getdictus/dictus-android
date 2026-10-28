# Debug logs and diagnostics

Réglages → À PROPOS shows the version, the app's own log (without any dictated or typed text), an export of that log, the licences, GitHub and legal notices.

- **ID:** `diagnostics.logs`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.settings.debug-logs`, `app.settings.licences`, `app.settings` — see [screens.md](screens.md)

## Sub-features

- `diagnostics.log-view`: « Debug Logs »: the log, with a menu to « Effacer » or « Copier ». — présent
- `diagnostics.log-export`: « Exporter les logs de débogage »: a ZIP (`dictus-logs.zip`) through the share sheet. — présent
- `diagnostics.app-group-check`: — Spécifique iOS.
- `diagnostics.about`: « Version », « Licences », « GitHub », « Mentions légales ». — présent

## How to get to it (user POV)

1. Dictus → Réglages, scroll to À PROPOS.
2. « Debug Logs » to read; « Exporter les logs de débogage » to send.

## Driving it with Argent

Preconditions: Launch and Doctor OK.

- **Scroll.** Réglages, `gesture-swipe` (0.5, 0.8) → (0.5, 0.2) twice. → `À PROPOS`, `Version`, `Debug Logs` (`evidence.sh about`).
- **Read the log.** Tap `Debug Logs`. → title `Debug Logs`, the menu `⋮`, log lines oldest first (`screen.sh app.settings.debug-logs`); `Retour`.
- **Export (sheet only).** Tap `Exporter les logs de débogage`. → the share sheet `Partage de 1 fichier`, `dictus-logs.zip` (seen 2026-10-20); system back, never pick a destination.
- **Licences.** Tap `Licences`. → the list (`screen.sh app.settings.licences`); `Retour`.
- **Proof.** The tree of each screen. The same log is readable from the host: `adb shell run-as dev.pivisolutions.dictus cat files/dictus.log`. The exported `dictus.log` inside the ZIP is that file with the context header on top; to pull it without a share target, `adb exec-out run-as dev.pivisolutions.dictus cat cache/dictus-logs.zip > dictus-logs.zip` after tapping export.

## What the export contains (#112)

- **Files**: `files/dictus.log` rotates past 2 MB into `files/dictus.previous.log` (one previous file only). The ZIP holds `dictus.previous.log` first (when it exists), then `dictus.log`.
- **Header**, at the top of `dictus.log`, before `---`: export time and zone; app version, versionCode, build type, `rev <sha>@<branch>`; manufacturer, model, Android version and API, build id, ABI; total and available RAM; active model, engine, transcription language; keyboard enabled and default IME; `RECORD_AUDIO` and `POST_NOTIFICATIONS`; the retained window, from the oldest line of the previous file to the newest of the current one.
- **Lines**: `2026-10-23T00:12:03.456+02:00 INFO    [IME] DictusImeService d=7f3a01: …` — zoned time, level, category (`LIFECYCLE`, `UI`, `IME`, `KEYBOARD`, `SERVICE`, `AUDIO`, `STT`, `MODEL`, `INSERT`, `APP`), calling class, and the dictation id while one is in flight.
- **One keyboard dictation**, `grep 'd=<id>'`: `Mic tap` → `Mic gate: command=START_RECORDING` → `Recording requested: source=keyboard` → `DictationService started foreground` → `AudioRecord started` → `Recording stopped: samples=…, audioMs=…` → `confirmAndTranscribe: model=…, engine=…` → `Transcription started` / `Transcription finished: engine=…, durationMs=…, rawLength=…` → `Transcription pipeline done` → `[INSERT] Dictation inserted: d=…, length=…, committed=…`. Lengths and timings only, never text.

## Gotchas

- Do not tap « Effacer » in the menu: it erases Pierre's log.
- The export opens the share sheet with Pierre's apps and accounts: prove the sheet opens, then system back; never send.
- Coming back from Debug Logs restores Réglages at another scroll offset: re-read the frames before the next tap (a stale frame opened the export sheet once).
- « GitHub » and « Mentions légales » open the browser.
