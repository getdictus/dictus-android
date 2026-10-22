# Learned words

Words the user types or picks repeatedly, and words whose correction was undone, are learned and no longer corrected.

- **ID:** `typing.learned-words`
- **Tier:** Gratuit
- **Statut Android:** partiel (no reset)
- **Screens:** `kbd.keys` — see [screens.md](screens.md)

## Sub-features

- `learned-words.learn`: A word typed (or picked in the bar) twice is learned; ⌫ right after an autocorrection learns the original at once. — présent
- `learned-words.reset`: — absent: no « Reset learned words » in Réglages; only clearing the app's data empties the personal dictionary.

## How to get to it (user POV)

1. Type a word Dictus does not know twice, or undo its correction with ⌫.
2. The next time, the word is left as typed.

## Driving it with Argent

**Not executable on Pierre's Pixel** (`99071FFAZ00EBN`) as long as Dictus has no way to reset the learned words: every step of this journey writes a word into his personal dictionary for good. Report it as not driven, with that precondition. It runs only on a disposable test device (the headless AVD `dictus_api35`, or a phone whose data may be wiped), with `DICTUS_SERIAL` pointing at it.

Preconditions: a test device, never Pierre's Pixel; Launch and Doctor OK on it; a scratch note focused; a nonsense word never typed before on that device.

- **First time (test device only).** Type the word through the keys, tap space. → it may be corrected; tap ⌫ to restore it.
- **Second time (test device only).** Type it again, tap space. → left as typed (`evidence.sh learned`).
- **Proof.** The field value in the tree at both steps.

## Gotchas

- **Never on Pierre's Pixel:** learning is permanent and there is no reset in Dictus; only clearing the app's data empties the dictionary, which would also delete his models and settings. The journey stays « non exécutable sur le Pixel de Pierre » until `learned-words.reset` exists.
- Other journeys can teach a word by accident (⌫ right after an autocorrection learns the original): `typing.autocorrect`'s ⌫ step has the same caveat.
- Learning is off in password/e-mail/URL/search fields and in apps that request no personalised learning.
