# Pitfalls Research

**Domain:** Android IME + on-device ML inference (voice dictation keyboard)
**Researched:** 2026-03-21
**Confidence:** HIGH (domain-specific issues verified across multiple sources)

## Critical Pitfalls

### Pitfall 1: ComposeView in InputMethodService Crashes Without Manual Lifecycle Wiring

**What goes wrong:**
Using Jetpack Compose inside `InputMethodService.onCreateInputView()` throws `IllegalStateException: Composed into the View which doesn't propagate ViewTreeLifecycleOwner!`. The keyboard never renders. This is the single most common blocker for Compose-based IMEs.

**Why it happens:**
`InputMethodService` is a `Service`, not an `Activity`. It does not implement `LifecycleOwner`, `ViewModelStoreOwner`, or `SavedStateRegistryOwner`. Compose requires all three to function. Unlike Activities/Fragments where this is automatic, in a Service you must wire it manually.

**How to avoid:**
Make `DictusIME` implement `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`. In `onCreateInputView()`, create a `ComposeView` and attach all three tree owners before setting content:

```kotlin
class DictusIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DictusIME)
            setViewTreeViewModelStoreOwner(this@DictusIME)
            setViewTreeSavedStateRegistryOwner(this@DictusIME)
            setContent { KeyboardView() }
        }
    }
}
```

**Warning signs:**
- Crash on first keyboard display attempt
- `IllegalStateException` in logcat mentioning `ViewTreeLifecycleOwner`

**Phase to address:**
Phase 1 (IME scaffold). This must be the very first thing built and validated. Everything else depends on the keyboard rendering.

---

### Pitfall 2: Foreground Service startForeground() Race Condition and Background Restrictions

**What goes wrong:**
`ForegroundServiceStartNotAllowedException` crashes the app on Android 12+ (API 31+). Even on API 29-30, failing to call `startForeground()` within 5 seconds of `startForegroundService()` triggers an ANR and service kill.

**Why it happens:**
Three interacting issues:
1. Android 12+ forbids starting foreground services from the background. If the user taps the mic in the keyboard, then quickly switches apps, the service start may arrive when the app is "backgrounded."
2. The `startForeground()` call requires a valid notification channel and notification. If channel creation fails or is delayed, the 5-second window expires.
3. `foregroundServiceType="microphone"` must be declared in the manifest (Android 14+) or the service cannot access the mic at all.

**How to avoid:**
- Declare `android:foregroundServiceType="microphone"` in AndroidManifest.xml on the `<service>` element.
- Call `ServiceCompat.startForeground()` as the very first line of `onStartCommand()` -- before any model loading or audio setup.
- Create the notification channel in `Application.onCreate()`, not in the service.
- Use `try/catch` around `startForegroundService()` and fall back gracefully with a user message.
- For the IME-to-service flow: since the IME is in the foreground (user is typing), the exemption for "visible activity" applies. But verify this on API 31+ devices.

**Warning signs:**
- `ForegroundServiceStartNotAllowedException` in crash logs
- Service killed silently with no transcription result
- ANR dialog appearing after mic tap

**Phase to address:**
Phase 2 (audio recording service). Must be validated on API 31+ before moving to whisper.cpp integration.

---

### Pitfall 3: whisper.cpp Android Builds Are Poorly Maintained and Thread Misconfiguration Causes 10x Slowdowns

**What goes wrong:**
The official whisper.cpp Android example has known build issues, outdated CMake configurations, and produces extremely slow inference (30+ seconds for 3 seconds of audio) if thread count and build flags are wrong. Developers waste days debugging build failures and then get unusable performance.

**Why it happens:**
- The whisper.cpp mobile examples have been acknowledged as "lacking maintenance for a long time" by the maintainers.
- The default Android build may not enable ARM NEON optimizations or may use debug flags.
- Thread count defaults may not match the Pixel 4's big.LITTLE architecture (1+3+4 cores). Using all 8 threads causes contention on the efficiency cores.
- Building with `ndkBuild` (Android.mk) hits `CXX1405` errors. CMake is required.

