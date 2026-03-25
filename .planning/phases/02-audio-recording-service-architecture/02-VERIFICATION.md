---
phase: 02-audio-recording-service-architecture
verified: 2026-03-25T00:00:00Z
status: human_needed
score: 3.5/4 truths verified
human_verification:
  - test: "Open any text field, bring up Dictus keyboard, tap the mic pill, start recording, then pull down the Android notification shade"
    expected: "A persistent 'Dictus - Recording' notification appears with a Stop action button. Tapping Stop ends the recording."
    why_human: "UAT test 4 was skipped in initial UAT (mic-tap crash blocked it) and marked not-retested after the crash fix (02-03). The foreground service implementation is correct in code (FOREGROUND_SERVICE_TYPE_MICROPHONE, notification channel, buildNotification() with Stop PendingIntent), but no human ever confirmed it displays correctly after the mic-tap fix."
---

# Phase 2: Audio Recording + Service Architecture Verification Report

**Phase Goal:** Users can tap the mic button on the keyboard to record audio, with the recording managed by a foreground service
**Verified:** 2026-03-25
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can tap the mic button to start recording and tap again to stop | VERIFIED | DictusImeService.handleMicTap() calls controller.startRecording()/stopRecording() based on _serviceState. UI switches between KeyboardScreen and RecordingScreen via collectAsState(). UAT tests 1, 2, 3: PASS. |
| 2 | User sees a notification while recording is active (foreground service) | VERIFIED IN CODE / NEEDS HUMAN | DictationService implements full foreground service with FOREGROUND_SERVICE_TYPE_MICROPHONE, notification channel, and Stop action PendingIntent. Verified in manifest. UAT test 4 never confirmed by human after the mic-tap crash fix. |
| 3 | User feels haptic feedback on key presses and mic button taps | PARTIAL | HapticHelper implements KEYBOARD_TAP and LONG_PRESS via View.performHapticFeedback (permission-free). UAT test 5: PASS. "Configurable" aspect of DICT-07 is intentionally deferred to Phase 4 per research decision. |
| 4 | Dictation state machine transitions are observable: idle -> recording -> idle | VERIFIED | DictationState sealed class (Idle/Recording) in core module. StateFlow<DictationState> exposed via DictationController interface. IME collects via _serviceState.collectAsState() and mirrors it. All state transitions covered. |

