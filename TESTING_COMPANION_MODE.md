# Companion Mode validation

## Current evidence record

Validation date: 2026-10-15. ARM builds, all-module tests/lint and the final combined 22-test API 35 instrumentation suite passed. A prior clean build also passed. Physical Samsung insertion retesting and per-app validation remain separate.

| Gate | Result | Evidence |
| --- | --- | --- |
| Toolchain | PASS | JDK 17; Gradle 8.11.1/AGP 8.7.3; SDK 35; NDK 27.2.12479018; CMake 3.22.1. Portable setup in ignored `.tools/`. |
| JVM tests | PASS | 607 tests: app 298, core 72, IME 237. Includes Samsung editing-span regressions. XML reports under each module's `build/test-results/testDebugUnitTest/`. |
| Debug and instrumentation APKs | PASS | Clean default ARM build; `app/build/outputs/apk/debug/app-debug.apk`. Instrumentation APK also assembled; the emulator test variant is separate. |
| Lint | PASS | All-module `lint` passed in the clean build; per-module `build/reports/lint-results-debug.html`. |
| Native accessibility actions | PASS | API 35 Google APIs emulator: twelve real native-editor cursor/protection tests, including an actual input-connection composing underline and rejection of a committed underline. |
| Enabled accessibility-service activation | PASS | Intentional activity return with insertion, real microphone startup with notifications enabled, unrelated-focus invalidation, and insertion into a separately installed application's native editor with Gboard visible before activation. |
| Cross-application native insertion | PASS on API 35 | The test APK owns an exported editor activity with a verified different UID/process from Dictus. Actual activation return and accessibility actions replaced selected emoji, preserved prefix/suffix, placed the cursor and preserved the default IME. This fixture is not an external-product compatibility claim. |
| Microphone/FGS lifecycle | PASS | Visible activation, actual capture configuration, cancellation, legacy-owner exclusion, unchanged Gboard default. No microphone-quality claim. |
| Offline Whisper/provider reuse | PASS | Native x86_64 build, airplane mode/Wi-Fi off, existing Tiny model/JFK fixture, repeated inference and automatic language detection. ARM translation aborted in libomp and is not an inference test route. |
| Overlay lifecycle | PASS | Actual non-focusable window, drag, saved position after hide/show, landscape bounds and hide. |
| Galaxy S24 Ultra + Samsung Keyboard | RETEST PENDING | Device owner corrected the requested phone to SM-S928U1. Android 16/API 36; Samsung Keyboard remained selected and local native Whisper recognition passed. Initial native-field insertion failed. The span correction is implemented, but the owner disconnected the phone before installing/retesting it; the external-app matrix remains open. |
| Gboard/SwiftKey + external apps | NOT RUN | Emulator Gboard default preservation is not Messages/Gmail/Chrome/ChatGPT integration evidence. |
| Quick Settings | PASS on API 35 | Actual SystemUI tile reached through semantic page navigation; tile click launched visible capture, second click stopped capture/transcription and released the microphone. Other OS/OEM versions remain unverified. |
| Notification action delivery | PASS on API 35 | Actual ongoing notification title/actions inspected; Stop and Cancel PendingIntents dispatched against real capture and microphone release verified. Visual shade taps and OEM behavior remain unverified. No pinned-launcher or hardware shortcut is offered. |

For every run, record revision/build ID, date, tester, device/API/One UI, keyboard version, target app/editor, model/provider/language, permission state, entry point, expected/actual behavior, and evidence. Use synthetic dictation and synthetic editor text when collecting screenshots or logs. Mark a case PASS, FAIL, BLOCKED, or NOT RUN; leave it PENDING until execution.

## Automated coverage inventory and commands

### Acceptance audit after phone disconnection

The implementation is ready for further device testing; the original acceptance criteria are **not all closed**. This audit distinguishes implemented behavior from the scope of its evidence.

