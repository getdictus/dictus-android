# Appearance (Spécifique Android)

The app and the keyboard follow a dark, light or automatic theme, and the interface language can be French, English or the system's.

- **ID:** `appearance`
- **Tier:** Gratuit
- **Statut Android:** présent — Spécifique Android (no equivalent in the iOS map)
- **Screens:** `app.settings`, `app.settings.picker` — see [screens.md](screens.md)

## Sub-features

- `appearance.theme` *(Spécifique Android)*: « Thème »: Sombre (default), Clair, Automatique; applies to the app and the keyboard.
- `appearance.ui-language` *(Spécifique Android)*: « Langue de l'interface »: Automatique, Français, English (per-app locale).

## How to get to it (user POV)

1. Dictus → Réglages → APPARENCE.
2. « Thème » or « Langue de l'interface », pick a value in the sheet.

## Driving it with Argent

Preconditions: Launch and Doctor OK. Note the current values.

- **Open a picker.** Tap `Thème`. → sheet `Thème de l'application` with the current value ticked (`evidence.sh theme-picker`); system back.
- **Proof.** The sheet in the tree; values unchanged at the end.

## Gotchas

- Changing the interface language recreates the activity and changes every label this map relies on: do not, or put it back at once.