**Score:** 3.5/4 truths verified (one needs human confirmation, one partial by design)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `core/src/main/java/.../core/service/DictationState.kt` | Sealed class Idle/Recording | VERIFIED | Substantive: sealed class with Idle (data object) and Recording (elapsedMs, energy). Wired: imported by DictationService and DictusImeService. |
| `app/src/main/java/.../service/AudioCaptureManager.kt` | AudioRecord 16kHz Float32 capture | VERIFIED | Substantive: full AudioRecord lifecycle, RMS energy with 20x amplification + sqrt curve, 30-bar rolling history pre-filled. Wired: used in DictationService.startAudioCapture(). |
| `app/src/main/java/.../service/DictationService.kt` | Foreground service with LocalBinder and StateFlow | VERIFIED | Substantive: full foreground service implementation — SERVICE_TYPE_MICROPHONE, notification channel, buildNotification() with Stop action, LocalBinder, StateFlow. Wired: declared in AndroidManifest.xml, bound by DictusImeService. |
| `core/src/main/java/.../core/service/DictationController.kt` | Interface in core avoiding circular dep | VERIFIED | Substantive: interface with state, startRecording(), stopRecording(), cancelRecording(). Wired: implemented by DictationService, consumed by DictusImeService. |
| `ime/src/main/java/.../ime/DictusImeService.kt` | Service binding + state-driven UI | VERIFIED | Substantive: ServiceConnection with reflection-based LocalBinder access, stateCollectionJob mirroring service StateFlow to local _serviceState, KeyboardContent() switching on dictationState. Wired: bound to DictationService on onCreate. |
| `ime/src/main/java/.../ime/ui/RecordingScreen.kt` | Recording overlay with waveform/timer/cancel/confirm | VERIFIED | Substantive: cancel (X) outlined pill, confirm (checkmark) outlined pill, WaveformBars, timer MM:SS, "En ecoute..." label. Wired: rendered by DictusImeService.KeyboardContent() when state is Recording. |
| `ime/src/main/java/.../ime/haptics/HapticHelper.kt` | Two-tier haptic feedback | VERIFIED | Substantive: KEYBOARD_TAP for keys, LONG_PRESS for mic — both via View.performHapticFeedback (no VIBRATE permission needed). Wired: called in KeyButton, MicButtonRow, RecordingScreen. |
| `app/src/main/res/drawable/ic_mic.xml` | Mic icon for notification | VERIFIED | File exists. Referenced in DictationService.buildNotification(). |
| `app/src/main/res/drawable/ic_stop.xml` | Stop icon for notification action | VERIFIED | File exists. Referenced in DictationService.buildNotification(). |
| `app/src/main/AndroidManifest.xml` | Service declaration + permissions | VERIFIED | RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE permissions declared. DictationService declared with foregroundServiceType="microphone". |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DictusImeService` | `DictationService` | ServiceConnection + LocalBinder reflection | WIRED | bindDictationService() on onCreate; onServiceConnected casts to DictationController via getMethod("getService") |
| `DictusImeService` | `DictationState` | _serviceState.collectAsState() | WIRED | stateCollectionJob collects service.state and mirrors to _serviceState; KeyboardContent() reads via collectAsState() |
| `MicButtonRow` | `DictusImeService.handleMicTap()` | onMicTap lambda | WIRED | MicButtonRow.onMicTap = { handleMicTap() } in KeyboardScreen; handleMicTap() delegates to controller |
| `RecordingScreen` cancel/confirm | `DictationController` | onCancel/onConfirm lambdas | WIRED | onCancel = { dictationController?.cancelRecording() }, onConfirm = { dictationController?.stopRecording() } |
| `DictationService` | `AudioCaptureManager` | startAudioCapture() | WIRED | AudioCaptureManager instantiated in startAudioCapture(), start(serviceScope) called, onEnergyUpdate updates _state |
| `DictationService` foreground | Manifest service declaration | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | WIRED | ServiceCompat.startForeground() called with FOREGROUND_SERVICE_TYPE_MICROPHONE; matches manifest foregroundServiceType="microphone" |

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| DICT-01 | User can tap the mic button to start voice recording | SATISFIED | handleMicTap() -> controller.startRecording() -> DictationService starts foreground + AudioRecord. UAT test 1: PASS. |
| DICT-02 | User can tap again to stop recording and trigger transcription | SATISFIED (partial — transcription is Phase 3 placeholder) | handleMicTap() when Recording -> controller.stopRecording(). Cancel/confirm buttons work. UAT tests 2, 3: PASS. Transcription is explicitly a Phase 3 concern. |
| DICT-07 | User feels haptic feedback on key press and mic button (configurable) | PARTIAL | Haptic feedback implemented and working (UAT test 5: PASS). "Configurable" toggle intentionally deferred to Phase 4 (APP-04) per 02-RESEARCH.md: "always-on in Phase 2, toggle in Phase 4". REQUIREMENTS.md traceability correctly marks this as Pending. |
| DICT-08 | Recording runs in a foreground Service with visible notification | VERIFIED IN CODE / NEEDS HUMAN | Full foreground service implementation confirmed in code. UAT test 4 was blocked by mic-tap crash initially, then not re-tested after fix in 02-03. Notification display needs human confirmation. |

**Orphaned requirements check:** No requirements mapped to Phase 2 in REQUIREMENTS.md that are unaccounted for in plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ime/.../DictusImeService.kt` | 197 | `Timber.d("Recording confirmed (transcription placeholder)")` | Info | Expected — Phase 3 placeholder. onConfirm stops recording but inserts no text. This is by design until whisper integration. |
| `app/.../service/DictationState.kt` | 11 | `typealias DictationState = ...core.service.DictationState` | Info | Backward compat shim. Not a blocker — clean architecture decision to move DictationState to core after 02-01. |

No blockers. No FIXME/TODO/stub patterns found in core implementation files.

### Human Verification Required

**1. Foreground notification displays after mic-tap fix**

**Test:** Open a text field in any app, bring up the Dictus keyboard. Tap the mic pill button — confirm the keyboard transitions to the recording overlay (waveform + timer visible). Pull down the Android notification shade.
**Expected:** A persistent "Dictus - Recording" notification appears with a Stop action button. Tapping the Stop action button stops recording and returns to the keyboard.
**Why human:** UAT test 4 was skipped in initial UAT because the mic-tap crash (SecurityException in HapticHelper) prevented recording from starting. After the 02-03 fix, tests 1-3 and 5-8 were confirmed, but test 4 was explicitly marked "not-retested" in 02-UAT.md. The code is correct (foreground service type, notification channel, PendingIntent for Stop), but no human has observed the notification in the shade after the crash fix.

### Gaps Summary

No blocking gaps. All core implementation artifacts are substantive and wired. All documented commits verified in git history.

One item requires human confirmation: the foreground notification (DICT-08 / UAT test 4) was never observed by a human after the mic-tap crash fix. This is a human verification item, not a code gap.

One partial requirement: DICT-07 haptic configurability is deferred to Phase 4 by design. The core haptic behavior (two tiers: KEYBOARD_TAP + LONG_PRESS) is implemented and confirmed.

---

_Verified: 2026-03-25_
_Verifier: Claude (gsd-verifier)_
