# Keyboard baseline (#382)

The first run of the [keyboard measurement protocol](../../keyboard-measurement.md),
taken 2026-11-19 on the `dictus_api35` emulator (Android 15, Google APIs x86_64,
1080 x 2280 at 440 dpi, the Pixel 4 screen; gesture navigation).

- **Dictus**: develop at `71f5ece` (Merge pull request #366, release 1.5.0), built with
  `-Pdictus.emulatorAbi=true`. The installed build reads `1.5.0-1-g71f5ece-dirty`: the
  working tree held the bench files, not yet committed, and no app code. Default
  settings, French keyboard for `fr` and English for `en`.
- **Gboard**: `14.2.09.629370537-preload-x86_64`, the one the image ships. English (US)
  QWERTY and Français (France) AZERTY, multilingual typing off for both, every other
  setting as shipped. The AVD returns to its snapshot at each boot, so this Gboard had
  typed nothing before.
- **Tool**: `tools/keyboard-bench/` at `41739e3`, the commit the reports name. The
  `report.md` files were scored again after review, with `score.py` timing letters by
  the key aimed at (one Gboard space tap that typed `v` had been timed as a letter).

## Files

| Path | What |
|---|---|
| `dictus-replay/`, `gboard-replay/` | `run.sh` for each keyboard. `report.md` as printed, then per language every tap (`taps.tsv`), every space-bar commit (`commits.tsv`), every sentence (`texts.tsv`) and the key positions read from the keyboard (`keys.tsv`). |
| `dictus-probe-fr.tsv`, `gboard-probe-fr.tsv` | `run.sh --mode probe`: taps 2 px apart across each boundary of the French top row, along the row and down into the row below. |
| `boundaries.txt` | The two probes, one line per boundary. |

## Dictus against Gboard, same 1607 taps

| | Dictus fr | Gboard fr | Dictus en | Gboard en |
|---|---:|---:|---:|---:|
| Wrong words in the final text | 21 of 152 | **1** of 152 | 25 of 157 | **5** of 157 |
| Wrong letters, per tap | 26 of 669 (3.9%) | 4 (0.6%) | 36 of 629 (5.7%) | 11 (1.7%) |
| of which the tap typed nothing | **19** | 0 | **24** | 0 |
| Autocorrect: fixed | 19 | 21 | 10 | 6 |
| Autocorrect: broken | 0 | 0 | 0 | 0 |
| Autocorrect: missed (accent only) | 9 (4) | 2 (2) | 11 | 2 |
| Autocorrect: miscorrected | **11** | 0 | **7** | 1 |
| Missed space taps | 1 of 152 | 1 of 152 | 3 of 157 | 2 of 157 |
| Glued words split back by the space bar | 0 of 1 | 1 of 1 | 0 of 3 | 2 of 2 |
| Letter, tap to text, p50 / p95 / max (ms) | 1.9 / 2.6 / 78 | 2.3 / 4.5 / 15 | 1.9 / 2.3 / 78 | 2.3 / 4.2 / 25 |

Same taps, same emulator, same session. Both keyboards autocorrect in both languages
here, unlike Apple's French keyboard on the iOS simulator.

- **Dead bands between keys are most of Dictus's wrong letters.** 43 of its 62 wrong
  letters are taps that typed nothing; Gboard has none. The probe shows why: every
  boundary of the Dictus top row, between two keys and between two rows, swallows a
  band of 10 to 12 px (4 dp) where a tap types nothing (`boundaries.txt`). That is the
  layout's spacing: `KeyRow` puts 4 dp between keys, and each key pads 2 dp above and
  below, all outside the key's touch area. Gboard's touch areas tile the keyboard, so
  every point types a key. With those bands closed, Dictus would be left with 6 and 11
  wrong keys, against Gboard's 4 and 9.
- **Gboard also moves its boundaries.** Near a boundary, the key a Gboard tap types
  changes with what was typed before (`boundaries.txt`, the alternating letters): its
  touch areas follow the language model, as Apple's key-target resizing does
  (dictus-ios #949). Dictus's boundaries are fixed.
- **Lost letters feed miscorrections.** A dropped letter leaves `douent`, `rning`,
  `tble`, `arve`; Dictus then picks a wrong word (`fluent`, `rving`, `t'blé`, `larve`).
  Gboard, which had the letter, corrects to the right word or never needs to.
- **Spacebar.** One French space tap landed on the row above on both keyboards. Gboard
  typed `v`, then split `Jevsuis` back into `Je suis` at the next space; Dictus typed
  nothing (the tap was in the band above the space bar) and lost `je`. The other missed
  spaces landed below the keyboard, where neither types anything. Gboard split every
  glued word back; Dictus split none.
- **Autocorrect, French:** both left `a` for `à` and `couches` for `couchés`. Dictus
  also left `trouve`, `oublie` without their accent (#501 on iOS) and miscorrected 11
  words: 8 after a lost or wrong letter, 3 typed right but for an accent
  (`décalée` became `dévaler`, `réservé` and `déchargé` lost their final accent).
- **Time per keystroke:** under 5 ms at p95 for both. Dictus's slowest letters (23 to 78 ms)
  all fall in the first sentence of a run, right after the language is loaded. An
  emulator figure, for comparing runs.

## Compared with iOS

Same corpus, same taps (dictus-ios `docs/research/954-keyboard-baseline/`): Dictus on
iOS left 9 wrong words in French and 9 in English, Dictus on Android 21 and 25. The iOS
grid tiles its cells with 1/9 pt gaps; the Android grid leaves 4 dp between keys.

## Repeatability

A second Dictus replay in the same session gave byte-identical `texts.tsv` and
`commits.tsv`, and identical taps apart from the time columns, in both languages.
Gboard was replayed once: it learns from what it types, so a second run on the same
Gboard is not the same keyboard.
