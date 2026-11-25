# Keyboard measurement: dictus, emulator replay, spatial autocorrect on

commit: `c5d6f24`

## Final text

| lang | sentences | words | wrong words | word error rate | char error rate | char error rate, accents ignored | glued words |
|---|---:|---:|---:|---:|---:|---:|---:|
| fr | 20 | 152 | 7 | 4.6% | 1.6% | 0.6% | 0 |
| en | 20 | 157 | 10 | 6.4% | 2.3% | 2.3% | 0 |

Word errors are a word-level edit distance against the intended sentence; a glued word is a final token that is two consecutive intended words without the space. Ignoring accents leaves the errors that are not a missing accent: with no autocorrect at work, they are the keyboard's wrong keys.

## Taps

| lang | letter taps | wrong letter | of which a stray space | space taps | missed space | missed space landed on |
|---|---:|---:|---:|---:|---:|---|
| fr | 669 | 14 (2.1%) | 0 | 152 | 0 (0.0%) | - |
| en | 629 | 26 (4.1%) | 0 | 157 | 3 (1.9%) | `none` 1, `edit` 1, `v` 1 |

## Time per keystroke, tap to text (ms)

From the finger lifting to the first change of the field, as the app sees it: touch dispatch, the keyboard's own work and the input connection. A keyboard that writes on touch-down is timed from the touch-down instead. Emulator on a shared machine: compare runs of the same session with each other, never with a device.

| lang | key | taps | p50 | p95 | max |
|---|---|---:|---:|---:|---:|
| fr | letter | 667 | 1.89 | 2.85 | 85.81 |
| fr | space | 152 | 4.34 | 9.78 | 33.21 |
| en | letter | 618 | 1.87 | 2.29 | 80.66 |
| en | space | 154 | 3.97 | 9.25 | 22.80 |

## Autocorrect, per word the space bar committed

| lang | words | typed right, kept | typed wrong, fixed | **broken** (typed right, changed) | missed (typed wrong, kept) | of which accent only | **miscorrected** | glued | glued, split back |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fr | 152 | 121 | 24 | **0** | 4 | 4 | **3** | 0 | 0 |
| en | 152 | 135 | 14 | **0** | 2 | 0 | **1** | 2 | 0 |

Wrong autocorrections are the two bold columns. A stray space splits one intended word over two commits; those halves count as typed wrong.

## Every word that did not come out right

| lang | outcome | intended | typed | final |
|---|---|---|---|---|
| en | miscorrected | my | finishy | finish |
| en | missed | been | Een | Een |
| en | missed | is | s | s |
| en | glued | ten minutes | tenminutes | terminates |
| en | glued | hour late | hourvlate | hourglass |
| fr | miscorrected | décalée | decalee | déclare |
| fr | miscorrected | déjà | feja | cela |
| fr | miscorrected | déchargé | decharge | décharge |
| fr | missed | réservé | reserve | reserve |
| fr | missed | trouvé | trouve | trouve |
| fr | missed | couchés | couches | couches |
| fr | missed | oublié | oublie | oublie |

