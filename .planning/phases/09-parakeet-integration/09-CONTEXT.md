# Phase 9: Parakeet Integration - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can download and use Parakeet/Nvidia STT models as an alternative to Whisper — both engines coexist safely behind the SttProvider interface without OOM or crashes. The model manager shows all models (Whisper + Parakeet) in a single list with provider badges and descriptive notes. Switching models automatically switches the underlying engine.

</domain>

<decisions>
## Implementation Decisions

### Provider switching UX
- Provider follows the model automatically — no separate "STT Engine" setting in Settings
- User picks a model in the model manager; the app determines the provider from `ModelInfo.provider`
- Selecting a Parakeet model unloads the Whisper engine immediately (`release()` before `initialize()`) — no dual-engine overlap
- Home screen shows model name only (no provider badge) — the model manager already shows the badge
- All models (Whisper + Parakeet) appear in a single scrollable list with a small provider badge per card

### Model catalog
- **Parakeet 110M CTC INT8** (~126 MB) — primary Parakeet model for v1.1
- **Parakeet 110M CTC FP16** (~220 MB) — full precision variant, slower on Pixel 4 but available
- **Parakeet 0.6B TDT v3** (~640 MB) — added to catalog with memory garde-fou: visible with RAM warning ("Necessite 8+ GB RAM"), app checks available RAM before initialization and blocks if insufficient
- Download source: Claude's discretion — check iOS repo for consistency (likely HuggingFace or sherpa-onnx GitHub Releases, whichever is most available)
- Short provider line on model cards: Whisper cards show "Multilingue - FR, EN, +97 langues", Parakeet cards show "Anglais uniquement - Rapide et precis"

### Engine coexistence & memory
- Strict single-engine policy: only one engine loaded at any time
- Selecting a new model calls `release()` on current provider, then `initialize()` on new provider — zero memory overlap
- libc++_shared.so collision between whisper.cpp and sherpa-onnx: validate FIRST before any feature code (build with both native libs, test on device, fix with pickFirst or repackaging if needed)
- ONNX Runtime memory release timing (sherpa-onnx #1939): empirical validation with `dumpsys meminfo` required
- Brief snackbar ("Chargement du modele...") during engine swap — non-blocking, disappears when ready

### Language warning UX
- Persistent amber warning banner on Parakeet model cards: "Anglais uniquement — la transcription francaise n'est pas supportee"
- Confirmation dialog when activating a Parakeet model while transcription language is FR or auto: "Ce modele ne supporte que l'anglais. Continuer ?"
- Warning logic checks `PreferenceKeys.TRANSCRIPTION_LANGUAGE` (not app locale) — this is the actual language the STT engine receives
- No warning on recording/keyboard screen — model card warning + activation dialog are sufficient

### Claude's Discretion
- Download URL scheme (HuggingFace vs sherpa-onnx GitHub Releases — pick most reliable/available)
- sherpa-onnx AAR integration approach (local libs/ vs Gradle dependency)
- ParakeetProvider implementation details (sherpa-onnx API usage)
- RAM check threshold and mechanism for 0.6B model guard
- Snackbar styling and duration
- NDK libc++_shared.so resolution strategy (pickFirst, repackaging, etc.)
- Exact precision/speed scores for Parakeet models in ModelInfo

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### SttProvider architecture
- `core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt` — Interface contract with providerId, displayName, supportedLanguages metadata
- `app/src/main/java/dev/pivisolutions/dictus/service/WhisperProvider.kt` — Reference implementation to mirror for ParakeetProvider
- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` — Orchestrates engine lifecycle; line 97 hardcodes `WhisperProvider()` — must become dynamic based on active model's provider

### Model management
- `app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt` — ModelCatalog, ModelInfo, AiProvider enum, download URL routing
- `app/src/main/java/dev/pivisolutions/dictus/ui/models/ModelCard.kt` — Model card UI where provider badge and warning banner go
- `app/src/main/java/dev/pivisolutions/dictus/models/ModelsScreen.kt` — Model list screen
- `app/src/main/java/dev/pivisolutions/dictus/models/ModelsViewModel.kt` — Model selection logic (ACTIVE_MODEL preference)

### Preferences
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — ACTIVE_MODEL key, TRANSCRIPTION_LANGUAGE for warning logic

### Prior phase context
- `.planning/phases/07-foundation/07-CONTEXT.md` — SttProvider extraction decisions, supportedLanguages semantics
- `.planning/phases/08-text-prediction/08-CONTEXT.md` — DictusImeService wiring patterns (EntryPointAccessors, by lazy)

### Requirements
- `.planning/REQUIREMENTS.md` — STT-02, STT-03, STT-04, STT-05 (Phase 9), STT-06 (v2 but catalog entry added now)
- `.planning/ROADMAP.md` — Phase 9 success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SttProvider` interface: Ready for ParakeetProvider — same contract (initialize/transcribe/release + metadata)
- `WhisperProvider`: Template for ParakeetProvider implementation — same structure, different native backend
- `AiProvider` enum: Already has `PARAKEET` variant — just needs wiring
- `ModelInfo` data class: Already supports `provider` field — Parakeet models slot in directly
- `ModelCatalog.downloadUrl()`: Has `AiProvider.PARAKEET` branch (currently errors) — implement with real URL

### Established Patterns
- Hilt DI with `EntryPointAccessors` for service-level dependencies
- DataStore for preferences (ACTIVE_MODEL, TRANSCRIPTION_LANGUAGE)
- StateFlow for reactive UI updates
- `by lazy` for applicationContext-dependent fields in IME/Service

### Integration Points
- `DictationService.sttProvider` (line 97): Change from `val` to dynamic provider selection based on `ACTIVE_MODEL`'s `AiProvider`
- `ModelCatalog.ALL`: Add 3 Parakeet model entries
- `ModelCatalog.downloadUrl()`: Implement PARAKEET URL scheme
- `ModelCard` composable: Add provider badge + language warning banner
- `ModelsViewModel`: Add activation dialog trigger when language mismatch detected
- `build.gradle`: Add sherpa-onnx AAR dependency + handle native lib conflicts

</code_context>

<specifics>
## Specific Ideas

- User wants Parakeet 0.6B TDT v3 in the catalog even though it can't run on Pixel 4 — future-proofing with RAM guard
- User defers to Claude on download CDN — check iOS Dictus repo for consistency
- User is beginner in Kotlin/Android — explain NDK integration, native lib conflicts, and memory management concepts

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (Parakeet 0.6B was added to Phase 9 catalog with guard, not deferred.)

</deferred>

---

*Phase: 09-parakeet-integration*
*Context gathered: 2026-03-31*
