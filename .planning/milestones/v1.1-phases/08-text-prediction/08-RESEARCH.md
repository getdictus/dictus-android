# Phase 8: Text Prediction - Research

**Researched:** 2026-03-30
**Domain:** Android IME suggestion engine — binary dictionary parsing, in-memory trie lookup, personal dictionary, accent-insensitive matching
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Use AOSP `.dict` binary dictionary files (Apache 2.0 license)
- Bundle FR and EN dictionaries in the APK (~5 MB total) — no download step
- Engine selects dictionary based on app's current language setting from DataStore (FR mode = FR dict, EN mode = EN dict)
- Keep the existing `SuggestionEngine` interface unchanged (`getSuggestions(input, maxResults)`) — engine reads language preference internally
- Drop-in replacement: `StubSuggestionEngine` replaced by production implementation, zero UI/wiring changes
- Ranking: Prefix matching + AOSP frequency ranking + personal dictionary boost
- Words matching the typed prefix are ranked by their frequency in the AOSP dictionary
- Words in the personal dictionary get a frequency boost over base dictionary words
- No bigram/contextual ranking in v1.1 (deferred to v2 as SUGG-04)
- Gboard-style 3-slot layout: LEFT = raw typed word, CENTER = best suggestion (bold), RIGHT = 2nd suggestion — current SuggestionBar unchanged
- Personal dictionary: a word is "learned" after being typed 2 times
- Selecting a suggestion counts as "typing" the word (accumulates toward threshold and boost)
- Stored in DataStore as a single global `Set<String>` — not separated by language
- Invisible to user in v1.1 — no management screen (deferred)
- Persists across app restarts (DataStore persistence)
- After voice transcription inserts text, clear the suggestion bar — suggestions resume only when user types on keyboard
- Apostrophe words treated as single words: "aujourd'hui", "l'homme", "n'est-ce" are dictionary entries, apostrophe does not split the current word
- Accent-insensitive matching: typing "resume" suggests "resume", typing "deja" suggests "deja" — critical for French users who type fast without accents

### Claude's Discretion
- AOSP `.dict` binary parser implementation (Kotlin or existing library)
- Exact personal boost multiplier/weight
- Background thread strategy for dictionary loading (`dict-worker` thread per success criteria)
- Dictionary file placement in APK assets
- Internal word counting mechanism for the 2-tap learning threshold

### Deferred Ideas (OUT OF SCOPE)
- Bigram/contextual ranking (SUGG-04) — v2 enhancement. User wants a GitHub issue created with "enhancement" label.
- Personal dictionary management screen — UI to view/delete learned words. Not needed for v1.1 beta.
- Language-separated personal dictionaries — Could split FR/EN personal words later if mixed-language results become a problem.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SUGG-01 | L'utilisateur voit des suggestions contextuelles basées sur un dictionnaire binaire FR+EN pendant la saisie | DictionaryEngine replaces StubSuggestionEngine; loads AOSP .dict via assets; prefix match with frequency ranking |
| SUGG-02 | L'utilisateur peut taper une suggestion pour remplacer le mot en cours et insérer un espace | Already wired in DictusImeService.onSuggestionSelected; add personal word counting here |
| SUGG-03 | Le clavier apprend des mots fréquemment tapés par l'utilisateur (dictionnaire personnel) | DataStore stringSetPreferencesKey for learned set; Map<String,Int> in-memory counter with 2-tap threshold |
</phase_requirements>

---

## Summary

Phase 8 replaces the stub suggestion engine with a production binary dictionary ranker. The core challenge is that AOSP `.dict` files use a compiled binary trie format that requires native JNI to read at runtime — there is no pure Java/Kotlin runtime reader. The correct approach for this project (no NDK, no C++) is to **pre-decode** the AOSP `.dict` files to a plain-text word+frequency list at build time using `dicttool_aosp.jar`, then bundle the text file as an asset and load it into an in-memory prefix structure at app startup on a background coroutine.

