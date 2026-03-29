---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: verifying
stopped_at: "Completed 06-03 Task 1 — checkpoint:human-verify for Task 2 (cross-app validation)"
last_updated: "2026-03-29T08:29:35.587Z"
last_activity: 2026-03-25 -- Phase 03 Plan 03 complete (E2E dictation pipeline wired)
progress:
  total_phases: 7
  completed_phases: 7
  total_plans: 29
  completed_plans: 29
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-21)

**Core value:** La dictee vocale on-device qui fonctionne comme clavier systeme -- gratuite, privee, sans cloud.
**Current focus:** Phase 03 Whisper Integration -- contracts defined, wiring next

## Current Position

Phase: 03 of 7 (Whisper Integration)
Plan: 3 of 3 in current phase -- ALL COMPLETE
Status: All plans complete, awaiting phase verification
Last activity: 2026-03-25 -- Phase 03 Plan 03 complete (E2E dictation pipeline wired)

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 13min | 3 tasks | 21 files |
| Phase 01 P02 | 2min | 2 tasks | 12 files |
| Phase 01 P03 | ~22h (2 sessions) | 2 tasks | 7 files |
| Phase 01 P04 | 2min | 2 tasks | 5 files |
| Phase 02 P01 | 4min | 2 tasks | 9 files |
| Phase 02 P02 | ~45min | 4 tasks | 10 files |
| Phase 02.1 P01 | 4min | 2 tasks | 13 files |
| Phase 02.1 P02 | 25min | 3 tasks | 8 files |
| Phase 02 P03 (gap) | ~45min | 3 tasks + UAT | 5 files |
| Phase 03 P02 | 14min | 2 tasks (TDD) | 12 files |
| Phase 03 P01 | 15min | 2 tasks | 11 files |
| Phase 03 P03 | 15min | 2 tasks | 7 files |
| Phase 04-model-management-onboarding P01 | 6min | 3 tasks | 14 files |
| Phase 04 P02 | 60min | 2 tasks | 14 files |
| Phase 04-model-management-onboarding P03 | 10min | 2 tasks | 16 files |
| Phase 04-model-management-onboarding P04 | 10min | 2 tasks | 13 files |
| Phase 04-model-management-onboarding P05 | 5min | 2 tasks | 3 files |
| Phase 04-model-management-onboarding P06 | 3min | 2 tasks | 4 files |
| Phase 04-model-management-onboarding P07 | 3min | 3 tasks | 5 files |
| Phase 04 P08 | 3min | 3 tasks | 5 files |
| Phase 05-polish-differentiators P03 | 3min | 2 tasks | 5 files |
| Phase 05 P04 | 15min | 2 tasks | 5 files |
| Phase 05-polish-differentiators P02 | 25min | 2 tasks | 10 files |
| Phase 05-polish-differentiators P01 | 12min | 2 tasks | 33 files |
| Phase 05-polish-differentiators P05 | 20min | 2 tasks | 11 files |
| Phase 06-release-readiness P02 | 4 | 3 tasks | 12 files |
| Phase 06-release-readiness P01 | 4min | 1 tasks | 9 files |
| Phase 06 P04 | 9min | 2 tasks | 13 files |
| Phase 06-release-readiness P03 | 15min | 1 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Front-load risk -- Compose-in-IME (Phase 1), foreground service (Phase 2), whisper.cpp native build (Phase 3)
- [Roadmap]: Phases 4 and 5 can run after Phase 3; Phase 5 does not depend on Phase 4
- [Roadmap]: UI designs from Pencil.dev arrive later -- architecture/engine phases proceed first
- [01-01]: Used Hilt 2.51.1 (plan's 2.57.1 not published in Maven repos)
- [01-01]: Used Compose BOM 2025.03.00 (plan's 2026.03.00 not yet available)
- [01-01]: Set JDK target 17 for AGP 8.x compatibility
- [Phase 01]: Used Hilt 2.51.1 (plan's 2.57.1 not published); Compose BOM 2025.03.00 (2026.03.00 not yet available); JDK 17 for AGP 8.x
- [Phase 01-02]: Used full package name in manifest android:name to avoid merger ambiguity
- [Phase 01-02]: Used Unicode escapes for special key labels for cross-platform source compatibility
- [Phase 01-03]: Used rememberUpdatedState for KeyButton callbacks to avoid stale closures with layer switching
- [Phase 01-03]: Set AccentPopup focusable=false to prevent stealing IME focus
- [Phase 01-04]: Used AccentHighlight for caps lock vs Accent for single-shift; added drawBehind underline for accessibility
- [02-01]: Made AudioCaptureManager energy methods public for testability
- [02-01]: Used START_NOT_STICKY for DictationService -- recording state is transient
- [02.1-01]: Moved WaveformBarTest from ime to core after WaveformBars extraction (internal visibility)
- [02.1-01]: ime WaveformBars.kt becomes thin wrapper delegating to core for backward compatibility
- [02.1-02]: Service binding in Activity lifecycle (onCreate/onDestroy), not Composable -- avoids lifecycle mismatch
- [02.1-02]: IME status checked in onResume so it refreshes when user returns from system Settings
- [03-02]: Used q5_1 instead of q5_0 for small model -- HuggingFace provides pre-built q5_1
- [03-02]: NDK updated from 25.2.9519653 to 27.2.12479018 to fix missing source.properties
- [Phase 03]: Used NDK 27.2.12479018 instead of planned 25.2.9519653 (corrupted install)
- [Phase 03]: Added kotlinx-coroutines-android as explicit whisper module dependency
- [Phase 04-model-management-onboarding]: ModelCatalog extracted as top-level object so tests can access catalog logic without Android context
- [Phase 04-model-management-onboarding]: AppDestination uses sealed class instead of enum to allow future data class variants with route parameters
- [Phase 04]: flow {} + flowOn(Dispatchers.IO) instead of callbackFlow for downloadWithProgress — test-friendly, avoids UncompletedCoroutinesError
- [Phase 04]: Hilt upgraded 2.51.1 -> 2.54 to fix Kotlin 2.1.x metadata incompatibility in hiltJavaCompileDebug
- [Phase 04-model-management-onboarding]: ModelDownloader class and downloadWithProgress() made open to allow FakeModelDownloader subclass in tests
- [Phase 04-model-management-onboarding]: when(currentStep) dispatch (not NavHost/AnimatedContent) for sequential onboarding — one-time flow needs no graph
- [Phase 04-model-management-onboarding]: LaunchedEffect(currentStep) for IME re-check on step 3 — keeps composable self-contained without onResume coupling
- [Phase 04-model-management-onboarding]: SharingStarted.Eagerly for SettingsViewModel StateFlows — testability with .value assertions
- [Phase 04-model-management-onboarding]: DictationServiceEntryPoint (EntryPointAccessors) for DataStore access — preserves LocalBinder pattern
- [Phase 04-model-management-onboarding]: LogExporter.createZip() split from exportLogs() — FileProvider cannot be tested in Robolectric unit tests
- [Phase 04-model-management-onboarding]: Phase 4 theme picker has single dark option; Phase 6 DSG-02 adds system/Automatique when Material You is implemented
- [Phase 04-model-management-onboarding]: savedStateHandle.getStateFlow() for currentStep/micPermissionGranted/imeActivated — survives mic permission dialog process death
- [Phase 04-model-management-onboarding]: DictationService binding deferred to LaunchedEffect(hasCompletedOnboarding) — reduces memory pressure during onboarding
- [Phase 04-model-management-onboarding]: Static WaveformDecoration replaced with shared WaveformBars + sine-wave animation for iOS parity on welcome screen
- [Phase 04-model-management-onboarding]: Use >= 95% threshold instead of exact size equality in getModelPath() to tolerate HuggingFace CDN mirror size variations
- [Phase 04-model-management-onboarding]: setActiveModel() added to ModelsViewModel (not SettingsViewModel) since model selection now lives on the Models screen
- [Phase 04]: FakeSettingsCard uses KeyboardEntryRow (name + subtitle) to match Android Clavier a l'ecran panel instead of iOS breadcrumb
- [Phase 04]: RecordingScreen in new recording/ package — single responsibility, auto-starts via LaunchedEffect(Unit) for iOS parity
- [Phase 04]: Bottom nav hidden via conditional if in Scaffold bottomBar when currentRoute is recording
- [Phase 05-03]: State hoisted to DictusImeService for emoji picker so onKeyDown can intercept KEYCODE_BACK (BackHandler silently fails in IME context)
- [Phase 05-03]: EmojiPickerScreen height fixed at 310.dp to match keyboard total height and prevent IME window resize
- [Phase 05]: StubSuggestionEngine as Phase 5 MVP: SuggestionEngine interface is drop-in replacement contract for AOSP LatinIME JNI — zero UI/wiring changes needed to swap
- [Phase 05]: SuggestionBar only renders when suggestions non-empty — keyboard height is unchanged when no suggestions available
- [Phase 05-polish-differentiators]: DictusTheme defaults themeMode=DARK for backward compat; theme string mapped to ThemeMode at each reading site
- [Phase 05-polish-differentiators]: ime module needs explicit datastore.preferences dependency for DataStore<Preferences> type in DictusImeEntryPoint
- [Phase 05-polish-differentiators]: withInfiniteAnimationFrameNanos from compose.animation.core — withFrameNanos is internal in Compose 1.x
- [Phase 05-polish-differentiators]: LaunchedEffect(Unit) for WaveformDriver.runLoop() — auto-cancels on composition exit, no manual stop() needed
- [Phase 05-polish-differentiators]: SoundPool pre-loaded in DictationService.onCreate() for sub-10ms playback; SOUND_ENABLED observed reactively via dataStore.data.map{}.collect{}
- [Phase 05-05]: remember(initialLayer) for currentLayer in KeyboardScreen — recomposition resets layer when KEYBOARD_MODE preference changes
- [Phase 05-05]: hapticsEnabled propagated as parameter through KeyboardScreen->KeyboardView->KeyRow->KeyButton (not CompositionLocal) — consistent with existing param style
- [Phase 05-05]: SettingsCard pattern: each settings section wrapped in clip(RoundedCornerShape(12.dp)) + background(Surface) Column for iOS visual parity
- [Phase 06-02]: resources.properties must be in app/src/main/res/ not app/src/main/ — AGP extractSupportedLocales task requires it inside the res source set
- [Phase 06-02]: AppCompatActivity extends ComponentActivity so all existing APIs remain available without changes when extending for locale switching support
- [Phase 06-release-readiness]: English strings in values/strings.xml (no qualifier) — no values-en/ created to avoid Android resource resolution quirks on non-FR non-EN devices
- [Phase 06-release-readiness]: Model name 'Tiny' not extracted to strings.xml — it is a proper name, not translated
- [Phase 06-release-readiness]: onboarding_test_recording_no_result uses context.getString() inside coroutine lambda — stringResource() requires Compose composition context
- [Phase 06]: noResultLabel captured via remember{stringResource()} before coroutine scope in RecordingScreen to avoid composable context restriction
- [Phase 06-03]: MutableStateFlow for uiLanguage — AppCompatDelegate is source of truth, DataStore duplication avoided; setApplicationLocales() called on main thread from onClick only

### Roadmap Evolution

- Phase 02.1 inserted after Phase 2: App Shell + Testability (URGENT) — Cannot test recording/waveform/haptics without a minimal app activity that handles mic permission and service binding. Must be testable before Whisper integration.

### Pending Todos

None yet.

### Blockers/Concerns

- Compose-in-IME lifecycle wiring is not in official docs -- rely on community examples (Gist, Medium sources from research)
- whisper.cpp Android examples are poorly maintained -- plan for build debugging time in Phase 3
- Hilt DI in InputMethodService requires EntryPointAccessors, not @AndroidEntryPoint

## Session Continuity

Last session: 2026-03-29T08:29:35.584Z
Stopped at: Completed 06-03 Task 1 — checkpoint:human-verify for Task 2 (cross-app validation)
Resume file: None
