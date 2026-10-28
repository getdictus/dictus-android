# Sounds and haptics

Dictation start, stop and cancel can play a sound chosen per event at a set volume; keys vibrate and click if wanted.

- **ID:** `feedback.sounds-haptics`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `app.settings.sounds`, `app.settings.sound-picker`, `app.settings` — see [screens.md](screens.md)

## Sub-features

- `sounds.toggle`: « Activer les sons » in Réglages → Sons, with the note « Les sons respectent le mode silencieux du téléphone. » — présent
- `sounds.volume`: « Volume » slider. — présent
- `sounds.per-event`: under « SONS PAR ÉVÉNEMENT », « Début », « Fin », « Annuler », each a list of sounds played on tap. — présent
- `haptics.toggle`: « Retour haptique » in Réglages → CLAVIER. — présent
- `sounds.key-clicks` *(Spécifique Android)*: « Sons du clavier »: a click on each key, distinct from the dictation sounds.

## How to get to it (user POV)

1. Dictus → Réglages → Sons.
2. Turn the dictation sound on, set the volume, pick a sound per event.

## Driving it with Argent

Preconditions: Launch and Doctor OK. Note the switches' states: they must be the same at the end.

- **Open.** Réglages → tap `Sons`. → `Activer les sons`, `Volume`, `Début`, `Fin`, `Annuler` (`screen.sh app.settings.sounds`).
- **A picker.** The three rows are `[disabled]` while `Activer les sons` is off (Pierre's setting): turn it on, open the picker, come back, turn it off again. Tap `Début`. → the list with the selected sound ticked (`Sélectionné`) (`screen.sh app.settings.sound-picker`); tap `Retour` without choosing.
- **Proof.** The tree of both screens; a toggled switch flips its state in the tree (then flip it back).

## Gotchas

- Rows read `Début / Electronic 01F`: the current sound is part of the label.
- Toggling « Activer les sons » off again writes `sound_enabled=false` explicitly in the DataStore where it was absent before: same behaviour (default is off), but `cleanup.sh` reports `PREFS differ`.
- Tapping a sound in the picker selects it and plays it: go back with `Retour` instead.
- Hearing a sound or feeling a vibration cannot be captured; only the preference is provable.