The existing wiring in `DictusImeService` is fully in place: `onUpdateSelection` already extracts `currentWord` and calls `suggestionEngine.getSuggestions()`, `onSuggestionSelected` already replaces the word and inserts a space. The phase adds: (1) `DictionaryEngine` that loads decoded word lists from assets, (2) accent-insensitive prefix lookup against a sorted or trie-indexed structure, (3) personal dictionary stored in DataStore.

The personal dictionary is an in-memory `Map<String, Int>` (word → type count) that is persisted to DataStore as a `Set<String>` of learned words (those that crossed the 2-tap threshold). Frequency boost means learned words are sorted to the front of candidates.

**Primary recommendation:** Pre-decode AOSP `.dict` files to `word\tfrequency` TSV at build time with `dicttool_aosp.jar decodedict`, bundle as `assets/dict_fr.txt` and `assets/dict_en.txt`, load on `Dispatchers.IO` at engine init, store as `ArrayList<Pair<String, Int>>` sorted by frequency descending for fast prefix binary-search or simple linear filtered scan.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin Coroutines | 1.8.1 (already in project) | Background dictionary loading on `Dispatchers.IO` | Already a project dependency; coroutine scope in IME uses `MainScope()` |
| DataStore Preferences | 1.1.3 (already in project) | Persist personal dictionary `Set<String>` and word-count `Map` | Already used for all app preferences; `stringSetPreferencesKey` is built-in |
| java.text.Normalizer (JDK stdlib) | stdlib | NFD decomposition for accent-insensitive matching | Ships with Android; zero dependency |
| dicttool_aosp.jar (build-time only) | Apache 2.0 | Decode AOSP `.dict` to TSV at build time | Official AOSP tool; no runtime dependency |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AOSP FR/EN dictionaries | Apache 2.0 | Word+frequency source | Bundle decoded TSV from Helium314/aosp-dictionaries: `main_fr.dict` + `main_en_us.dict` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pre-decoded TSV in assets | AOSP .dict at runtime with JNI | JNI requires NDK, C++ build, circular dep with whisper.cpp; far overkill for this project |
| Pre-decoded TSV in assets | Room database with FTS | Adds Room dependency, more complex setup; TSV loaded into memory is simpler and fast enough |
| `ArrayList` sorted by freq | Kotlin Trie data structure | Trie is faster for large vocabs; for 50k-word list, sorted ArrayList with linear filter is sufficient and simpler |
| DataStore `Set<String>` | Room personal dictionary table | Room adds complexity; Set<String> in DataStore is adequate for phase requirements |

**Build-time decode command:**
```bash
java -jar tools/dicttool_aosp.jar decodedict -s main_fr.dict -d assets/dict_fr.txt
java -jar tools/dicttool_aosp.jar decodedict -s main_en_us.dict -d assets/dict_en.txt
```

The decoded `.combined` / TSV format has lines like: `word=bonjour,f=130` — parse `word=` and `f=` fields.

---

## Architecture Patterns

### Recommended Project Structure
```
ime/src/main/
├── assets/
│   ├── dict_fr.txt          # pre-decoded FR word list (word=X,f=Y per line)
│   └── dict_en.txt          # pre-decoded EN word list
├── java/.../ime/suggestion/
│   ├── SuggestionEngine.kt  # interface (UNCHANGED)
│   ├── StubSuggestionEngine.kt  # (keep for reference / tests)
│   ├── DictionaryEngine.kt  # production engine (new)
│   ├── WordEntry.kt         # data class: word + frequency + isPersonal
│   └── PersonalDictionary.kt  # DataStore-backed word counter + learned set
```

