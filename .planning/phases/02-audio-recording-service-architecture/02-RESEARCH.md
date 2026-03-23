# Phase 2: Audio Recording + Service Architecture - Research

**Researched:** 2026-03-23
**Domain:** Android Foreground Service, AudioRecord API, IME-Service IPC, Haptic Feedback, Waveform Rendering
**Confidence:** HIGH

## Summary

Phase 2 wires the mic button to a foreground service that captures 16kHz mono Float32 audio, exposes state via StateFlow, and swaps the keyboard UI between idle and recording modes. The Android platform provides well-documented APIs for every piece: `AudioRecord` for capture, foreground service with `microphone` type for background audio, local `Binder` for same-process IPC between the IME and service, `HapticFeedbackConstants` for tactile response, and Compose `Canvas` for waveform drawing.

The key architectural difference from the iOS version is that on Android the IME and the audio service run in the **same process** (same app), so communication is trivial via a local Binder + StateFlow. iOS required cross-process Darwin notifications + App Group UserDefaults because the keyboard extension and main app are separate processes. This makes the Android implementation significantly simpler.

**Primary recommendation:** Use a `DictationService` (foreground service with microphone type) in the `app` module, bound to `DictusImeService` via local Binder. AudioRecord reads into a FloatArray at 16kHz mono. State machine (idle/recording) is a `MutableStateFlow<DictationState>` exposed through the Binder. The IME observes it with `collectAsState()` to swap between `KeyboardScreen` and `RecordingScreen`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Full keyboard replacement during recording -- keys disappear, waveform + controls take over the entire keyboard area (matches mockup WwpNL)
- Recording screen shows: 30-bar waveform, timer (MM:SS), "En ecoute..." label, X (cancel) and check (confirm) buttons
- X button = discard recording, return to keyboard idle
- Check button = stop recording, trigger transcription (placeholder action in Phase 2)
- No maximum recording duration -- timer is informational only
- Bottom row (globe + mic) persists across all states
- Start with dark theme for Phase 2
- Minimal notification: title "Dictus - Recording" + single Stop action button
- Notification body tap = no-op
- Haptics on ALL interactions: key press, special keys, mic button, cancel/confirm during recording
- Haptics enabled by default, light intensity; mic button gets stronger haptic
- Haptic toggle deferred to Phase 4 settings
- In-memory buffer approach: accumulate 16kHz mono Float32 samples in FloatArray
- No intermediate WAV file
- AudioRecord API with 16kHz sample rate, mono channel, Float encoding
- Audio capture runs in DictationService (foreground service), not in IME process
- Phase 2 states: idle -> recording -> idle (no transcribing)
- State exposed via StateFlow from DictationService, observed by IME via bound service connection

### Claude's Discretion
- Exact waveform energy calculation from audio buffer (RMS, peak, etc.)
- DictationService lifecycle management details
- Binder/ServiceConnection implementation approach
- AudioRecord buffer size configuration
- Notification channel setup
- Haptic intensity values (light vs. strong for mic)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DICT-01 | User can tap the mic button to start voice recording | MicButtonRow wiring to DictationService via Binder; state machine idle->recording transition |
| DICT-02 | User can tap again to stop recording and trigger transcription | Check button stops recording; placeholder transcription action; state machine recording->idle |
| DICT-07 | User feels haptic feedback on key press and mic button (configurable) | HapticFeedbackConstants + View.performHapticFeedback(); always-on in Phase 2, toggle in Phase 4 |
| DICT-08 | Recording runs in a foreground Service with visible notification | DictationService as foreground service with microphone type; notification channel + minimal notification |
</phase_requirements>

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.10 | Language | Already configured |
| Jetpack Compose BOM | 2025.03.00 | UI framework | Already configured |
| Hilt | 2.51.1 | Dependency injection | Already configured, EntryPointAccessors pattern for IME |
| Timber | 5.0.1 | Structured logging | Already used throughout |
| Lifecycle | 2.8.7 | lifecycle-service, lifecycle-runtime-ktx | Already in version catalog |

### Supporting (already available, no new dependencies)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.core-ktx | 1.15.0 | ServiceCompat.startForeground() | Starting foreground service with type |
| compose-foundation | BOM-managed | Canvas API for waveform | Custom waveform drawing |
| compose-ui | BOM-managed | LocalView, HapticFeedback | Haptic feedback in Compose |

