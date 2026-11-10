# N-gram asset provenance and reproducibility

This document inventories the exact inputs used by the French and English `NGRM` v1 assets and the Spanish and German `DTRI` v1 / `NGRM` v1 assets in the Android APK. It is an engineering provenance record, not legal advice.

## Shipping decision

The larger n-gram binaries from Dictus iOS are **not copied**. Their builder combines OpenSubtitles-derived sentences and Wikimedia project dumps, but the repository does not retain an exact input manifest or article-level attribution. OpenSubtitles data redistribution terms are not explicit enough for this project, while several Wikimedia inputs are CC BY-SA and would require a separately reviewed attribution/share-alike strategy.

### N-grams come from Tatoeba

The first Android n-gram assets were derived from the `orgtre/google-books-ngram-frequency` extracts. That source is a **top-N frequency list, not a corpus**: it publishes only the 5,000 most frequent bigrams and 3,000 most frequent trigrams per language. After token filtering, French kept 772 bigram contexts — enough for `je` and `le`, and nothing else. On a real keyboard the prediction bar was empty for almost every word typed.

N-grams are now derived from the **Tatoeba sentence exports** instead, which are released under CC BY 2.0 FR and explicitly permit commercial use and redistribution. Tatoeba ships example sentences rather than pre-counted n-grams, so the builder counts the pairs and triples itself and is no longer capped by somebody else's top-N cut. It is also the right register: everyday sentences, not book prose spanning 1500–2019.

Coverage of the previous source is not lost. 763 of the 770 Google Books French bigram contexts already appear in the Tatoeba-derived set, so the old source was retired rather than merged.

| Asset | Bigram contexts | Trigram contexts |
|---|---:|---:|
| `fr_ngrams.dict` | 772 → **28,855** | 1,144 → **134,304** |
| `en_ngrams.dict` | 1,077 → **31,520** | 1,620 → **252,388** |
| `es_ngrams.dict` | 1,029 → **23,431** | 1,443 → **89,053** |
| `de_ngrams.dict` | 656 → **27,602** | 1,112 → **131,695** |

Spell-check unigram tries (`build_spellcheck_assets.py`) are unchanged and still come from Google Books.

### The 32-bit key ceiling

NGRM v1 addresses a context by a 32-bit FNV-1a hash. At a few thousand keys collisions were exotic; at 134,000 trigram keys they are near-certain. The builder now drops **every** key involved in a collision rather than keeping the more frequent side, because a surviving key would otherwise serve its continuations for an unrelated context. Between 2 and 18 keys per language are discarded this way — under 0.01% — and the counts are recorded in the build manifest.

Raising this ceiling means a 64-bit key, which is an NGRM v2 on both Android and iOS. It is not needed at these sizes; it will be before the corpus grows another order of magnitude.

### Snapshot pinning

Tatoeba publishes one rolling export per language rather than versioned revisions, so each source is pinned by content hash against the `2026-08-30` snapshot. A hash mismatch is fatal by design: it means upstream moved, and the regenerated assets must be reviewed and re-pinned rather than changing silently under an unchanged builder.

## Pinned source inventory

### N-gram sources

Upstream: <https://downloads.tatoeba.org/exports/per_language>

