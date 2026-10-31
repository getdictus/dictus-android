# Emoji

The emoji key opens a picker in place of the letters: one category at a time, the category bar at the bottom, the recently used emoji, and a search with French keywords; ABC returns to the letters. A Compose port of iOS `EmojiPickerView` + `EmojiCategoryBar` since #192 (the androidx `EmojiPickerView` is gone).

- **ID:** `keyboard.emoji`
- **Tier:** Gratuit
- **Statut Android:** partiel (the keyboard bar still disappears while picking, #139)
- **Screens:** `kbd.emoji` — see [screens.md](screens.md)

## Sub-features

- `emoji.picker`: the 9 iOS categories, 8 columns, the category name in capitals above the grid (« SMILEYS », « PERSONNES »…, FR and EN resources; iOS shows the English names). Bottom bar: `ABC`, 🔍 `Rechercher un emoji`, one emoji per category, `⌫`. Emoji the device font cannot draw are left out (`Paint.hasGlyph`). No skin tones (iOS has none). — présent
- `emoji.recents`: clock icon `Récents`, first in the bar and opened by default when not empty; 30 at most, the last used first. The androidx list was copied once, read-only, on the first open after #192. — présent
- `emoji.search`: 🔍 → field « Rechercher des Emoji », a row of results (the recents while empty), a mini AZERTY/QWERTY keyboard with `⌫` and `espace`; « Aucun résultat ». French keyboard: French keywords then English Unicode names; other languages: Unicode names. `×` clears, then closes the search. — présent
- `emoji.back-to-letters`: `ABC` at the bottom left. — présent

## How to get to it (user POV)

1. In the Dictus keyboard, tap 😊 left of the space bar.
2. Pick emoji; tap 🔍 to search; tap ABC to return to the letters.

## Driving it with Argent

Preconditions: Launch and Doctor OK, a scratch note focused.

- **Open.** Tap `😊` (bottom row, second from left). → the picker replaces the keys *and the mic row*: a capitalized category name, emoji cells (`View "😀"`), the bar `ABC`, `Rechercher un emoji`, category buttons by name (`Smileys`, `Personnes`…), `Effacer` (`screen.sh kbd.emoji`).
- **Category.** Tap `Animaux et nature`. → the label reads « ANIMAUX ET NATURE ».
- **Search.** Tap `Rechercher un emoji`, then the mini keys (`c`, `h`, `a`, `t`). → the field shows the text and the row starts with 🐱 (French keyboard). `Effacer la recherche` clears, a second tap closes.
- **Back.** Tap `ABC`. → the letters are back.
- **Proof.** Screenshots of both modes; a search on video; an inserted emoji appears in the field's value.

## Gotchas

- System back (◁) also closes the picker before closing the keyboard, from either mode.
- Inserting an emoji adds it to the recents of the device: avoid it on Pierre's Pixel, or say so.
- The mini search keyboard only types into the search field; nothing reaches the editor until an emoji is tapped.
