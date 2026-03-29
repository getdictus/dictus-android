# Phase 6: Release Readiness - Context

**Gathered:** 2026-03-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Bilingual UI (FR/EN) with device locale detection, cross-app text insertion validation, and minimal performance sanity checks. Users can use Dictus in French or English based on their device language.

Does NOT include: Material You dynamic colors (abandoned -- Dictus brand identity takes priority), heavy performance profiling, new features.

</domain>

<decisions>
## Implementation Decisions

### Bilingual string strategy
- Default language: **English** in `values/strings.xml`, French in `values-fr/strings.xml`
- Per-module strings: app/ has onboarding+settings strings, ime/ has keyboard strings, core/ has shared strings
- String IDs: English descriptive convention (e.g. `settings_section_transcription`, `onboarding_welcome_subtitle`, `key_label_return`)
- 50+ hardcoded French strings to extract from Compose files into string resources
- Prep for future languages: add an in-app language override picker in Settings (FR/EN for v1, extensible later)
- Device locale detection drives UI language automatically

### Material You dynamic colors
- **Abandoned for v1** -- Dictus has a strong brand identity (accent blue #3D7EFF, waveform colors) that would be diluted by dynamic system colors
- The existing theme system (Sombre/Clair/Automatique with Dictus custom colors) is the final deliverable
- DSG-02 requirement updated: light theme + auto mode already shipped in Phase 5. Material You deferred to v2 only if user feedback requests it
- No code changes needed for theme system

### Cross-app validation
- Validate in 2-3 apps (WhatsApp + Gmail + Chrome) -- InputConnection.commitText() is a standard Android contract, if it works in one it works everywhere
- **Cold start transcription**: Critical test -- keyboard mic tap -> record -> transcribe WITHOUT the main app open. Verify foreground service + whisper.cpp model loading works standalone from IME only
- Basic text insertion: commitText works across messaging, email, browser search bar
- Backspace + Enter behavior: deleteSurroundingText and KEYCODE_ENTER work correctly (new line in Gmail, send action in WhatsApp, etc.)
- Special characters/accents not explicitly tested (already validated in Phase 1)

### Performance profiling
- Minimal sanity checks only: verify no memory leak in long keyboard sessions, service cleanup after transcription
- Document existing Phase 3 benchmarks (<8s for 10s audio) as v1 baseline
- No new profiling effort -- Pixel 4 test device too old for representative benchmarks
- Full profiling deferred to v1.1 with a more recent device

### Claude's Discretion
- Exact string extraction order (which files first)
- In-app language picker UI placement and design within Settings
- Memory leak detection approach (Android Studio profiler vs log-based)
- Cross-app test protocol documentation format

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` -- DSG-02 (partially done in Phase 5, Material You abandoned), DSG-03 (bilingual UI)

### Prior phase context
- `.planning/phases/05-polish-differentiators/05-CONTEXT.md` -- Light theme + auto mode decisions (DSG-02 partial), theme picker implementation
- `.planning/phases/04-model-management-onboarding/04-CONTEXT.md` -- Settings screen structure, PreferenceKeys pattern, onboarding flow (strings to extract)

### Design
- `design/dictus-android-design.pen` -- UI mockups (use Pencil MCP tools to view)
- `design/README.md` -- Frame IDs and descriptions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `core/src/main/res/values/strings.xml`: Minimal strings.xml exists -- extend with all shared strings
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt`: DataStore keys pattern -- add LANGUAGE_UI key for in-app language override
- `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusTheme.kt`: ThemeMode enum (DARK/LIGHT/AUTO) -- no changes needed
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt`: Settings UI with SettingPickerRow pattern -- reuse for language picker

### Established Patterns
- DataStore preferences: SettingsViewModel reads/writes via `dataStore.edit{}` + `dataStore.data.map{}`
- String literals hardcoded in 10+ Compose files (onboarding, settings, home, models, recording screens)
- Per-module resource pattern: core/ already has strings.xml, app/ and ime/ would each get their own

### Integration Points
- `app/src/main/java/dev/pivisolutions/dictus/onboarding/`: 7 onboarding screens with hardcoded French strings
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt`: Settings sections with hardcoded French labels
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt`: commitText(), deleteSurroundingText(), sendReturnKey() -- validation targets
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt`: Key labels (mostly symbols, minimal string extraction needed)

</code_context>

<specifics>
## Specific Ideas

- Pierre wants to test cold start scenario specifically: IME triggers dictation without main app being open -- does foreground service + whisper.cpp model loading work in that context?
- Dictus brand identity (accent blue, waveform colors) is non-negotiable -- no dynamic/system colors
- Language picker should be extensible for future languages (Spanish, German, etc.) but v1 ships FR/EN only

</specifics>

<deferred>
## Deferred Ideas

- **Material You dynamic colors** -- Abandoned for v1. Revisit in v2 only if user feedback specifically requests it. Dictus brand identity takes priority.
- **Full performance profiling** -- Deferred to v1.1 with a more representative test device. Current Pixel 4 too old for meaningful benchmarks.
- **Multi-device testing** (Samsung, Xiaomi) -- Out of scope per PROJECT.md, planned for v1.1.

</deferred>

---

*Phase: 06-release-readiness*
*Context gathered: 2026-03-29*
