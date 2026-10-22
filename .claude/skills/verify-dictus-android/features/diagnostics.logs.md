# Debug logs and diagnostics

Réglages → A PROPOS shows the version, the app's own log (without any dictated or typed text), an export of that log, the licences, GitHub and legal notices.

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

1. Dictus → Réglages, scroll to A PROPOS.
2. « Debug Logs » to read; « Exporter les logs de débogage » to send.

## Driving it with Argent

Preconditions: Launch and Doctor OK.

- **Scroll.** Réglages, `gesture-swipe` (0.5, 0.8) → (0.5, 0.2) twice. → `A PROPOS`, `Version`, `Debug Logs` (`evidence.sh about`).
- **Read the log.** Tap `Debug Logs`. → title `Debug Logs`, the menu `⋮`, log lines oldest first (`screen.sh app.settings.debug-logs`); `Retour`.
- **Export (sheet only).** Tap `Exporter les logs de débogage`. → the share sheet `Partage de 1 fichier`, `dictus-logs.zip` (seen 2026-10-20); system back, never pick a destination.
- **Licences.** Tap `Licences`. → the list (`screen.sh app.settings.licences`); `Retour`.
- **Proof.** The tree of each screen. The same log is readable from the host: `adb shell run-as dev.pivisolutions.dictus cat files/dictus.log`.

## Gotchas

- Do not tap « Effacer » in the menu: it erases Pierre's log.
- The export opens the share sheet with Pierre's apps and accounts: prove the sheet opens, then system back; never send.
- Coming back from Debug Logs restores Réglages at another scroll offset: re-read the frames before the next tap (a stale frame opened the export sheet once).
- « GitHub » and « Mentions légales » open the browser.
