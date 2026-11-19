# Keyboard measurement: gboard, emulator replay

commit: `41739e3`

## Final text

| lang | sentences | words | wrong words | word error rate | char error rate | char error rate, accents ignored | glued words |
|---|---:|---:|---:|---:|---:|---:|---:|
| fr | 20 | 152 | 1 | 0.7% | 0.1% | 0.0% | 0 |
| en | 20 | 157 | 5 | 3.2% | 0.7% | 0.7% | 0 |

Word errors are a word-level edit distance against the intended sentence; a glued word is a final token that is two consecutive intended words without the space. Ignoring accents leaves the errors that are not a missing accent: with no autocorrect at work, they are the keyboard's wrong keys.

## Taps

| lang | letter taps | wrong letter | of which a stray space | space taps | missed space | missed space landed on |
|---|---:|---:|---:|---:|---:|---|
| fr | 669 | 4 (0.6%) | 0 | 152 | 1 (0.7%) | `v` 1 |
| en | 629 | 11 (1.7%) | 0 | 157 | 2 (1.3%) | `none` 2 |

## Time per keystroke, tap to text (ms)

From the finger lifting to the first change of the field, as the app sees it: touch dispatch, the keyboard's own work and the input connection. A keyboard that writes on touch-down is timed from the touch-down instead. Emulator on a shared machine: compare runs of the same session with each other, never with a device.

| lang | key | taps | p50 | p95 | max |
|---|---|---:|---:|---:|---:|
| fr | letter | 670 | 2.30 | 4.52 | 15.04 |
| fr | space | 151 | 2.35 | 2.81 | 10.17 |
| en | letter | 627 | 2.31 | 4.16 | 25.09 |
| en | space | 155 | 2.38 | 2.93 | 6.39 |

## Autocorrect, per word the space bar committed

| lang | words | typed right, kept | typed wrong, fixed | **broken** (typed right, changed) | missed (typed wrong, kept) | of which accent only | **miscorrected** | glued | glued, split back |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fr | 150 | 127 | 21 | **0** | 2 | 2 | **0** | 1 | 1 |
| en | 153 | 144 | 6 | **0** | 2 | 0 | **1** | 2 | 2 |

Wrong autocorrections are the two bold columns. A stray space splits one intended word over two commits; those halves count as typed wrong.

## Every word that did not come out right

| lang | outcome | intended | typed | final |
|---|---|---|---|---|
| en | miscorrected | morning | .irning | .morning |
| en | missed | been | Een | Een |
| en | missed | my | finishy | finishy |
| fr | missed | à | a | a |
| fr | missed | couchés | couches | couches |

