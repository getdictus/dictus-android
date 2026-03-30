# Phase 8: Text Prediction - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the stub suggestion engine with a production binary dictionary ranker. Users see real, ranked FR+EN word suggestions while typing. The SuggestionBar UI and DictusImeService wiring are already done (Phase 5) — this phase replaces `StubSuggestionEngine` with a real implementation behind the same `SuggestionEngine` interface.

</domain>

<decisions>
## Implementation Decisions

### Dictionary source & format
- Use AOSP `.dict` binary dictionary files (Apache 2.0 license)
- Bundle FR and EN dictionaries in the APK (~5 MB total) — no download step
- Engine selects dictionary based on app's current language setting from DataStore (FR mode = FR dict, EN mode = EN dict)
- Keep the existing `SuggestionEngine` interface unchanged (`getSuggestions(input, maxResults)`) — engine reads language preference internally
- Drop-in replacement: `StubSuggestionEngine` replaced by production implementation, zero UI/wiring changes

### Ranking strategy
- Prefix matching + AOSP frequency ranking + personal dictionary boost
- Words matching the typed prefix are ranked by their frequency in the AOSP dictionary
- Words in the personal dictionary get a frequency boost over base dictionary words
- No bigram/contextual ranking in v1.1 (deferred to v2 as SUGG-04)
- Gboard-style 3-slot layout: LEFT = raw typed word, CENTER = best suggestion (bold), RIGHT = 2nd suggestion — current SuggestionBar unchanged

### Personal dictionary (SUGG-03)
- A word is "learned" after being typed 2 times (avoids typo noise, fast enough to be useful)
- Selecting a suggestion in the bar counts as "typing" the word (accumulates toward threshold and personal boost)
- Stored in DataStore as a single global `Set<String>` — not separated by language
- Invisible to user in v1.1 — no management screen (deferred)
- Persists across app restarts (DataStore persistence)

### Post-dictation behavior
- After voice transcription inserts text, clear the suggestion bar — suggestions resume only when user types on keyboard
- Apostrophe words treated as single words: "aujourd'hui", "l'homme", "n'est-ce" are dictionary entries, apostrophe does not split the current word
- Accent-insensitive matching: typing "resume" suggests "resume", typing "deja" suggests "deja" — critical for French users who type fast without accents

### Claude's Discretion
- AOSP `.dict` binary parser implementation (Kotlin or existing library)
- Exact personal boost multiplier/weight
- Background thread strategy for dictionary loading (`dict-worker` thread per success criteria)
- Dictionary file placement in APK assets
- Internal word counting mechanism for the 2-tap learning threshold

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Suggestion engine (existing code)
- `ime/src/main/java/dev/pivisolutions/dictus/ime/suggestion/SuggestionEngine.kt` — Interface contract + StubSuggestionEngine to replace
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/SuggestionBar.kt` — Gboard-style 3-slot UI (no changes needed)
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` — Wiring: onUpdateSelection extracts currentWord, feeds to engine, collects suggestions via StateFlow
- `ime/src/test/java/dev/pivisolutions/dictus/ime/ui/SuggestionBarTest.kt` — Existing UI tests

### Preferences & language
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — DataStore keys (language preference used by engine, personal dict storage)

### Requirements
- `.planning/REQUIREMENTS.md` — SUGG-01, SUGG-02, SUGG-03 requirements
- `.planning/ROADMAP.md` — Phase 8 success criteria (3 candidates, tap-to-replace, personal dict, microbenchmark)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SuggestionEngine` interface: Drop-in contract — production engine implements this, no wiring changes
- `SuggestionBar` composable: 3-slot Gboard layout, already wired in KeyboardScreen
- `DictusImeService.onUpdateSelection`: Already extracts `currentWord` from InputConnection and feeds to engine via `_currentWord` / `_suggestions` StateFlows
- `PreferenceKeys` / DataStore: Established pattern for reading preferences in IME via `EntryPointAccessors`

### Established Patterns
- Hilt DI with `EntryPointAccessors` for IME — new engine dependencies follow this pattern
- DataStore for preferences — personal dictionary can use same DataStore instance
- StateFlow for reactive data — `_suggestions` MutableStateFlow already wired to SuggestionBar

### Integration Points
- `DictusImeService` line 87: `private val suggestionEngine: SuggestionEngine = StubSuggestionEngine()` — replace with production engine (likely via Hilt)
- `onUpdateSelection` (line ~183): Already calls `suggestionEngine.getSuggestions(currentWord)` — no change needed
- `onSuggestionSelected` callback (line ~297): Already replaces word + adds space — need to add personal dictionary counting here
- Post-dictation: Need to clear `_suggestions` and `_currentWord` when transcription text is committed

</code_context>

<specifics>
## Specific Ideas

- User is a beginner in Kotlin/Android — explain the "why" behind dictionary parsing and threading choices
- User specifically asked for a GitHub issue to track bigram ranking (SUGG-04) as an enhancement
- Accent-insensitive matching is important for French users — this was an explicit concern during discussion

</specifics>

<deferred>
## Deferred Ideas

- **Bigram/contextual ranking (SUGG-04)** — v2 enhancement. User wants a GitHub issue created with "enhancement" label to track this.
- **Personal dictionary management screen** — UI to view/delete learned words. Not needed for v1.1 beta.
- **Language-separated personal dictionaries** — Could split FR/EN personal words later if mixed-language results become a problem.

</deferred>

---

*Phase: 08-text-prediction*
*Context gathered: 2026-03-30*
