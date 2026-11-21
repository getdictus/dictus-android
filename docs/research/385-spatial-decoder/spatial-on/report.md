# Keyboard measurement: dictus, emulator replay, spatial autocorrect on

commit: `a3a222f`

## Final text

| lang | sentences | words | wrong words | word error rate | char error rate | char error rate, accents ignored | glued words |
|---|---:|---:|---:|---:|---:|---:|---:|
| fr | 20 | 152 | 20 | 13.2% | 4.6% | 3.2% | 0 |
| en | 20 | 157 | 21 | 13.4% | 4.3% | 4.3% | 0 |

Word errors are a word-level edit distance against the intended sentence; a glued word is a final token that is two consecutive intended words without the space. Ignoring accents leaves the errors that are not a missing accent: with no autocorrect at work, they are the keyboard's wrong keys.

## Taps

| lang | letter taps | wrong letter | of which a stray space | space taps | missed space | missed space landed on |
|---|---:|---:|---:|---:|---:|---|
| fr | 669 | 26 (3.9%) | 0 | 152 | 1 (0.7%) | `none` 1 |
| en | 629 | 36 (5.7%) | 0 | 157 | 3 (1.9%) | `none` 3 |

## Time per keystroke, tap to text (ms)

From the finger lifting to the first change of the field, as the app sees it: touch dispatch, the keyboard's own work and the input connection. A keyboard that writes on touch-down is timed from the touch-down instead. Emulator on a shared machine: compare runs of the same session with each other, never with a device.

| lang | key | taps | p50 | p95 | max |
|---|---|---:|---:|---:|---:|
| fr | letter | 649 | 1.79 | 2.34 | 78.02 |
| fr | space | 151 | 4.14 | 12.42 | 43.89 |
| en | letter | 604 | 1.76 | 2.10 | 78.02 |
| en | space | 154 | 3.77 | 6.48 | 21.95 |

## Autocorrect, per word the space bar committed

| lang | words | typed right, kept | typed wrong, fixed | **broken** (typed right, changed) | missed (typed wrong, kept) | of which accent only | **miscorrected** | glued | glued, split back |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fr | 150 | 111 | 20 | **0** | 9 | 4 | **10** | 1 | 0 |
| en | 151 | 123 | 12 | **0** | 10 | 0 | **6** | 3 | 1 |

Wrong autocorrections are the two bold columns. A stray space splits one intended word over two commits; those halves count as typed wrong.

## Every word that did not come out right

| lang | outcome | intended | typed | final |
|---|---|---|---|---|
| en | miscorrected | thursday | tutsda | tuesday |
| en | miscorrected | morning | rning | rving |
| en | miscorrected | document | douent | fluent |
| en | miscorrected | booked | booke | brooke |
| en | miscorrected | hour | bou | boy |
| en | miscorrected | need | meef | meer |
| en | missed | up | u | u |
| en | missed | the | he | he |
| en | missed | in | n | n |
| en | missed | been | Een | Een |
| en | missed | is | s | s |
| en | missed | my | y | y |
| en | missed | she | Sh | Sh |
| en | missed | an | n | n |
| en | missed | we | E | E |
| en | missed | to | t | t |
| en | glued | ten minutes | tenminutes | terminates |
| en | glued | still have | illhave | illhave |
| fr | miscorrected | rentrant | rentrn | rentre |
| fr | miscorrected | retrouve | retouv | retour |
| fr | miscorrected | réunion | reùion | région |
| fr | miscorrected | décalée | decalee | dévaler |
| fr | miscorrected | réservé | reserve | réserve |
| fr | miscorrected | table | tble | t'blé |
| fr | miscorrected | arrivé | arve | rave |
| fr | miscorrected | une | ue | je |
| fr | miscorrected | déjà | feja | fera |
| fr | miscorrected | déchargé | decharge | décharge |
| fr | missed | à | a | a |
| fr | missed | une | ne | ne |
| fr | missed | trouvé | trouve | trouve |
| fr | missed | les | le | le |
| fr | missed | couchés | couches | couches |
| fr | missed | il | I | I |
| fr | missed | viendrai | viendra | viendra |
| fr | missed | nous | Nus | Nus |
| fr | missed | oublié | oublie | oublie |
| fr | glued | je suis | Esuis | Suis |