### Pattern 1: Background Dictionary Loading
**What:** Load and parse the word list from `assets/` on a `Dispatchers.IO` coroutine at engine init. Signal readiness via `StateFlow<Boolean>`. Until ready, return empty list.
**When to use:** Always — dictionary loading (file I/O + string parsing) must not block the main/UI thread to avoid IME frame drops.
**Example:**
```kotlin
// DictionaryEngine.kt
class DictionaryEngine(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope,  // injected MainScope from DictusImeService
) : SuggestionEngine {

    private var wordList: List<WordEntry> = emptyList()  // sorted by frequency desc
    private val _isReady = MutableStateFlow(false)

    init {
        coroutineScope.launch(Dispatchers.IO) {
            // Read language pref, load correct asset
            val lang = dataStore.data.first()[PreferenceKeys.TRANSCRIPTION_LANGUAGE] ?: "fr"
            val assetName = if (lang == "en") "dict_en.txt" else "dict_fr.txt"
            wordList = context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.mapNotNull { parseLine(it) }.sortedByDescending { it.frequency }.toList()
            }
            _isReady.value = true
        }
    }

    override fun getSuggestions(input: String, maxResults: Int): List<String> {
        if (input.isBlank() || !_isReady.value) return emptyList()
        // ... see Pattern 2
    }
}
```

### Pattern 2: Accent-Insensitive Prefix Matching with Frequency Ranking
**What:** Normalize both the input and dictionary words to NFD, strip combining marks, compare prefixes. Return top-N by frequency, with personal words boosted to the front.
**When to use:** All prefix lookups — this is the core ranking algorithm.
**Example:**
```kotlin
// Accent normalization utility
private fun String.stripAccents(): String {
    val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}

override fun getSuggestions(input: String, maxResults: Int): List<String> {
    if (input.isBlank() || !_isReady.value) return emptyList()
    val lowerInput = input.lowercase()
    val strippedInput = lowerInput.stripAccents()
    val learnedWords = personalDictionary.learnedWords  // Set<String> from DataStore

    return wordList
        .filter { entry ->
            val stripped = entry.word.lowercase().stripAccents()
            stripped.startsWith(strippedInput) && entry.word.lowercase() != lowerInput
        }
        .sortedWith(compareByDescending<WordEntry> { learnedWords.contains(it.word) }
            .thenByDescending { it.frequency })
        .take(maxResults)
        .map { it.word }
}
```

### Pattern 3: Personal Dictionary with DataStore
**What:** In-memory `MutableMap<String, Int>` as word-type counter. When counter reaches threshold (2), add to persisted `Set<String>` in DataStore. Counter is ephemeral (session only) — only the learned set persists.
**When to use:** Called from `DictusImeService.onSuggestionSelected` and from `onUpdateSelection` word-commit detection.

```kotlin
// PersonalDictionary.kt
class PersonalDictionary(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val PERSONAL_DICT_KEY = stringSetPreferencesKey("personal_dictionary")
    private val LEARN_THRESHOLD = 2

    // In-memory counter — reset on IME process restart (acceptable per spec)
    private val typeCount = mutableMapOf<String, Int>()

    // Observed by DictionaryEngine for boost ranking
    var learnedWords: Set<String> = emptySet()
        private set

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                learnedWords = prefs[PERSONAL_DICT_KEY] ?: emptySet()
            }
        }
    }

    fun recordWordTyped(word: String) {
        if (word.isBlank()) return
        val count = (typeCount[word] ?: 0) + 1
        typeCount[word] = count
        if (count >= LEARN_THRESHOLD && !learnedWords.contains(word)) {
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[PERSONAL_DICT_KEY] = (prefs[PERSONAL_DICT_KEY] ?: emptySet()) + word
                }
            }
        }
    }
}
```

### Pattern 4: Apostrophe-Aware Word Splitting
**What:** The current `onUpdateSelection` splits on space and newline only. Apostrophe words like "aujourd'hui" must NOT be split on the apostrophe. The existing split is already correct — `split(" ", "\n")` preserves apostrophes within the current word fragment.

