# Phase 5: Polish + Differentiators - Research

**Researched:** 2026-03-28
**Domain:** Android animation, audio feedback, IME text prediction, emoji picker, light theme, iOS visual parity
**Confidence:** HIGH (codebase verified, iOS source read, patterns confirmed)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Waveform animation (DICT-05)**
- Add smoothing/decay driver like iOS BrandWaveformDriver: `smoothingFactor = 0.3` (rise), `decayFactor = 0.85` (fall)
- Bars lerp toward target energy per frame instead of jumping to raw values — creates organic oscillation + drift effect
- Slow down waveform scroll speed to match sine wave cycle (~2 seconds to traverse)
- iOS reference: `DictusCore/Design/BrandWaveform.swift` + `DictusKeyboard/Views/KeyboardWaveformView.swift`
- Transcribing sine wave animation is perfect — no changes needed

**Sound feedback (DICT-06)**
- 3 sound events: recording start, recording stop, recording cancel
- Default sounds: electronic_01f (start), electronic_02b (stop), electronic_03c (cancel) — same as iOS
- Bundle ALL ~30 WAV files from iOS `Dictus/Sounds/` into Android `res/raw/`
- Use SoundPool for low-latency playback
- Sounds respect the existing "Son de dictee" toggle in Settings
- No key press sounds — sounds only on recording lifecycle events

