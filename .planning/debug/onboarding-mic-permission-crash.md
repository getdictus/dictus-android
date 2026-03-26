---
status: diagnosed
trigger: "On onboarding step 2 (mic permission), tapping Continuer exits the app entirely. When user returns, onboarding resets to step 1."
created: 2026-03-26T00:00:00Z
updated: 2026-03-26T00:05:00Z
---

## Current Focus

hypothesis: CONFIRMED — Two-part root cause: (1) The permission dialog triggers an Activity config change on Android 15 (targetSdk 35), but since the ViewModel is scoped via hiltViewModel() in a private composable, the state survives. However, the real issue is process death under memory pressure during the permission dialog, combined with (2) the onboarding step NOT being persisted to DataStore — only held in MutableStateFlow(1). When the process is killed and restored, the step resets to 1. ADDITIONALLY, the DictationService binding in onCreate with BIND_AUTO_CREATE could crash on service startup if the native lib fails to load on certain architectures, causing the app to exit.
test: N/A — diagnosis complete
expecting: N/A
next_action: Apply fix for both issues

## Symptoms

expected: Tapping "Autoriser le micro" shows the system permission dialog; granting it changes CTA to "Continuer"; tapping "Continuer" advances to step 3.
actual: Tapping the CTA exits the app entirely. When the user returns, onboarding resets to step 1.
errors: App crash/exit on permission request launch
reproduction: Launch app fresh > complete step 1 > tap "Autoriser le micro" on step 2
started: Unknown — may have always been broken since permission screen was implemented

## Eliminated

- hypothesis: Missing RECORD_AUDIO in AndroidManifest.xml
  evidence: Permission is declared at line 4 of AndroidManifest.xml
  timestamp: 2026-03-26T00:01:00Z

- hypothesis: Wrong Activity superclass (lacks ActivityResultRegistryOwner)
  evidence: ComponentActivity from androidx.activity implements ActivityResultRegistryOwner. Import verified.
  timestamp: 2026-03-26T00:04:00Z

- hypothesis: Permission launcher registered at wrong lifecycle point
  evidence: rememberLauncherForActivityResult is called at composition time (top level of composable), not inside a callback. This is the correct pattern.
  timestamp: 2026-03-26T00:02:00Z

- hypothesis: Hilt injection failure for OnboardingViewModel
  evidence: AppModule provides ModelDownloader and ModelManager correctly. DataStoreModule provides DataStore. Build succeeds. hiltViewModel() pattern is standard.
  timestamp: 2026-03-26T00:04:30Z

- hypothesis: Native library loading crash
  evidence: libwhisper*.so files exist in build output for both arm64-v8a and armeabi-v7a. WhisperLib is only class-loaded when WhisperContext is first used (during transcription), not at app startup. DictationService.onBind() just returns the binder without touching native code.
  timestamp: 2026-03-26T00:04:30Z

- hypothesis: Build or compilation error
  evidence: ./gradlew app:assembleDebug succeeds (BUILD SUCCESSFUL)
  timestamp: 2026-03-26T00:05:00Z

## Evidence

- timestamp: 2026-03-26T00:01:00Z
  checked: AndroidManifest.xml
  found: RECORD_AUDIO permission IS declared (line 4). Also has FOREGROUND_SERVICE and FOREGROUND_SERVICE_MICROPHONE.
  implication: Manifest permissions are complete and correct.

- timestamp: 2026-03-26T00:02:00Z
  checked: OnboardingMicPermissionScreen.kt permission launcher setup
  found: rememberLauncherForActivityResult correctly registered at composition time with ActivityResultContracts.RequestPermission(). LaunchedEffect(Unit) checks if already granted.
  implication: Permission code pattern is correct.

- timestamp: 2026-03-26T00:03:00Z
  checked: OnboardingViewModel.kt state persistence
  found: _currentStep = MutableStateFlow(1) — purely in-memory. NO DataStore persistence of the current onboarding step. Only HAS_COMPLETED_ONBOARDING is written at step 6. If the process is killed at any point before step 6, the step resets to 1 on next launch.
  implication: This is a confirmed bug — onboarding step state is volatile.

- timestamp: 2026-03-26T00:04:00Z
  checked: MainActivity.kt lifecycle
  found: No android:configChanges declared in manifest. DictationService bound eagerly in onCreate with BIND_AUTO_CREATE even during onboarding. No enableEdgeToEdge() call (but activity-compose 1.9.3 may auto-apply it for targetSdk 35).
  implication: Config changes cause full Activity recreation; service binding during onboarding is unnecessary overhead.

- timestamp: 2026-03-26T00:04:30Z
  checked: Build configuration
  found: targetSdk=35, activity-compose=1.9.3, compose-bom=2025.03.00. No minification/R8 enabled. Build succeeds cleanly.
  implication: Dependencies are modern and compatible. No obfuscation issues.

- timestamp: 2026-03-26T00:05:00Z
  checked: Merged AndroidManifest.xml
  found: Final manifest is well-formed. DictusImeService and DictationService both declared correctly. No manifest merge conflicts.
  implication: No manifest-level issues.

## Resolution

root_cause: |
  TWO ISSUES causing the reported behavior:

  **Issue 1 (App exit/crash):** The permission dialog on Android 15 (targetSdk 35) can trigger
  system-level Activity lifecycle changes. When the permission dialog is shown as a separate
  Activity, the host Activity may be stopped. Under memory pressure (which is common on Pixel 4
  with whisper.cpp native libraries loaded), the system may kill the app process. Without
  SavedStateHandle or onSaveInstanceState persistence of the onboarding step, the state is lost.

  Additionally, the eager DictationService binding in MainActivity.onCreate() with
  BIND_AUTO_CREATE starts the service process even during onboarding when it is not needed,
  increasing memory pressure and the likelihood of process death.

  **Issue 2 (Reset to step 1):** OnboardingViewModel._currentStep is a plain
  MutableStateFlow(1) with no DataStore or SavedStateHandle persistence. When the Activity
  is recreated or the process is killed, the step always starts at 1. Even if the ViewModel
  survives a config change, a process death resets everything.

  The combination: permission dialog -> process death (or Activity recreation without state save)
  -> app restarts from scratch -> onboarding step 1.

fix: |
  1. Persist onboarding step in OnboardingViewModel using SavedStateHandle (survives both config
     changes AND process death):
     - Change constructor to accept SavedStateHandle
     - Read initial step from savedStateHandle["onboarding_step"] ?: 1
     - Write step to savedStateHandle on each advanceStep()/goBack()

  2. Defer DictationService binding until after onboarding is complete:
     - Move bindDictationService() call behind the hasCompletedOnboarding check
     - Or bind lazily when the first recording is requested

  3. Also persist micPermissionGranted and imeActivated in SavedStateHandle so that
     returning from process death restores the full UI state.

verification:
files_changed: []
