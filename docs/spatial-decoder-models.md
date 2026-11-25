# Spatial decoder models (#385)

The spatial autocorrect decoder (`ime/src/main/java/dev/pivisolutions/dictus/ime/spatial/`,
ported from Hex) reads two models from `ime/src/main/assets/spatial/`. They are the iOS port's
files (getdictus/dictus-ios#1013, `DictusKeyboard/Resources/`), copied unchanged, so both
platforms decode with the same numbers. This is an engineering provenance record, not legal
advice.

| File | Bytes | SHA-256 | Built from |
|---|---:|---|---|
| `en.hextm` | 3,600,000 | `14370569c747a7efb5cd2f8e75b70d2899937c12e149671fe7a0929c5e0c4028` | Hex's own file, byte-identical to `android/app/src/main/assets/typing/en.hextm` at Hex `be2f394` |
| `fr.hextm` | 3,599,993 | `62d0cb3b6a9c6e128f6ddec5f35d6c0e20491871454fa47e2c7510a0bfaa77fc` | built by the iOS team on 2026-11-21 with Hex's script extended to French |

The format is Hex's HXTM v3: a sorted word list, a quantized cost per word and the word pairs
that tell the most, read from the APK through a memory mapping. The APK stores them
uncompressed (`noCompress += "hextm"` in `app/build.gradle.kts`), so the mapping is the file
itself and nothing is copied or inflated.

## Sources and licences

- **Code:** Hex (github.com/anomalyco/hex, commit `be2f394`), MIT, Copyright (c) 2026
  Christopher Langton. Credited in each ported file and in the app's Licences screen.
- **Counts:** Google Books Ngram Viewer, version 3 (2020-02-17), by Google, under
  [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/): word and word-pair counts summed
  over 1980-2019. The attribution is in the Licences screen.
- **English word list:** SCOWL and VarCon, sizes up to 70, by Kevin Atkinson. Its notice
  travels with the output, in the Licences screen.
- **French word list:** Grammalecte's « Classique » French dictionary, version 7.7, as
  LibreOffice ships it, under the [MPL 2.0](https://mozilla.org/MPL/2.0/). Read
  conservatively, `fr.hextm` is a file derived from it, so `fr.hextm` itself is offered under
  the MPL 2.0, as on iOS; the rest of Dictus stays MIT (MPL §3.3). The Licences screen gives
  the notice, the licence and where the source forms are: the dictionary at
  <https://github.com/LibreOffice/dictionaries/tree/master/fr_FR>, and the script that builds
  `fr.hextm` from it, `tools/typing-model/build-typing-model.py` in getdictus/dictus-ios
  (with its `README.md`: inputs, checksums, how to rebuild).

Not used: Gboard (closed), HeliBoard and OpenBoard (GPL-3.0), FUTO Keyboard (non-free licence).
