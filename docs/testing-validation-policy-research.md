# Dictus Android testing and validation policy

## Decision

Dictus should use a **three-lane evidence policy**, not a blanket “manual QA” rule:

1. **Agent-autonomous, host/emulator:** the default and fastest lane for every change.
2. **Agent-autonomous, physical Pixel:** mandatory when the claim depends on real microphone hardware, ARM-only native ASR, end-user performance, or whole-system IME behavior. “Physical device” does **not** imply “manual test”; a Pixel reachable through ADB or device-farm instrumentation can be operated and evaluated remotely.
3. **Human product validation:** reserved for genuine product ambiguity, intentional divergence from an approved reference, subjective ergonomics, and public-release acceptance.

This follows Android’s distinction between fast local tests and higher-fidelity instrumented tests. Android says automation is faster and more repeatable than manual testing, local tests are normally small and fast, and instrumented tests run on physical or emulated Android devices.[1] It recommends unit tests for ViewModels, repositories/data, domain logic, utilities, and edge cases, plus screen and common-navigation UI tests.[2] Instrumented tests provide more fidelity but are slower, so Android recommends them when device behavior is actually required.[3]

## Proposed validation matrix

| Dictus claim / change | Agent on host or emulator | Agent on physical Pixel | Human product owner |
|---|---|---|---|
| Buildability, lint/static checks, unit tests | **Required on every change** | No | No |
| ViewModel/state/repository/model metadata, file and error logic | **Required:** deterministic JVM tests, including denial, corruption, low-storage and recreation cases where applicable | Only for framework integration | No |
| Compose screen behavior and navigation | **Required:** Compose instrumented tests on emulator; actions/assertions through semantics | Sample critical flow before release or after platform-specific failures | Only if interaction intent changes |
| Compose rendering, themes, font scale, locale, compact/expanded sizes | **Required:** small golden set plus screenshot diffs in a stable CI environment | Device screenshots as a supplemental check | **Approve a new/changed golden** when the visual change is intentional |
| Permission allow/deny and app-level recording state machine | **Required:** UI/state coverage with fakes or injected test audio | **Required** for actual microphone acquisition and lifecycle | Only permission/trust wording and experience |
| Custom IME lifecycle, editor types, commit/cursor behavior | Emulator coverage for deterministic lifecycle and protocol cases | **Required** across real system settings and representative target apps | Only ergonomics, discoverability, and trust |
| Offline Whisper/ASR correctness with fixed WAV corpus | Host test if the core is host-runnable; otherwise ARM device instrumentation | **Required** because Dictus ships ARM-only native inference; verify model load, inference, cancellation, repeated use, memory, and fully offline operation | Only acceptance of real-world language quality |
| Microphone capture quality and interruptions | Emulator may prove wiring only; not a release claim | **Required:** real speech, silence, noisy room, headset/Bluetooth if supported, permission revocation, calls/audio focus as applicable | Listen/use it when judging product quality |
| Startup, recording-to-text latency, jank, memory/thermal stability | Smoke/dry-run only; emulator numbers are non-gating | **Required:** release-like build and repeatable benchmark on the same Pixel model/OS | Perceived responsiveness only; objective threshold remains automated |
| Broad API/device compatibility | Emulator matrix first | Firebase Test Lab physical matrix for selected regressions | No |
| iOS parity and product acceptance | Agent supplies side-by-side screenshots, flow recordings, and diffs; the approved iOS behavior is the default oracle | Agent captures Pixel evidence | Only when intent is ambiguous or Android should deliberately diverge |

## Lane 1 — fully autonomous host/emulator gate

### Required on every pull request

