# Spatial autocorrect, before and after (#385)

The [keyboard measurement](../../keyboard-measurement.md) on the same runs as the
[#382 baseline](../382-keyboard-baseline/README.md), taken 2026-11-21 on `dictus_api35`
(Android 15, x86_64, Pixel 4 screen), with the build `1.5.0-21-ga3a222f`
(`-Pdictus.emulatorAbi=true`): `run.sh --spatial off`, then `--spatial on`. Each language was
replayed on its own, `dumpsys meminfo` read right after.

## Files

| Path | What |
|---|---|
| `spatial-off/`, `spatial-on/` | `report.md` (fr and en scored together), and per language `taps.tsv`, `commits.tsv`, `texts.tsv`, `keys.tsv` |
| `changed-words.tsv` | Every commit whose final word differs between the two runs |
| `meminfo-<off\|on>-<lang>.txt` | `dumpsys meminfo dev.pivisolutions.dictus` after each run |

## Off is develop

With the switch off, `texts.tsv` and `commits.tsv` are byte-identical to the #382 baseline
(develop at `71f5ece`) in both languages, and so are the taps apart from their times.

## Before and after

| | fr off | fr on | en off | en on |
|---|---:|---:|---:|---:|
| Wrong words in the final text | 21 of 152 | **20** | 25 of 157 | **21** |
| Autocorrect: fixed | 19 | 20 | 10 | 12 |
| Autocorrect: broken | 0 | **0** | 0 | **0** |
| Autocorrect: miscorrected | 11 | 10 | 7 | 6 |
| Glued words split back | 0 of 1 | 0 of 1 | 0 of 3 | 1 of 3 |

Eight words change, read one by one:

| lang | intended | typed | off | on | verdict |
|---|---|---|---|---|---|
| fr | il | Ul | Cul | Il | better |
| fr | arrivé | arve | larve | rave | wrong both times |
| fr | une | ue | une | je | worse: the `n` fell in a dead band (#370) and the `u` landed low, towards `j` |
| fr | retard | reard | regard | retard | better |
| en | the | thw | thaw | the | better |
| en | hour late | hourlate | hourlate | hour late | better (a glued word split back) |
| en | been | bern | bern | been | better |
| en | hour | bou | bout | boy | wrong both times |

## Cost

Time per keystroke, tap to text, ms (p50 / p95 / max):

| | fr off | fr on | en off | en on |
|---|---|---|---|---|
| Letter | 1.76 / 2.38 / 129.2 | 1.79 / 2.34 / 78.0 | 1.75 / 2.08 / 62.8 | 1.76 / 2.10 / 78.0 |
| Space | 2.15 / 3.64 / 10.8 | 4.14 / 12.42 / 43.9 | 2.12 / 3.25 / 4.9 | 3.77 / 6.48 / 22.0 |

A letter costs nothing measurable: it adds one point to a list. A space runs the decoder on
the main thread, the trie's lookup having already run in the background: about +2 ms at the
median, +3 to +9 ms at p95, in a debug build on the emulator. The first space of a language
also maps its model.

Memory, `dumpsys meminfo` (KB). The IME runs in the app's process, so these are the app's
numbers, Whisper included:

| | fr off | fr on | en off | en on |
|---|---:|---:|---:|---:|
| TOTAL PSS | 241,051 | 261,109 | 248,806 | 227,612 |
| Private Dirty | 40,696 * | 156,348 | 151,952 | 151,960 |
| `.apk mmap` PSS | 60,114 | 73,038 | 69,505 | 46,789 |

\* 112,996 KB of that run was swapped out (TOTAL SWAP PSS), which hides the comparison.

The total moves by tens of megabytes between runs with nothing to do with the decoder: the
clean pages of the APK come and go with the page cache. Between the two English runs the whole
process's Private Dirty moved by +8 KB, the sum of opposite moves (Dalvik Heap +3,968 KB, `.art
mmap` -4,032 KB): no isolated measure of what the decoder allocates, only no visible growth. Its
models are clean pages of the APK file (`.apk mmap`), 3.6 MB per language at most, read in only
as a search touches them, and which the system can drop under pressure.

APK (`app-debug.apk`, `-Pdictus.emulatorAbi=true`): 201,031,116 bytes on develop `f36e579`,
208,353,457 on the branch, +7,322,341, of which 7,199,993 are the two stored models.

## Limits

The limits of the [protocol](../../keyboard-measurement.md#limits), and:

- **One language at a time.** Mixed French and English is where the decoder alone breaks words
  of the other language (19 French words broken by the English keyboard on iOS, against 10
  without it, dictus-ios `hex-spatial-etude.md` section 7). This bench has no mixed corpus.
- **Synthetic taps.** The offsets are drawn from a chosen Gaussian, close to Hex's sigmas, so
  the decoder's model fits them by construction. A real thumb is for the Pixel.
- **Debug build on the emulator.** The space cost is to be measured again in a release build
  on the phone.
