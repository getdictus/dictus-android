# Pitfalls Research

**Domain:** Android IME + on-device ML inference (voice dictation keyboard)
**Researched:** 2026-03-21 (v1.0) | Updated: 2026-03-30 (v1.1)
**Confidence:** HIGH (domain-specific issues verified across multiple sources)

---

## v1.0 Pitfalls (Existing — Already Addressed)

> These pitfalls were identified and addressed during the v1.0 milestone. They are retained here as reference for regression risk, since several v1.1 features touch the same subsystems.

### Pitfall 1: ComposeView in InputMethodService Crashes Without Manual Lifecycle Wiring

**What goes wrong:**
Using Jetpack Compose inside `InputMethodService.onCreateInputView()` throws `IllegalStateException: Composed into the View which doesn't propagate ViewTreeLifecycleOwner!`. The keyboard never renders.

**Why it happens:**
`InputMethodService` is a `Service`, not an `Activity`. It does not implement `LifecycleOwner`, `ViewModelStoreOwner`, or `SavedStateRegistryOwner`. Compose requires all three.

**How to avoid:**
Make `DictusIME` implement all three interfaces and wire them manually in `onCreateInputView()`.

**Phase to address:** Phase 1 (IME scaffold) — already done in v1.0.

---

### Pitfall 2: Foreground Service startForeground() Race Condition

**What goes wrong:**
`ForegroundServiceStartNotAllowedException` on Android 12+. ANR if `startForeground()` is not called within 5 seconds.

**How to avoid:**
Call `ServiceCompat.startForeground()` as the first line of `onStartCommand()`. Declare `android:foregroundServiceType="microphone"` in manifest.

**Phase to address:** Phase 2 (audio service) — already done in v1.0.

---

### Pitfall 3: whisper.cpp Thread Misconfiguration Causes 10x Slowdowns

**What goes wrong:**
Using all 8 threads on Pixel 4's big.LITTLE causes contention. Default Android build lacks NEON optimizations.

**How to avoid:**
Use CMake with `-DGGML_NEON=ON`, pin thread count to 4 (big cores only). Already validated: q5_1 small model runs <8s for 10s audio on Pixel 4.

**Phase to address:** Phase 3 (whisper.cpp) — already done in v1.0.

---

### Pitfall 4: JNI Memory Leaks from Unmanaged Native Pointers

**How to avoid:**
Wrap native pointer in `Closeable`, call `whisper_free()` in `onDestroy()` and `try/finally`. Already addressed in v1.0.

---

### Pitfall 5–10: IPC Binding, Model Corruption, AudioRecord Quality, Compose Recomposition, commitText() Compatibility, Model Loading ANR

All addressed in v1.0. See original research notes. Regression risk: Parakeet integration adds a second model lifecycle — apply the same JNI safety patterns to the new engine.

---

---

## v1.1 Pitfalls — New Milestone: Text Prediction, Parakeet/ONNX, Beta Distribution, OSS Prep

These pitfalls are specific to the features being added in v1.1. They were NOT previously researched.

---

## Critical Pitfalls

### Pitfall 11: Native Text Prediction Called from Compose's Recomposition Thread Causes Deadlock

**What goes wrong:**
The text prediction pipeline (C++ native dictionary lookup via JNI) is invoked inside a `LaunchedEffect` or `snapshotFlow` that runs on `Dispatchers.Default`. Meanwhile, the IME's main looper is blocked waiting for a result. This produces an intermittent freeze (500ms–2s) on key press, manifesting as "lag only on some presses."

**Why it happens:**
Compose's coroutine-based state observation does not run on the main thread. Developers assume the native call is fast enough to tolerate inline invocation, but dictionary operations (bigram lookup, fuzzy matching) take 5–40ms under load. When the result is then dispatched back to update the suggestion bar via `mutableStateOf`, Compose scheduling adds another frame boundary. The combined 16ms frame budget is blown.

The v1.0 suggestion bar is a stub with hardcoded strings — real prediction machinery is absent, so this has never been exercised. The architectural assumption ("suggestions update after transcription") does not hold for per-keystroke prediction.