**How to avoid:**
- Use CMake (not ndk-build) with explicit release flags: `-DCMAKE_BUILD_TYPE=Release -DGGML_NEON=ON`.
- Set thread count to 4 (targeting the 1 prime + 3 gold big cores on Snapdragon 855). Do NOT use `hardware_concurrency()` which returns 8.
- Pin to a specific whisper.cpp release tag (not main branch). Test the build before adding any JNI wrapper.
- Build the `.so` once with a standalone CMake invocation and verify inference speed with a test audio file BEFORE integrating into the app.
- Benchmark: `tiny` model on Pixel 4 should take ~1-2s for 10s audio. If you see >5s, the build is wrong.

**Warning signs:**
- CMake configure errors mentioning missing headers or `ggml` symbols
- Inference taking >10s for the `tiny` model on Pixel 4
- App using >90% CPU on all cores (should only saturate big cores)

**Phase to address:**
Phase 3 (whisper.cpp integration). Isolate the native build in a standalone module and validate performance before any JNI wrapper work.

---

### Pitfall 4: JNI Memory Leaks and Crashes from Mismanaged Native Pointers

**What goes wrong:**
The whisper context (`whisper_ctx*`) is allocated in C++ and referenced via a `Long` (pointer) in Kotlin. If the model is not properly freed on service destroy, or if transcription is called on a freed context, the app crashes with a SIGSEGV or leaks hundreds of MB of native memory.

**Why it happens:**
- JNI operates outside the JVM garbage collector. Native allocations must be manually freed.
- The whisper model (190-460MB) stays in native memory. If the service is restarted without freeing, a second copy is loaded.
- Android can kill and restart foreground services. If `onDestroy()` does not call `whisper_free()`, the native memory is orphaned.
- Passing audio data across JNI requires careful buffer management (`FloatArray` to `float*`).

**How to avoid:**
- Wrap the native pointer in a Kotlin class with `Closeable`/`AutoCloseable` semantics.
- Call `whisper_free()` in both `Service.onDestroy()` and in a `try/finally` around transcription.
- Use `GetFloatArrayElements()` with `JNI_ABORT` flag (no copy-back) for read-only audio buffers.
- Add a health check: before transcription, verify the context pointer is non-zero.
- Use `android:process` attribute on the service to isolate native memory in a separate process (optional but safer for large models).

**Warning signs:**
- Memory usage growing after each dictation session (check via `adb shell dumpsys meminfo`)
- `SIGSEGV` or `SIGABRT` in native crash logs
- `whisper_init` returning null (insufficient memory for second model load)

**Phase to address:**
Phase 3 (whisper.cpp integration). Design the JNI bridge with explicit lifecycle management from day one.

---

### Pitfall 5: IME-to-Service IPC Binding Fails Silently When App Process Is Dead

**What goes wrong:**
The IME calls `bindService()` to connect to `DictationService`, but if the main app process was killed by the system (low memory), the bind returns `false` or the `ServiceConnection.onServiceConnected()` never fires. The user taps the mic and nothing happens -- no error, no feedback.

**Why it happens:**
- On Android, the IME runs in the app's process by default. But if the app's activities have been destroyed and the process has been reclaimed, the service may not be available.
- `bindService()` with `BIND_AUTO_CREATE` will start the service, but only if the process can be started. Under memory pressure, this can fail.
- Unlike iOS (where the keyboard extension is a separate process and explicitly bridges to the main app), Android IMEs and their app services share a process -- until the system decides they don't.

**How to avoid:**
- Use `startForegroundService()` first, THEN `bindService()`. The foreground service keeps the process alive.
- Implement a retry mechanism: if `bindService()` returns false, show the user a "Tap to reconnect" message and retry.
- Handle `onServiceDisconnected()` -- this is called when the service crashes. Re-bind immediately.
- Consider using `startService()` + `bindService()` pattern (started + bound service) so the service survives even if the IME unbinds briefly.

**Warning signs:**
- Mic button tap does nothing (no recording starts)
- `onServiceConnected()` never called in debug logs
- Works after opening the main app, breaks after hours of keyboard-only use

**Phase to address:**
Phase 2 (IPC architecture). Design the binding strategy before implementing audio recording.

---

### Pitfall 6: GGML Model Files Corrupt or Incomplete After Interrupted Downloads

**What goes wrong:**
A 190-460MB model file is partially downloaded (network interruption, user closes app), saved to disk, and then `whisper_init_from_file()` crashes or returns null. The user sees "Model loaded" in the UI but transcription fails. Or worse: the partial file passes a size check but produces garbage transcription.