Snapshot: `2026-08-30`. Licence: CC BY 2.0 FR (<https://creativecommons.org/licenses/by/2.0/fr/>), which permits commercial use and redistribution with attribution. A part of the Tatoeba corpus is additionally available under CC0 1.0.

| Language | Input | SHA-256 | Transformation scope |
|---|---|---|---|
| French | `fra/fra_sentences.tsv.bz2` | `02c6a4041697aa3bd3e96f5b245dbada4523442d4755ccbc842af86ff9b5b412` | lowercase/apostrophe normalization, punctuation- and digit-bounded segmentation, token filtering, pairs and triples counted per segment, single occurrences pruned, max 16 results/context, log-scaled scores |
| English | `eng/eng_sentences.tsv.bz2` | `d1a7d45ad531a2cf5ee3b3660aaef3bdf6e1915f247893afd40dacc3a90a8175` | same |
| Spanish | `spa/spa_sentences.tsv.bz2` | `6902d2d7d6170378003fd36b8b036abe2ce5776921995a9bea8fd94a38032bb6` | same; no authored seeds |
| German | `deu/deu_sentences.tsv.bz2` | `d94890e3f1ca3cf1fa928b4fa727119421458d11081a1afc1144faf73648847b` | same; no authored seeds |

French and English also carry the short Dictus-authored seed pairs stored in `tools/build_ngram_assets.py`.

### Spell-check sources

Upstream repository: <https://github.com/orgtre/google-books-ngram-frequency>

Pinned data revision: `8e16017414d88aa75cba348416ba877a9135889a`

Upstream license statement: CC BY 3.0 Unported. The original Google Books Version 3 compilation also states CC BY 3.0 at <https://storage.googleapis.com/books/ngrams/books/datasetsv3.html>.

| Language | Input | SHA-256 | Transformation scope |
|---|---|---|---|
| German | `ngrams/1grams_german.csv` | `28bd694f5c9bbe6e4b106fe6f731ea65acb21056bacf4e7ddea43930bb4a4982` | lowercase/NFC/apostrophe normalization, invalid/non-positive/over-32-code-unit entry filtering, Patricia compression, log-scaled frequencies |
| Spanish | `ngrams/1grams_spanish.csv` | `9b94729d538b4335b14f5776c8451d48a0b3fa23237bc5bc0da7224efa421fae` | same |

The input extracts are not bundled in Git. Their URL revision and hashes are enforced before generation.

## Exact candidate outputs

Builders: `tools/build_spellcheck_assets.py` (`c756dd619cc1743e00d2e2b9a5e8b4e1b917f0f2ace55a686054c4f1d349b50e`) and `tools/build_ngram_assets.py`. Output identity is enforced by builder tests and runtime mappings.

| Asset | Bigram keys | Trigram keys | Keys dropped to collision | Bytes | SHA-256 |
|---|---:|---:|---:|---:|---|
| `fr_ngrams.dict` | 28,855 | 134,304 | 6 | 3,509,057 | `cd8a505818a333d6d27076a7c47d1a86b838a0054b2003df408f8f4d4b191720` |
| `en_ngrams.dict` | 31,520 | 252,388 | 18 | 6,460,007 | `9d1a0a1383c0cc02f2697652d58e1fcb8cd12819b1c11aa15edb807ac8ab557d` |
| `es_ngrams.dict` | 23,431 | 89,053 | 2 | 2,353,496 | `bdaec24949ddeae8e7f4840d5e59de58fed227ecc62f4ffc78218e4c8981e4ed` |
| `de_ngrams.dict` | 27,602 | 131,695 | 2 | 3,497,225 | `889df643d4079fe8408996af53f840b58609a86aa2a205618b5b51d047cf6e6d` |

| Asset | Words | Nodes | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `de_spellcheck.dict` | 9,999 | 12,420 | 114,612 | `663b8945ac8d94b4c1da322965454e4ae52fc9adac04b4206b41b96b1199a18a` |
| `es_spellcheck.dict` | 10,000 | 13,049 | 110,654 | `39015e063ea69282ff6ac3099e852fe06b94118f41a69a680f75582899bb9ab4` |

All listed assets are below the 20 MiB target and 64 MiB reader hard limit. The four n-gram assets add about 15.6 MiB to the APK, against 0.17 MiB before. The German unigram source has 10,000 rows but emits 9,999 words because `ma` and `mA` intentionally collide after lowercase normalization. German uses lowercase rather than Unicode case-folding so `ss` and `ß` remain distinct.

## Reproduction

From the repository root:

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py'
python3 tools/build_spellcheck_assets.py --language es \
  --output trie/src/main/assets/es_spellcheck.dict
python3 tools/build_spellcheck_assets.py --language de \
  --output trie/src/main/assets/de_spellcheck.dict
for language in fr en es de; do
  python3 tools/build_ngram_assets.py --language "$language" \
    --output "trie/src/main/assets/${language}_ngrams.dict"
done
sha256sum trie/src/main/assets/{fr,en,es,de}_ngrams.dict \
  trie/src/main/assets/{es,de}_spellcheck.dict
```

The n-gram builder downloads roughly 53 MiB of compressed sentence exports on a cold cache and holds the counts in memory; English is the heaviest at about 2 GiB of resident memory. The builders use pinned URLs, verify input hashes before parsing, sort words/keys/results deterministically, drop NGRM FNV-1a key collisions, and validate generated header/section bounds. Network failure is fatal; a partial-source model is never emitted.

## Packaging and runtime lifecycle

`NativeTrie.open` copies each spell trie and matching n-gram through a temporary file in `noBackupFilesDir/trie`, verifies the pinned output hash, and atomically renames the immutable file before mmap. One off-main opener constructs the spell+n-gram candidate; the language generation is published only after that candidate returns. Superseded candidates close without publication, and replaced handles retire only after in-flight queries finish.

A missing, hash-mismatched, or malformed n-gram fails closed to a spell-only candidate. It cannot publish an n-gram from a different language and cannot break existing spelling completion/correction.

## Attribution

The n-gram data is derived from the **Tatoeba** sentence corpus, licensed under Creative Commons Attribution 2.0 France: <https://creativecommons.org/licenses/by/2.0/fr/>. Source: <https://tatoeba.org>. Dictus segmented, normalized and counted the sentences and converted the counts into its `NGRM` v1 binary format. French and English n-gram data also has Dictus-authored seed pairs; Spanish and German do not.

The spell-check data is derived from the Google Books Ngram Corpus Version 3 and the `orgtre/google-books-ngram-frequency` extracts, licensed under Creative Commons Attribution 3.0 Unported: <https://creativecommons.org/licenses/by/3.0/>. Dictus filtered and normalized the frequencies and converted them into its `DTRI` v1 format.
