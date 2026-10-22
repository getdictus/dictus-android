# Emoji

The emoji key opens a picker in place of the letters, with categories and recently used emoji; ABC returns to the letters.

- **ID:** `keyboard.emoji`
- **Tier:** Gratuit
- **Statut Android:** partiel (no search)
- **Screens:** `kbd.emoji` — see [screens.md](screens.md)

## Sub-features

- `emoji.picker`: Emoji by category (androidx `EmojiPickerView`); ⌫ in the picker. — présent
- `emoji.recents`: « UTILISÉS RÉCEMMENT », first category (empty: « Vous n'avez pas encore utilisé d'emoji »). — présent
- `emoji.search`: — absent.
- `emoji.back-to-letters`: `ABC` at the bottom left. — présent

## How to get to it (user POV)

1. In the Dictus keyboard, tap 😊 left of the space bar.
2. Pick emoji; tap ABC to return to the letters.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a scratch note focused.

- **Open.** Tap `😊` (bottom row, second from left). → the picker replaces the keys *and the mic row*: category tabs (`UTILISÉS RÉCEMMENT`, `ÉMOTICÔNES ET ÉMOTIONS`…), emoji as `View "😀"`, `ABC`, `⌫` (`screen.sh kbd.emoji`).
- **Back.** Tap `ABC`. → the letters are back.
- **Proof.** Screenshots of both states; an inserted emoji appears in the field's value.

## Gotchas

- System back (◁) also closes the picker before closing the keyboard.
- Inserting an emoji adds it to Pierre's recents: avoid it, or say so.