**Why it happens:**
- Downloading large files over mobile networks is inherently unreliable.
- Naive download implementations write directly to the final file path. If interrupted, the partial file looks like a valid model.
- HuggingFace CDN supports range requests (resume), but this must be explicitly implemented.
- No SHA-256 verification means corruption is undetectable.

**How to avoid:**
- Download to a `.tmp` file, rename to final path only after completion + hash verification.
- Implement HTTP range request resume: store `Content-Length` and bytes downloaded, send `Range` header on retry.
- Verify SHA-256 hash against the known hash from HuggingFace model card.
- Show download progress with percentage and allow cancel/retry.
- Before loading a model, verify file size matches expected size as a quick sanity check.

**Warning signs:**
- `whisper_init_from_file()` returns null
- Model file size does not match expected size
- Transcription produces empty string or garbage text
- Users report "model download stuck at 99%"

**Phase to address:**
Phase 4 (model manager). Build download-with-resume and hash verification before connecting to the inference pipeline.

---

### Pitfall 7: AudioRecord Buffer Underrun Produces Garbled Audio for Whisper

**What goes wrong:**
Whisper receives audio with gaps, clicks, or silence where speech should be. Transcription quality drops dramatically or produces hallucinations (whisper "invents" text for silence).

**Why it happens:**
- `AudioRecord` requires reading from the buffer fast enough. If the reading thread is blocked (e.g., by GC pause, IPC call, or disk write), samples are dropped.
- Using a buffer size smaller than `AudioRecord.getMinBufferSize()` causes immediate underruns.
- Whisper expects 16kHz mono 16-bit PCM. Recording at 44.1kHz and resampling incorrectly produces artifacts.
- Long recordings accumulate audio in memory. A 60-second recording at 16kHz 16-bit = ~1.9MB, manageable. But storing it as `FloatArray` doubles the size.

**How to avoid:**
- Use a dedicated high-priority thread (`Process.THREAD_PRIORITY_URGENT_AUDIO`) for the `AudioRecord` read loop.
- Set buffer size to `max(minBufferSize * 2, 16000 * 2)` (at least 1 second of buffer).
- Record directly at 16000 Hz sample rate (Pixel 4 supports this). Do NOT record at 44.1kHz and resample.
- Write audio to a ring buffer or file, not an ever-growing array.
- Add silence detection: if energy is below threshold for N seconds, stop automatically to avoid Whisper hallucination on silence.

**Warning signs:**
- `AudioRecord.read()` returning fewer bytes than requested
- Transcription contains repeated phrases or nonsense words (Whisper hallucination)
- Audio playback (if debug-enabled) sounds choppy

**Phase to address:**
Phase 2 (audio recording). Validate audio quality independently before connecting to whisper.cpp.

---

### Pitfall 8: Compose Recomposition Lag Makes Keyboard Feel Unresponsive

**What goes wrong:**
Key presses have visible delay (50-100ms+). The waveform animation stutters. The keyboard feels laggy compared to GBoard or SwiftKey, making users immediately switch back.

**Why it happens:**
- Each key press triggers recomposition of the entire keyboard if state is not properly scoped.
- The waveform view recomposes at 60fps, which can cause the keyboard layout to recompose too if they share state.
- Compose in an IME window does not get hardware-accelerated rendering by default on older Android versions.
- Measure/layout passes are expensive for a grid of 30+ key buttons.

**How to avoid:**
- Use `@Stable` or `@Immutable` annotations on key data classes to prevent unnecessary recomposition.
- Isolate the waveform `Canvas` from the keyboard layout -- they should not share any `State` objects.
- Use `derivedStateOf` for computed values (shift state, current layer) rather than raw state reads.
- Profile with Android Studio Layout Inspector's recomposition counter. Target: key press should trigger recomposition of 1-3 composables, not 30+.
- Consider `AndroidView` wrapping a custom `Canvas` for the waveform if Compose Canvas causes frame drops.
- Set `android:hardwareAccelerated="true"` on the IME service in the manifest.

**Warning signs:**
- Key press to character appearance >50ms (use systrace to measure)
- Recomposition count >5 per key press (Layout Inspector)
- Dropped frames visible in systrace during typing
- Waveform animation causing keyboard to stutter

