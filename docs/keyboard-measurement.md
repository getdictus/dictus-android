# Measuring the keyboard

The reference measurement for keyboard work (#382): wrong letters, wrong
autocorrections, missed spacebar taps and time per keystroke, for the Dictus keyboard
and for Gboard on the same input. Any change to the key grid, autocorrect, suggestions
or the space bar (#370 first) reports these numbers before and after.

It is the Android port of the iOS protocol (getdictus/dictus-ios#954,
`docs/keyboard-measurement.md` there): same corpus, same taps, same counts, so an
Android number and an iOS number come from the same input. The tool is
`tools/keyboard-bench/` ([README](../tools/keyboard-bench/README.md)). The first
baseline is [`docs/research/382-keyboard-baseline/`](research/382-keyboard-baseline/README.md).

## The input

- **Corpus.** `tools/keyboard-bench/corpus/fr.txt` and `en.txt`: 20 everyday sentences
  each, copied unchanged from iOS. Lowercase, no punctuation, no apostrophes, no digits.
  French keeps its accents: they are part of what autocorrect has to produce.
- **Touch runs.** `tools/keyboard-bench/runs/fr.tsv` and `en.tsv`: one row per tap.
  The typist types fast and plainly: lowercase, no accent typed (`tres` for `très`),
  one space after every word. Each tap aims at the centre of its key and lands off it
  by a Gaussian offset (sigma 0.22 key widths across, 0.18 row heights down, centred
  0.05 rows below the key centre), 170 ms apart on average. The seed is fixed (954) and
  the files are committed: 821 French taps and 786 English ones, byte-identical to the
  iOS runs (`make_runs.py` rewrites them byte for byte). Offsets are in key units, which
  is what lets the same run drive keyboards whose keys are not in the same place.

The touch model is chosen, not measured. It decides how many letters go wrong before
any keyboard logic runs, so numbers are comparable between runs of the same files and
not with typing studies. Changing it (`make_runs.py`) starts a new series, on both
platforms.

## Two ways to run it

| | Emulator replay | Device |
|---|---|---|
| Command | `run.sh` | typing by hand, then `score.py --typed` |
| Takes | ~5 min per language and keyboard | ~10 min per language and keyboard |
| Dictus | yes | yes |
| Gboard | yes, same taps | yes |
| Wrong letters | per tap | character error rate only, autocorrect included |
| Autocorrect outcomes | per word | no: the final text cannot tell a word typed right from one corrected |
| Missed spacebar | per tap, and glued words | glued words |
| Time per keystroke | tap to text, emulator | no |

Use the emulator replay for every before/after on a branch, and for the comparison with
Gboard. Use a device run before claiming a result holds on a phone, with a real thumb.

### 1. Emulator replay

The replay drives the real keyboard. A small harness app holds one text field; its
instrumentation test injects each tap of a run through `UiAutomation`, into whichever
keyboard is selected, and reads the field after each tap. So the hit test is the
keyboard's own, touch-target rules included, and every space runs the autocorrect the
space bar runs. Key positions come from the keyboard window's accessibility tree.

The machine rules hold: one emulator at a time, under the emulator lock and the Gradle
lock (`.claude/skills/verify-dictus-android/SKILL.md`, Emulator tier), and no build next
to it. So build first:

```bash
./gradlew -p tools/keyboard-bench/harness assembleDebug assembleDebugAndroidTest
./gradlew :app:assembleDebug -Pdictus.emulatorAbi=true
```

Then boot `dictus_api35` (Pixel 4 screen, 1080 x 2280 at 440 dpi, gesture navigation),
install Dictus and replay:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard dictus > dictus.md
```

`run.sh` selects the keyboard, sets the Dictus keyboard language for each run
(`dictus_prefs.py`, every other setting kept) and prints the report. The rows are in
`build/keyboard-bench/<keyboard>-<lang>/`.

**Gboard.** The Google APIs emulator images ship Gboard. Its languages live in its own
settings, which no script can write, and `dictus_api35` returns to its snapshot at each
boot, so add French once per emulator session, by hand:

1. In any text field, Gboard's toolbar → settings → Languages → Add keyboard.
2. Search `fran` **by tapping Gboard's keys**. Never `adb shell input text` or another
   key event there: Gboard then takes the emulator for a device with a physical
   keyboard and shows a floating toolbar instead of its keys, until the next boot.
3. Français (France), AZERTY, turn Multilingual typing off, Done. Open English (US) and
   turn its Multilingual typing off too: each run measures one language, as on Dictus.

With two languages, Gboard draws a `langue suivante` / `next language` key; the harness
taps it until the first row starts with `az` (fr) or `qw` (en), and refuses to run
otherwise. Then:

```bash
tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard gboard > gboard.md
```

`run.sh` checks the selected keyboard before and after each language: stopping the
Dictus app to set its language can leave the system on Gboard, and it fails rather than
measure the wrong keyboard. Do not run Argent on the emulator during a replay: its own
`UiAutomation` connection stops the harness from registering one.

**Boundaries.** `run.sh --mode probe --langs fr` taps 2 px apart across every boundary
of the top row, along the row and down into the row below, and writes `probe.tsv`: what
each tap typed, or `none`.

**Touch maps and touch-area simulations** (#370). `run.sh --mode map --langs fr` taps 2 px
apart along lines around the space bar, the edge keys, the top row and the bottom of the
keyboard, and writes `map.tsv`; `run.sh --snap gaps|gaps,below` replays the runs with each tap
that lands in no key area moved into the nearest one, which simulates tiled touch areas on the
keyboard as it ships. Dictus's accessibility nodes are its labels, so both take its key
rectangles with `--rects`, measured on a capture. See
[`docs/research/370-ergonomics/`](research/370-ergonomics/README.md).

Leave the emulator's keyboard as you found it (Gboard on `dictus_api35`).

### 2. Device

What a device adds is the real thumb and the real hardware. What it cannot give is the
touch points: recording them needs code in the keyboard, which this protocol does not
add. Pierre's Pixel 4 is the device; the agents do not drive it for this.

For each keyboard (Gboard, then Dictus) and each language:

1. Open a notes app, a new note.
2. Type the 20 sentences of `tools/keyboard-bench/corpus/<lang>.txt`, one per line, at
   your normal fast speed, thumbs only, looking at the sentence, not the keys. No
   backspace, no tapping a suggestion: the keyboard's own output is what is measured.
3. Copy the note into a text file, one sentence per line.
4. Score it:

   ```bash
   python3 -I tools/keyboard-bench/score.py --typed fr:dictus-fr.txt --keyboard "Dictus, Pixel 4"
   ```

A person's errors are not a seeded run: the same person on the same day gives
comparable numbers, a different day less so. Type Gboard and Dictus back to back.

## Reading the report

- **Wrong letter**: a letter tap that typed another character than the one aimed at, or
  nothing. Geometry and hit test only. #370 moves this number.
- **Broken / miscorrected**: the wrong autocorrections. Broken is a correctly typed
  word that autocorrect changed; miscorrected is a mistyped word changed into another
  wrong word. These two must not go up.
- **Missed (typed wrong, kept)**, with its **accent only** part: words autocorrect
  left wrong. The accent-only column is French words like `arrivé` typed `arrive`.
- **Missed space / glued**: a space tap that did not type a space, and the glued words
  left in the final text.
- **Char error rate, accents ignored**: what is left once missing accents are forgiven.
- **Time per keystroke, tap to text**: from the touch-up (or the touch-down, for a
  keyboard that writes on touch-down) to the field's change, as the app sees it. It
  includes touch dispatch, the keyboard's own work and the input connection; the
  suggestion lookups a letter starts in the background are not in it. iOS times the
  engine work in-process instead, so the two time columns do not compare.

## Limits

- **The emulator is not the phone.** Same screen size and density as the Pixel 4, an
  x86_64 build of the native dictionary, a shared host. Times compare between runs of
  one session only.
- **Taps are synthetic.** One finger, a 40 ms press, no rollover: a tap starts once the
  previous one has settled. A real thumb overlaps keys at speed.
- **A tap above the keys reaches the suggestion strip.** On both keyboards a tap that
  lands that high can pick a suggestion: the report counts it as an edit, as a real
  thumb would have caused it. The iOS replay typed nothing there.
- **One keyboard state.** Default settings, no learned words, no contacts. A tap on
  shift, the symbols key or delete does what it does on the real keyboard, and the next
  sentence starts from a fresh field.
- **Gboard's state is its own.** It learns from what it types, so only its first replay
  after a boot is a clean one. Note its version
  (`dumpsys package com.google.android.inputmethod.latin | grep versionName`): the
  emulator's is a preload, the Pixel's is whatever the Play Store installed.
- **Gboard's boundaries move with context.** Its touch areas follow what was typed, so a
  probe across a Gboard boundary measures that context as much as the geometry.