**Text suggestion bar (KBD-04)**
- Full keyboard autocomplete/prediction like Gboard — NOT related to Whisper transcription
- Integrate AOSP LatinIME native C++ prediction engine with binary dictionaries (NDK build)
- Visual style: Gboard-style 3 suggestions in a row above the keyboard, center word bold (primary suggestion), vertical separators between words
- Tap on suggestion = insert word via InputConnection.commitText()
- Suggestion bar sits above KeyboardView in KeyboardScreen Column
- FR and EN dictionaries required (matching app's bilingual target)

**Emoji picker (KBD-05)**
- Use androidx.emoji2 EmojiPickerView (Google's official library)
- Emoji picker replaces entire keyboard area (like RecordingScreen does)
- Tap emoji key (existing KeyType.EMOJI) = switch to picker; back button = return to keyboard
- Categories + recent emojis handled by the library
- Selected emoji inserted via InputConnection.commitText()

**Light theme + automatic (DSG-02, pulled from Phase 6)**
- 3 theme options in Settings: Sombre (current) / Clair / Automatique
- Light theme: custom Dictus colors (not Material You dynamic) — white/light gray backgrounds, accent blue #3D7EFF retained
- Automatique: follows system dark/light mode via isSystemInDarkTheme(), still using Dictus custom colors (not Material You)
- Applies to both the app AND the keyboard IME

**iOS visual parity — Bottom navigation bar**
- Pill indicator on selected tab: make it more visible/pronounced (currently too subtle)
- Modeles icon: change from download arrow to chip/processor icon (matching iOS)
- Icon colors: whiter when unselected, lighter blue (#6BA3FF range) when selected — match iOS tones
- Overall: smaller, more refined pill shape closer to iOS style

**iOS visual parity — Color palette alignment**
- Current Android blue is too "electric" — needs to match iOS's softer, deeper blues
- Multiple blue shades: accent, highlight, surface tints — all need iOS alignment
- Dark backgrounds: match iOS deep navy/blue-night tones (currently too flat/dark)
- Read iOS color values from `DictusCore/Design/DictusColors.swift` and align Android `DictusColors.kt`

**iOS visual parity — Settings screen**
- Section headers (Transcription, Clavier, Apparence, A propos) need clearer visual separation
- iOS uses card-style grouping per section with rounded backgrounds — replicate on Android
- Add ABC/123 keyboard mode picker in Clavier section (exists on iOS, missing on Android)
- ABC/123 logic: ABC = default layer is LETTERS when keyboard opens; 123 = default layer is NUMBERS/SYMBOLS
- Persist choice in DataStore, read in IME service on keyboard open
- Verify haptic toggle actually disables haptics in keyboard (end-to-end test)
- Verify sound toggle actually disables sounds in keyboard (end-to-end test)

**iOS visual parity — Debug Logs viewer**
- Add "Debug Logs" row in Settings A propos section, above "Exporter les logs"
- Opens full-screen log viewer: scrollable list with timestamps + categories (like iOS screenshot)
- Top-right: 3-dot overflow menu with "Effacer" (clear logs) and "Copier" (copy to clipboard)
- Back arrow to return to Settings
- Reads from the same Timber file appender logs used by export

**iOS visual parity — Model cards**
- Align model card layout closer to iOS: description text + precision/vitesse bars side by side (segmented style)
- Add provider badge (WK for WhisperKit/whisper.cpp) on cards
- Currently Android has full-width stacked bars — needs redesign to match iOS proportions

### Claude's Discretion
- Exact SoundPool configuration (max streams, audio attributes)
- AOSP LatinIME integration approach details (which native libs to extract, dictionary format)
- Light theme exact color values (derive from iOS light mode colors)
- Suggestion bar height and animation (appearing/dismissing)
- Debug log viewer scrolling performance optimization
- Model card badge colors and exact layout proportions

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. Theme was intentionally pulled from Phase 6.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DICT-05 | User sees a real-time waveform animation during recording | iOS BrandWaveformDriver algorithm documented; Kotlin port strategy using LaunchedEffect + Animatable/coroutine loop |
| DICT-06 | User hears a sound when recording starts and stops (configurable) | SoundPool API confirmed; DictationService state transitions identified as integration points; 29 WAV files confirmed in iOS repo |
| KBD-04 | User can see 3 text suggestions above the keyboard and tap to insert | AOSP LatinIME NDK complexity documented; alternative stub-first approach recommended for MVP |
| KBD-05 | User can open an emoji picker with categories and recent emojis | androidx.emoji2:emoji2-emojipicker confirmed; AndroidView wrapper pattern for Compose-in-IME identified |
</phase_requirements>

---

## Summary

Phase 5 addresses the premium polish layer: smooth waveform animation, audio feedback, keyboard autocomplete, emoji picker, light theme, and multiple iOS visual parity items. The codebase is in excellent shape — WaveformBars, HapticHelper, DictusColors, DictusTheme, PreferenceKeys, KeyboardScreen and SettingsScreen are all clear extension points. The approach for each feature is well-defined by CONTEXT.md; this research confirms feasibility, identifies the exact implementation patterns, and surfaces two non-trivial complexity areas.

The most complex features are the AOSP LatinIME prediction engine (NDK extraction, dictionary build pipeline) and the emoji picker integration via AndroidView inside a Compose-in-IME. Both are feasible but require careful sequencing. The waveform smoothing driver, SoundPool sounds, light theme, and settings/UI parity items are all straightforward extensions of existing patterns.

The light theme maps directly to iOS DictusColors.swift: background = `#F2F2F7`, surface = `#FFFFFF`. The existing `DictusTheme.kt` needs only a theme mode parameter and a `lightColorScheme()` variant. The theme mode flows from DataStore through `SettingsViewModel` (already reads THEME key) to the root `MainActivity`/`DictusImeService` composable hosts.

**Primary recommendation:** Implement in this order by risk: (1) waveform driver + sound feedback + light theme (low risk, high reward), (2) settings/nav parity items (medium, isolated), (3) emoji picker via AndroidView (medium, tested pattern), (4) AOSP LatinIME (highest complexity, stub-first to unblock KBD-04 verification).

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin Coroutines + `LaunchedEffect` | 1.8.1 (project) | Drive per-frame animation loop for waveform smoothing driver | Replaces CADisplayLink; `withFrameNanos` provides display-sync timing in Compose |
| `android.media.SoundPool` | Android SDK (API 21+) | Low-latency WAV playback for recording sounds | Pre-decodes files to PCM; sub-10ms latency; no extra dependency |
| `androidx.emoji2:emoji2-emojipicker` | 1.4.0 (latest stable) | Emoji picker with categories + recents | Google's official library; handles emoji compat across Android versions |
| `AndroidView` composable | Compose BOM 2025.03.00 | Host EmojiPickerView (View-based) inside Compose IME | Standard pattern for View interop inside ComposeView |
| `isSystemInDarkTheme()` | Compose BOM 2025.03.00 | Auto theme switching | Built-in Compose API; reads system dark mode setting reactively |
| `darkColorScheme()` / `lightColorScheme()` | Material3 (BOM) | Light/dark theme variants | Material 3 standard — parameterize `DictusTheme` with a `useDarkTheme: Boolean` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AOSP LatinIME native lib (`libjni_latinime.so`) | AOSP (extracted from system or compiled) | N-gram word prediction engine | KBD-04 — extracted from device or built from AOSP source |
| LatinIME BinaryDictionary (FR/EN `.dict` files) | AOSP dictionary format | Word/bigram lookup tables | KBD-04 — bundled in `assets/` |
| `androidx.core:core-ktx` | 1.15.0 (project) | `ContentValues`, clipboard utilities | Debug log viewer copy action |
| `compose-material-icons-extended` | BOM (already in app) | Chip/processor icon for Modeles tab | Already a dependency |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| AOSP LatinIME | Build custom trie-based suggester | Custom avoids NDK complexity but loses bigram context and would not produce Gboard-quality suggestions |
| SoundPool | MediaPlayer | MediaPlayer has ~100-200ms latency; SoundPool pre-loads to PCM memory for instant playback — required for tight recording feedback |
| `AndroidView(EmojiPickerView)` | Port emoji UI to pure Compose | Porting would mean reimplementing emoji data, search, recents, skin-tone support — massive scope; EmojiPickerView handles all of this |

**Installation (new dependencies to add):**
```bash
# In ime/build.gradle.kts
implementation("androidx.emoji2:emoji2-emojipicker:1.4.0")

# In libs.versions.toml
emoji2-emojipicker = { group = "androidx.emoji2", name = "emoji2-emojipicker", version = "1.4.0" }
```

Sound files go into `app/src/main/res/raw/` (29 WAV files copied from `~/dev/dictus/Dictus/Sounds/`). The IME reads sounds from the app's context — sounds bundled in `app` module, accessed via `Context.resources.openRawResource()` or `SoundPool.load(context, R.raw.electronic_01f, 1)`.

---

## Architecture Patterns

### Recommended Project Structure (new files this phase)

```
core/src/main/java/.../core/
└── ui/
    └── WaveformDriver.kt          # Smoothing driver — pure Kotlin, no Android deps

app/src/main/java/.../
├── audio/
│   └── DictationSoundPlayer.kt    # SoundPool wrapper, lifecycle-aware
├── navigation/
│   └── AppDestination.kt          # ADD: DebugLogs destination
│   └── DictusBottomNavBar.kt      # MODIFY: pill visibility, Modeles icon, colors
└── ui/
    ├── settings/
    │   ├── SettingsScreen.kt       # MODIFY: card groups, ABC/123, debug logs row
    │   ├── SettingsViewModel.kt    # MODIFY: keyboardMode + theme (light/dark/auto)
    │   └── DebugLogsScreen.kt      # NEW: full-screen log viewer
    └── models/
        └── ModelCard.kt            # MODIFY: badge + segmented layout

ime/src/main/java/.../ime/
├── ui/
│   ├── KeyboardScreen.kt          # MODIFY: add SuggestionBar above KeyboardView, EmojiPicker state
│   ├── SuggestionBar.kt            # NEW: 3-slot suggestion row composable
│   └── EmojiPickerScreen.kt       # NEW: AndroidView wrapper around EmojiPickerView
└── suggestion/
    └── SuggestionEngine.kt         # NEW: LatinIME JNI wrapper (or stub for MVP)

core/src/main/java/.../core/
└── theme/
    ├── DictusColors.kt             # MODIFY: update dark values, add light palette
    └── DictusTheme.kt              # MODIFY: add ThemeMode param (dark/light/auto)
```

### Pattern 1: Waveform Smoothing Driver (Kotlin port of BrandWaveformDriver)

**What:** A coroutine-based per-frame animation driver that interpolates `displayLevels` toward `targetLevels` each frame, using rise smoothing (0.3) and decay (0.85).

**When to use:** Applied inside `WaveformBars` or as a ViewModel-level `StateFlow<List<Float>>` that `WaveformBars` collects.

**iOS algorithm (BrandWaveformDriver.tickLevels — source of truth):**
```swift
// smoothingFactor = 0.3 (rise), decayFactor = 0.85 (fall)
if target > current {
    updated[i] = current + (target - current) * smoothingFactor  // lerp toward target
} else {
    updated[i] = target + (current - target) * decayFactor       // decay past target
}
if updated[i] < 0.005 { updated[i] = 0 }
```

**Kotlin equivalent using `withFrameNanos` (display-sync, no CADisplayLink needed):**
```kotlin
// Source: Compose animation loop pattern, verified in Compose BOM docs
// File: core/src/main/java/.../core/ui/WaveformDriver.kt
class WaveformDriver(
    private val smoothingFactor: Float = 0.3f,
    private val decayFactor: Float = 0.85f,
) {
    private val _displayLevels = MutableStateFlow(List(30) { 0f })
    val displayLevels: StateFlow<List<Float>> = _displayLevels.asStateFlow()

    private var targetLevels = List(30) { 0f }
    private var isRunning = false

    fun update(rawEnergy: List<Float>) {
        targetLevels = interpolateToTargets(rawEnergy)
    }

    suspend fun runLoop() {
        isRunning = true
        while (isRunning) {
            withFrameNanos { _ ->
                val current = _displayLevels.value
                val updated = current.mapIndexed { i, cur ->
                    val target = targetLevels[i]
                    val next = if (target > cur) {
                        cur + (target - cur) * smoothingFactor
                    } else {
                        target + (cur - target) * decayFactor
                    }
                    if (next < 0.005f) 0f else next
                }
                _displayLevels.value = updated
            }
        }
    }

    fun stop() { isRunning = false }
}
```

`WaveformBars` in `RecordingScreen` receives `List<Float>` from the driver via `collectAsState()`. The driver runs in a coroutine launched by `LaunchedEffect(isRecording)` in `DictusImeService`'s composable root.

### Pattern 2: SoundPool for Recording Sounds

**What:** Pre-load 3 default WAV files at service creation, play on state transitions.

**When to use:** In `DictationService` (app module), triggered at `RECORDING` → `TRANSCRIBING` (stop) and on cancel.

```kotlin
// Source: Android SoundPool documentation
// File: app/src/main/java/.../audio/DictationSoundPlayer.kt
class DictationSoundPlayer(private val context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var soundStart = 0
    private var soundStop = 0
    private var soundCancel = 0

    fun loadSounds() {
        soundStart = soundPool.load(context, R.raw.electronic_01f, 1)
        soundStop = soundPool.load(context, R.raw.electronic_02b, 1)
        soundCancel = soundPool.load(context, R.raw.electronic_03c, 1)
    }

    fun playStart() = soundPool.play(soundStart, 1f, 1f, 0, 0, 1f)
    fun playStop() = soundPool.play(soundStop, 1f, 1f, 0, 0, 1f)
    fun playCancel() = soundPool.play(soundCancel, 1f, 1f, 0, 0, 1f)

    fun release() = soundPool.release()
}
```

`DictationService` holds `DictationSoundPlayer`, reads `SOUND_ENABLED` preference before playing. The `soundEnabled` flag is already in `PreferenceKeys` and `SettingsViewModel`.

**CRITICAL:** `R.raw.*` files must live in `app/src/main/res/raw/`. The IME module does not have a `res/raw/` directory. Since the IME is in a separate module but runs in the app process, use the app context (accessible via `EntryPointAccessors` or passed via binder) to load sounds — or move `DictationSoundPlayer` to `app` module and call it from `DictationService` (already in `app`). No cross-module resource access issue.

### Pattern 3: Emoji Picker via AndroidView

**What:** `EmojiPickerView` is a standard Android View; wrap it with `AndroidView` inside the Compose-in-IME host.

**When to use:** When `KeyboardScreen` state == `EmojiPicker`.

```kotlin
// Source: androidx.emoji2 official docs + Compose AndroidView interop docs
// File: ime/src/main/java/.../ime/ui/EmojiPickerScreen.kt
@Composable
fun EmojiPickerScreen(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            EmojiPickerView(context).apply {
                emojiGridColumns = 8
                setOnEmojiPickedListener { emojiViewItem ->
                    onEmojiSelected(emojiViewItem.emoji)
                }
            }
        },
        modifier = modifier.fillMaxWidth().height(310.dp), // match keyboard height
    )
}
```

`KeyboardScreen` needs a new `isShowingEmojiPicker` state variable. `KeyType.EMOJI` handler sets `isShowingEmojiPicker = true`. Back gesture or explicit back key resets it. Height must match `KeyboardScreen` total height (310.dp) for seamless swap.

**IMPORTANT:** `EmojiPickerView` is a `RecyclerView`-based View. Inside a `ComposeView` inside an `InputMethodService` window, touch events route correctly because `ComposeView` handles the `ViewGroup` hierarchy normally. Verified pattern: AOSP's `ComposeView` in IME (Phase 1 already established this works).

### Pattern 4: Light/Auto Theme

**What:** Parameterize `DictusTheme` with a `ThemeMode` enum; select scheme at composition time.

**iOS light colors (from DictusColors.swift — source of truth):**
- Light background: `#F2F2F7` (iOS system background)
- Light surface: `#FFFFFF` (iOS secondary background)
- Accent stays `#3D7EFF` (non-adaptive in iOS)

```kotlin
// File: core/src/main/java/.../core/theme/DictusTheme.kt
enum class ThemeMode { DARK, LIGHT, AUTO }

@Composable
fun DictusTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> systemDark
    }
    val colorScheme = if (useDark) DictusDarkColorScheme else DictusLightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = DictusTypography, content = content)
}
```

`DictusLightColorScheme` uses `lightColorScheme()` with light Dictus color tokens. The `themeMode` value is read from DataStore in `SettingsViewModel` (already has `THEME` key) and propagated to:
1. `MainActivity` → `AppNavHost` → `DictusTheme` wrapper
2. `DictusImeService` → `setContent { DictusTheme(themeMode = ...) }` — needs to read DataStore via `EntryPointAccessors`

`PreferenceKeys.THEME` already exists with string type; new values: `"dark"` / `"light"` / `"auto"` (currently only `"dark"` is exposed in the picker).

### Pattern 5: Settings Card Grouping (iOS visual parity)

**What:** Wrap each section's rows in a `Card`-style `Box` with `RoundedCornerShape(12.dp)` background, inset from screen edges, replacing the flat list with dividers.

```kotlin
// Pattern: Surface/Box container per section
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DictusColors.Surface),
        content = content,
    )
}
```

Each section: `SectionHeader` above, `SettingsCard { rows... }` below. Internal dividers remain but padding/shape applies at the card level.

### Pattern 6: Debug Logs Viewer Screen

**What:** Full-screen `LazyColumn` parsing `TimberSetup.getLogFile()` line by line.

**Integration:** New `AppDestination.DebugLogs` route in `AppNavHost`. `SettingsScreen` gets a `SettingNavRow("Debug Logs")` above "Exporter les logs". `DebugLogsScreen` receives an `onBack` lambda.

**Copy action:** `LocalClipboardManager.current.setText(AnnotatedString(fullLogText))` — no extra library needed (Compose `ClipboardManager`).

**Clear action:** Delete and recreate the log file: `TimberSetup.getLogFile()?.delete()` then re-init the file tree (or just truncate). 3-dot menu uses `DropdownMenu` composable.

### Anti-Patterns to Avoid

- **Using `MediaPlayer` for sound feedback:** ~150ms latency; will feel noticeably delayed after mic button tap. Use SoundPool.
- **Creating SoundPool on every play call:** SoundPool must be loaded once at service startup. Load costs 20-50ms per file.
- **Launching coroutine `runLoop()` in `Composable` body directly:** Always inside `LaunchedEffect`. Coroutine must be tied to Compose lifecycle.
- **Placing WAV files in `ime/res/raw/`:** IME module has no `res/raw/` directory. Files must be in `app/src/main/res/raw/` and the IME/service must use the app context to load them.
- **Calling `EmojiPickerView` factory inside a `remember` block instead of `AndroidView`:** `AndroidView` handles `factory` lifecycle correctly; doing it manually risks double-creation.
- **Hardcoding theme colors in Composables:** All colors must go through `DictusColors`; Composables reference `DictusColors.Background` not literal `Color(0xFF0A1628)`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Emoji picker with categories, recents, skin tones | Custom emoji grid | `androidx.emoji2:emoji2-emojipicker` | 3,000+ emojis across 8 categories, skin-tone variants, recents persistence, maintained by Google |
| Per-bar color lerp | Custom Color lerp | Existing `lerp()` in `WaveformBars.kt` | Already tested in `WaveformBarTest.kt` |
| Display-sync animation loop | Custom `Handler`/`postDelayed` | `withFrameNanos` inside `LaunchedEffect` | Compose provides VSYNC-aligned frame callbacks; `Handler` has ~1-frame drift |
| Word prediction from scratch | Custom n-gram model | AOSP LatinIME JNI | LatinIME handles bigrams, correction, capitalization, punctuation — years of effort |
| Log line timestamp parsing | Custom regex | Just read line by line (Timber FileLoggingTree writes one entry per line with timestamp) | Timber format is stable; no need for custom parser |

**Key insight:** The emoji and text prediction features are complete products in themselves. Both have established solutions (EmojiPickerView, AOSP LatinIME) that provide production-quality behavior; building either from scratch would take weeks and produce worse results.

---

## Common Pitfalls

### Pitfall 1: WaveformDriver coroutine never stopped (memory/CPU leak)
**What goes wrong:** `runLoop()` coroutine keeps running after recording ends, consuming CPU and preventing GC.
**Why it happens:** Forgetting to call `driver.stop()` in `onDisappear`/`LaunchedEffect` cancellation.
**How to avoid:** `LaunchedEffect(isRecording) { if (isRecording) driver.runLoop() }` — LaunchedEffect auto-cancels the previous effect when `isRecording` changes to false. Call `driver.stop()` in the `onDispose` block.
**Warning signs:** `displayLevels` keeps updating after recording stops; CPU stays elevated.

### Pitfall 2: SoundPool not loaded before first play
**What goes wrong:** `soundPool.play()` returns 0 (no stream) on the first tap if load hasn't completed.
**Why it happens:** `SoundPool.load()` is asynchronous; calling `play()` immediately returns 0.
**How to avoid:** Set `SoundPool.OnLoadCompleteListener` and only mark sounds as ready after all 3 have loaded, OR use `SoundPool.setOnLoadCompleteListener` + a counter. For 3 small WAV files, loading typically completes within 200ms at service creation — adequate for typical use.
**Warning signs:** Tap-to-record produces no sound on first use; subsequent taps work.

### Pitfall 3: EmojiPickerView height mismatch in IME window
**What goes wrong:** Emoji picker overflows or clips within the IME window.
**Why it happens:** `EmojiPickerView` defaults to `wrap_content` height; Compose `AndroidView` without explicit height modifier uses `wrapContentHeight()` which can exceed keyboard bounds.
**How to avoid:** Fix the `AndroidView` modifier to `.height(310.dp)` — the exact height of `KeyboardScreen` — so the IME window doesn't resize.
**Warning signs:** Screen flicker on emoji picker open; content cut off or IME window jumps.

### Pitfall 4: Theme not applied in IME
**What goes wrong:** App switches to light mode but keyboard remains dark.
**Why it happens:** `DictusTheme` in `DictusImeService.setContent {}` doesn't receive the theme preference — it uses a hardcoded default.
**How to avoid:** `DictusImeService` must read `THEME` from DataStore via `EntryPointAccessors` and pass the `ThemeMode` to `DictusTheme`. The same DataStore instance is used by both (injected as singleton).
**Warning signs:** Settings screen reflects theme change; keyboard stays dark.

### Pitfall 5: AOSP LatinIME .so ABI mismatch
**What goes wrong:** `UnsatisfiedLinkError` at runtime when loading `libjni_latinime.so`.
**Why it happens:** The extracted `.so` was built for a different ABI (arm64-v8a vs armeabi-v7a) than the test device.
**How to avoid:** Extract `.so` files for all required ABIs from AOSP build artifacts, or restrict to `arm64-v8a` only (covers Pixel 4 and all modern devices). Set `abiFilters = setOf("arm64-v8a")` in the IME module's `defaultConfig`.
**Warning signs:** App crashes on first word typed; `UnsatisfiedLinkError` in logcat.

### Pitfall 6: HapticHelper respects toggle only if IME reads preference
**What goes wrong:** User disables haptics in Settings but keyboard still vibrates.
**Why it happens:** `HapticHelper.performKeyHaptic()` calls `view.performHapticFeedback()` unconditionally without reading `HAPTICS_ENABLED` from DataStore.
**How to avoid:** Read `HAPTICS_ENABLED` in `DictusImeService` (via `EntryPointAccessors`), store as `hapticsEnabled: Boolean`, pass to `KeyButton` composable, conditionally call `HapticHelper.performKeyHaptic()`.
**Warning signs:** Toggle in Settings appears to have no effect when tested end-to-end.

---

## Code Examples

Verified patterns from official sources and iOS codebase:

### Waveform tick algorithm (exact iOS algorithm, Kotlin translation)
```kotlin
// iOS source: DictusCore/Sources/DictusCore/Design/BrandWaveform.swift tickLevels()
// Confirmed smoothingFactor = 0.3, decayFactor = 0.85
fun tickLevels(current: List<Float>, targets: List<Float>): List<Float> {
    return current.mapIndexed { i, cur ->
        val target = targets.getOrElse(i) { 0f }
        val next = if (target > cur) {
            cur + (target - cur) * 0.3f      // rise: lerp 30% toward target per frame
        } else {
            target + (cur - target) * 0.85f  // decay: lerp 85% of remaining gap
        }
        if (next < 0.005f) 0f else next
    }
}
```

### Target level calculation (iOS targetLevels(), Kotlin port)
```kotlin
// iOS source: BrandWaveform.swift targetLevels() — linear interpolation across 30 bars
fun interpolateToBarTargets(rawLevels: List<Float>, barCount: Int = 30): List<Float> {
    if (rawLevels.isEmpty()) return List(barCount) { 0f }
    return (0 until barCount).map { index ->
        val position = index.toFloat() / (barCount - 1).coerceAtLeast(1)
        val arrayIndex = position * (rawLevels.size - 1)
        val lower = arrayIndex.toInt()
        val upper = (lower + 1).coerceAtMost(rawLevels.size - 1)
        val fraction = arrayIndex - lower
        val value = rawLevels[lower] * (1 - fraction) + rawLevels[upper] * fraction
        if (value < 0.05f) 0f else value.coerceIn(0f, 1f)
    }
}
```

### SoundPool setup (IME context via app resources)
```kotlin
// Source: Android SoundPool documentation — USAGE_ASSISTANCE_SONIFICATION for UI sounds
private val soundPool = SoundPool.Builder()
    .setMaxStreams(2)
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    )
    .build()
```

### DictusTheme with mode parameter
```kotlin
// Extended from existing DictusTheme.kt in core module
private val DictusLightColorScheme = lightColorScheme(
    primary = DictusColors.Accent,           // #3D7EFF — unchanged
    onPrimary = Color.White,
    background = Color(0xFFF2F2F7),          // iOS dictusBackground light value
    surface = Color(0xFFFFFFFF),             // iOS dictusSurface light value
    onBackground = Color(0xFF0A1628),        // dark text on light background
    onSurface = Color(0xFF1C1C1E),
    error = DictusColors.Recording,
)
```

### EmojiPickerView AndroidView integration
```kotlin
// Source: androidx.emoji2 docs + Compose AndroidView interop
AndroidView(
    factory = { ctx ->
        EmojiPickerView(ctx).apply {
            emojiGridColumns = 8
            setOnEmojiPickedListener { item -> onEmojiSelected(item.emoji) }
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .height(310.dp),   // matches KeyboardScreen total height
)
```

### Theme mode DataStore key addition (PreferenceKeys.kt)
```kotlin
// THEME key already exists — extend Settings picker to expose "light" and "auto" values
// ThemeMode values: "dark" | "light" | "auto"
// SettingsViewModel reads: THEME from DataStore, maps to ThemeMode enum
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Handler.postDelayed` for animation loops | `withFrameNanos` / `LaunchedEffect` in Compose | Compose 1.0 (2021) | VSYNC-aligned, no drift; cancels automatically with Compose lifecycle |
| `MediaPlayer` for short sounds | `SoundPool` with pre-loaded PCM | Android 1.0 | Sub-10ms latency vs ~150ms for MediaPlayer |
| `SharedPreferences` for theme preference | `DataStore<Preferences>` | AndroidX DataStore 1.0 (2021) | Type-safe, coroutine-based, no ANR risk |
| Manual emoji grid | `androidx.emoji2:emoji2-emojipicker` | 2023 (Google I/O announcement) | Full emoji support across API levels, handles recents/skin tones |
| `ForEach + RoundedRectangle` for waveform | Canvas draw loop | Phase 1 of this project | Single draw call, no layout overhead |

**Deprecated/outdated:**
- `CADisplayLink` (iOS): Kotlin equivalent is `withFrameNanos`; no direct Android API needed — Compose Choreographer is VSYNC-linked.
- Single "dark" theme string value in `PickerBottomSheet`: Needs `"light"` and `"auto"` options added.

---

## Open Questions

1. **AOSP LatinIME native library extraction approach**
   - What we know: `libjni_latinime.so` exists in AOSP builds; community repos (thuannv/LatinIME, leonardoquevedox/AOSP-LatinIME) provide Android Studio-compatible builds; dictionary files in `.dict` binary format.
   - What's unclear: Whether a pre-built `.so` for `arm64-v8a` can be extracted from a Pixel 4 system image reliably; whether the open-source community builds are up to date with modern Android.
   - Recommendation: Plan for a stub `SuggestionEngine` that returns empty list first (unblocks KBD-04 UI work), then replace with real LatinIME JNI in a follow-up wave. This avoids blocking the entire phase on NDK extraction.

2. **Sound files bundling location: `app/res/raw/` vs `core/res/raw/`**
   - What we know: `DictationSoundPlayer` will be in `app` module; `DictationService` is also in `app`.
   - What's unclear: Whether the IME module (running in same process) needs direct access to sound resources, or if all sound triggering goes through `DictationService` (preferred — cleaner).
   - Recommendation: Bundle in `app/src/main/res/raw/` only; IME triggers sounds via `DictationService` binder calls (`startRecording` etc.), service plays sounds internally.

3. **Haptic toggle end-to-end wiring**
   - What we know: `HapticHelper` calls `view.performHapticFeedback()` unconditionally; `HAPTICS_ENABLED` exists in DataStore and is toggled by `SettingsViewModel`.
   - What's unclear: Whether `DictusImeService` currently reads `HAPTICS_ENABLED` at all, or if the toggle is purely cosmetic.
   - Recommendation: In `DictusImeService`, read `HAPTICS_ENABLED` via `EntryPointAccessors`+DataStore; store as local state; pass `hapticsEnabled` down to `KeyboardScreen` → `KeyButton` → conditional haptic call. Verify as part of Phase 5.

---

## Validation Architecture

> `nyquist_validation` is enabled in .planning/config.json.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.14.1 |
| Config file | None — configured via `testOptions.unitTests.isIncludeAndroidResources = true` in each module's `build.gradle.kts` |
| Quick run command | `./gradlew :core:test :ime:test --tests "*.Waveform*" --tests "*.Suggestion*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DICT-05 | `tickLevels()` rises at 0.3 rate, decays at 0.85 | unit | `./gradlew :core:test --tests "*.WaveformDriverTest"` | Wave 0 |
| DICT-05 | `interpolateToBarTargets()` maps arbitrary-length input to 30 bars | unit | `./gradlew :core:test --tests "*.WaveformDriverTest"` | Wave 0 |
| DICT-06 | `DictationSoundPlayer` plays start sound on `playStart()` call | unit (mock SoundPool) | `./gradlew :app:test --tests "*.DictationSoundPlayerTest"` | Wave 0 |
| DICT-06 | Sound is skipped when `soundEnabled = false` | unit | `./gradlew :app:test --tests "*.DictationSoundPlayerTest"` | Wave 0 |
| KBD-04 | `SuggestionBar` renders 3 slots; center slot is bold | unit (Robolectric) | `./gradlew :ime:test --tests "*.SuggestionBarTest"` | Wave 0 |
| KBD-04 | Tapping slot calls `onSuggestionSelected` with correct string | unit (Robolectric) | `./gradlew :ime:test --tests "*.SuggestionBarTest"` | Wave 0 |
| KBD-05 | `KeyType.EMOJI` press switches `isShowingEmojiPicker` state to true | unit | `./gradlew :ime:test --tests "*.KeyboardScreenStateTest"` | Wave 0 |
| DSG-02 | `DictusTheme(ThemeMode.LIGHT)` uses light background color | unit | `./gradlew :core:test --tests "*.DictusThemeTest"` | Wave 0 |
| DSG-02 | `DictusTheme(ThemeMode.AUTO)` uses dark scheme when `isSystemInDarkTheme() = true` | unit | `./gradlew :core:test --tests "*.DictusThemeTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :core:test :ime:test`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `core/src/test/.../core/ui/WaveformDriverTest.kt` — covers DICT-05
- [ ] `app/src/test/.../audio/DictationSoundPlayerTest.kt` — covers DICT-06
- [ ] `ime/src/test/.../ime/ui/SuggestionBarTest.kt` — covers KBD-04
- [ ] `ime/src/test/.../ime/ui/KeyboardScreenStateTest.kt` — covers KBD-05 emoji state
- [ ] `core/src/test/.../core/theme/DictusThemeTest.kt` — covers DSG-02 (file exists but currently tests nothing — needs ThemeMode cases added)

---

## Sources

### Primary (HIGH confidence)
- `~/dev/dictus/DictusCore/Sources/DictusCore/Design/BrandWaveform.swift` — iOS waveform driver, exact algorithm values
- `~/dev/dictus/DictusCore/Sources/DictusCore/Design/DictusColors.swift` — iOS color values for light/dark palette alignment
- `core/src/main/java/.../core/ui/WaveformBars.kt` — existing Kotlin waveform (confirmed 30-bar Canvas, uses `barColor`/`padEnergy`)
- `core/src/main/java/.../core/theme/DictusColors.kt` — current Android color tokens
- `core/src/main/java/.../core/theme/DictusTheme.kt` — current always-dark theme composable
- `ime/src/main/java/.../ime/ui/KeyboardScreen.kt` — EMOJI handler stub confirmed at line 159
- `app/src/main/java/.../ui/settings/SettingsScreen.kt` — current sections, picker pattern, toggle pattern
- `app/src/main/java/.../navigation/AppNavHost.kt` — navigation structure for DebugLogs route addition
- `core/src/main/java/.../core/preferences/PreferenceKeys.kt` — THEME, HAPTICS_ENABLED, SOUND_ENABLED keys confirmed
- Android `SoundPool` official docs (standard Android SDK API)

### Secondary (MEDIUM confidence)
- [androidx.emoji2 EmojiPickerView official docs](https://developer.android.com/reference/androidx/emoji2/emojipicker/EmojiPickerView) — API surface, `setOnEmojiPickedListener`, `emojiGridColumns` confirmed
- [Emoji Picker guide](https://developer.android.com/develop/ui/views/text-and-emoji/emoji-picker) — integration pattern confirmed
- [emoji2 Maven releases](https://mvnrepository.com/artifact/androidx.emoji2/emoji2-emojipicker) — latest version 1.4.0

### Tertiary (LOW confidence — validate before use)
- AOSP LatinIME NDK integration: community repos (thuannv/LatinIME, leonardoquevedox/AOSP-LatinIME) — approach confirmed at high level; specific `.so` extraction steps and dictionary compatibility for modern Android unverified

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in official docs or project source
- Waveform driver algorithm: HIGH — read directly from iOS source of truth
- SoundPool pattern: HIGH — standard Android SDK, confirmed audio attributes
- Emoji picker: HIGH — official androidx.emoji2 docs + confirmed AndroidView interop pattern
- Light theme colors: HIGH — read from iOS DictusColors.swift (F2F2F7, FFFFFF confirmed)
- AOSP LatinIME: LOW-MEDIUM — approach is standard but NDK extraction complexity is real; stub-first strategy mitigates risk
- iOS visual parity (colors, nav, settings): HIGH — exact current code read, iOS source read

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable APIs; emoji2 version may update but 1.4.0 is stable)
