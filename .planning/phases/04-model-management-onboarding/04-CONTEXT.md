# Phase 4: Model Management + Onboarding - Context

**Gathered:** 2026-03-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can set up Dictus from scratch via a guided onboarding, manage Whisper models (download/delete with progress), and configure app settings. The app has a proper 3-tab navigation (Home/Models/Settings).

Does NOT include: Follow System theme (Phase 6), bilingual UI (Phase 6), text suggestions (Phase 5), emoji picker (Phase 5), waveform polish (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Onboarding flow
- 6 sequential steps, same as iOS: Welcome → Mic permission → Keyboard layout → IME activation → Model download → Test dictation
- User cannot skip steps — each must be validated before advancing
- Model download is MANDATORY during onboarding (app doesn't work without a model)
- Default model: tiny (~77 MB) for now. Later: device-aware selection (old device → tiny, recent device → best model). iOS does this already — check DictusApp for reference logic
- After successful test dictation → Success screen ("Tout est prêt !") → button to Home
- Onboarding is a full-screen experience that replaces the app. After completion, switch to Home with 3 tabs
- Persist onboarding completion in DataStore (HAS_COMPLETED_ONBOARDING)

### Model management
- 4 models for MVP: tiny (~77 MB), base (~142 MB), small (~466 MB), small-q5_1 (~190 MB)
- CRITICAL architecture decision: ModelManager must support multiple AI providers (Whisper today, Parakeet soon, others later). Each provider can have different model formats, download sources, and initialization logic. Design the model catalog with a provider abstraction from day 1 — same pattern as iOS
- Cannot delete the last remaining model — show "Au moins un modèle requis" message
- Download UI: progress bar + percentage text
- Storage indicator per model + total storage consumed
- ModelDownloader needs progress callback interface for UI binding (currently only logs to Timber)

### Settings screen
- 3 sections matching iOS: Transcription (langue, modèle actif), Clavier (layout, haptic, son), À propos (version, debug export, liens)
- Phase 4 includes: language picker (auto/FR/EN), active model, keyboard layout, haptics toggle, sound toggle, debug log export
- Phase 4 does NOT include: Follow System theme toggle, UI language switch (→ Phase 6)
- Debug log export: generate ZIP → open Android share sheet (email, Slack, etc.)
- DictationService reads language + active model from DataStore preferences (currently hard-coded "fr" and "tiny")

### App navigation
- 3-tab bottom navigation bar (pill style per mockup frame d7cJl): Home / Models / Settings
- Home tab: active model status + last transcription preview
- Models tab: model manager with download/delete/storage
- Settings tab: all preferences
- Jetpack Compose Navigation (NavController) for tab routing
- Onboarding is a separate full-screen route, shown when HAS_COMPLETED_ONBOARDING is false

### Claude's Discretion
- Loading/skeleton states during model operations
- Exact onboarding page animations/transitions
- Error state UI (download failure, permission denial recovery)
- Home tab layout details beyond active model + last transcription
- TimberSetup file appender implementation for log export

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design mockups
- `design/dictus-android-design.pen` — Home frame `wrqi0`, Models frame `kSWvw`, Settings frame `CqKvx`, BottomNavBar component `d7cJl`, GlassCard component `eKShU`
- `design/README.md` — Frame IDs and component specs

### iOS reference (functional alignment)
- `~/dev/dictus/DictusApp/Models/ModelManager.swift` — Multi-provider model management, device-aware default selection, download state machine, "cannot delete last model" guard
- `~/dev/dictus/DictusApp/Onboarding/OnboardingView.swift` — 6-page sequential onboarding, @SceneStorage for state persistence, step indicator dots
- `~/dev/dictus/DictusApp/Views/SettingsView.swift` — 3-section settings, @AppStorage with App Group, debug log export

### Existing Android code to extend
- `app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt` — Current 2-model catalog, path/URL resolution, size validation
- `app/src/main/java/dev/pivisolutions/dictus/service/ModelDownloader.kt` — OkHttp download with temp-file-rename, needs progress callback
- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` — Hard-coded "fr" language and "tiny" model to replace with DataStore reads
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — Existing DataStore keys to extend

### Requirements
- `.planning/REQUIREMENTS.md` — APP-01 through APP-05 requirements for this phase

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ModelManager.kt`: Model path resolution and size validation — extend with provider abstraction, base model, delete, storage calculation
- `ModelDownloader.kt`: OkHttp download with atomic rename — extend with progress callback Flow
- `PermissionHelper.kt`: Mic + notification permission launcher — reuse in onboarding mic permission page
- `ImeStatusCard.kt`: IME enabled/selected status check — reuse in onboarding IME activation page
- `WaveformBars.kt`: 30-bar waveform visualization — reuse in onboarding test dictation page
- `DictusTheme.kt` + `DictusColors.kt`: Full dark theme — use for all new screens
- `PreferenceKeys.kt`: DataStore keys — extend with new preference keys

### Established Patterns
- Service binding via LocalBinder + MutableStateFlow<DictationController?> in Activity
- DataStore for preferences (not SharedPreferences)
- Hilt DI throughout (@HiltAndroidApp, @AndroidEntryPoint)
- Timber for structured logging
- Compose for all UI (no XML layouts)
- TDD red-green for utility/model classes

### Integration Points
- `MainActivity.kt`: Currently shows TestSurfaceScreen — will be rewritten with NavController + 3 tabs + onboarding gate
- `DictationService.confirmAndTranscribe()`: Reads model key and language — wire to DataStore
- `DictusApplication.kt`: Timber init — extend with file appender for log export

</code_context>

<specifics>
## Specific Ideas

- Multi-provider model architecture from day 1: Whisper models today, Parakeet models coming soon, potentially others. The iOS app already has this pattern — check ModelManager.swift for the provider abstraction
- Device-aware model recommendation: check device capabilities (SoC, RAM) to suggest the best model. For MVP, default to tiny. The iOS app has this logic already
- Same onboarding feel as iOS: sequential, guided, can't skip, success celebration at the end

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 04-model-management-onboarding*
*Context gathered: 2026-03-26*