| Original acceptance criterion | Current evidence and remaining gate |
| --- | --- |
| A — Independent keyboard operation | Samsung Keyboard remained selected during ordinary live transcription on S24 Ultra. API 35 tests preserve Gboard selection during actual capture and external native-editor insertion. Other keyboard/app combinations remain pending. |
| B — Offline transcription | Existing Tiny Whisper provider and reuse exercised on ARM phone and native x86_64 emulator; emulator inference ran with airplane mode and Wi-Fi off. Shared Parakeet integration is implemented, but Companion Parakeet runtime recognition remains unverified. |
| C — Floating microphone | API 35 actual window/drag/persistence/rotation/hide test passed. Owner observed the bubble and used it for live recognition on S24 Ultra. Samsung cutout/navigation/lifecycle variations remain pending. |
| D — Automatic insertion | Twelve native action tests and four enabled-service focus tests passed on API 35, including separate-app UID, selection replacement and composing underline. Initial Samsung insertion failed. Corrected APK must be installed and tested on S24 Ultra before closing this criterion. |
| E — Graceful failure | Policy/controller tests cover stale targets, protected/unsupported fields, retained results, uncertain writes and no replay. Native rich/protected/changed-field cases passed. OEM revocation and interruption matrix still needs device execution. |
| F — Original keyboard preserved | 237 IME JVM tests passed; shared recording ownership exercised with actual capture. Full legacy Dictus IME typing/dictation regression on the phone remains pending. |
| G — Privacy | Source limits access to explicitly captured native editor, RAM-only snapshots/results, no automatic clipboard writes or new cloud/telemetry path. Debug logs emit generic enum codes. Runtime third-party editor/clipboard/history matrix remains pending; no external privacy certification is claimed. |
| H — Build and tests | Final default ARM build, 607 JVM tests, all-module lint and 22 API 35 instrumentation tests passed. Both final APKs pass packaged 64-bit ELF/ZIP alignment checks; actual 16 KB-page hardware runtime remains unverified. |
| I — Documentation | README setup plus COMPANION_MODE.md architecture/permissions/limits and this test matrix are present. Screenshots record actual onboarding/setup, not fabricated successful insertion. |
| J — Usable deliverable | Standard ARM APK and separate Dictus Companion Test APK are built, hashed and listed below. The corrected side build is not yet installed on the disconnected phone. |

Onboarding/settings, controller states, entry points, foreground-service admission, insertion policy, recognition reuse, and failure handling have source and automated coverage described below. API 29–34 runtime, incoming calls, OEM process termination/permission revocation, and the named Messages/Gmail/Chrome/ChatGPT matrix are not established by the API 35 fixture. The primary next gate is the corrected Samsung native-field test, followed by that physical matrix; no test or documentation update substitutes for those observations.

JVM suites exercise policy and controller behavior. Instrumentation additionally exercises actual native text actions, the enabled service and foreground microphone acquisition. These results do not establish Samsung event ordering or compatibility with every external editor.