**How to avoid:**
- Dedicate a single-threaded executor for all native dictionary calls: `Executors.newSingleThreadExecutor(Thread("dict-worker"))`. Never call native prediction on `Dispatchers.Default` or `Dispatchers.Main`.
- Results must be posted back to the IME main thread: `Handler(Looper.getMainLooper()).post { updateSuggestions(results) }`.
- Debounce input: do not invoke the C++ layer on every keystroke. Use a 50ms debounce — collect the last key state and discard intermediate ones.
- Make the JNI call cancellable: if a new key is pressed before the previous lookup completes, abort the old call (pass a cancellation flag to C++ via an atomic bool).
- Budget: the entire suggestion pipeline (keystroke → JNI → C++ → result → UI) must complete in under 100ms wall-clock. If it cannot, fall back to empty suggestions rather than blocking.

**Warning signs:**
- Key press lag that disappears when the suggestion bar is hidden
- `Choreographer: Skipped N frames` in logcat during fast typing
- `Thread: dict-worker` absent from `adb shell ps` (means C++ is running inline on a wrong thread)
- Native dictionary call time >20ms measured via `System.nanoTime()` wrappers

**Phase to address:**
Text Prediction phase. Validate the threading model with a microbenchmark (1000 sequential lookups) before integrating with the keyboard UI.

---

### Pitfall 12: ONNX Runtime and whisper.cpp libc++ Symbol Collision Crashes the Process

**What goes wrong:**
Adding sherpa-onnx (which bundles `libonnxruntime.so` + `libonnxruntime4j_jni.so`) to an app that already has whisper.cpp (which links against `libc++_shared.so`) produces a symbol collision at runtime. The dynamic linker loads two incompatible copies of the C++ runtime — one statically linked inside `libwhisper.so`, one bundled with ONNX — causing `SIGABRT` or `SIGSEGV` on first ONNX inference call, but only on some devices.

**Why it happens:**
Android's bionic linker uses `RTLD_LOCAL` by default, but when two `.so` files each bring their own copy of STL internals (especially thread-local storage emulation `__emutls_*`), the runtime state diverges. The NDK documentation is explicit: "If an AAR includes `libc++_shared.so` but a different version than the app uses, only one will be installed to the APK and that may lead to unreliable behavior." whisper.cpp built with `c++_shared` and sherpa-onnx built with a different `c++_shared` version will conflict.

