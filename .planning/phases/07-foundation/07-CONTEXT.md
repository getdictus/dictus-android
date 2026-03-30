# Phase 7: Foundation - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

The codebase is ready for multi-provider STT and a public beta: v1.0 bugs are fixed (AZERTY toggle, POST_NOTIFICATIONS, stale tests), and the SttProvider abstraction exists before any new engine code is written. No new user-facing features — this is cleanup + interface extraction.

</domain>

<decisions>
## Implementation Decisions

### SttProvider abstraction
- Rename `TranscriptionEngine` to `SttProvider`, `WhisperTranscriptionEngine` to `WhisperProvider`
- Add minimal metadata: `providerId` (String), `displayName` (String), `supportedLanguages` (List<String>)
- Keep existing method signatures (`initialize`, `transcribe`, `release`) — they already cover the contract
- `DictationService` continues to manage provider lifecycle (no separate SttManager yet — Phase 9 can revisit if needed)

### Claude's Discretion — SttProvider package
- Claude decides whether to move SttProvider to `core/stt/` (provider-neutral) or keep in `core/whisper/` (simpler rename). Either is acceptable.

### Settings propagation (AZERTY/QWERTY)
- IME must observe the keyboard layout preference from DataStore via Flow and update the layout without IME restart
- Claude picks the exact mechanism (DataStore Flow in DictusImeService is the recommended approach given the existing Compose + DataStore architecture)

### POST_NOTIFICATIONS permission
- Declare `POST_NOTIFICATIONS` in AndroidManifest.xml
- Add runtime permission request flow for API 33+ (e.g., during first recording or app launch) so notifications actually work end-to-end on modern devices
- Current Pixel 4 (API 29) is unaffected — this is forward-compatibility for Android 13+ users

### Stale tests
- Fix all 3 stale test files: AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest
- Update assertions to match current code, do not remove tests
- Full test suite must pass (`./gradlew test`) before phase is considered complete

### Claude's Discretion
- Package organization for SttProvider (core/stt/ vs core/whisper/)
- Exact timing of POST_NOTIFICATIONS runtime request (app launch vs first recording)
- How to wire DataStore Flow observation in DictusImeService (LaunchedEffect, coroutine scope, etc.)
- Test assertion fixes — whatever makes them match current behavior

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### STT architecture
- `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt` — Current interface to rename to SttProvider
- `app/src/main/java/dev/pivisolutions/dictus/service/WhisperTranscriptionEngine.kt` — Current implementation to rename to WhisperProvider
- `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt` — Orchestrates engine lifecycle, will use SttProvider

### Settings / IME
- `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt` — IME service that needs to observe layout preference
- `core/src/main/java/dev/pivisolutions/dictus/core/preferences/PreferenceKeys.kt` — DataStore preference keys
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt` — Settings UI writing the layout toggle
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsViewModel.kt` — Settings logic

### Notifications
- `app/src/main/AndroidManifest.xml` — Missing POST_NOTIFICATIONS declaration
- `app/src/main/java/dev/pivisolutions/dictus/permission/PermissionHelper.kt` — Existing permission handling code

### Stale tests
- `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt` — Stale assertions
- `app/src/test/java/dev/pivisolutions/dictus/model/ModelCatalogTest.kt` — Stale assertions
- `app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt` — Stale assertions

### Project context
- `.planning/REQUIREMENTS.md` — DEBT-01, DEBT-02, DEBT-03, STT-01 requirements
- `.planning/ROADMAP.md` — Phase 7 success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TranscriptionEngine` interface: Already has the right shape (initialize/transcribe/release) — rename rather than rewrite
- `WhisperTranscriptionEngine`: Clean wrapper around WhisperContext — becomes WhisperProvider with minimal changes
- `PermissionHelper`: Existing permission handling code — extend for POST_NOTIFICATIONS
- `PreferenceKeys`: DataStore keys already defined for layout preference — just need IME to observe them

### Established Patterns
- Hilt DI with EntryPointAccessors for IME (not @AndroidEntryPoint) — any new IME dependencies use this pattern
- DataStore for preferences — consistent across app and core modules
- Compose + Flow for reactive UI — IME uses Compose via LifecycleInputMethodService

### Integration Points
- `DictationService` → `SttProvider` (rename the engine reference)
- `DictusImeService` → `PreferenceKeys` (add DataStore observation for layout)
- `AndroidManifest.xml` → add POST_NOTIFICATIONS permission
- All callers of `TranscriptionEngine` must update imports after rename

</code_context>

<specifics>
## Specific Ideas

- User is a beginner in Kotlin/Android — explain the "why" behind architectural choices (from CLAUDE.md)
- User didn't understand IME concept or POST_NOTIFICATIONS issue — was explained during discussion. Downstream agents should include clear comments in code explaining these Android concepts.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-foundation*
*Context gathered: 2026-03-30*
