# Phase 1: Core Foundation + Keyboard Shell - Context

**Gathered:** 2026-03-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Multi-module Android project with a Compose-based custom keyboard (IME) rendering full AZERTY/QWERTY layouts, text insertion via InputConnection, branded Dictus dark theme, and structured logging via Timber. No audio, no dictation, no whisper — just a working keyboard that inserts text into any app.

</domain>

<decisions>
## Implementation Decisions

### Keyboard layout & keys
- Use an existing open-source Android keyboard project as a foundation/reference (e.g., FlorisBoard, Simple Keyboard, AOSP LatinIME) — avoid reinventing layout logic, long-press handling, key sizing
- Accented characters via popup bubble on long-press (standard Android convention, like Gboard)
- Mic button in a dedicated row below the keyboard (like iOS Dictus) — mic is non-functional in Phase 1, just a placeholder
- Key sizes match Gboard proportions for familiarity
- Minimum layers: Letters (AZERTY/QWERTY with shift/caps) + Numbers/Symbols (?123). Take whatever layers the open-source base provides
- Keyboard switcher via standard Android method (long-press spacebar or dedicated button triggers InputMethodManager.showInputMethodPicker())

### Theme & visual style
- Dictus branded dark theme with exact iOS color tokens (#0A1628 bg, #3D7EFF accent, #161C2C surface, etc.) applied via Material 3 surfaces and elevation — looks like Dictus, feels native Android
- System default font (Roboto) for keyboard keys — no custom font
- Gboard-style rounded rectangle keys with subtle elevation/shadow on dark background

### Project & module setup
- 3 modules from the start: `app`, `ime`, `core` (whisper module added in Phase 3)
- Package name: `dev.pivisolutions.dictus`
- Dependency injection: Hilt (note: IME requires EntryPointAccessors, not @AndroidEntryPoint)
- Kotlin + Jetpack Compose
- Gradle Kotlin DSL
- Min SDK 29 (Android 10)

### IME integration
- Keyboard height matches Gboard proportions (~40% of screen) + mic button row below
- Standard Android keyboard switcher behavior

### Dev environment setup
- Plan includes a setup task: install Android Studio, configure SDK 29+, create Pixel 4 emulator
- Testing strategy: emulator for fast iteration + physical Pixel 4 for real IME/input testing
- Physical device: Pixel 4 running LineageOS (not stock Android) — fully compatible, needs USB debugging enabled
- First time Android native dev — plans should include clear setup instructions

### Claude's Discretion
- Number row (top persistent number row) — Claude decides based on open-source keyboard base default
- Choice of which open-source keyboard to use as reference/base — Claude researches and picks best fit
- Exact keyboard height in dp
- Key spacing and padding values
- Timber logging configuration details
- Hilt module organization

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project requirements
- `PRD.md` — Full product requirements document with architecture, feature parity matrix, color palette, project structure proposal, and iOS-to-Android mapping glossary
- `.planning/REQUIREMENTS.md` — Phase-mapped requirements (KBD-01, KBD-02, KBD-03, KBD-06, DSG-01, APP-06 for this phase)
- `.planning/PROJECT.md` — Key decisions and constraints (Hilt DI, Bound Service IPC, Compose in IME)

### iOS reference
- iOS app source code at `~/dev/dictus` — reference for keyboard layout data, color tokens, key definitions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — green-field project, no code exists yet

### Established Patterns
- None yet — this phase establishes the foundational patterns for all subsequent phases

### Integration Points
- The `core` module will provide shared theme, logging, and models to both `app` and `ime` modules
- The `ime` module's InputMethodService is the entry point for the keyboard
- The `app` module hosts the main activity (minimal in Phase 1 — just settings to enable IME)

</code_context>

<specifics>
## Specific Ideas

- "Utiliser au maximum les standards Android + des projets open source si on peut. Si on a des claviers open source Android, ca peut nous faire une bonne base" — Pierre veut s'appuyer sur un clavier open-source existant plutot que tout coder from scratch
- Le Pixel 4 de test tourne sous LineageOS (pas stock Android)
- Pierre est un utilisateur iOS, pas Android — le clavier doit suivre les conventions Android (pas essayer de copier iOS UX)
- Premiere experience en dev Android natif — les plans doivent etre clairs et detailles

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-core-foundation-keyboard-shell*
*Context gathered: 2026-03-21*