Source: [Android NDK C++ library support](https://developer.android.com/ndk/guides/cpp-support), [NDK middleware vendor advice](https://developer.android.com/ndk/guides/middleware-vendors)

**How to avoid:**
- Build whisper.cpp with `c++_static` (statically link the STL into `libwhisper.so`) and use symbol visibility scripts to hide STL symbols: `-Wl,--exclude-libs,libc++_static.a -Wl,--exclude-libs,libc++abi.a`.
- Use sherpa-onnx's pre-built AAR only if its bundled `libc++_shared.so` version matches the NDK version used to build whisper.cpp. Check both `.so` files' NDK version via `readelf -d libwhisper.so`.
- Alternatively, build sherpa-onnx from source with the same NDK and same `ANDROID_STL=c++_static`, then link both engines against the app's single `libc++_shared.so`.
- The safest architectural choice: run each STT engine in a separate process (`android:process=":whisper"` vs `android:process=":onnx"`). IPC overhead is ~2ms for audio data, acceptable compared to the seconds-long inference window.

**Warning signs:**
- `dlopen failed: cannot locate symbol "__emutls_get_address"` in logcat
- Crash only reproducible on specific devices or only after cold start
- `libwhisper.so` and `libonnxruntime.so` both appear in `adb shell cat /proc/PID/maps` with overlapping address ranges for STL symbols
- App works in debug build but crashes in release (release strips debug symbols, changing linker behavior)

**Phase to address:**
Parakeet/Multi-provider STT phase, before any ONNX code is executed. Validate with a standalone test: load both engines in the same process, call inference on each, verify no crash after 20 iterations.

---

### Pitfall 13: ONNX Runtime Android Binaries Incompatible with 16KB Page Size (Android 15+)

**What goes wrong:**
The pre-built `libonnxruntime4j_jni.so` (arm64-v8a) from ONNX Runtime versions up to at least 1.20.x does not comply with Google Play's 16KB memory page alignment requirement for Android 15 (API 35+). Installing the app on a Pixel 9 or newer device (which uses 16KB pages) crashes on library load with `dlopen: "libonnxruntime4j_jni.so" is not compatible with 16 KB page size`.

**Why it happens:**
Android 16 mandates 16KB page size support on ARM64. Pre-built ONNX Runtime JNI bindings were compiled with 4KB alignment assumptions. The issue is tracked and not yet resolved in stable releases as of early 2026.

Sources: [ONNX Runtime Issue #24902](https://github.com/microsoft/onnxruntime/issues/24902), [ONNX Runtime Issue #25859](https://github.com/microsoft/onnxruntime/issues/25859), [sherpa-onnx Issue #2641](https://github.com/k2-fsa/sherpa-onnx/issues/2641)

**How to avoid:**
- Check the fix status before integrating: look for a resolved version in [ONNX Runtime Issue #26228](https://github.com/microsoft/onnxruntime/issues/26228).
- If using sherpa-onnx's AAR, build from source with NDK 27+ which enables 16KB alignment by default: add `android.defaultConfig.externalNativeBuild.cmake.arguments "-DANDROID_SUPPORT_16KB_PAGE_SIZES=ON"`.
- For the beta targeting Pixel 4 (which uses 4KB pages, API 29), this is not a blocker. But it blocks Play Store submission for apps targeting API 35+.
- Dictus v1.1 targets API 29+ and distributes via APK/GitHub Releases, so this is low risk for the beta. Document it as a known blocker for future Play Store submission.

**Warning signs:**
- `dlopen failed: cannot locate symbol` or alignment errors only on Pixel 9 / Android 15+ test devices
- Play Store upload rejected with "native library page size incompatibility" error
- `readelf -l libonnxruntime4j_jni.so | grep -i load` shows `Align: 0x1000` (4KB) instead of `0x4000` (16KB)

**Phase to address:**
Parakeet/ONNX integration phase. Document the limitation explicitly. Do not block v1.1 beta on this since distribution is APK-only, but flag it for the future Play Store phase.

---

### Pitfall 14: Parakeet 0.6B Model Consumes 1.2–2GB RAM — Coexisting with whisper.cpp Triggers OOM on Pixel 4

**What goes wrong:**
Loading the Parakeet TDT 0.6B INT8 model via sherpa-onnx consumes approximately 1.2GB RAM — measured at 1.23GB on iOS in independent benchmarks. The Pixel 4 has 6GB total RAM, but after Android OS (~2.5GB), the existing whisper.cpp small q5_1 model (~500MB), and the app's working set (~300MB), the headroom is ~2.4GB. Loading Parakeet pushes total ML memory to ~1.7GB, leaving only ~700MB for audio buffers and the OS killer threshold. On memory-pressured devices or after prolonged use, Android kills the process.

**Why it happens:**
Developers assess memory in isolation ("1.2GB RAM for Parakeet, 6GB device, fine"). They do not account for:
1. Android system processes reserve ~2.5GB on a 6GB device
2. whisper.cpp model remains loaded in the foreground service
3. Audio buffers (10s recording at 16kHz float = ~640KB, negligible) plus ONNX session working memory during inference (can spike to 2x model size)
4. The OS low-memory killer triggers at a threshold (typically 200–400MB free), not at 0MB

Sources: [sherpa-onnx Parakeet memory issue #2626](https://github.com/k2-fsa/sherpa-onnx/issues/2626), [sherpa-onnx memory leak #1939](https://github.com/k2-fsa/sherpa-onnx/issues/1939)

**How to avoid:**
- Enforce mutual exclusivity at runtime: only one STT engine model is loaded in memory at a time. When the user selects Parakeet in Settings, unload whisper.cpp's model (`whisper_free()`), then load Parakeet. Reverse when switching back.
- Never preload both models simultaneously. The multi-provider architecture must be lazy-load, not eager-load-all.
- Add an explicit memory check before loading Parakeet: `ActivityManager.getMemoryInfo()` — if `availMem < 1.5GB`, warn the user and refuse to load.
- During ONNX inference, do not keep whisper.cpp allocated. Release it, run Parakeet, then reload whisper on demand.
- For low-memory devices: expose a setting "Active STT engine" that makes the choice explicit rather than loading both.

**Warning signs:**
- `lowMemory = true` from `ActivityManager.getMemoryInfo()` during Parakeet load
- ONNX `OrtException: Failed to allocate memory` in logs
- App killed silently mid-recording (system OOM killer, no crash log — check `adb shell cat /proc/last_kmsg`)
- `whisper_init` returns null after Parakeet session is freed (lingering allocations from ONNX not fully released)

**Phase to address:**
Parakeet/Multi-provider STT phase, specifically when designing the provider switching logic. Memory lifecycle must be validated on Pixel 4 with `adb shell dumpsys meminfo` before each model load/unload cycle.

---

### Pitfall 15: Beta APK Signing Key Not Separated from Play Store Upload Key — Irrecoverable if Lost

**What goes wrong:**
A developer uses the same keystore for APK sideloading/GitHub Releases and for Play Store submission. The keystore is stored only on their dev machine. The machine is lost, stolen, or the keystore file is accidentally deleted. The developer can never update the Play Store version of the app again. For the GitHub Releases APK, users who installed earlier versions cannot be updated (Android enforces certificate continuity — the new APK must be signed with the same key).

**Why it happens:**
For a solo developer or small OSS project, creating multiple keystores feels like unnecessary overhead. GitHub CI requires the keystore to be accessible, so developers store it as a base64-encoded GitHub Secret — but then never back it up separately, or use the same key for both Play Store and direct APK distribution.

**How to avoid:**
- Generate two separate keystores before any public release:
  1. **App signing key** (for Play Store submission, enrolled in Play App Signing — Google stores the signing key, you only need the upload key)
  2. **APK direct signing key** (for GitHub Releases / sideloading — you own and must back up this key)
- Store both keystores in at minimum two locations: encrypted password manager (e.g., 1Password) + encrypted backup on a separate device/cloud.
- In GitHub Actions CI: store as `KEYSTORE_BASE64` + `KEY_ALIAS` + `KEYSTORE_PASSWORD` + `KEY_PASSWORD` as repository secrets. Never commit the `.jks` file.
- For an OSS repository: the signing step MUST be excluded from the public workflow. Use a separate `release.yml` that only runs on the main repo (not forks) and reads from secrets. Contributors cannot trigger signed releases.
- Document the key backup procedure in a private note, not in the public repo.

**Warning signs:**
- Keystore file stored only in `~/.android/` with no backup
- The same key alias used for both Play Store upload and direct APK signing
- `gradlew assembleRelease` works on dev machine but fails in CI with "keystore not found" (means the keystore is not in version control or secrets)
- Public CI workflow file references `KEYSTORE_BASE64` — any fork can read this secret on a PR (GitHub leaks secrets to PRs from forks on public repos — use `pull_request_target` carefully or block secret access for forks)

**Phase to address:**
Beta Distribution phase, before the first signed APK is created. Key setup is irreversible — you cannot re-key existing installed APKs.

---

### Pitfall 16: Crash Reporting for Sideloaded APKs — Firebase Crashlytics Requires Play Services, Breaks on Custom ROMs

**What goes wrong:**
Firebase Crashlytics is the obvious choice for crash reporting, but it requires Google Play Services. Sideloaded APKs on devices without Play Services (GrapheneOS, LineageOS without GApps, some Pixel devices in developer mode) will silently fail to initialize Crashlytics and report no crashes at all. For an OSS app targeting privacy-conscious users, this is precisely the audience most likely to use a custom ROM.

**Why it happens:**
Crashlytics initialization checks for `GoogleApiAvailability`. On devices without Play Services, the check fails silently or throws a non-fatal exception that developers miss because their test devices all have Play Services.

**How to avoid:**
- Use ACRA (Application Crash Reports for Android) as the primary crash reporter. ACRA has no Play Services dependency, works on any Android device, and is MIT licensed — compatible with Dictus's OSS model.
- Configure ACRA to send reports to a self-hosted Acrarium instance, or to a GitHub Issue via the ACRA GitHub sender plugin.
- If Firebase Crashlytics is also desired (for Play Store distribution), add it alongside ACRA and wrap initialization in a Play Services availability check: `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS`.
- The ACRA crash report sender for GitHub Issues creates issues directly in the OSS repo — transparent and requires no backend server.

**Warning signs:**
- Zero crash reports from beta users who are known to use GrapheneOS or LineageOS
- `FirebaseCrashlytics: Failed to initialize, Google Play services not available` in logcat (silent in release builds)
- No `acrarium` or ACRA dependency in `build.gradle` — crash reporting is Firebase-only

**Phase to address:**
Beta Distribution phase, during crash reporting setup. Validate on a device without Play Services (emulator with no GApps) before declaring crash reporting operational.

---

### Pitfall 17: OSS Repo Contains Hardcoded HuggingFace URLs or API Tokens — Breaks Contributors' Builds

**What goes wrong:**
The model download URLs point to specific HuggingFace model revisions hardcoded in Kotlin source files or resource strings. A contributor cloning the repo and building it gets a working app, but when HuggingFace moves a file or changes the CDN structure, all historical builds break. Worse: if a HuggingFace API token was ever used for download authentication and committed to the repo (even transiently), it is now permanently in git history.

**Why it happens:**
During solo development, the URL pattern is "obviously correct" and doesn't feel like a secret. The developer tests downloads, they work, they ship. When the repo goes public, the implicit assumption that "HuggingFace URLs are stable" is never documented.

Additionally, when a developer uses a HuggingFace token to access gated models during development and stores it in `BuildConfig` or `local.properties`, forgetting to add `local.properties` to `.gitignore` commits the token.

**How to avoid:**
- Store all model URLs and SHA-256 hashes in a versioned JSON catalog file (`assets/model_catalog.json`). This is the single source of truth. Contributors see immediately what URLs are expected and can update them without changing Kotlin.
- Never require authentication for model downloads in the public app. Parakeet and Whisper GGML models are publicly accessible without tokens. If a token is needed for testing a gated variant, use only environment variables — never `local.properties` unless `local.properties` is in `.gitignore` (it should be — verify this).
- Run `git secrets --scan` or `truffleHog` on the repo before making it public. These tools scan git history, not just current files.
- In `CONTRIBUTING.md`, document: "Model URLs are in `assets/model_catalog.json`. Do not hardcode URLs in Kotlin."

**Warning signs:**
- `assets/` or `res/values/` contain full HuggingFace `https://` URLs with `?download=true` query parameters directly in Kotlin/XML
- `local.properties` is NOT in `.gitignore`
- `BuildConfig.HF_TOKEN` or similar constants exist in source files
- `git log --all --full-history -- local.properties` shows the file was ever committed

**Phase to address:**
OSS Repo Preparation phase. Run `truffleHog` or equivalent BEFORE making the repo public. The repo going public is a one-way door — secrets in git history cannot be deleted without a rebase that invalidates all existing forks.

---

### Pitfall 18: Multi-Provider STT Architecture Implemented as a God Object — Impossible to Extend

**What goes wrong:**
`DictationService` grows a large `when (provider) { WHISPER -> ...; PARAKEET -> ... }` block. Adding a third provider requires modifying the same file in 6–10 places. The service becomes a 1500+ line file that cannot be tested without a physical device. Contributors cannot understand the boundaries between engines.

**Why it happens:**
The v1.0 DictationService was designed for one engine (whisper.cpp). Adding Parakeet as a second engine is the natural inflection point where architecture either scales or collapses. The path of least resistance is to add `if (isParakeet)` branches everywhere.

**How to avoid:**
Define a `SttEngine` interface before writing any Parakeet code:

```kotlin
interface SttEngine {
    val isLoaded: Boolean
    suspend fun load(modelPath: String)
    suspend fun transcribe(audio: FloatArray, language: String): String
    fun unload()
}
```

Implement `WhisperEngine : SttEngine` and `ParakeetEngine : SttEngine` as separate classes. `DictationService` holds a `var activeEngine: SttEngine` and delegates all calls to it — zero `when` blocks on provider type inside the service.

This is the pattern used by florisboard and FUTO Keyboard for their multi-engine architecture.

**Warning signs:**
- `DictationService.kt` growing past 600 lines after Parakeet is added
- `when (selectedProvider)` appearing more than once in the service
- Tests for `WhisperEngine` require mocking `DictationService` (means the engine is not independently testable)
- `ParakeetEngine` class does not exist — all ONNX code is inline in the service

**Phase to address:**
Parakeet/Multi-provider STT phase, at design step. Define and test the `SttEngine` interface with `WhisperEngine` before touching any ONNX code.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| SharedPreferences for IME-Service IPC instead of Bound Service | Faster to implement | Race conditions, no type safety | Never for production |
| Loading model on main thread | Fewer coroutine concerns | ANR on larger models | Never |
| Skipping download hash verification | Faster to ship | Corrupted models produce silent failures | MVP only, must add before public release |
| Hardcoded keyboard height | Works on Pixel 4 | Breaks on every other device | MVP single-device only |
| Single-process architecture | Simpler debugging | Native crash in whisper/ONNX kills keyboard | Acceptable for MVP |
| Inline `when (provider)` branches in DictationService | Fastest to implement Parakeet | Makes third engine addition a rewrite | Never — define `SttEngine` interface first |
| Loading both whisper + Parakeet models simultaneously | No engine switch delay | OOM on 6GB devices under memory pressure | Never on current hardware |
| Using Firebase Crashlytics only for crash reporting | Standard Android practice | Blind on custom ROMs, breaks on no-GApps devices | Only if OSS/privacy audience is not a concern |
| Committing keystore to repository | CI "just works" | Keystore compromise = app takeover | Never for public OSS repos |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| sherpa-onnx AAR + whisper.cpp | Both link `libc++_shared.so` at different versions — silent ABI mismatch | Build whisper.cpp with `c++_static`, use symbol visibility scripts; OR run engines in separate processes |
| ONNX Runtime Android | Using pre-built AAR without checking 16KB page size compatibility | Verify `readelf` alignment on arm64 `.so` before shipping; build from source if needed for Android 15+ |
| Parakeet model (0.6B INT8) | Loading alongside whisper.cpp model — combined memory exceeds safe threshold | Enforce single-engine-loaded-at-a-time policy via `SttEngine` interface |
| GitHub Actions signing | Referencing `KEYSTORE_BASE64` secret in workflow visible to fork PRs | Use `pull_request_target` with explicit permission gates, or exclude signing from PRs entirely |
| ACRA crash reporting | Using HTTP sender to a public URL in the source code | Use GitHub Issue sender (requires token in secrets) or self-hosted Acrarium with URL in BuildConfig |
| HuggingFace model downloads | Hardcoding revision URLs in Kotlin | Use a versioned `model_catalog.json` asset file as single source of truth |
| Native text prediction (C++) | Calling JNI lookup inline in Compose state observation | Dedicate a single-threaded executor, debounce 50ms, dispatch results back to main thread |
| ONNX memory after inference | Assuming ONNX session releases memory on `close()` | Verify with `dumpsys meminfo` — ONNX Runtime has known delayed release issues (#1939) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Recomposing entire keyboard on each key press | 30+ recompositions/press, visible lag | `@Stable` data classes, scoped state | Immediately |
| Loading model on every dictation session | 2–5s delay before recording | Keep model in foreground service | Every session |
| Calling native prediction on `Dispatchers.Default` | Key lag, frame skips during fast typing | Dedicated dict-worker thread + 50ms debounce | >2 keys/second typing |
| Loading whisper + Parakeet simultaneously | OOM, silent process kill | Mutual exclusivity — unload before loading new engine | On any 6GB device under load |
| ONNX session working memory spike during inference | RAM spike to 2x model size mid-inference | Reserve memory headroom: require 1.5GB free before Parakeet load | Every Parakeet inference |
| Suggestion update on every character | JNI called 60x/second for fast typists | Debounce: min 50ms between C++ calls | Typing speed >3 chars/second |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing model files on external storage | Model poisoning by other apps | Use `context.filesDir` (sandboxed) |
| Logging transcribed text in production | User dictation in logcat via ADB | Release Timber tree strips sensitive tags |
| Not clearing audio buffers after transcription | Raw audio extractable via memory dump | Zero-fill audio arrays after processing |
| HuggingFace download over HTTP | Model tampered in transit | Enforce HTTPS + SHA-256 verification |
| Keystore committed to git history | Permanent key exposure — all signed APKs can be re-signed by attacker | `git secrets` scan + `truffleHog` before repo goes public |
| Crash reports sent to third-party cloud (Crashlytics) without disclosure | Privacy policy violation for an "offline-only" app | ACRA with self-hosted backend OR explicit disclosure in privacy policy |
| CI workflow secrets accessible to fork PRs | Contributor fork can exfiltrate keystore | `pull_request_target` with explicit write permission gate, no secrets in PR workflows |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No feedback between mic tap and recording start | User double-taps, cancels recording | Immediate visual + haptic change before service is ready |
| Model download with no progress | User kills app, corrupts download | Show %, speed, ETA, allow cancel |
| Silent transcription failure | User waits, loses trust | Always show result or explicit error |
| No memory warning before Parakeet load | Silent OOM kill, user confused | Check `ActivityManager.getMemoryInfo()` before loading, warn if tight |
| Engine switching with no loading indicator | UI appears frozen during model unload/reload | Show "Switching STT engine…" progress in keyboard or settings |
| Suggestion bar shows stale predictions after voice dictation | Confusing — typed words mixed with post-dictation context | Clear suggestion state on every `commitText()` from voice path |
| Crash report dialog (ACRA default) on beta | Alarming to non-technical beta users | Customize ACRA dialog: brief friendly message, pre-filled report, opt-in not opt-out |

## "Looks Done But Isn't" Checklist

**v1.0 Items (regression risk in v1.1):**
- [ ] **Keyboard rendering:** Works in Chrome, WhatsApp, Gmail, Samsung Messages, a WebView, landscape mode
- [ ] **Text insertion:** `commitText()` verified with `getTextBeforeCursor()` in 5+ apps
- [ ] **Model download:** Resume works after airplane mode toggle mid-download
- [ ] **Foreground service:** Starts on API 29, 31, 34 — verified cold-boot after force-stop

**v1.1 New Items:**
- [ ] **Native text prediction:** Suggestion latency <100ms measured end-to-end at 3 chars/second typing speed
- [ ] **Native text prediction:** Dict-worker thread visible in `adb shell ps`, never runs on `main`
- [ ] **ONNX + whisper coexistence:** 20-iteration test of loading each engine, running inference, switching — no crash, stable memory
- [ ] **Parakeet model load:** `adb shell dumpsys meminfo` shows <1.5GB native heap increase on Pixel 4
- [ ] **Engine switch:** Unloading whisper before loading Parakeet reduces native heap by expected amount
- [ ] **ONNX 16KB page size:** `readelf -l libonnxruntime4j_jni.so` alignment verified (document finding)
- [ ] **Signing:** Two separate keystores exist, both backed up in password manager, neither committed to git
- [ ] **GitHub Actions:** Signed release workflow only runs on main repo, not forks; no secrets in PR-triggered workflow
- [ ] **Crash reporting:** ACRA sends a test report from a device without Google Play Services (emulator without GApps)
- [ ] **OSS repo scan:** `truffleHog` or `git secrets --scan` passes with zero findings before repo goes public
- [ ] **Model catalog:** All HuggingFace URLs live in `model_catalog.json`, not hardcoded in Kotlin
- [ ] **`local.properties` in `.gitignore`:** Verified — `git ls-files local.properties` returns empty

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| ComposeView lifecycle crash | LOW | Add lifecycle wiring — no architectural change |
| Foreground service crash API 31+ | LOW | Add `foregroundServiceType`, try/catch, test emulator |
| whisper.cpp build failures | MEDIUM | Pin to known-good tag, use CMake, verify `.so` standalone |
| JNI memory leaks | MEDIUM | `Closeable` wrapper, `dumpsys meminfo`, fix leak sites |
| libc++ symbol collision (ONNX + whisper) | HIGH | Switch to separate-process architecture — significant refactor |
| Parakeet OOM on Pixel 4 | MEDIUM | Implement mutual exclusivity — requires service refactor |
| ONNX 16KB page size incompatibility | MEDIUM | Build ONNX from source with NDK 27+ — time-consuming but documented path |
| Keystore lost | HIGH (for future updates) | Play App Signing upload key can be reset; direct APK key is irrecoverable — users on old APK cannot be updated |
| Crash reports missing on custom ROMs | LOW | Add ACRA alongside Crashlytics — additive, no existing code changes |
| Secrets in git history | HIGH | `git filter-repo` to purge + rotate all exposed credentials + notify all cloners |
| God-object DictationService | HIGH | Refactor into `SttEngine` interface — requires re-testing all dictation flows |
| Hardcoded model URLs break | LOW | Move to `model_catalog.json` — localized change, no logic change |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| ComposeView lifecycle in IME | Phase 1: IME Scaffold | Keyboard renders in 3+ apps without crash (v1.0 done) |
| Foreground service restrictions | Phase 2: Audio Service | Service starts on API 29/31/34 emulators (v1.0 done) |
| whisper.cpp build + performance | Phase 3: Native Integration | `.so` builds, tiny model <2s on Pixel 4 (v1.0 done) |
| JNI memory leaks | Phase 3: Native Integration | Stable native heap after 10 transcriptions (v1.0 done) |
| IPC binding failures | Phase 2: IPC Architecture | IME binds after force-stop (v1.0 done) |
| Model download corruption | Phase 4: Model Manager | Resume + hash verify (v1.0 done) |
| Native text prediction threading | Text Prediction phase | Suggestion latency <100ms, dict-worker thread confirmed |
| libc++ symbol collision (ONNX + whisper) | Parakeet/ONNX phase — first task | 20-iteration engine switch test passes with no crash |
| ONNX 16KB page size | Parakeet/ONNX phase | `readelf` alignment verified, limitation documented |
| Parakeet OOM | Parakeet/ONNX phase — provider switch design | `dumpsys meminfo` stable after engine switch on Pixel 4 |
| Multi-provider God object | Parakeet/ONNX phase — interface design | `SttEngine` interface defined before any ONNX code written |
| Signing key separation | Beta Distribution phase — first task | Two keystores backed up, CI workflow validated on fork |
| Crash reporting on custom ROMs | Beta Distribution phase | ACRA test report received from no-GApps emulator |
| Secrets in git history | OSS Repo Preparation phase — first task | `truffleHog` passes with zero findings |
| Hardcoded model URLs | OSS Repo Preparation phase | `model_catalog.json` is single source; grep confirms no bare HF URLs in Kotlin |

## Sources

**v1.0 Sources:**
- [InputMethodService with Jetpack Compose — ComposeView lifecycle issue](https://www.androidbugfix.com/2022/08/inputmethodservice-with-jetpack-compose.html)
- [whisper.cpp Issue #1022: Android demo extremely slow](https://github.com/ggml-org/whisper.cpp/issues/1022)
- [Android Developers: Foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android Developers: JNI Tips](https://developer.android.com/ndk/guides/jni-tips)

**v1.1 Sources:**
- [Android NDK C++ library support — libc++ sharing rules](https://developer.android.com/ndk/guides/cpp-support)
- [Android NDK middleware vendor advice — STL conflict prevention](https://developer.android.com/ndk/guides/middleware-vendors)
- [ONNX Runtime Issue #24902: libonnxruntime4j_jni.so 16KB page incompatibility](https://github.com/microsoft/onnxruntime/issues/24902)
- [ONNX Runtime Issue #25859: 16KB page size on Android ARM64](https://github.com/microsoft/onnxruntime/issues/25859)
- [ONNX Runtime Issue #26228: Support 16KB page sizes tracking](https://github.com/microsoft/onnxruntime/issues/26228)
- [sherpa-onnx Issue #2641: 16KB page size Play Store requirement](https://github.com/k2-fsa/sherpa-onnx/issues/2641)
- [sherpa-onnx Issue #2626: Parakeet 0.6B INT8 consumes 1.2GB RAM](https://github.com/k2-fsa/sherpa-onnx/issues/2626)
- [sherpa-onnx Issue #1939: Memory not freed after repeated inference](https://github.com/k2-fsa/sherpa-onnx/issues/1939)
- [nvidia/parakeet-tdt-0.6b-v3 HuggingFace model card — memory requirements](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)
- [Android linker changes for NDK developers — RTLD_LOCAL behavior](https://android.googlesource.com/platform/bionic/+/master/android-changes-for-ndk-developers.md)
- [FUTO Keyboard native dictionary implementation — threading reference](https://deepwiki.com/futo-org/android-keyboard/8.2-native-dictionary-implementation)
- [ACRA: Application Crash Reports for Android — no Play Services required](https://github.com/ACRA/acra)
- [How to store an Android Keystore safely on GitHub Actions](https://stefma.medium.com/how-to-store-a-android-keystore-safely-on-github-actions-f0cef9413784)
- [Play App Signing — upload key vs app signing key separation](https://support.google.com/googleplay/android-developer/answer/9842756)
- [ProAndroidDev: Securely build and sign Android app with GitHub Actions](https://proandroiddev.com/how-to-securely-build-and-sign-your-android-app-with-github-actions-ad5323452ce)

---
*Pitfalls research for: Android IME + on-device ML inference (Dictus Android v1.1)*
*Researched: 2026-03-30*
