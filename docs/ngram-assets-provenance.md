# N-gram asset provenance and reproducibility

This document inventories the exact inputs used by the French and English `NGRM` v1 assets and the Spanish and German `DTRI` v1 / `NGRM` v1 assets in the Android APK. It is an engineering provenance record, not legal advice.

## Shipping decision

The larger n-gram binaries from Dictus iOS are **not copied**. Their builder combines OpenSubtitles-derived sentences and Wikimedia project dumps, but the repository does not retain an exact input manifest or article-level attribution. OpenSubtitles data redistribution terms are not explicit enough for this project, while several Wikimedia inputs are CC BY-SA and would require a separately reviewed attribution/share-alike strategy.

Android instead ships a smaller, reproducible corpus made only from:

1. the Google Books Ngram Corpus Version 3 frequency extracts published by `orgtre/google-books-ngram-frequency` under CC BY 3.0; and
2. short Dictus-authored French and English seed pairs stored directly in `tools/build_ngram_assets.py`. Spanish and German intentionally have no authored n-gram seeds.

This reduces coverage from roughly 50,000/30,000 bigram/trigram keys in the current iOS assets to the counts below. The quality trade-off is deliberate: known provenance takes priority over silently redistributing ambiguous corpus data. Runtime language registration remains a separate gate from packaging a validated asset pair.

## Pinned source inventory

Upstream repository: <https://github.com/orgtre/google-books-ngram-frequency>

Pinned data revision: `8e16017414d88aa75cba348416ba877a9135889a`

Upstream license statement: CC BY 3.0 Unported. The original Google Books Version 3 compilation also states CC BY 3.0 at <https://storage.googleapis.com/books/ngrams/books/datasetsv3.html>.

| Language | Input | SHA-256 | Transformation scope |
|---|---|---|---|
| French | `ngrams/2grams_french.csv` | `63f48cc4f9511306cdd15dacd93d2550447ba789c6a58b1ee302bbf3f562d48d` | lowercase/apostrophe normalization, token filtering, max 16 results/context, log-scaled scores |
| French | `ngrams/3grams_french.csv` | `0e1829c66c2244b567c2a18ca088daf1797770c90047855f8b04ee162aa84b90` | same; two-token context encoded with NUL separator |
| English | `ngrams/2grams_english.csv` | `9e2ae9e6149785a078e62325d468e462974ce6eb94eefe8f27b16e6d011e8ee5` | lowercase/apostrophe normalization, token filtering, max 16 results/context, log-scaled scores |
| English | `ngrams/3grams_english.csv` | `6cfdd4cc65cbd0df606d174df00274843c81eb430cfbeddce4711fb07f4e12df` | same; two-token context encoded with NUL separator |
| German | `ngrams/1grams_german.csv` | `28bd694f5c9bbe6e4b106fe6f731ea65acb21056bacf4e7ddea43930bb4a4982` | lowercase/NFC/apostrophe normalization, invalid/non-positive/over-32-code-unit entry filtering, Patricia compression, log-scaled frequencies |
| German | `ngrams/2grams_german.csv` | `a9b5e20c707337d01106014263ae6ee3b6769ce03934e2e0279b1c84614175bc` | lowercase/apostrophe normalization, token filtering, max 16 results/context, log-scaled scores; no authored seeds |
| German | `ngrams/3grams_german.csv` | `51c53108407278fb77ecd58e429c2417d61b91c154faa20e0fd9a42a810db867` | same; two-token context encoded with NUL separator; no authored seeds |
| Spanish | `ngrams/1grams_spanish.csv` | `9b94729d538b4335b14f5776c8451d48a0b3fa23237bc5bc0da7224efa421fae` | lowercase/NFC/apostrophe normalization, invalid/non-positive/over-32-code-unit entry filtering, Patricia compression, log-scaled frequencies |
| Spanish | `ngrams/2grams_spanish.csv` | `fa2b6368b08744d03d604cdacdaab1c15834dfe2f1d81c9a3ad2f102a6643774` | lowercase/apostrophe normalization, token filtering, max 16 results/context, log-scaled scores; no authored seeds |
| Spanish | `ngrams/3grams_spanish.csv` | `85b49f0521c57dd8bc2718c367c326397a06e45a0b36cdbf86b0f06cdf80258d` | same; two-token context encoded with NUL separator; no authored seeds |

The input extracts are not bundled in Git. Their URL revision and hashes are enforced before generation.