| Suite | Source-level coverage |
| --- | --- |
| `CompanionSessionTest` | Full success state sequence; cancellation while preparing/recording/transcribing; begin and recognition errors; blank results; rapid repeated start/stop including job-completion callbacks; retained Copy result blocks new sessions until dismissal; successful retry once; throwing insertion/retry retains text; obsolete preparation/recognition completions cannot alter or insert into replacement sessions; obsolete completion cannot release the new ownership; recording/transcription capture failures clean up once and reject late results; repeated failure is inert for idle/completed sessions, including retained Copy results. |
| `TextInsertionManagerTest` | Cursor and reversed-range insertion, prefix/suffix preservation, supplementary Unicode/UTF-16 checks, sensitive rejection before text reads, identity/text/selection changes, single-use targets, unavailable/unsupported editors, partial writes, failed verification/cursor placement, exceptions before/after write. |
| `CompanionAccessibilityServiceTest` | Native plain-editor snapshot, password/sensitive and numeric/OTP rejection, rich/unknown editor rejection, invalid editor state/actions, focus invalidation and interruption. The actual activation round trip is additionally covered by `ServiceFocusRecoveryTest`. |
| `CompanionSettingsSectionTest` | Models reachability before enablement; choosing Copy dismisses accessibility consent without opening system Accessibility settings. |
| `NativeCursorInsertionTest`, `ServiceFocusRecoveryTest` | Actual Android set-text/selection, UTF-16, protection/rich rejection, actual service activation return and unrelated-focus invalidation. A separate test-application UID/process verifies cross-application selection replacement with the regular keyboard visible before activation. Its exported activity exists only in the instrumentation APK. |
| `CompanionRecordingLifecycleTest`, `CompanionOverlayLifecycleTest` | Actual capture/cancel/shared ownership/default-IME preservation, notification Stop/Cancel delivery and SystemUI tile start/stop; real floating window, dragging, persistence, rotation and hiding. Consult the result table for execution status. |
| `OfflineWhisperPipelineTest` | Opt-in native Tiny/JFK inference through the existing provider slot, reuse, explicit English and automatic language; requires local fixtures. |
| `DictationRecorderTest` | Upstream single-capture ownership, plus a microphone that fails to start leaving Idle. Companion/legacy exclusivity is now a main-thread lease in `DictationService` (replacing the former token class) and needs re-verification in the instrumentation suite. Former note: exclusivity, pending starts, duplicate requests, stale tokens, transcription exclusivity, release waits for cleanup, retry after failed capture start, destruction. |
| `AudioCaptureManagerTest`, `RecordingDurationPolicyTest`, `SttProviderSlotTest` | Capture cleanup/final-read behavior and energy snapshots, short-clip policy, shared provider reuse/model replacement, serialized use, initialization failures, idle expiry and release. |