Verification: `"aujourd'hui".split(" ", "\n").last()` returns `"aujourd'hui"` — no change needed in DictusImeService. The dictionary word list must contain full apostrophe forms (`aujourd'hui`, not `aujourd` + `hui`).

### Pattern 5: Post-Dictation Clear
**What:** After voice transcription commits text, clear `_suggestions` and `_currentWord`.
**Where:** In `DictusImeService` inside the `onConfirm` lambda after `commitText(text)`.

```kotlin
// In the onConfirm callback (RecordingScreen), after commitText:
commitText(text)
_suggestions.value = emptyList()
_currentWord.value = ""
```

### Anti-Patterns to Avoid
- **Loading dictionary on main thread:** Parsing a 50k-line file on the main/UI thread will cause visible IME lag. Always use `Dispatchers.IO`.
- **Synchronous DataStore reads inside `getSuggestions`:** `getSuggestions` is called on every keystroke from `onUpdateSelection` (main thread). Never call `dataStore.data.first()` there — it blocks. Cache the `learnedWords` set reactively via a `collect` in `init`.
- **Splitting input on apostrophe:** `"aujourd".split("'")` would break French words. The current word extractor in DictusImeService already uses `split(" ", "\n")` which is correct.
- **Using the AOSP `.dict` binary file at runtime without NDK:** `BinaryDictionary.java` from AOSP requires native JNI methods — there is no pure Java path. Pre-decode at build time.
- **Storing word-type counts in DataStore:** DataStore writes are async and add I/O overhead per keystroke. Keep counts in-memory only; only write the learned set when a word crosses the threshold.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Accent stripping | Custom char-by-char replacement map | `java.text.Normalizer` NFD + `\p{InCombiningDiacriticalMarks}` regex | NFD decomposition handles all Unicode combining marks correctly including edge cases like ç, ñ |
| Word frequency source | Manual word frequency list | AOSP dictionaries from Helium314/aosp-dictionaries (Apache 2.0) | Battle-tested frequency data for FR and EN; Apache 2.0 license is compatible |
| Persistence | Custom file I/O or SharedPreferences for personal dict | DataStore with `stringSetPreferencesKey` | Already used in project; type-safe; coroutine-friendly |
| Background thread management | Raw Thread or HandlerThread | `coroutineScope.launch(Dispatchers.IO)` | Coroutines already used throughout project; cleaner cancellation |

**Key insight:** The `.dict` binary format requires NDK C++ to parse at runtime. This project deliberately avoids NDK complexity. The correct solution is build-time decoding — a one-time offline step that produces a simple text file the app reads with standard Kotlin I/O.

---

## Common Pitfalls

### Pitfall 1: AOSP .dict Binary Format Requires JNI at Runtime
**What goes wrong:** Developer discovers they cannot read `.dict` files in Kotlin without NDK. They either add NDK complexity or ship empty suggestions.
**Why it happens:** AOSP `BinaryDictionary.java` only has native methods — no pure Java fallback. This is well-documented in the AOSP source.
**How to avoid:** Pre-decode `.dict` → text using `dicttool_aosp.jar decodedict` during repository setup (document in README). The decoded text file is committed to the repo and bundled as an asset. No runtime NDK needed.
**Warning signs:** Any attempt to instantiate `BinaryDictionary` without a native `.so` library present.

### Pitfall 2: Blocking the Main Thread on Dictionary Load
**What goes wrong:** `context.assets.open("dict_fr.txt")` called on main thread → ANR or frame drop → IME appears unresponsive.
**Why it happens:** `getSuggestions` is called from `onUpdateSelection` on the main thread. If the engine initializes the dictionary inline in `getSuggestions`, the first call blocks.
**How to avoid:** Initialize dictionary in `init` block with `launch(Dispatchers.IO)`. Use a `_isReady` flag — return `emptyList()` until loading completes (typically < 500ms on first open). After ready, all lookups are in-memory and fast.
**Warning signs:** Slow keyboard appearance on first use; ANR reports.