## Exact candidate outputs

Builders: `tools/build_spellcheck_assets.py` (`c756dd619cc1743e00d2e2b9a5e8b4e1b917f0f2ace55a686054c4f1d349b50e`) and `tools/build_ngram_assets.py` (`f8ab23f0f977840d44a188f8fcd4d0914bf6791da988b1f5ecda583d5051aa43`). Output identity is enforced by builder tests and runtime mappings.

| Asset | Bigram keys | Trigram keys | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `fr_ngrams.dict` | 772 | 1,144 | 38,139 | `ba8f8b3ea9ade673eeb158e4aaeebcb129647938b8cb619189393cbb1c6da712` |
| `en_ngrams.dict` | 1,077 | 1,620 | 48,296 | `eecdf421c71c39e9f7822cb48a8624efb9ae8d86c6a01e976c6fa506f2fc71bd` |
| `de_ngrams.dict` | 656 | 1,112 | 41,172 | `89219a332ed98b651abde529f9156ebf9f3e251355d9a880468bddbbfb3a303d` |
| `es_ngrams.dict` | 1,029 | 1,443 | 45,054 | `d48691aa2c8c95fff9f831f32e3ae565917f17352ea696f1fd6d655fb320750e` |

| Asset | Words | Nodes | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `de_spellcheck.dict` | 9,999 | 12,420 | 114,612 | `663b8945ac8d94b4c1da322965454e4ae52fc9adac04b4206b41b96b1199a18a` |
| `es_spellcheck.dict` | 10,000 | 13,049 | 110,654 | `39015e063ea69282ff6ac3099e852fe06b94118f41a69a680f75582899bb9ab4` |

All listed assets are below the 20 MiB target and 64 MiB reader hard limit. They are intentionally much smaller than the approximately 6 MiB iOS assets because ambiguous OpenSubtitles/Wikimedia inputs were excluded. The German unigram source has 10,000 rows but emits 9,999 words because `ma` and `mA` intentionally collide after lowercase normalization. German uses lowercase rather than Unicode case-folding so `ss` and `ß` remain distinct. The pinned extracts are a provenance-safe baseline, not evidence that the original #29 iOS-size corpus or equivalent language quality has been reached.

## Reproduction

From the repository root:

```bash
python3 -m unittest discover -s tools/tests -p 'test_*.py'
python3 tools/build_spellcheck_assets.py --language es \
  --output trie/src/main/assets/es_spellcheck.dict
python3 tools/build_spellcheck_assets.py --language de \
  --output trie/src/main/assets/de_spellcheck.dict
python3 tools/build_ngram_assets.py --language fr \
  --output trie/src/main/assets/fr_ngrams.dict
python3 tools/build_ngram_assets.py --language en \
  --output trie/src/main/assets/en_ngrams.dict
python3 tools/build_ngram_assets.py --language es \
  --output trie/src/main/assets/es_ngrams.dict
python3 tools/build_ngram_assets.py --language de \
  --output trie/src/main/assets/de_ngrams.dict
sha256sum trie/src/main/assets/{fr,en,es,de}_ngrams.dict \
  trie/src/main/assets/{es,de}_spellcheck.dict
```

The builders use commit-qualified URLs, verify input hashes before parsing, sort words/keys/results deterministically, reject invalid layouts (and NGRM FNV-1a key collisions), and validate generated header/section bounds. Network failure is fatal; a partial-source model is never emitted.

## Packaging and runtime lifecycle

`NativeTrie.open` copies each spell trie and matching n-gram through a temporary file in `noBackupFilesDir/trie`, verifies the pinned output hash, and atomically renames the immutable file before mmap. One off-main opener constructs the spell+n-gram candidate; the language generation is published only after that candidate returns. Superseded candidates close without publication, and replaced handles retire only after in-flight queries finish.

A missing, hash-mismatched, or malformed n-gram fails closed to a spell-only candidate. It cannot publish an n-gram from a different language and cannot break existing spelling completion/correction.

## Attribution

The generated data is derived from the Google Books Ngram Corpus Version 3 and the `orgtre/google-books-ngram-frequency` extracts, licensed under Creative Commons Attribution 3.0 Unported: <https://creativecommons.org/licenses/by/3.0/>. Dictus filtered and normalized the frequencies and converted them into its `DTRI` v1 and `NGRM` v1 formats. French and English NGRM data also has authored seeds; Spanish and German do not.
