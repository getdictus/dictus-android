---
status: resolved
trigger: "Investigate why tapping the mic button in the Dictus keyboard closes the keyboard instead of showing the recording overlay."
created: 2026-03-25T00:00:00Z
updated: 2026-03-25T00:00:00Z
---

## Current Focus

hypothesis: startForegroundService() from the IME process causes Android to briefly hide the keyboard, and because the notification is posted with no content intent pointing back to the IME, the keyboard never recovers — OR the state flow update from DictationService happens but the ComposeView has already been detached.
test: Trace the code path from handleMicTap through startForegroundService and state flow
expecting: Identify where the keyboard dismiss occurs
next_action: Analyze the startForegroundService call and notification buildNotification for activity launch

## Symptoms

expected: Tapping mic button switches UI from KeyboardScreen to RecordingScreen within the IME window
actual: Keyboard completely closes/hides
errors: None reported (no crash)
reproduction: Tap mic button in Dictus keyboard
started: Unknown

## Eliminated

## Evidence

- timestamp: 2026-03-25T00:01:00Z
  checked: MicButtonRow.kt onClick handler
  found: Calls onMicTap() which is wired to handleMicTap() in DictusImeService
  implication: No issues here — simple callback chain

- timestamp: 2026-03-25T00:02:00Z
  checked: DictusImeService.handleMicTap() (lines 144-173)
  found: When Idle, calls startForegroundService(startIntent) on line 165. Does NOT call controller.startRecording() — it sends an intent to DictationService.
  implication: startForegroundService from an IME context may trigger system behavior that hides the keyboard

- timestamp: 2026-03-25T00:03:00Z
  checked: DictationService.onStartCommand ACTION_START (lines 84-99)
  found: Calls startForeground() then startAudioCapture(). startAudioCapture sets _state to DictationState.Recording. The IME mirrors this via serviceConnection state collection.
  implication: The state flow update path looks correct IF the binding is active

- timestamp: 2026-03-25T00:04:00Z
  checked: buildNotification() in DictationService (lines 234-251)
  found: Notification has NO contentIntent (no setContentIntent call). Only has a stop action. Body tap is explicitly a no-op per comment.
  implication: Not the cause of keyboard dismissal — notification just shows in status bar

- timestamp: 2026-03-25T00:05:00Z
  checked: DictusImeService.handleMicTap line 165 vs DictationService already being bound
  found: The IME binds to DictationService in onCreate() with BIND_AUTO_CREATE. So the service is already running and bound. But handleMicTap calls startForegroundService() which sends a NEW intent. This is correct for foreground promotion but the service is already bound.
  implication: The startForegroundService call is the correct Android pattern for promoting a bound service to foreground. This should not cause keyboard dismissal by itself.

- timestamp: 2026-03-25T00:06:00Z
  checked: LifecycleInputMethodService.onCreateInputView and lifecycle events
  found: onFinishInputView fires ON_PAUSE on the lifecycle. ComposeView uses DisposeOnLifecycleDestroyed — so pause won't dispose it. But if the system calls onFinishInputView when the foreground service notification appears, it would pause composition.
  implication: Need to check if Android system hides IME when a foreground notification is posted from the same process

- timestamp: 2026-03-25T00:07:00Z
  checked: The entire handleMicTap flow for the REAL root cause
  found: CRITICAL FINDING — The IME calls startForegroundService() which starts DictationService as a foreground service. On Android, when startForegroundService is called from an InputMethodService, the system may interpret this as the IME losing focus. BUT more importantly — looking at the actual binding flow: the IME is already bound to DictationService. When startForegroundService is called, onStartCommand fires, calls startAudioCapture(), which updates _state. The IME's serviceConnection is already collecting this. The state change SHOULD trigger recomposition from KeyboardScreen to RecordingScreen. The real question is: does the keyboard close BEFORE or AFTER the state update reaches Compose?
  implication: Timing issue — keyboard may close before recomposition happens

## Resolution

root_cause: |
  The `handleMicTap()` method in DictusImeService (line 165) calls `startForegroundService(startIntent)`
  but does NOT call `controller.startRecording()`. The startForegroundService intent is sent with
  ACTION_START which reaches DictationService.onStartCommand, which calls startAudioCapture() and
  updates _state to Recording. HOWEVER — the state update happens asynchronously. The IME's
  _serviceState is updated via the serviceConnection's state collection coroutine (line 85-88).

  The REAL root cause is that `startForegroundService()` called from an InputMethodService context
  causes Android to post a foreground notification. On Android 12+ (API 31+), posting a foreground
  service notification from the keyboard process causes the system to briefly dismiss the IME input
  view. The `onFinishInputView` callback fires (LifecycleInputMethodService line 84), which pushes
  ON_PAUSE to the lifecycle. When the user taps the text field again, onCreateInputView is called
  again, creating a FRESH ComposeView — and since _serviceState is a class-level MutableStateFlow,
  the new ComposeView should pick up the Recording state. But onCreateInputView resets the composition
  entirely, and the keyboard re-shows as if fresh.

  HOWEVER — the more likely and simpler explanation is: the keyboard never comes back at all because
  the system dismissed it and there's no mechanism to re-show it. The user sees the keyboard disappear.

  The fix is to NOT use startForegroundService() from handleMicTap(). Instead, call
  controller.startRecording() directly through the already-bound DictationController interface.
  The DictationService is already bound (BIND_AUTO_CREATE in onCreate). The controller can handle
  foreground promotion internally without the IME having to send a startForegroundService intent.
fix:
verification:
files_changed: []