- Build the exact commit and run lint/static analysis and all JVM tests.
- Unit-test state reducers/ViewModels, repositories, preferences/model metadata, file handling, cancellation, error mapping, and deterministic text-processing logic. Include edge cases rather than relying on exploratory testing; Android explicitly notes that edge cases are where unit tests outperform humans and larger flows.[2]
- Run a focused emulator instrumentation suite for each changed screen and one adjacent critical flow. Compose provides APIs to find semantic elements, assert attributes, perform actions, and manipulate time.[4] Compose actions/assertions synchronize with the UI by default; background work that Compose cannot observe needs a registered idling resource or an explicit test hook, not sleeps.[5]
- Cover permission grant/deny, process/activity recreation, portrait/landscape, supported locales, font scale, and at least compact plus expanded layout bounds when relevant.
- Maintain a **minimal** set of golden screenshots for high-value screens/states. Android calls screenshot testing the recommended way to verify Compose visual attributes, but warns against combinatorial golden growth and platform-dependent rendering; generate/compare goldens in one pinned CI environment.[6]
- Use UI Automator when the flow leaves the Dictus process—for example Android Settings, the IME picker, another app’s editor, or system permission UI. UI Automator is specifically designed to interact with user and system apps from outside the target process and supports screenshots and explicit stability waits.[7]

### What this lane may approve by itself

The agent may approve and merge a change when all applicable gates are green. Hardware-sensitive changes add the physical-Pixel lane; they do not automatically add a human gate. Human approval is required only when product intent is ambiguous, an approved visual or interaction baseline changes deliberately, or a public release is being authorized.

The Android Emulator is the default device for breadth: Android describes it as high-fidelity, able to simulate many API levels/configurations and most real-device capabilities, and says it is the best option for most testing needs.[8] That breadth is evidence of functional compatibility—not evidence of real microphone quality, ARM-native execution, or user-perceived performance.

## Lane 2 — agent-run physical Pixel gate

A dedicated Pixel (local USB/Wi-Fi ADB, lab host, or suitable physical-device service) should be treated as CI infrastructure. The agent installs the exact APK, resets or records device state, runs instrumentation/UI Automator/benchmark commands, captures logcat, screenshots/video, test XML, benchmark JSON/traces, and reports commit + device model + OS.

### Trigger conditions

Require this lane for any change touching:

- `InputMethodService`, `InputConnection`, editor handling, IME switching, keyboard window/lifecycle, or cross-app text commit;
- microphone permission/capture, audio source/format, foreground/background lifecycle, interruptions or routing;
- native Whisper/ASR binaries, model loading, tokenization/decoding, memory ownership, ABI/NDK/CMake, or performance-sensitive inference;
- startup, animation/jank, recording-to-first-text latency, sustained inference, memory or thermal behavior;
- a release candidate, even if no hardware-sensitive files changed.

### Minimum physical-Pixel script

1. Install the release-like candidate from a clean state; verify launch and inspect logcat for crashes/ANRs.
2. Enable/select Dictus through the real system IME path. Exercise text, multiline, search, URL/email, and password-class editors in multiple apps; verify cursor replacement, commit/cancel, back, rotation, app switching, and switching to/from another IME. Android’s IME contract spans `InputMethodService`, `EditorInfo`, multiple input types, `InputConnection`, orientations, and system IME switching, so an in-app keyboard preview is insufficient.[14]
3. Grant, deny, revoke, and re-grant microphone permission. Record real audio, stop/cancel, background/foreground, lock/unlock, rotate, and repeat. Android classifies `RECORD_AUDIO` as a dangerous runtime permission, notes hardware/audio-source differences, and restricts background microphone access on Android 9+ unless recording is in the foreground or a foreground service is used.[15]
4. With radios/network disabled, load the shipped model and run a fixed audio corpus plus one live sample. Record success/failure, transcript, latency, peak memory, repeated-run behavior, cancellation, and model reload. This is the release evidence for Dictus’s ARM-only offline path.
5. Run Macrobenchmark (or an equivalent instrumented measurement) against a non-debuggable, release-like build for startup and the critical record→transcribe→commit flow. Android strongly discourages emulator benchmark numbers because they reflect host hardware rather than realistic user experience and tells CI users to benchmark on physical devices.[10][11]

The emulator’s current extended controls can forward the host microphone when explicitly enabled,[9] while the MediaRecorder guide still says the emulator cannot record audio and directs developers to a real device.[15] The safe policy is therefore: emulator audio may validate plumbing, but **never closes** a microphone, acoustic-quality, latency, or release gate.