### New Dependencies Needed
None. All required APIs are part of the Android SDK or already-declared dependencies. `AudioRecord`, `Service`, `NotificationManager`, `NotificationChannel`, `HapticFeedbackConstants`, and `VibrationEffect` are all Android framework classes.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| AudioRecord (raw) | MediaRecorder | MediaRecorder writes to file, cannot get raw Float32 buffer for whisper.cpp |
| Local Binder | AIDL/Messenger | Over-engineered for same-process; local Binder is simpler and zero-overhead |
| StateFlow | LiveData | StateFlow is more idiomatic with Compose collectAsState(); LiveData works too but adds conversion layer |
| Canvas waveform | 30 Box composables | Canvas is vastly more efficient; design README explicitly says "pas 30 Views separees" |

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/dev/pivisolutions/dictus/
  service/
    DictationService.kt        # Foreground service with AudioRecord
    DictationState.kt          # Sealed class/enum for state machine
    AudioCaptureManager.kt     # AudioRecord wrapper, buffer management
  di/
    ServiceModule.kt           # Hilt module if needed

ime/src/main/java/dev/pivisolutions/dictus/ime/
  DictusImeService.kt          # Updated: binds to DictationService
  di/DictusImeEntryPoint.kt    # Updated: may expose service accessor
  ui/
    KeyboardScreen.kt          # Updated: switches between keyboard/recording based on state
    RecordingScreen.kt         # NEW: waveform + timer + controls
    WaveformBar.kt             # NEW: Canvas-based 30-bar waveform
    MicButtonRow.kt            # Updated: wired to start/stop recording
  haptics/
    HapticHelper.kt            # Centralized haptic feedback utility
