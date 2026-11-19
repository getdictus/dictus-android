# Keyboard bench (#382)

Measures the Dictus keyboard on fast typing, and Gboard on the same taps. Measurement
only: it drives the keyboard as it ships and changes nothing in it.

It is the Android port of the iOS bench (getdictus/dictus-ios#954, `tools/keyboard-bench/`):
same corpus, same taps, same counts. The protocol (what to run, how to read the
numbers, what each run can and cannot show) is
[`docs/keyboard-measurement.md`](../../docs/keyboard-measurement.md).

## Pieces

| File | What it is |
|---|---|
| `corpus/fr.txt`, `corpus/en.txt` | 20 everyday sentences per language, the text the typist means. Copied from iOS unchanged. |
| `make_runs.py` | Turns the corpus into touch runs, with the iOS seed and touch model. Rerunning it rewrites the same files byte for byte. |
| `runs/fr.tsv`, `runs/en.tsv` | One row per tap: the key aimed at, where the finger lands relative to its centre (in key units), and when. Byte-identical to the iOS runs. |
| `harness/` | A Gradle build of its own: an app with one text field, and the instrumentation test `KeyboardReplay` that replays a run on whichever keyboard the emulator has selected. |
| `run.sh` | One command: installs the harness, selects the keyboard, replays each language, pulls the files, prints the report. |
| `dictus_prefs.py` | Sets the Dictus keyboard language in the debug build's DataStore, keeping every other setting. |
| `score.py` | The report, for every source: a replay, or text typed by hand on a device. Same counts as the iOS scorer. |

## Run

```bash
./gradlew -p tools/keyboard-bench/harness assembleDebug assembleDebugAndroidTest
./gradlew :app:assembleDebug -Pdictus.emulatorAbi=true      # Dictus with its x86_64 libraries
# boot the emulator (docs/keyboard-measurement.md), install Dictus, then:
tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard dictus > dictus.md
tools/keyboard-bench/run.sh --serial emulator-5554 --keyboard gboard --langs en > gboard-en.md
```

About 4 minutes per language and keyboard. Writes `taps.tsv`, `commits.tsv`, `texts.tsv`
and `keys.tsv` to `build/keyboard-bench/<keyboard>-<lang>/`.

## What the replay does

For each sentence, `KeyboardReplay`:

1. empties the field and restarts the input session, as a new message does, and waits
   for the keyboard's letter page;
2. reads where the keys are from the keyboard window's accessibility tree: the centre of
   each letter and of the space bar. One key unit across is the distance between two
   neighbouring letter centres, one unit down the distance between two letter rows, so
   the run's offsets land the same way on any keyboard;
3. for each tap, injects a touch-down and, 40 ms later, a touch-up at the aimed centre
   plus the run's offset, through `UiAutomation`. The keyboard's own hit test decides
   which key it is, and its own space bar runs its own autocorrect;
4. reads what changed in the field, and when: the change is the key that resolved, a
   space that may have corrected the word before it, or nothing;
5. keeps the run's rhythm (170 ms between taps on average), but never taps before the
   keyboard has settled from the previous one.

The iOS bench compiles the keyboard's code into a replay process. On Android the real
keyboard runs instead: Compose's hit test (touch targets included) cannot be restated
outside the keyboard window, and the space bar's autocorrect waits on suggestion
lookups that run in the keyboard service. The cost is that the time column is a tap
to its text in the field, not the engine work alone.
