# Learned words

Words the user types or picks repeatedly, and words whose correction was undone, are learned and no longer corrected.

- **ID:** `typing.learned-words`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `kbd.keys`, `app.settings` — see [screens.md](screens.md)

## Sub-features

- `learned-words.learn`: A word typed (or picked in the bar) twice is learned; ⌫ right after an autocorrection learns the original at once. — présent
- `learned-words.reset`: « Réinitialiser les mots appris » (red row) at the bottom of Réglages → Clavier asks « Réinitialiser les mots appris ? » with « Oublier N mots appris » / « Annuler » (texts of iOS `SettingsView.swift`). Confirming empties the personal dictionary, and an already loaded keyboard stops suggesting the learned words at once (#194). — présent

## How to get to it (user POV)

1. Type a word Dictus does not know twice, or undo its correction with ⌫.
2. The next time, the word is left as typed and offered in the suggestion bar.
3. To start over: Dictus → Réglages → « Réinitialiser les mots appris » → « Oublier N mots appris ».

## Driving it with Argent

**Not executable on Pierre's Pixel** (`99071FFAZ00EBN`): learning writes a word into his personal dictionary, and the reset that would remove it also forgets every word he learned himself. Report it as not driven, with that precondition. It runs on a disposable test device (the headless AVD `dictus_api35`, or a phone whose data may be wiped), with `DICTUS_SERIAL` pointing at it.

Preconditions: a test device, never Pierre's Pixel; Launch and Doctor OK on it; a scratch note focused; a nonsense word never typed before on that device.

- **Learn (test device only).** Type the word through the keys, tap space, twice. → left as typed (`evidence.sh learned`); typing its first letters offers it in the suggestion bar.
- **Reset (test device only).** Switch to Dictus (keyboard left loaded), Réglages, scroll to the bottom of Clavier, tap « Réinitialiser les mots appris ». → the dialog. « Annuler » closes it and changes nothing; « Oublier N mots appris » closes it and empties the dictionary.
- **After reset.** Back in the note, type the same first letters. → the word is no longer suggested, without restarting the keyboard.
- **Proof.** The field value and the suggestion bar in the tree at each step; screenshots of the row and the dialog.

## Gotchas

- **Never on Pierre's Pixel:** both learning and the reset change his personal dictionary for good.
- Other journeys can teach a word by accident (⌫ right after an autocorrection learns the original): `typing.autocorrect`'s ⌫ step has the same caveat.
- Learning is off in password/e-mail/URL/search fields and in apps that request no personalised learning.
- The count in « Oublier N mots appris » is the number of words on disk; a word learned a fraction of a second earlier may not be counted yet.