From the repository root, after tool setup, run and capture results:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "dev.pivisolutions.dictus.companion.*" --tests "dev.pivisolutions.dictus.service.DictationRecorderTest" --tests "dev.pivisolutions.dictus.service.AudioCaptureManagerTest" --tests "dev.pivisolutions.dictus.service.RecordingDurationPolicyTest" --tests "dev.pivisolutions.dictus.service.SttProviderSlotTest"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :core:testDebugUnitTest :ime:testDebugUnitTest :app:lintDebug
```

Use `./gradlew` on Unix. Preserve dependency/toolchain errors as blockers rather than test failures. Default app unit-test reports are under `app/build/reports/tests/testDebugUnitTest/` and `app/build/test-results/testDebugUnitTest/`; the usual debug APK is under `app/build/outputs/apk/debug/`. Confirm generated paths before recording artifacts. Normal builds retain the ARM defaults; the optional test ABI property below is for an emulator only.

## Reproduce emulator instrumentation

Use an unlocked API 35 Google APIs x86_64 emulator. Seed a locally downloaded Tiny model and the repository's JFK sample. The script checks transfer hashes and handles Windows binary-pipe limitations without root. On a physical ARM device, omit the test ABI property and use the normal build.

```powershell
.\gradlew.bat -Pdictus.emulatorAbi=true :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python scripts/seed_companion_test_fixtures.py --serial emulator-5554 --model /path/to/ggml-tiny.bin --audio third_party/whisper.cpp/samples/jfk.wav
adb -s emulator-5554 shell cmd connectivity airplane-mode enable
adb -s emulator-5554 shell svc wifi disable
adb -s emulator-5554 shell am instrument -w -r -e offlineModel /data/user/0/dev.pivisolutions.dictus/files/models/ggml-tiny.bin -e offlineAudio /data/user/0/dev.pivisolutions.dictus/files/companion-tests/jfk.wav dev.pivisolutions.dictus.test/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (… tests)` and no failure/crash result: `am instrument` may return shell exit 0 on failure. The service/overlay tests temporarily configure accessibility or overlay app-ops through test-only shell privileges and restore them afterward. Production always requires user consent and Android permission screens. Offline/capture tests skip if fixtures are absent. These tests cannot establish speech quality, native ARM performance, Samsung One UI behavior or protected-app compatibility.

## Manual setup and permission matrix

The primary target is a **real Samsung Galaxy S24 Ultra with Samsung Keyboard selected**, as clarified by the device owner. The SM-S928U1 was disconnected at the owner's request; corrected-build insertion retesting and the app matrix are pending. Gboard is the secondary keyboard target; repeat applicable rows with Gboard and Dictus IME to detect interference. Keep the default keyboard/version recorded. Use the smallest downloaded model for initial smoke tests, then repeat recognition with Whisper and Parakeet where supported.

Every row below is **PENDING**. Expected outcomes are acceptance criteria, not observations.

| ID | Action / variation | Expected outcome |
| --- | --- | --- |
| S1 | Fresh install; choose Companion onboarding; finish all steps and revisit Settings | Models and setup remain reachable; usual keyboard stays selected; no requirement to enable Dictus IME; onboarding does not start capture by itself. |
| S2 | Decline automatic-insertion disclosure; leave accessibility and overlay off | Copy mode remains usable via tile/settings launch; no Accessibility settings launch or forced grant. |
| S3 | Accept insertion disclosure; enable Android service; return; disable it again | In-app disclosure precedes system settings; indicators refresh; revocation fails closed to Copy. |
| S4 | Grant/deny microphone; deny permanently; revoke during active recording | Helpful recovery, no unauthorized capture; active failure cleans up into an error; a new recording requires explicit action. |
| S5 | Grant/deny notifications; disable recording/result channels | Behavior is honest about unavailable shade controls/reminders; other reachable controls work; no invisible silent restart. |
| S6 | Deny/grant/revoke overlay; switch floating control and Companion off | Tile/Copy remain alternatives; overlay appears only with opt-in and permission; disablement hides it and cancels busy work as appropriate. |
| S7 | No model; missing/deleted active model; change model/language; airplane mode after download | Actionable failure for missing model; no session-triggered download; downloaded model transcribes locally with shared selection. |
| S8 | Toggle global haptics/sounds from Companion and existing settings | Shared preferences are respected across dictation entry points. |

## Manual controls and lifecycle matrix

| ID | Action / variation | Expected outcome |
| --- | --- | --- |
| C1 | Floating button start and stop in an external field | Visible activation before microphone capture; returns after acknowledged capture; recording notification/indicator present; stop transcribes once. |
| C2 | Tile addition, start/stop, removal; shade open/close; API-specific tile launch | User action launches visible activation; repeat during busy states does not duplicate work; shade/focus uncertainty falls back to Copy. |
| C3 | Use notification shortcut actions | Stop/Cancel control the same session. Pinned launcher and hardware shortcuts are intentionally not offered. |
| C4 | Notification Stop, Cancel, overlay-controls tap, and result tap | Correct current session controlled; no accidental new recording; result opens without revealing transcript in notification text. |
| C5 | Rapid repeated taps across overlay, tile, and notification; stop before ready; repeat stop | One active capture/transcription; one verified insertion at most; no stale callback affects replacement work. |
| C6 | Cancel during preparation, recording, and transcription; force a delayed native completion | Capture stops, result discarded, no late insert; obsolete cleanup cannot release new ownership. |
| C7 | Lock/screen-off while preparing, recording, transcribing; attempt tile while locked; unlock | Overlay hidden/locked activation rejected; active capture/failure cleanup propagated; no late insertion or automatic restart on unlock. |
| C8 | Rotate, recreate activation/controls, switch apps, press Home/Back during binding | No recording restart; visibility rechecked before capture; retained result preserved only while process survives; unrelated focus invalidates insertion. |
| C9 | Drag control to each edge; rotate; gesture/button navigation; cutout; landscape | Control remains reachable and non-focusable; saved side/height clamped; long press opens controls; Hide removes it. |
| C10 | Start Dictus IME or recording-screen dictation while Companion pending/active, and reverse | Shared ownership rejects overlap; existing dictation unaffected after proper cleanup. |
| C11 | Another app owns mic; incoming call/audio interruption; recorder read failure | Actionable failure/cancellation and no lingering capture; no stale insertion; retry only on explicit user action. |
| C12 | Kill process/force-stop during capture, transcription, and retained-result display; relaunch | Audio ends; uncopied ephemeral result lost; no recovery claim, history write, or silent recording restart. |
| C13 | Short tap/clip below 500 ms; silent recording held longer; long speech | Short-clip policy enforced; **no silence/VAD auto-stop**; explicit Stop required; transcription timeout/failure releases ownership. |
| C14 | Repeat dictation within 10 minutes, idle past 10 minutes, switch model, unbind/destroy service | Same ready model reused only while service lives; idle lease expires without racing inference; switch/destruction releases provider; no guaranteed warm lifetime. Record timing/memory evidence. |

## Manual editor and result matrix

For each editor case, record both the final field contents and cursor/selection. A safe Copy fallback is acceptable for an unsupported editor; a write to the wrong field or duplicated content is a failure. Check the destination before manual paste after any unverified insertion.

| ID | Action / variation | Expected outcome |
| --- | --- | --- |
| E1 | Settings native test `EditText` and a separate app's plain native `EditText`; empty field/end cursor | Optional insertion only after target and activation handshake verification; otherwise retained Copy. No device success inferred from the built-in field alone. |
| E2 | Middle cursor; forward/reversed selection; multiline text | Prefix/suffix preserved; selection replaced once; caret immediately after transcript if insertion verified. |
| E3 | Emoji/supplementary Unicode, accented text, punctuation, line breaks | UTF-16 boundaries respected; no split surrogates/corruption; expected post-processing recorded. |
| E4 | Password, PIN, OTP, ordinary numeric input, visible/web password, accessibility-sensitive input; password/sensitive ancestors | All numeric fields rejected for OTP safety even without password metadata; no automatic insertion or sensitive text read; transcription available through explicit Copy. |
| E5 | WebView/browser contenteditable, including a focused node reporting `android.widget.EditText` beneath a WebView ancestor; rich/spanned editor; Compose/custom/virtual editor | WebView ancestor and unsupported editor checks reject automatic insertion; no automatic whole-field rewrite; retain Copy. Do not advertise universal app compatibility. |
| E6 | Notes, messaging composer, email composer, browser/search field with Samsung Keyboard first, then Gboard | Record actual editor class/exposed actions where available, version, outcome and cursor; unsupported fields retain Copy; no app-level support claim from one field. |
| E7 | Move cursor, edit text, switch fields/apps/windows, dismiss/reopen keyboard during recording | Snapshot/identity changes reject insertion even if returning later; transcript retained; no write into changed target. |
| E8 | Activation window round trip with original focus restored; unrelated own-app window, shade, popup or permission dialog | Only exact bracketed activation return may preserve capability; ambiguous transitions invalidate it. Validate on Galaxy S24 Ultra/Samsung Keyboard first, then Gboard. |
| E9 | Accessibility disabled/disconnected mid-session; target removed, invisible, disabled or actions missing | Fail closed; no stale node use; retained Copy result. |
| E10 | Editor ignores/transforms set-text; cursor action fails; exception after a write | Unverified/partial outcome retains transcript with inspect-before-paste warning; no automatic retry or duplicate write. |
| E11 | Copy mode, unavailable target, home/settings/shortcut launch without external target | Result controls retain text in memory; clipboard unchanged until explicit Copy; manual paste only. |
| E12 | Retained result: repeated start/stop, notification dismissal, hide overlay, Copy, explicit result dismissal | New session blocked while transcript retained; Copy does not silently clear it; explicit dismissal clears session result; notification dismissal alone is not durable deletion/storage. |
| E13 | Process death with retained result; check history, clipboard, notifications and logs | Ephemeral result lost; no Companion history entry or automatic clipboard write; notification/log bodies do not expose dictated/field text. Explicitly copied text is outside session retention. |
| E14 | Insertion succeeds, then duplicate notification/tile/stop actions | No second insertion or replay; next session requires an explicit start. |

## Updating validation results

Replace pending entries only with evidence from the exact final revision. Include known limitations and failing matrix IDs in the release decision. In particular, real **Galaxy S24 Ultra/Samsung Keyboard insertion and focus-transition behavior remain unverified** until tested on that device. Gboard is a secondary validation target. Emulator/unit-test outcomes cannot close the physical-device gate. Update shortcut availability and failure-path/handshake coverage as integration changes are completed.

## Recorded build and environment

- Base repository revision: `bd142df6b23cfe2d76ebe407029e114b2266c6d0`, plus the uncommitted Companion implementation. No commit or push was performed.
- Clean host command: `gradlew.bat clean testDebugUnitTest assembleDebug lint :app:assembleDebugAndroidTest --offline --console=plain --no-daemon --continue` — **PASS**, 382 Gradle tasks, 603 JVM tests and zero failures.
- Latest default validation: `gradlew.bat testDebugUnitTest assembleDebug :app:assembleDebugAndroidTest lint --offline --console=plain --no-daemon` — **PASS**, 607 JVM tests, zero failures, all-module lint and both APKs. Log: `.tools/logs/final-default-span-validation.log`.
- Default device APK: `app/build/outputs/apk/debug/app-debug.apk`, 166,885,250 bytes. Its Whisper/trie libraries were inspected and contain `arm64-v8a` and `armeabi-v7a`, with no x86 Whisper/trie libraries.
- Default APK SHA-256: `dc4646fd46171f4c79552346635fcc7fa60659e83f7635ee1627710e344e590b`.
- Corrected side-by-side APK: `app/build/outputs/apk/companionTest/app-companion-test.apk`, 166,027,256 bytes; package `dev.pivisolutions.dictus.companiontest`, label **Dictus Companion Test**. SHA-256: `e600aaec8ca83e13dedbe382955ba183ad4d00f427d563a2574f929f39bbb11c`. Build/test/lint command: `gradlew.bat -PdictusSideBySide=true testDebugUnitTest assembleDebug lint --offline --console=plain --no-daemon`; 607 JVM tests passed. Both final APKs pass `scripts/check_android_page_alignment.py`. This corrected side build has not been installed or retested on the disconnected phone.
- Emulator: `google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys`, API 35. Native x86_64 test build used the explicit test ABI property. The final device APK uses default ARM settings.
- Gboard: `14.2.09.629370537-preload-x86_64`; default IME was unchanged by capture/cancellation. This establishes default-selection preservation in a native test field, not Gboard compatibility with Messages/Gmail/Chrome/ChatGPT.
- Offline fixture: Tiny Whisper, 77,691,713 bytes; the existing `third_party/whisper.cpp/samples/jfk.wav`. Fixtures were checksum-verified during transfer. Airplane mode was enabled and Wi-Fi disabled for native inference.
- The final combined **22-test instrumentation suite passed** in 78.870 seconds, including the composing/committed underline regressions, actual SystemUI Quick Settings start/stop, notification Stop/Cancel PendingIntents, armed-target recovery during microphone startup, cross-application native insertion, overlay lifecycle and native offline inference. Its log is `.tools/logs/final-emulator-span-22.log`.
- Earlier 22-test attempts failed intermittently in the test harness. The external-editor test now invokes the production MainThread insertion API on the main thread and explicitly finishes its separate activity; the Quick Settings harness retries expansion only when SystemUI exposes no expanded pager/tile, and searches from the first page. The final complete run above passed after those corrections. These fixes do not broaden production focus acceptance.
- Local detailed logs are retained in ignored `.tools/logs/`, including `final-side-span-validation.log`, `final-default-span-validation.log`, `final-emulator-span-22.log` and page-alignment results. Build reports remain in each module's `build/` directory.

Validation corrected a Windows-specific path assertion in `ModelManagerTest` without changing model storage behavior. Emulator execution also found and fixed rejection of native non-copying editing spans, empty-field hint handling, an inset callback that reset dragging, and premature target invalidation during the recording activity's return. Focus resolution now uses a single window root and waits through strictly identified activation transitions without authorizing insertion before the original editor returns. Temporary metadata tracing was removed. Test setup errors (ActivityScenario's unrelated EmptyActivity, stale node geometry, SystemUI tile readiness/page setup, and asserting broadcast cancellation before its asynchronous controller update) are distinguished from successful production-path checks above.

## Physical Samsung session

The owner confirmed the primary phone is a Galaxy S24 Ultra, replacing the S26 named in the initial request. Observed: SM-S928U1, Android 16/API 36, One UI build property `80500`, Samsung Keyboard component `com.samsung.android.honeyboard/.service.HoneyBoardService`; package reports version `5.9.30.97`. The phone uses 4 KB memory pages.

The existing Dictus 1.1.0 (version code 10100) was preserved. Android rejected replacing it with debug version code 1. A separate `-PdictusSideBySide=true` build installs as `dev.pivisolutions.dictus.companiontest`, label **Dictus Companion Test**, with independent data. Normal builds retain the original application ID. Component references use actual classes so the separate application ID does not break Accessibility, tile setup or the activation handshake. The local test APK is `.tools/artifacts/s24-companion-test-aligned.apk`.

The physical native `OfflineWhisperPipelineTest` passed (one test, six seconds) using the local Tiny/JFK fixtures, before the alignment fix. It verifies repeated recognition, provider reuse and automatic language detection on ARM; it does not test live microphone speech, and phone networking was not disabled. Log: `.tools/logs/s24-offline-whisper.log`.

Opening the initial debug APK exposed Android's 16 KB compatibility warning. ELF inspection found the project-built Whisper/ggml/trie libraries had 4 KB load alignment; bundled prebuilt libraries already passed. Both CMake builds now apply 16 KB linker alignment. `python scripts/check_android_page_alignment.py app/build/outputs/apk/debug/app-debug.apk` passes all packaged 64-bit ELF and ZIP checks. Opening the updated side-by-side app no longer displayed the warning. Runtime on an actual 16 KB-page device remains unverified.

The owner completed in-app permission setup, then restored Accessibility and the floating button after a test-runner interruption. Four physical `ServiceFocusRecoveryTest` cases timed out waiting for service connection and did not reach their assertions: Android instrumentation force-stopped the target app (`USER REQUESTED`, `start instr`/`finished inst`), and One UI placed its enabled Accessibility service in the disconnected/crashed-service list. No Java fatal exception was recorded for this app. These are **not passing physical service tests**. The optional `preserveDeviceAccessibility=true` argument avoids changing secure settings but cannot prevent the runner's process stop. Subsequent physical checks use the ordinary app flow rather than this harness. Logs: `.tools/logs/s24-service-focus-runtime.log`.

The owner confirmed the service is allowed and the floating button visible. The first ordinary live speech test preserved Samsung Keyboard and produced a retained Copy transcript, but **did not insert into the built-in native field**. A text-free debugger trace identified the failure before recording: native field rejection checks passed, but `plainEditorText` rejected its editing spans. The field exposed Android DynamicLayout/spell-check observers and an IME composing underline. The fix accepts those specific editing annotations while retaining rejection of committed underlines and other styles; regression tests cover the distinction. Physical verification of the corrected build is pending.

The temporary method-level debugger stalled the test app during diagnosis; the test package was stopped to release capture and the debugger was disconnected. Generic enum-only debug diagnostics now avoid that intrusive tracing. These debugger/harness interruptions are separate from the initial insertion failure, which the owner reproduced without a debugger. The named external-app matrix remains pending. Never send test messages or email; use synthetic text in controlled, unsent drafts.
