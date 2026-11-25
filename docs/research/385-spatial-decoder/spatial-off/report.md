# Keyboard measurement: dictus, emulator replay, spatial autocorrect off

commit: `c5d6f24`

## Final text

| lang | sentences | words | wrong words | word error rate | char error rate | char error rate, accents ignored | glued words |
|---|---:|---:|---:|---:|---:|---:|---:|
| fr | 20 | 152 | 8 | 5.3% | 2.0% | 0.7% | 0 |
| en | 20 | 157 | 13 | 8.3% | 3.1% | 3.1% | 0 |

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
| fr | letter | 667 | 1.88 | 3.02 | 97.16 |
| fr | space | 152 | 2.29 | 3.62 | 4.49 |
| en | letter | 618 | 1.86 | 2.22 | 78.42 |
| en | space | 154 | 2.28 | 3.66 | 9.37 |

## Autocorrect, per word the space bar committed

| lang | words | typed right, kept | typed wrong, fixed | **broken** (typed right, changed) | missed (typed wrong, kept) | of which accent only | **miscorrected** | glued | glued, split back |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fr | 152 | 121 | 23 | **0** | 5 | 4 | **3** | 0 | 0 |
| en | 152 | 135 | 11 | **0** | 3 | 0 | **3** | 2 | 0 |

Wrong autocorrections are the two bold columns. A stray space splits one intended word over two commits; those halves count as typed wrong.

## Every word that did not come out right

| lang | outcome | intended | typed | final |
|---|---|---|---|---|
| en | miscorrected | my | finishy | finish |
| en | miscorrected | hour | boue | blue |
| en | miscorrected | need | meef | meet |
| en | missed | been | Een | Een |
| en | missed | is | s | s |
| en | missed | been | bern | bern |
| en | glued | ten minutes | tenminutes | terminates |
| en | glued | hour late | hourvlate | hourglass |
| fr | miscorrected | décalée | decalee | déclare |
| fr | miscorrected | déjà | feja | cela |
| fr | miscorrected | déchargé | decharge | décharge |
| fr | missed | réunion | reùion | reùion |
| fr | missed | réservé | reserve | reserve |
| fr | missed | trouvé | trouve | trouve |
| fr | missed | couchés | couches | couches |
| fr | missed | oublié | oublie | oublie |