**Phase to address:**
Phase 1 (keyboard UI) for key press performance. Phase 5 (waveform) for animation performance. Performance profiling should be a gate at each phase.

---

### Pitfall 9: InputConnection.commitText() Fails Silently in Certain Apps

**What goes wrong:**
Text insertion works in most apps but silently fails in specific apps (WebViews, games, React Native apps, some Samsung apps). The user dictates, sees "ready," but no text appears.

**Why it happens:**
- Some apps implement `InputConnection` incorrectly or use custom text fields that ignore `commitText()`.
- WebView-based apps may require `sendKeyEvent()` instead of `commitText()` for character-by-character insertion.
- The `InputConnection` can become stale if the target field loses focus during transcription.
- `getCurrentInputConnection()` can return null if the IME is in a transitional state.

**How to avoid:**
- Always check `getCurrentInputConnection() != null` before calling `commitText()`.
- After `commitText()`, verify by calling `getTextBeforeCursor()` to confirm insertion succeeded.
- Implement a fallback: if `commitText()` fails, try `sendKeyEvent()` with individual `KeyEvent` characters.
- Show the transcribed text in the IME's suggestion bar so the user can copy it manually as a last resort.
- Test with: Chrome, Gmail, WhatsApp, Samsung Messages, a WebView app, and a game with chat.

**Warning signs:**
- "It works in some apps but not others" user reports
- `getCurrentInputConnection()` returning null in logs
- Text appearing doubled or not at all

**Phase to address:**
Phase 1 (text insertion). Test `commitText()` across multiple apps early. Don't assume it works everywhere because it works in one test app.

---

### Pitfall 10: Model Loading Blocks the Main Thread and Triggers ANR