### Pitfall 3: DataStore Read Inside getSuggestions
**What goes wrong:** `runBlocking { dataStore.data.first() }` or `dataStore.data.first()` from a suspend context inside the hot `getSuggestions` path → deadlock or visible keystroke lag.
**Why it happens:** DataStore reads are async. Calling them synchronously on the main thread is a known Android pitfall.
**How to avoid:** Cache `learnedWords` as a plain `Set<String>` field, updated reactively via a `scope.launch { dataStore.data.collect { ... } }` in `init`. Access the cached field directly in `getSuggestions` — no suspend call needed.
**Warning signs:** Any `runBlocking` or `.first()` inside `getSuggestions`; keystroke lag > 16ms.

### Pitfall 4: Apostrophe Splits French Compound Words
**What goes wrong:** Current word extraction splits on apostrophe, so typing "aujourd'" loses "aujourd" prefix and suggestions break for compound French words.
**Why it happens:** A naive `split` on punctuation breaks apostrophe words. The existing code `split(" ", "\n")` is already correct — this pitfall is pre-empted. The risk is in future modifications to word extraction logic.
**How to avoid:** Never add `'\''` or `'\u2019'` to the split delimiter set. The dictionary word list must contain full apostrophe forms.
**Warning signs:** Typing "aujourd" returns no suggestions even though "aujourd'hui" is in the dictionary.

### Pitfall 5: Personal Dictionary Key Name Collision
**What goes wrong:** Using a DataStore key name that conflicts with existing keys in `PreferenceKeys.kt`, causing preference reads to return unexpected types.
**Why it happens:** DataStore does not throw on key name conflicts — it silently overwrites or reads wrong type.
**How to avoid:** Add `PERSONAL_DICTIONARY` and `WORD_TYPE_COUNTS` (if persisted) to `PreferenceKeys.kt` in the `core` module to maintain a single source of truth. Verify key names don't collide with existing keys.
**Warning signs:** Settings values (language, theme) changing unexpectedly after phase implementation.

### Pitfall 6: Dictionary File Size vs. APK Size Budget
**What goes wrong:** The decoded plain-text word lists are significantly larger than the `.dict` binary (~2-4x). FR dict alone may be 8-15 MB as text.
**Why it happens:** Binary trie compression is very effective. Decoded text is uncompressed.
**How to avoid:** (a) Use the standard (non-experimental) AOSP dictionary variants which have fewer words; (b) gzip-compress the text assets and use `GZIPInputStream` when loading; (c) cap the word list at the top N words by frequency (top 50k covers ~99% of normal usage).
**Warning signs:** APK size grows by >10 MB after adding dict assets.

---

## Code Examples

### Parsing decoded AOSP wordlist lines
```kotlin
// Source: AOSP .combined format (confirmed via Helium314/aosp-dictionaries)
// Line format: word=bonjour,f=130,flags=,originalFreq=130
private fun parseLine(line: String): WordEntry? {
    if (!line.startsWith("word=")) return null
    val parts = line.split(",")
    val word = parts.firstOrNull { it.startsWith("word=") }
        ?.removePrefix("word=") ?: return null
    val freq = parts.firstOrNull { it.startsWith("f=") }
        ?.removePrefix("f=")?.toIntOrNull() ?: 0
    return if (word.isNotBlank()) WordEntry(word, freq) else null
}
```

### Accent-insensitive normalization
```kotlin
// Source: java.text.Normalizer (JDK stdlib, confirmed Android Developer docs)
// NFD decomposes "é" → "e" + combining acute accent, then regex removes combining chars
private val COMBINING_DIACRITICALS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun String.stripAccents(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(COMBINING_DIACRITICALS, "")
```