```

### Pattern 1: Foreground Service with Local Binder
**What:** DictationService extends Service, exposes a LocalBinder inner class. The IME binds to it via ServiceConnection and accesses StateFlow + control methods directly.
**When to use:** Same-process service communication (IME and app share a process).
**Example:**
```kotlin
// Source: Android official docs - Bound Services
class DictationService : Service() {
    private val binder = LocalBinder()
    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): DictationService = this@DictationService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun startRecording() {
        // Start foreground, init AudioRecord, begin capture
        _state.value = DictationState.Recording(elapsed = 0L, energy = emptyList())
    }

    fun stopRecording(): FloatArray {
        // Stop capture, return buffer
        _state.value = DictationState.Idle
        return audioBuffer
    }

    fun cancelRecording() {
        // Discard buffer
        _state.value = DictationState.Idle
    }
}
```

### Pattern 2: State-Driven UI Switching in IME
**What:** The IME observes `DictationState` via `collectAsState()` and conditionally renders KeyboardScreen or RecordingScreen.
**When to use:** Whenever the keyboard UI needs to react to service state changes.
**Example:**
```kotlin
// In DictusImeService.KeyboardContent()
@Composable
override fun KeyboardContent() {
    val dictationState by serviceState.collectAsState()

    when (dictationState) {
        is DictationState.Idle -> KeyboardScreen(...)
        is DictationState.Recording -> RecordingScreen(
            elapsed = dictationState.elapsed,
            energy = dictationState.energy,
            onCancel = { service?.cancelRecording() },
            onConfirm = { service?.stopRecording() },
        )
    }
}
```

### Pattern 3: AudioRecord Capture Loop on Coroutine
**What:** AudioRecord.read() runs in a coroutine on Dispatchers.Default, posting energy updates to StateFlow at ~10Hz.
**When to use:** Continuous audio capture with periodic UI updates.
**Example:**
```kotlin
// Source: Android AudioRecord API docs
private fun startCapture() {
    val sampleRate = 16000
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT
    )
    recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
        bufferSize * 2  // Double minimum for safety
    )
    recorder.startRecording()

    captureJob = serviceScope.launch(Dispatchers.Default) {
        val readBuffer = FloatArray(bufferSize / 4) // Float = 4 bytes
        while (isActive) {
            val read = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                // Append to main buffer
                synchronized(audioSamples) {
                    audioSamples.addAll(readBuffer.take(read).toList())
                }
                // Calculate RMS energy for waveform
                val rms = sqrt(readBuffer.take(read).map { it * it }.average().toFloat())
                updateWaveformEnergy(rms)
            }
        }
    }
}
```

### Pattern 4: Canvas Waveform Rendering
**What:** Use Compose Canvas to draw 30 bars with gradient colors per the design spec.
**When to use:** Recording overlay waveform visualization.
**Example:**
```kotlin
@Composable
fun WaveformBars(
    energyLevels: List<Float>, // 30 values, 0.0-1.0
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barCount = 30
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val centerY = size.height / 2

        energyLevels.forEachIndexed { index, energy ->
            val barHeight = max(2.dp.toPx(), energy * size.height)
            val x = index * (barWidth + gap)
            val color = when (index) {
                in 11..18 -> accentGradient(index) // Center bars: #6BA3FF -> #2563EB
                else -> edgeFadeColor(index)         // Edge bars: white with opacity fade
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
```

### Anti-Patterns to Avoid
- **Starting AudioRecord in the IME process directly:** Audio capture MUST run in DictationService. The foreground service keeps the process alive; without it, Android can kill the recording at any time.
- **Using MediaRecorder instead of AudioRecord:** MediaRecorder outputs to a file and cannot provide raw Float32 buffers needed for whisper.cpp.
- **Polling service state:** Use StateFlow + collectAsState(), never poll with a Timer.
- **Creating 30 separate Compose Views for waveform bars:** Use Canvas. Design README explicitly forbids this.
- **Forgetting to call startForeground() within 10 seconds:** Android kills the service with ANR if startForeground() is not called promptly after startForegroundService().

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Notification channels | Custom notification management | NotificationChannel + NotificationManager | Standard API since API 26, handles all edge cases |
| Haptic feedback | Custom vibration patterns | HapticFeedbackConstants + View.performHapticFeedback() | Device-consistent, no VIBRATE permission needed, respects system settings |
| Audio format conversion | Manual PCM_16BIT to Float32 conversion | ENCODING_PCM_FLOAT directly | Supported since API 23, minSdk is 29 |
| Service lifecycle | Manual thread management | CoroutineScope tied to service lifecycle | Structured concurrency, automatic cancellation |
| State observation | Custom callback interfaces | StateFlow + collectAsState() | Built-in Compose integration, lifecycle-aware |

**Key insight:** The entire audio capture + service + IPC stack uses standard Android APIs. No third-party libraries are needed. The complexity is in wiring the pieces together correctly, not in any individual API.

## Common Pitfalls

### Pitfall 1: Foreground Service Type Not Declared
**What goes wrong:** App crashes with `MissingForegroundServiceTypeException` on API 34+.
**Why it happens:** Since API 34, `android:foregroundServiceType` is mandatory in manifest.
**How to avoid:** Declare both `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` permissions, plus `android:foregroundServiceType="microphone"` on the service element.
**Warning signs:** SecurityException or MissingForegroundServiceTypeException at runtime.

### Pitfall 2: startForeground() Called Too Late
**What goes wrong:** ANR or `ForegroundServiceDidNotStartInTimeException`.
**Why it happens:** After `startForegroundService()`, you have ~10 seconds to call `startForeground()`. If AudioRecord setup takes too long, the deadline passes.
**How to avoid:** Call `startForeground()` with notification FIRST in `onStartCommand()`, THEN initialize AudioRecord.
**Warning signs:** Intermittent crashes on slower devices.

### Pitfall 3: ENCODING_PCM_FLOAT Not Supported on Device
**What goes wrong:** `AudioRecord.getMinBufferSize()` returns `ERROR_BAD_VALUE` (-2).
**Why it happens:** Some older/cheaper devices may not support float encoding despite API level being sufficient.
**How to avoid:** Check `getMinBufferSize()` return value. Fall back to `ENCODING_PCM_16BIT` and convert to Float32 post-read. With minSdk 29 and Pixel 4 target, this is very unlikely but defensive coding is wise.
**Warning signs:** AudioRecord initialization failure, `getMinBufferSize()` returning negative values.

### Pitfall 4: AudioRecord Buffer Underrun
**What goes wrong:** Gaps or glitches in recorded audio, missed samples.
**Why it happens:** Read loop not consuming samples fast enough; buffer too small.
**How to avoid:** Use `bufferSize * 2` (or more) for AudioRecord constructor. Read on a dedicated coroutine with `Dispatchers.Default`. Never read on Main thread.
**Warning signs:** `AudioRecord.read()` returning 0 or negative values.

### Pitfall 5: Service Not Bound When IME Opens
**What goes wrong:** Mic button tap does nothing because service reference is null.
**Why it happens:** `bindService()` is async; `onServiceConnected()` may not fire before user taps mic.
**How to avoid:** Use `startForegroundService()` + `bindService()` together. Disable mic button UI until service is connected (or start service on first mic tap). Use a `StateFlow<DictationService?>` for the service reference.
**Warning signs:** NullPointerException or silent failure on mic tap.

### Pitfall 6: Memory Growth from Unbounded FloatArray
**What goes wrong:** OutOfMemoryError on long recordings.
**Why it happens:** 16kHz * 4 bytes = 64KB/second. 10 minutes = ~38MB. Longer recordings grow linearly.
**How to avoid:** Since there's no max duration, consider using an `ArrayList<Float>` with pre-allocated capacity, or chunked `FloatArray` list. For MVP, a single growing list is fine -- whisper.cpp models handle ~30s chunks anyway (Phase 3 concern).
**Warning signs:** Memory warnings in logcat during long recordings.

### Pitfall 7: Haptic Feedback Not Working in IME
**What goes wrong:** `LocalHapticFeedback` or `LocalView` returns null or non-functional in IME context.
**Why it happens:** IME window setup differs from Activity; Compose tree may not have proper View attachment.
**How to avoid:** Use `LocalView.current.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` which works in IME because LifecycleInputMethodService attaches Compose to the decorView. Test on real device (emulator haptics are unreliable).
**Warning signs:** No haptic response despite code executing.

## Code Examples

### Foreground Service Manifest Declaration
```xml
<!-- Source: Android official docs - Declare foreground services -->
<!-- In app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<application ...>
    <service
        android:name=".service.DictationService"
        android:foregroundServiceType="microphone"
        android:exported="false" />
</application>
```

### Notification Channel + Foreground Notification
```kotlin
// Source: Android official docs - Notification channels
private fun createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Dictus Recording",
        NotificationManager.IMPORTANCE_LOW  // No sound, minimal presence
    ).apply {
        description = "Active recording notification"
        setShowBadge(false)
    }
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

private fun buildNotification(): Notification {
    val stopIntent = Intent(this, DictationService::class.java).apply {
        action = ACTION_STOP
    }
    val stopPendingIntent = PendingIntent.getService(
        this, 0, stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Dictus - Recording")
        .setSmallIcon(R.drawable.ic_mic) // Need to create this
        .setOngoing(true)
        .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
        .build()
}
```

### ServiceCompat.startForeground with Type
```kotlin
// Source: Android official docs - Launch foreground service
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START -> {
            createNotificationChannel()
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            startAudioCapture()
        }
        ACTION_STOP -> {
            stopRecording()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    return START_NOT_STICKY
}
```

### Haptic Feedback in Compose (IME-compatible)
```kotlin
// Source: Android official docs - Haptic feedback
@Composable
fun HapticKeyButton(
    // ... key params
) {
    val view = LocalView.current

    Box(
        modifier = Modifier.clickable {
            // Light haptic for regular keys
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onKeyPress()
        }
    )
}

// Stronger haptic for mic button (recording state change)
fun performMicHaptic(view: View) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
```

### Waveform Energy Calculation (RMS)
```kotlin
// Matches iOS UnifiedAudioEngine pattern
private fun calculateRmsEnergy(samples: FloatArray, count: Int): Float {
    if (count == 0) return 0f
    var sum = 0f
    for (i in 0 until count) {
        sum += samples[i] * samples[i]
    }
    return sqrt(sum / count)
}

// Maintain rolling 30-bar energy history
private val energyHistory = ArrayDeque<Float>(30)

private fun updateWaveformEnergy(rms: Float) {
    // Normalize to 0.0-1.0 range (typical speech RMS is 0.01-0.3)
    val normalized = (rms * 5f).coerceIn(0f, 1f)
    energyHistory.addLast(normalized)
    if (energyHistory.size > 30) energyHistory.removeFirst()

    _state.value = DictationState.Recording(
        elapsedMs = elapsedMs,
        energy = energyHistory.toList()
    )
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No FGS type required | `foregroundServiceType` mandatory | API 34 (Android 14) | Must declare microphone type in manifest |
| startForeground(id, notification) | ServiceCompat.startForeground(svc, id, notif, type) | API 34 | Must pass FOREGROUND_SERVICE_TYPE_MICROPHONE |
| ENCODING_PCM_16BIT only | ENCODING_PCM_FLOAT supported | API 23 (Android 6.0) | Can capture float directly, no conversion needed |
| LiveData for state | StateFlow + collectAsState() | ~2021 | Better Compose integration, no lifecycle observer boilerplate |
| View.performHapticFeedback() | Same API, more constants added | Ongoing | KEYBOARD_TAP, CONFIRM (API 30+) give richer options |

**Deprecated/outdated:**
- `Service.startForeground(id, notification)` without type parameter -- still compiles but throws on API 34+
- `IntentService` -- deprecated in API 30, use coroutines instead
- Notification without channel -- crashes on API 26+

## Open Questions

1. **RECORD_AUDIO permission flow**
   - What we know: Permission must be granted before starting foreground service with microphone type. The IME itself cannot show permission dialogs.
   - What's unclear: When/where to request RECORD_AUDIO. Likely needs to happen in onboarding (Phase 4) or on first mic tap via a redirect to the main Activity.
   - Recommendation: For Phase 2, assume permission is granted (manually grant in settings for testing). Add a permission check guard in the mic tap handler that logs a warning if not granted. Full permission flow comes in Phase 4 onboarding.

2. **Notification icon resource**
   - What we know: Need `ic_mic` and `ic_stop` drawable resources for the notification.
   - What's unclear: Whether to use vector drawables or Material Icons.
   - Recommendation: Use simple vector drawables (24dp) matching Material Design icon style. Can be placeholder in Phase 2.

3. **Service process configuration**
   - What we know: By default, the service runs in the same process as the app/IME.
   - What's unclear: Whether this is optimal for audio performance.
   - Recommendation: Keep same-process (default). It enables local Binder and avoids IPC serialization overhead. Audio capture coroutine runs on Dispatchers.Default (background thread pool) which is sufficient.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (already in project) |
| Config file | Per-module build.gradle.kts (testImplementation already declared) |
| Quick run command | `./gradlew :app:testDebugUnitTest :ime:testDebugUnitTest --tests "*.DictationState*" -x lint` |
| Full suite command | `./gradlew testDebugUnitTest -x lint` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DICT-01 | Mic tap starts recording (state idle->recording) | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest*" -x lint` | No -- Wave 0 |
| DICT-02 | Confirm stops recording (state recording->idle) | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationStateTest*" -x lint` | No -- Wave 0 |
| DICT-07 | Haptic feedback fires on interactions | manual-only | N/A -- requires real device vibration motor | N/A |
| DICT-08 | Foreground service starts with notification | unit | `./gradlew :app:testDebugUnitTest --tests "*.DictationServiceTest*" -x lint` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest :ime:testDebugUnitTest -x lint`
- **Per wave merge:** `./gradlew testDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/dev/pivisolutions/dictus/service/DictationStateTest.kt` -- covers state machine transitions (DICT-01, DICT-02)
- [ ] `app/src/test/java/dev/pivisolutions/dictus/service/AudioCaptureManagerTest.kt` -- covers buffer management, energy calculation
- [ ] `ime/src/test/java/dev/pivisolutions/dictus/ime/ui/WaveformBarTest.kt` -- covers energy-to-bar-height mapping
- [ ] Notification icon drawables (`ic_mic.xml`, `ic_stop.xml`) -- needed for notification builder

## Sources

### Primary (HIGH confidence)
- [Android Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types) -- microphone type requirements, permissions
- [Declare Foreground Services](https://developer.android.com/develop/background-work/services/fgs/declare) -- manifest declarations, permission requirements
- [Launch Foreground Service](https://developer.android.com/develop/background-work/services/fgs/launch) -- ServiceCompat.startForeground(), timing requirements
- [Bound Services](https://developer.android.com/develop/background-work/services/bound-services) -- local Binder pattern, ServiceConnection
- [AudioRecord API](https://developer.android.com/reference/kotlin/android/media/AudioRecord) -- ENCODING_PCM_FLOAT (API 23+), getMinBufferSize, read(FloatArray)
- [HapticFeedbackConstants](https://developer.android.com/develop/ui/views/haptics/haptic-feedback) -- KEYBOARD_TAP, CONFIRM, no permission needed
- [StateFlow and SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) -- collectAsState() in Compose

### Secondary (MEDIUM confidence)
- [5 Haptic Feedback Implementations in Compose](https://www.sinasamaki.com/haptic-feedback-in-jetpack-compose/) -- LocalView.current pattern for haptics in Compose
- [Fundamentals of Raw Audio on Android](https://www.telefonica.com/en/communication-room/blog/unprocessed-audio-android-kotlin/) -- AudioRecord configuration patterns
- [ServiceCompat and Foreground Services](https://medium.com/@mahbooberezaee68/servicecompat-in-android-a27b1ccc44e2) -- ServiceCompat usage pattern

### Tertiary (LOW confidence)
- iOS reference code (`UnifiedAudioEngine.swift`, `KeyboardState.swift`) -- functional reference, not directly applicable to Android APIs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all APIs are standard Android SDK, well-documented, already partially in the project
- Architecture: HIGH -- local Binder + StateFlow is textbook Android pattern for same-process service communication
- Pitfalls: HIGH -- foreground service requirements are well-documented; ENCODING_PCM_FLOAT gotcha is verified against API level
- Waveform rendering: MEDIUM -- Canvas approach is standard but exact energy calculation/normalization needs tuning on device

**Research date:** 2026-03-23
**Valid until:** 2026-04-23 (stable Android APIs, unlikely to change)
