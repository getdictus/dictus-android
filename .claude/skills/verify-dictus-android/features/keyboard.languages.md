# Keyboard languages and layouts

The Dictus keyboard types and corrects in French, English, Spanish or German, each language on its own layout (AZERTY, QWERTY or QWERTZ, #134), opening on letters or numbers; the transcription language is set separately.

- **ID:** `keyboard.languages`
- **Tier:** Gratuit
- **Statut Android:** partiel (no panel to pick a layout, no number row)
- **Screens:** `kbd.keys`, `kbd.numbers`, `kbd.symbols`, `app.settings`, `app.settings.picker` — see [screens.md](screens.md)

## Sub-features

- `keyboard.language-switch`: Tap the language code at the top left of the keyboard (`FR`, `EN`, `ES`, `DE`) to cycle; also « Langue du clavier » in Réglages. — présent (a tap that cycles, not a panel)
- `keyboard.layout`: one layout per keyboard language, like iOS #272 (#134): FR AZERTY, EN QWERTY, ES QWERTY, DE QWERTZ (Ü at the end of row 1, Ö Ä at the end of row 2, Y on row 3, ß on a long press of S). Switching language from the bar applies that language's layout. — partiel: no way to choose another layout yet; the single « Disposition du clavier » row was removed from Réglages until the keyboard panel (#191).
- `keyboard.number-row`: — absent.
- `keyboard.default-page`: ABC / 123 segment under « Langue du clavier » (iOS `DefaultLayerPicker`, #189). — partiel: no keyboard preview under the segment.
- `keyboard.transcription-language`: « Langue » under Transcription: Automatique, Français, English, Español, Deutsch. — partiel: no « Suivre la langue du clavier »; the two settings are independent.
- `keyboard.open-app`: Long-press the language code: Dictus opens on Réglages (without the bottom tabs). — présent

## How to get to it (user POV)

1. In the Dictus keyboard, tap `FR` at the top left to switch to `EN`, `ES`, `DE`.
2. Dictus → Réglages → Clavier for the language and default page; Transcription → Langue for dictation. The layout follows the keyboard language (no row since #134).

## Driving it with Argent

Preconditions: Launch and Doctor OK. Note the keyboard language, layout, default mode and transcription language shown in Réglages: they must read the same at the end.

- **Réglages.** Tap `Réglages`. → `Langue` (value `Automatique`), `Langue du clavier`, the `ABC` / `123` segment, no `Disposition du clavier` (#134) (`screen.sh app.settings`).
- **A picker without choosing.** Tap `Langue du clavier / …`. → a menu anchored to the row, the current language with ✓ (`screen.sh app.settings.picker`); system back closes it without choosing.
- **Cycle in the keyboard.** With a scratch note focused, tap `Langue du clavier DE` four times (`delayMs: 700`). → `FR`→`EN`→`ES`→`DE`, back to Pierre's `DE`; space and return read `espace`/`retour` in FR, `Leertaste`/`Eingabe` in DE. Each step redraws that language's layout: FR `a z e r t y` with the `'` key, EN and ES `q w e r t y`, DE `q w e r t z … ü` / `… l ö ä` / `y x c v b n m` (seen on the emulator 2026-10-31, #134). The stored layouts are under `keyboard_layouts_by_language` (`fr=azerty` …; an absent language follows its default). Check the stored value: `adb -s 99071FFAZ00EBN exec-out run-as dev.pivisolutions.dictus cat files/datastore/dictus_prefs.preferences_pb | strings | grep -A1 keyboard_language`.
- **Open the app.** Long-press the language code (`gesture-custom` Down, 900 ms, Up). → Dictus on `Réglages`, no bottom tabs (seen 2026-10-20); system back returns to the note.
- **The 123 and #+= pages.** With a scratch note focused, tap `123` (bottom left). → row 2 reads `- / : ; ( ) € & @ "` (`kbd.numbers`). Tap `#+=` (row 3, left). → row 2 reads `_ \ | ~ < > $ £ ¥ ·`, the toggle reads `123` (`kbd.symbols`). `ABC` goes back to the letters. Same keys and order as iOS (`DictusKeyboard/KeyboardLayouts.swift:299, 323`, #264); row 3 punctuation is still narrower than on iOS (#266).
- **Proof.** The code label in the tree after each tap; the Réglages values identical before and after.

## Gotchas

- Every change here is Pierre's setting; his keyboard language is DE. Cycle a full turn (4 taps) so it ends where it started — the screen can lag behind the stored value, so check the DataStore.
- The system keyboard picker still lists Dictus's subtypes as « AZERTY (Français) » and « QWERTY (Anglais) », unrelated to the four languages.
- The transcription language must stay `Automatique` (#130 maps unknown languages to auto).
- The IME settings in Android (`ime enable/set`) are not this feature: those are `onboarding.ime-setup`.
