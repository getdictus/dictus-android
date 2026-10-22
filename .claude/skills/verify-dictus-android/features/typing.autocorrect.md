# Autocorrect and suggestions

While typing with the Dictus keyboard, a three-slot bar above the keys offers completions and next-word predictions, a mistyped word is corrected when space is pressed, and Backspace right after a correction restores what was typed.

- **ID:** `typing.autocorrect`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `kbd.keys`, `kbd.suggestions` — see [screens.md](screens.md)

## Sub-features

- `typing.suggestions`: Suggestion bar (three slots) with completions of the current word and next-word predictions after a space. — présent
- `typing.autocorrect-on-space`: A misspelt word is replaced on space (FR, EN, ES, DE dictionaries). — présent
- `typing.undo-correction`: — partiel: Backspace immediately after a correction restores the original word (and learns it); there is no undo chip in the bar as on iOS.
- `typing.auto-full-stop`: — absent: double space inserts two spaces.
- `typing.accents`: Long-press a letter to open its accent strip, slide to choose. — présent
- `typing.dead-keys`: — absent.
- `typing.autocorrect-toggle`: « Correction automatique » in Réglages → CLAVIER. — présent
- `typing.trackpad` *(Spécifique Android)*: Hold the space bar and slide: the key labels disappear and the cursor moves character by character.
- `typing.adaptive-accent-key` *(Spécifique Android)*: In French AZERTY, the `'` key next to N becomes é / à / ù / î / ô after the matching vowel (replaces it); long-press offers the other variants (è ê ë, â ä á…).
- `typing.suggestions-toggle` *(Spécifique Android)*: « Suggestions » switch in Réglages → CLAVIER, independent of « Correction automatique ».
- `typing.private-fields` *(Spécifique Android)*: no suggestion bar and no learning in password, e-mail, URL and search fields, or when the app asks for no personalised learning.

## How to get to it (user POV)

1. Type with the Dictus keyboard in a notes app.
2. Pick a word in the bar above the keys; a mistyped word is fixed when you press space; press ⌫ at once to keep what you typed.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a scratch note focused, keyboard language FR (the language code in the top bar reads `FR`).

- **Type through the keys.** Tap the letter keys `b o n j o u r` by coordinate (AZERTY, from the `kbd.keys` capture); `mcp__argent__keyboard` would bypass the Dictus keys and prove nothing. → the bar proposes completions (`evidence.sh 01-typing`).
- **Misspell, then space.** Type `b o n j o r u`, tap space. → the field reads `bonjour ` in the tree (`evidence.sh 02-after-space`).
- **Undo the correction (test device only, see Gotchas).** Tap ⌫ once. → the field reads `bonjoru` again.
- **Proof.** The field's value in `mcp__argent__describe` before and after space, and after ⌫. Autocorrect decisions are not logged with text (privacy): the field value is the proof.

## Gotchas

- `mcp__argent__keyboard` types through adb (`input text`), not through the Dictus keys: it never exercises suggestions or autocorrect.
- A word learned earlier (`typing.learned-words`) is no longer corrected, and the ⌫ undo step **teaches the misspelling permanently** (no reset exists): on Pierre's Pixel, stop at the correction on space and skip the ⌫ step; run the undo only on a test device.
- The bar is empty (hidden) when there is nothing to suggest; with « Suggestions » off it never appears, but the undo chip after a dictation still does.
- Search, e-mail, URL and password fields hide the bar on purpose (`typing.private-fields`).
