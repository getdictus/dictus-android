# Keyboard measurement: dictus, emulator replay, taps moved: gaps,below

commit: `f36e579`

## Final text

| lang | sentences | words | wrong words | word error rate | char error rate | char error rate, accents ignored | glued words |
|---|---:|---:|---:|---:|---:|---:|---:|
| fr | 20 | 152 | 12 | 7.9% | 2.6% | 1.5% | 0 |
| en | 20 | 157 | 13 | 8.3% | 2.6% | 2.6% | 1 |

Word errors are a word-level edit distance against the intended sentence; a glued word is a final token that is two consecutive intended words without the space. Ignoring accents leaves the errors that are not a missing accent: with no autocorrect at work, they are the keyboard's wrong keys.

## Taps

| lang | letter taps | wrong letter | of which a stray space | space taps | missed space | missed space landed on |
|---|---:|---:|---:|---:|---:|---|
| fr | 669 | 16 (2.4%) | 0 | 152 | 0 (0.0%) | - |
| en | 629 | 24 (3.8%) | 0 | 157 | 1 (0.6%) | `none` 1 |

## Time per keystroke, tap to text (ms)

From the finger lifting to the first change of the field, as the app sees it: touch dispatch, the keyboard's own work and the input connection. A keyboard that writes on touch-down is timed from the touch-down instead. Emulator on a shared machine: compare runs of the same session with each other, never with a device.

| lang | key | taps | p50 | p95 | max |
|---|---|---:|---:|---:|---:|
| fr | letter | 664 | 1.74 | 2.26 | 82.05 |
| fr | space | 152 | 2.14 | 3.47 | 4.18 |
| en | letter | 621 | 1.71 | 1.98 | 12.33 |
| en | space | 156 | 2.10 | 3.34 | 7.91 |

## Autocorrect, per word the space bar committed

| lang | words | typed right, kept | typed wrong, fixed | **broken** (typed right, changed) | missed (typed wrong, kept) | of which accent only | **miscorrected** | glued | glued, split back |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fr | 152 | 120 | 20 | **0** | 4 | 4 | **8** | 0 | 0 |
| en | 155 | 135 | 10 | **0** | 4 | 0 | **6** | 1 | 0 |

Wrong autocorrections are the two bold columns. A stray space splits one intended word over two commits; those halves count as typed wrong.

## Every word that did not come out right

| lang | outcome | intended | typed | final |
|---|---|---|---|---|
| en | miscorrected | morning | irning | ironing |
| en | miscorrected | the | thw | thaw |
| en | miscorrected | my | finishy | finish |
| en | miscorrected | she | Shr | Shri |
| en | miscorrected | hour | bou | bout |
| en | miscorrected | need | meef | meer |
| en | missed | in | n | n |
| en | missed | been | Een | Een |
| en | missed | is | s | s |
| en | missed | been | bern | bern |
| en | glued | hour late | hourlate | hourlate |
| fr | miscorrected | rentrant | rentrn | rentre |
| fr | miscorrected | retrouve | retrouvr | retrouver |
| fr | miscorrected | réunion | reùion | région |
| fr | miscorrected | décalée | decalee | dévaler |
| fr | miscorrected | il | Ul | Cul |
| fr | miscorrected | réservé | reserve | réserve |
| fr | miscorrected | déjà | feja | fera |
| fr | miscorrected | déchargé | decharge | décharge |
| fr | missed | à | a | a |
| fr | missed | trouvé | trouve | trouve |
| fr | missed | couchés | couches | couches |
| fr | missed | oublié | oublie | oublie |