**What goes wrong:**
Loading a 190MB+ GGML model takes 1-5 seconds of CPU time and significant I/O. If done on the main thread (or on the service's `onStartCommand` without dispatching), Android shows an ANR dialog after 5 seconds.

**Why it happens:**
- `whisper_init_from_file()` is a blocking call that reads the entire model into memory and initializes compute buffers.
- Developers test with small models (tiny, 75MB) which load in <1s and seem fine. The issue only appears with the recommended `small` quantized model (190MB).
- The service's `onStartCommand()` runs on the main thread by default.

**How to avoid:**
- Load the model on a background coroutine (`Dispatchers.IO`) or a dedicated thread.
- Show a loading state in the keyboard UI ("Loading model...") during initialization.
- Pre-load the model when the app starts (if a model is selected), not on first mic tap.
- Keep the model loaded in memory between dictation sessions (the foreground service keeps the process alive).
- Add a timeout: if model loading takes >10s, log an error and suggest the user switch to a smaller model.

**Warning signs:**
- ANR dialog when tapping mic for the first time after boot
- StrictMode violations for disk I/O on main thread
- First dictation takes 5-10s longer than subsequent ones

**Phase to address:**
Phase 3 (whisper.cpp integration). Model loading strategy must be async from the start.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| SharedPreferences for IME-Service IPC instead of Bound Service | Faster to implement, simpler code | Race conditions, no type safety, polling required, no lifecycle awareness | Never for production -- use for a 1-day spike test only |
| Loading model on main thread | Fewer coroutine/thread concerns | ANR on larger models, poor first-use experience | Never |
| Skipping download hash verification | Faster download flow implementation | Corrupted models cause mysterious transcription failures | MVP only, must add before public release |
| Hardcoded keyboard height instead of measuring | Works on Pixel 4 | Breaks on every other device, broken in landscape mode | MVP only if targeting single device |
| Single-process architecture (no `android:process` on service) | Simpler debugging, shared memory | Native crash in whisper.cpp kills the keyboard too | Acceptable for MVP, revisit if native crashes are frequent |
| Using `View` instead of Compose for keyboard | Proven approach, better IME performance documentation | Two UI frameworks in the project, harder to maintain | Only if Compose performance is unacceptable after optimization |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| whisper.cpp JNI | Passing `String` model path without converting to absolute path | Use `context.filesDir.absolutePath + "/models/" + modelName` -- JNI receives the full path |
| AudioRecord | Creating with `CHANNEL_IN_STEREO` when whisper needs mono | Use `CHANNEL_IN_MONO` at 16000 Hz -- whisper.cpp expects single-channel 16kHz PCM |
| HuggingFace download | Using `DownloadManager` which cannot report fine-grained progress | Use OkHttp with a custom `ResponseBody` wrapper for progress callbacks and range-request resume |
| InputConnection | Calling `commitText()` from a background thread | Always dispatch to main thread: `handler.post { inputConnection.commitText(text, 1) }` |
| Hilt DI in IME | Annotating `InputMethodService` with `@AndroidEntryPoint` | IME services need manual Hilt injection via `EntryPointAccessors.fromApplication()` |
| DataStore in IME | Creating DataStore instance in the IME (second instance conflicts) | Share a single DataStore instance via the Application class or DI |
| Foreground notification | Using a notification without a channel (crashes on API 26+) | Create `NotificationChannel` in `Application.onCreate()`, reference it in the service |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Recomposing entire keyboard on each key press | 30+ recompositions per press, visible lag | Scope state to individual keys, use `@Stable` data classes | Immediately on any device |
| Loading model on every dictation session | 2-5s delay before recording starts each time | Keep model loaded in foreground service memory | Every session |
| Recording at 44.1kHz and resampling to 16kHz | Audio artifacts, reduced transcription accuracy | Record directly at 16000 Hz | Every recording |
| Accumulating audio in `ArrayList<Float>` | GC pauses during recording, OOM on long recordings | Use pre-allocated `FloatArray` or write to file | Recordings >30 seconds |
| Running whisper inference on `Dispatchers.Default` | Coroutine thread pool starved, UI hangs | Use `Dispatchers.IO` or a dedicated single-thread executor | When CPU is loaded |
| Waveform animation at 60fps via `LaunchedEffect` + `delay` | Frame drops, battery drain | Use `withFrameMillis` from Compose animation framework | Immediately visible |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing model files on external storage | Other apps can replace model with a malicious one (model poisoning) | Store in `context.filesDir` (app-internal, sandboxed) |
| Logging transcribed text in production | User dictation data in logcat, accessible via ADB | Use Timber with a release tree that strips sensitive data |
| Not clearing audio buffers after transcription | Raw audio persists in memory, extractable via memory dump | Zero-fill audio arrays after whisper.cpp processing |
| HuggingFace download over HTTP | Model file tampered in transit | Enforce HTTPS (default) + verify SHA-256 hash |
| IME reading clipboard without user action | Privacy violation, Play Store policy violation | Only access clipboard on explicit user paste action |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No feedback between mic tap and recording start | User thinks tap didn't register, taps again, cancels recording | Show immediate visual change (button color) + haptic before service is ready |
| Model download with no progress indicator | User thinks app is frozen, kills it, corrupts download | Show percentage, speed, ETA, and allow cancel |
| Silent transcription failure | User waits, nothing happens, loses trust | Always show result or explicit error ("Transcription failed, tap to retry") |
| Requiring user to open Settings to enable IME | Confusing multi-step process, users give up | Provide a one-tap "Enable Keyboard" button that opens the right settings page, with step-by-step instructions |
| Keyboard flickers or resizes during dictation | Jarring experience, text field scrolls unpredictably | Keep keyboard height constant; overlay waveform on top of keys rather than replacing them |
| No silence detection cutoff | Whisper hallucinates text from ambient noise if left recording | Auto-stop after 3-5 seconds of silence with user-visible countdown |

## "Looks Done But Isn't" Checklist

- [ ] **Keyboard rendering:** Works in one app -- verify in Chrome, WhatsApp, Gmail, Samsung Messages, a WebView, and landscape mode
- [ ] **Text insertion:** `commitText()` succeeds -- verify by checking `getTextBeforeCursor()` returns the inserted text
- [ ] **Model download:** Completes on WiFi -- verify resume works after airplane mode toggle mid-download
- [ ] **Audio recording:** Records in foreground -- verify still records when screen turns off (foreground service keeps it alive)
- [ ] **Foreground service:** Starts successfully -- verify on cold boot when app was force-stopped (API 31+ background restrictions)
- [ ] **IME activation:** Keyboard appears after enable -- verify after device reboot (IME settings persist but service may need restart)
- [ ] **Model loading:** Loads `tiny` model -- verify with `small` quantized (4x larger, different perf characteristics)
- [ ] **Long recording:** Works for 5 seconds -- verify 60-second recording doesn't OOM or produce buffer underruns
- [ ] **Multi-language:** French works -- verify English transcription with the same model (language parameter must be passed correctly)
- [ ] **Theme:** Dark theme looks good -- verify "Follow System" with light mode does not produce invisible text on the keyboard

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| ComposeView lifecycle crash | LOW | Add lifecycle wiring to IME class -- no architectural change needed |
| Foreground service crash on API 31+ | LOW | Add `foregroundServiceType`, wrap in try/catch, test on API 31+ emulator |
| whisper.cpp build failures | MEDIUM | Pin to known-good release tag, use CMake, verify `.so` builds independently |
| JNI memory leaks | MEDIUM | Add `Closeable` wrapper, instrument with `dumpsys meminfo`, fix leak sites |
| Model corruption from interrupted download | LOW | Add `.tmp` + rename pattern and hash verification -- isolated to download module |
| IPC binding failures | MEDIUM | Switch to started+bound service pattern, add retry logic in IME |
| Keyboard performance issues | HIGH | May require architectural change from Compose to View-based rendering if optimization is insufficient |
| AudioRecord buffer underruns | LOW | Increase buffer size, use dedicated audio thread, record at 16kHz directly |
| commitText() failures in some apps | MEDIUM | Add KeyEvent fallback, test matrix of target apps |
| Model loading ANR | LOW | Move to coroutine on Dispatchers.IO -- straightforward refactor |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| ComposeView lifecycle in IME | Phase 1: IME Scaffold | Keyboard renders in 3+ apps without crash |
| Foreground service restrictions | Phase 2: Audio Service | Service starts on API 29, 31, 34 emulators |
| whisper.cpp build issues | Phase 3: Native Integration | `.so` builds, `tiny` model transcribes in <2s on Pixel 4 |
| JNI memory leaks | Phase 3: Native Integration | `dumpsys meminfo` shows stable native heap after 10 transcriptions |
| IPC binding failures | Phase 2: IPC Architecture | IME successfully binds after app force-stop + keyboard use |
| Model download corruption | Phase 4: Model Manager | Download resumes after network interruption, hash verified |
| AudioRecord quality | Phase 2: Audio Recording | Recorded audio plays back clearly, 16kHz mono PCM verified |
| Compose recomposition lag | Phase 1 + Phase 5 | <50ms key-to-screen latency, <5 recompositions per key press |
| commitText() compatibility | Phase 1: Text Insertion | Text inserts correctly in Chrome, WhatsApp, Gmail, Samsung Messages |
| Model loading ANR | Phase 3: Model Loading | No ANR with `small` quantized model on Pixel 4 |

## Sources

- [InputMethodService with Jetpack Compose -- ComposeView lifecycle issue](https://www.androidbugfix.com/2022/08/inputmethodservice-with-jetpack-compose.html)
- [Implementation of a custom soft keyboard in Android using Compose (Medium)](https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd)
- [whisper.cpp Issue #1022: Android demo extremely slow transcription](https://github.com/ggml-org/whisper.cpp/issues/1022)
- [whisper.cpp Issue #1070: Android inference too slow](https://github.com/ggml-org/whisper.cpp/issues/1070)
- [whisper.cpp Issue #2310: Large memory consumption on long files](https://github.com/ggml-org/whisper.cpp/issues/2310)
- [whisper.cpp Issue #856: ndkBuild CXX1405 error](https://github.com/ggml-org/whisper.cpp/issues/856)
- [whisper.cpp Issue #962: Android build difficulties](https://github.com/ggml-org/whisper.cpp/issues/962)
- [Android Developers: Troubleshoot foreground services](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)
- [Android Developers: Restrictions on starting foreground service from background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android Developers: Foreground service types required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Android Developers: JNI Tips](https://developer.android.com/ndk/guides/jni-tips)
- [Building LocalMind: Porting Whisper to Android via JNI (Medium)](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc)
- [Debunking myths about ANRs in Android services (Embrace)](https://embrace.io/blog/debunking-myths-anrs-android-services/)
- [ForegroundServiceStartNotAllowedException (Google Issue Tracker)](https://issuetracker.google.com/issues/307329994)
- [whisper.cpp Discussion #3567: Streaming vs batch performance](https://github.com/ggml-org/whisper.cpp/discussions/3567)

---
*Pitfalls research for: Android IME + on-device ML inference (Dictus Android)*
*Researched: 2026-03-21*