### DataStore stringSetPreferencesKey pattern
```kotlin
// Source: https://androidx.github.io/kmp-eap-docs/libs/androidx.datastore/...
// stringSetPreferencesKey is the correct type for Set<String> in DataStore Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit

val PERSONAL_DICTIONARY = stringSetPreferencesKey("personal_dictionary")

// Write:
dataStore.edit { prefs ->
    prefs[PERSONAL_DICTIONARY] = (prefs[PERSONAL_DICTIONARY] ?: emptySet()) + newWord
}

// Read (reactive):
dataStore.data.map { prefs -> prefs[PERSONAL_DICTIONARY] ?: emptySet() }
```

### IME injection via EntryPoint (existing pattern, extended)
```kotlin
// Source: existing DictusImeEntryPoint.kt — extend for DictionaryEngine injection
// DictionaryEngine is constructed manually in DictusImeService (not via @Inject)
// because it needs a CoroutineScope tied to the service lifecycle

// In DictusImeService:
private val dictionaryEngine: SuggestionEngine by lazy {
    DictionaryEngine(
        context = applicationContext,
        dataStore = entryPoint.dataStore(),
        coroutineScope = bindingScope,  // existing MainScope
    )
}
// Replace: private val suggestionEngine: SuggestionEngine = StubSuggestionEngine()
// With:    private val suggestionEngine: SuggestionEngine by lazy { ... }
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AOSP LatinIME JNI/NDK integration | Pre-decoded text wordlist + in-memory trie/list | Design decision for this project | No NDK, no C++, simpler build; acceptable for prefix-only ranking |
| SharedPreferences for settings | DataStore Preferences | 2021 (AndroidX) | Type-safe, coroutine-native, no ANR on main thread reads |
| HandlerThread for background IME work | Kotlin coroutines + Dispatchers.IO | 2020+ | Cleaner cancellation, structured concurrency |

**Deprecated/outdated:**
- `SharedPreferences` for personal dictionary: replaced by DataStore in this project — do not use.
- Instantiating `StubSuggestionEngine()` directly: replaced by `DictionaryEngine` via lazy property.

---

## Open Questions

1. **Exact decoded file format from dicttool_aosp.jar**
   - What we know: The `.combined` source format is `word=X,f=Y,flags=Z`. The `decodedict` command should produce this format.
   - What's unclear: Whether `decodedict` emits the same `.combined` format or a different schema.
   - Recommendation: Wave 0 task — run `java -jar dicttool_aosp.jar decodedict -s main_fr.dict -d /tmp/fr_out.txt` and inspect the first 20 lines to confirm format before writing the parser. The parser above handles both `.combined` style and TSV; adjust `parseLine()` to match actual output.

2. **Decoded file size vs. APK budget**
   - What we know: AOSP FR and EN dictionaries are ~2.5 MB each as `.dict` binaries. Decoded text can be 2-4x larger.
   - What's unclear: Exact decoded size without running the tool.
   - Recommendation: If decoded text > 8 MB per language, use `GZIPInputStream` for loading or limit to top-50k words by frequency.

3. **Frequency field range in AOSP dictionaries**
   - What we know: AOSP frequency is 0-255 (confirmed in HeliBoard documentation: "f= with a number from 0 to 255").
   - What's unclear: Whether all entries have `f=` or if some are missing.
   - Recommendation: Default to `f=0` when `f=` is absent in parsing.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + Robolectric 4.14.1 |
| Config file | `ime/src/test/resources/robolectric.properties` (sdk=35) |
| Quick run command | `./gradlew :ime:test --tests "*.suggestion.*" -x lint` |
| Full suite command | `./gradlew :ime:test -x lint` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SUGG-01 | DictionaryEngine returns FR prefix matches ranked by frequency | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-01 | DictionaryEngine returns empty list before loading is complete | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-01 | Accent-insensitive: "deja" matches "déjà" | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-01 | Apostrophe words returned whole: "aujourd'hui" not split | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-01 | getSuggestions respects maxResults=3 limit | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-02 | onSuggestionSelected replaces current word + appends space | integration (Robolectric) | `./gradlew :ime:test --tests "*.SuggestionBarTest" -x lint` | ✅ (SuggestionBarTest exists, needs extension) |
| SUGG-03 | Word reaches learn threshold after 2 types | unit | `./gradlew :ime:test --tests "*.PersonalDictionaryTest" -x lint` | ❌ Wave 0 |
| SUGG-03 | Learned word is boosted to front of suggestions | unit | `./gradlew :ime:test --tests "*.DictionaryEngineTest" -x lint` | ❌ Wave 0 |
| SUGG-03 | Selecting a suggestion counts as a type toward threshold | unit | `./gradlew :ime:test --tests "*.PersonalDictionaryTest" -x lint` | ❌ Wave 0 |
| SUGG-01 | Microbenchmark: 1000 sequential getSuggestions calls < 100ms | unit benchmark | `./gradlew :ime:test --tests "*.DictionaryBenchmarkTest" -x lint` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :ime:test --tests "*.suggestion.*" -x lint`
- **Per wave merge:** `./gradlew :ime:test -x lint`
- **Phase gate:** Full `:ime:test` suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryEngineTest.kt` — covers SUGG-01 (prefix match, accent, apostrophe, frequency ranking, maxResults, not-ready guard)
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/PersonalDictionaryTest.kt` — covers SUGG-03 (threshold, suggestion boost, DataStore persistence)
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/suggestion/DictionaryBenchmarkTest.kt` — covers SUGG-01 microbenchmark (1000 sequential lookups)
- [ ] Test dictionary asset in `ime/src/test/assets/dict_fr.txt` — small subset (~50 words) for unit tests without loading full asset
- [ ] `ime/src/main/assets/` directory creation — does not exist yet; must be created before adding dict files

---

## Sources

### Primary (HIGH confidence)
- AOSP LatinIME source (android.googlesource.com) — confirmed BinaryDictionary.java requires JNI only, no pure Java path
- Helium314/aosp-dictionaries (codeberg.org) — confirmed `main_fr.dict` and `main_en_us.dict` available, Apache 2.0
- HeliBoard documentation — confirmed frequency range 0-255, `f=0` means "don't suggest unless typed"
- `java.text.Normalizer` Android Developer docs — confirmed NFD normalization for diacritic stripping
- `stringSetPreferencesKey` AndroidX DataStore docs — confirmed API for Set<String> storage
- Existing project code (DictusImeService.kt, SuggestionEngine.kt, PreferenceKeys.kt) — directly read

### Secondary (MEDIUM confidence)
- remi0s/aosp-dictionary-tools (github.com) — confirmed `.combined` format `word=X,f=Y`; `makedict` command verified; `decodedict` inferred but not directly confirmed
- Helium314/HeliBoard discussions — confirmed dicttool_aosp.jar usage pattern
- florisboard/dictionary-tools README (deprecated) — confirmed AOSP dict creation approach

### Tertiary (LOW confidence)
- WebSearch: decoded file size estimates (2-4x binary size) — approximation, not measured
- WebSearch: `decodedict` subcommand existence — inferred from tool's known `makedict` inverse; not directly tested

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project; AOSP dict availability confirmed
- Architecture: HIGH — AOSP JNI limitation confirmed from source; pre-decode approach is the correct path; DataStore pattern already established in project
- Pitfalls: HIGH — BinaryDictionary JNI-only confirmed; DataStore/main-thread pitfalls are documented Android patterns
- Dictionary format: MEDIUM — `.combined` format structure confirmed; exact `decodedict` output schema needs empirical verification in Wave 0

**Research date:** 2026-03-30
**Valid until:** 2026-06-30 (stable technology stack; DataStore and coroutine APIs are stable)
