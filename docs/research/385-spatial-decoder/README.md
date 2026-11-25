# Spatial autocorrect, before and after (#385)

The [keyboard measurement](../../keyboard-measurement.md) on the same runs as the
[#382 baseline](../382-keyboard-baseline/README.md), taken 2026-11-25 on `dictus_api35`
(Android 15, x86_64, Pixel 4 screen), with the branch head `c5d6f24` (develop `998de03` merged in,
then every authoritative rule ahead of the decoder) built with `-Pdictus.emulatorAbi=true`:
`run.sh --spatial off`, then `--spatial on`. Each language was replayed on its own,
`dumpsys meminfo` read right after. The first measure, before the merge (2026-11-21, `a3a222f`),
is summed up under [Before the merge](#before-the-merge).

## Files

| Path | What |
|---|---|
| `spatial-off/`, `spatial-on/` | `report.md` (fr and en scored together), and per language `taps.tsv`, `commits.tsv`, `texts.tsv`, `keys.tsv` |
| `changed-words.tsv` | Every commit whose final word differs between the two runs |
| `meminfo-<off\|on>-<lang>.txt` | `dumpsys meminfo dev.pivisolutions.dictus` after each run |

## Off is develop

The same runs replayed on develop `998de03` (the merged base, its own debug build) give
`texts.tsv` and `commits.tsv` byte-identical to the "off" runs here, in both languages. Develop
itself moved since the first measure: the key touch areas (#400, #401) closed the dead bands, so
the taps resolve to other letters in places, and the trie's ranking (#388) and the context rules
(#392) changed. That is why "off" is better here than in the first measure.

## Before and after

| | fr off | fr on | en off | en on |
|---|---:|---:|---:|---:|
| Wrong words in the final text | 8 of 152 | **7** | 13 of 157 | **10** |
| Autocorrect: fixed | 23 | 24 | 11 | 14 |
| Autocorrect: broken | 0 | **0** | 0 | **0** |
| Autocorrect: miscorrected | 3 | 3 | 3 | 1 |
| Glued words split back | 0 of 0 | 0 of 0 | 0 of 2 | 0 of 2 |

Four words change, all for the better:

| lang | intended | typed | off | on |
|---|---|---|---|---|
| fr | réunion | reùion | reùion | réunion |
| en | been | bern | bern | been |
| en | hour | boue | blue | hour |
| en | need | meef | meet | need |

Two words of the French corpus are decided by the words before, a rule that answers before the
decoder (#386, as iOS `correctionProposal`): sentence 4 types `a` where `à` is meant and gets `à`,
sentence 11 keeps `elle a`; both come out the same off and on. No sentence has a curated name or
`il ma`: the unit tests cover those.

## Cost

Time per keystroke, tap to text, ms (p50 / p95 / max):

| | fr off | fr on | en off | en on |
|---|---|---|---|---|
| Letter | 1.88 / 3.02 / 97.2 | 1.89 / 2.85 / 85.8 | 1.86 / 2.22 / 78.4 | 1.87 / 2.29 / 80.7 |
| Space | 2.29 / 3.62 / 4.5 | 4.34 / 9.78 / 33.2 | 2.28 / 3.66 / 9.4 | 3.97 / 9.25 / 22.8 |

A letter costs nothing measurable: it adds one point to a list. A space runs the decoder on
the main thread, the trie's lookup having already run in the background: about +2 ms at the
median, +6 ms at p95, in a debug build on the emulator. The first space of a language
also maps its model.

Memory, `dumpsys meminfo` (KB). The IME runs in the app's process, so these are the app's
numbers, Whisper included:

| | fr off | fr on | en off | en on |
|---|---:|---:|---:|---:|
| TOTAL PSS | 254,836 | 259,865 | 248,685 | 253,289 |
| Private Dirty | 155,644 | 156,216 | 152,640 | 152,256 |
| `.apk mmap` PSS | 68,639 | 72,767 | 69,374 | 72,858 |

Private Dirty moves by +572 KB (fr) and -384 KB (en): no visible growth from the decoder. The
`.apk mmap` line grows by about 3.5 to 4 MB: the model's clean pages of the APK, read in as a
search touches them, which the system can drop under pressure.

## Before the merge

First measure, 2026-11-21, `a3a222f` on develop `71f5ece` (replayed again on `5d91f64` with the
same words): fr 21 → 20 wrong words, en 25 → 21, broken 0 and 0, eight words changed (five better,
two wrong both times, one worse: `ue` → `je`, its `n` lost in a dead band that #400 has since
closed). Space p95 3.64 → 12.42 ms (fr) and 3.25 → 6.48 ms (en). Its raw files are in git
history at `aa236fe`.

## Limits

The limits of the [protocol](../../keyboard-measurement.md#limits), and:

- **One language at a time.** Mixed French and English is where the decoder alone breaks words
  of the other language (19 French words broken by the English keyboard on iOS, against 10
  without it, dictus-ios `hex-spatial-etude.md` section 7). This bench has no mixed corpus.
- **Synthetic taps.** The offsets are drawn from a chosen Gaussian, close to Hex's sigmas, so
  the decoder's model fits them by construction. A real thumb is for the Pixel.
- **Debug build on the emulator.** The space cost is to be measured again in a release build
  on the phone.