### Firebase Test Lab role

Use Firebase Test Lab for scripted compatibility breadth, not as a substitute for the dedicated microphone/ASR Pixel. Test Lab runs instrumentation tests (Espresso/UI Automator) on selected physical or virtual device matrices and is scriptable with `gcloud`; its docs explicitly say lab devices can reveal issues missed by Android Studio emulators.[12] It can also retain instrumentation screenshots.[13] Because the cited Test Lab docs do not guarantee controllable real microphone input or Dictus’s required audio-routing conditions, a generic Test Lab pass is not microphone evidence.

## Lane 3 — human product validation

Human review should be requested only where no objective oracle exists:

- **First-use trust and comprehension:** Is enabling a third-party keyboard and granting microphone access understandable, reassuring, and appropriately private?
- **Visual/functional intent:** Does Android intentionally match the existing iOS app/design where it should, while still feeling native to Android? A product owner approves deliberate baseline changes; automation detects future drift.
- **Keyboard ergonomics:** Is the IME comfortable, discoverable, appropriately sized, and easy to switch away from in normal use?
- **Perceived responsiveness:** Does record→feedback→text feel immediate and stable, in addition to meeting measured thresholds?
- **Speech usefulness:** When a model, language, or decoding policy changes materially, are real-world transcripts acceptable across representative voices, pacing, punctuation expectations, and noise?
- **Release acceptance:** One short scripted “day-in-the-life” pass on the signed candidate after automated and physical gates are already green.

Humans should **not** be asked to re-check deterministic regressions, enumerate device configurations, inspect logs, compare pixels manually, or prove microphone/IME/native execution. When human review is triggered, provide a candidate with a one-page evidence bundle: commit/APK, green gate summary, Pixel model/OS, benchmark deltas, representative screenshot/iOS diffs, known limitations, and a 5–10 minute subjective checklist.

## Enforcement rules

- **No evidence, no claim:** an emulator pass cannot be reported as microphone, ARM-native, or representative performance coverage.
- **Physical does not mean manual:** default owner of the physical-Pixel lane is the agent.
- **Human approval is sticky:** once a visual or interaction baseline is approved, automated tests own regression detection until intent changes.
- **Risk-based cadence:** host/emulator on every PR; physical Pixel on trigger changes and nightly/release; small Firebase matrix nightly or pre-release; broader matrix before public releases.
- **Release gate:** all host/emulator checks green, all triggered Pixel checks green on the exact candidate, no unexplained crash/ANR, benchmark thresholds met, and a short product-owner sign-off completed before a public release.

## Sources

[1] https://developer.android.com/training/testing/fundamentals — Fundamentals of testing Android apps
[2] https://developer.android.com/training/testing/fundamentals/what-to-test — What to test in Android
[3] https://developer.android.com/training/testing/instrumented-tests — Build instrumented tests
[4] https://developer.android.com/develop/ui/compose/testing — Test your Compose layout
[5] https://developer.android.com/develop/ui/compose/testing/synchronization — Synchronize your Compose tests
[6] https://developer.android.com/training/testing/ui-tests/screenshot — Screenshot testing
[7] https://developer.android.com/training/testing/other-components/ui-automator — Write automated tests with UI Automator
[8] https://developer.android.com/studio/run/emulator — Run apps on the Android Emulator
[9] https://developer.android.com/studio/run/emulator-extended-controls — Android Emulator extended controls
[10] https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci — Benchmark in Continuous Integration
[11] https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview — Write a Macrobenchmark
[12] https://firebase.google.com/docs/test-lab/android/get-started — Get started testing for Android with Firebase Test Lab
[13] https://firebase.google.com/docs/test-lab/android/instrumentation-test — Get started with Test Lab instrumentation tests
[14] https://developer.android.com/guide/topics/text/creating-input-method — Create an input method
[15] https://developer.android.com/media/platform/mediarecorder — MediaRecorder overview
