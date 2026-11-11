# Autocorrect and suggestions

While typing with the Dictus keyboard, a three-slot bar above the keys offers completions and next-word predictions, a mistyped word is corrected when space, a punctuation mark or Return is pressed, and the undo chip the bar then shows restores what was typed.

- **ID:** `typing.autocorrect`
- **Tier:** Gratuit
- **Statut Android:** présent
- **Screens:** `kbd.keys`, `kbd.suggestions` — see [screens.md](screens.md)

## Sub-features

- `typing.suggestions`: Suggestion bar (three slots) with completions of the current word and next-word predictions after a space. — présent
- `typing.autocorrect-on-space`: A misspelt word is replaced on space (FR, EN, ES, DE dictionaries). A word with a digit in it (`14h`, `3€`, `2e`) is never corrected, at space, a punctuation mark or Return, as iOS #74; the periodic `Autocorrect outcomes` log counts it as `NOT_A_WORD`. — présent (#287)
- `typing.autocorrect-on-punctuation`: A misspelt word is also corrected by `, . ; : ! ?` (the mark follows it with no space) and by Return, never by `'` or `-`; the undo chip follows, as after space, and keeps the mark. Same marks as iOS `AutocorrectTrigger` (#872). A word of an e-mail address or a URL is never corrected, in any field (`helene.bethmont@gmail.com`, `www.getdictus.com`, `https://github.com/x`): the word before the first `.` or `:` is corrected at the mark, then put back, without learning it, as soon as a letter, a digit or `/ _ @` is typed right after the mark (iOS #919). — présent (#268, #332)
- `typing.undo-correction`: The suggestion bar offers to undo the last correction: after a correction (space, punctuation or Return) the first slot shows the typed word with ↶ on an accent-tinted chip that pulses once (`Annuler la correction, rétablir « … »`); a tap puts the typed word back, keeps what follows it, and learns it. The chip stays while the next word is typed and goes at the next space (after a punctuation correction, at the second one), when the cursor moves, or once ⌫ reaches the corrected word. ⌫ only ever deletes one character, as on iOS; no vibration on a correction (iOS #224). Shown even with « Suggestions » off. Editors that hide their text: the chip goes at the next input but ⌫. — présent (#276)
- `typing.auto-full-stop`: a second space after anything but a space or a `.` becomes `. ` and the next letter is capitalised (same rule as iOS `AutoFullStop.shouldSubstitute`: digits and other punctuation get it too). ⌫ right after turns `. ` back into one space; iOS has no such undo. Never in password fields; no setting, as on iOS. — présent
- `typing.accents`: Long-press a letter to open its accent strip, slide to choose. — présent
- `typing.dead-keys`: — absent.
- `typing.autocorrect-toggle`: « Correction automatique » in Réglages → Clavier. — présent
- `typing.auto-capitalization`: shift lights up on its own at the start of a field, after `.` `!` `?` and a space, and after a newline, then drops after one letter; tapping shift overrides it, caps lock is never touched; nothing in password, e-mail, URL, number fields or fields that do not ask for capitals. Same rules as iOS (`DictusKeyboardBridge.updateCapitalization`; the ID is not in the iOS map yet). — présent
- `typing.trackpad` *(Spécifique Android)*: Hold the space bar and slide: the key labels disappear and the cursor moves character by character.
- `typing.adaptive-accent-key` *(Spécifique Android)*: In French AZERTY, the `'` key next to N becomes é / à / ù / î / ô after the matching vowel (replaces it); long-press offers the other variants (è ê ë, â ä á…).
- `typing.suggestions-toggle` *(Spécifique Android)*: « Suggestions » switch in Réglages → Clavier, independent of « Correction automatique ».
- `typing.private-fields` *(Spécifique Android)*: no suggestion bar and no learning in password, e-mail, URL and search fields, or when the app asks for no personalised learning.

## How to get to it (user POV)

1. Type with the Dictus keyboard in a notes app.
2. Pick a word in the bar above the keys; a mistyped word is fixed when you press space; tap the ↶ chip that replaces the first suggestion to keep what you typed.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a scratch note focused, keyboard language FR (the space bar reads `espace`).

- **Type through the keys.** Tap the letter keys `b o n j o u r` by coordinate (AZERTY, from the `kbd.keys` capture); `mcp__argent__keyboard` would bypass the Dictus keys and prove nothing. → the bar proposes completions (`evidence.sh 01-typing`).
- **Misspell, then space.** Type `b o n j o r u`, tap space. → the field reads `bonjour ` in the tree (`evidence.sh 02-after-space`).
- **Digit words (#287).** Type `1 4` from the 123 page, `h` with `ABC`, then space; same with `3 €`. → the field keeps `14h 3€ `, no `INVALID_TOKEN` WARN in logcat.
- **Punctuation and Return.** Type `b o n j o r u`, then `,` from the 123 page. → the field reads `bonjour,`. Same with `.`; with Return (the ↵ icon, labelled `retour`) the field reads `bonjour` and a new line.
- **Address (#332).** In a plain text field, type `h e l e n e . b e t h m o n t @ g m a i l . c o m` with the keys (`.` and `@` from the 123 page). → the field reads `helene.bethmont@gmail.com`, no `Hélène`, no `Gmail`; `Hélène.` may show between the `.` and the `b`.
- **Undo the correction (test device only, see Gotchas).** After the correction on space, the first slot shows `↶ bonjoru` (`kbd.suggestions`). Tap ⌫ once. → the field reads `bonjour`, the chip is still there. Tap the chip. → the field reads `bonjoru` again.
- **Auto-capitalization.** Focus an empty scratch note. → the shift key is lit (accent background) and the letter keys show capitals (`evidence.sh 04-autocap-start`). Type `o k . ` with the keys (`.` from the 123 page, back with `ABC`). → shift is lit again after the space (`evidence.sh 05-autocap-after-stop`). Focus an e-mail or password field (a login screen). → shift stays off.
- **Double-space full stop.** In a scratch note, type `o k` with the keys, then tap space twice. → the field reads `ok. ` and shift is lit (`evidence.sh 06-full-stop`). Tap ⌫ once. → the field reads `ok ` and shift is off (`evidence.sh 07-full-stop-undo`).
- **Proof.** The field's value before and after space, after ⌫ and after the chip (screenshots; `describe` where the session allows reading the tree). Autocorrect decisions are not logged with text (privacy): the field value is the proof.

## Gotchas

- `mcp__argent__keyboard` types through adb (`input text`), not through the Dictus keys: it never exercises suggestions or autocorrect.
- A word learned earlier (`typing.learned-words`) is no longer corrected, and the undo chip step **teaches the misspelling** (only « Réinitialiser les mots appris » removes it, and that forgets every learned word): on Pierre's Pixel, stop at the correction on space and skip the chip step; run the undo only on a test device.
- With nothing to suggest, the bar shows ☰ and the mic only; with « Suggestions » off the slots never appear, but the undo pill after a dictation (#139) and the autocorrect undo chip alone (#276) still do.
- Search, e-mail, URL and password fields hide the bar on purpose (`typing.private-fields`).
- Auto-capitalization follows the field's cap flags: a plain text field that does not ask for capitals (some search boxes, some apps' custom fields) never lights shift, by design. The Pixel shows it as the key colour; the tree has no shift state.
