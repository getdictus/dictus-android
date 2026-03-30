# Phase 5: Polish + Differentiators - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Users experience premium feedback and convenience features during dictation and typing: smooth waveform animation, configurable sound feedback, text suggestion bar (AOSP LatinIME), emoji picker, light theme + automatic theme switching, and iOS visual parity polish across the app.

Pulled from Phase 6: light theme + automatic theme mode (DSG-02). Phase 6 retains: bilingual UI (DSG-03), cross-app validation, performance profiling.

</domain>

<decisions>
## Implementation Decisions

### Waveform animation (DICT-05)
- Add smoothing/decay driver like iOS BrandWaveformDriver: `smoothingFactor = 0.3` (rise), `decayFactor = 0.85` (fall)
- Bars lerp toward target energy per frame instead of jumping to raw values — creates organic oscillation + drift effect
- Slow down waveform scroll speed to match sine wave cycle (~2 seconds to traverse)
- iOS reference: `DictusCore/Design/BrandWaveform.swift` + `DictusKeyboard/Views/KeyboardWaveformView.swift`
- Transcribing sine wave animation is perfect — no changes needed

### Sound feedback (DICT-06)
- 3 sound events: recording start, recording stop, recording cancel
- Default sounds: electronic_01f (start), electronic_02b (stop), electronic_03c (cancel) — same as iOS
- Bundle ALL ~30 WAV files from iOS `Dictus/Sounds/` into Android `res/raw/`
- Use SoundPool for low-latency playback
- Sounds respect the existing "Son de dictee" toggle in Settings
- No key press sounds — sounds only on recording lifecycle events

### Text suggestion bar (KBD-04)
- Full keyboard autocomplete/prediction like Gboard — NOT related to Whisper transcription
- Integrate AOSP LatinIME native C++ prediction engine with binary dictionaries (NDK build)
- Visual style: Gboard-style 3 suggestions in a row above the keyboard, center word bold (primary suggestion), vertical separators between words
- Tap on suggestion = insert word via InputConnection.commitText()
- Suggestion bar sits above KeyboardView in KeyboardScreen Column
- FR and EN dictionaries required (matching app's bilingual target)

### Emoji picker (KBD-05)
- Use androidx.emoji2 EmojiPickerView (Google's official library)
- Emoji picker replaces entire keyboard area (like RecordingScreen does)
- Tap emoji key (existing KeyType.EMOJI) = switch to picker; back button = return to keyboard
- Categories + recent emojis handled by the library
- Selected emoji inserted via InputConnection.commitText()

### Light theme + automatic (DSG-02, pulled from Phase 6)
- 3 theme options in Settings: Sombre (current) / Clair / Automatique
- Light theme: custom Dictus colors (not Material You dynamic) — white/light gray backgrounds, accent blue #3D7EFF retained
- Automatique: follows system dark/light mode via isSystemInDarkTheme(), still using Dictus custom colors (not Material You)
- Applies to both the app AND the keyboard IME

### iOS visual parity — Bottom navigation bar
- Pill indicator on selected tab: make it more visible/pronounced (currently too subtle)
- Modeles icon: change from download arrow to chip/processor icon (matching iOS)
- Icon colors: whiter when unselected, lighter blue (#6BA3FF range) when selected — match iOS tones
- Overall: smaller, more refined pill shape closer to iOS style

### iOS visual parity — Color palette alignment
- Current Android blue is too "electric" — needs to match iOS's softer, deeper blues
- Multiple blue shades: accent, highlight, surface tints — all need iOS alignment
- Dark backgrounds: match iOS deep navy/blue-night tones (currently too flat/dark)
- Read iOS color values from `DictusCore/Design/DictusColors.swift` and align Android `DictusColors.kt`

### iOS visual parity — Settings screen
- Section headers (Transcription, Clavier, Apparence, A propos) need clearer visual separation
- iOS uses card-style grouping per section with rounded backgrounds — replicate on Android
- Add ABC/123 keyboard mode picker in Clavier section (exists on iOS, missing on Android)
- ABC/123 logic: ABC = default layer is LETTERS when keyboard opens; 123 = default layer is NUMBERS/SYMBOLS
- Persist choice in DataStore, read in IME service on keyboard open
- Verify haptic toggle actually disables haptics in keyboard (end-to-end test)
- Verify sound toggle actually disables sounds in keyboard (end-to-end test)

### iOS visual parity — Debug Logs viewer
- Add "Debug Logs" row in Settings A propos section, above "Exporter les logs"
- Opens full-screen log viewer: scrollable list with timestamps + categories (like iOS screenshot)
- Top-right: 3-dot overflow menu with "Effacer" (clear logs) and "Copier" (copy to clipboard)
- Back arrow to return to Settings
- Reads from the same Timber file appender logs used by export

### iOS visual parity — Model cards
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Waveform (iOS reference for smoothing/decay driver)
- `~/dev/dictus/DictusCore/Sources/DictusCore/Design/BrandWaveform.swift` — BrandWaveformDriver with smoothingFactor/decayFactor, Canvas rendering, processingEnergy sine wave
- `~/dev/dictus/DictusKeyboard/Views/KeyboardWaveformView.swift` — KeyboardWaveformDriver with CADisplayLink tick loop, targetLevels interpolation

### Sound files (copy to Android)
- `~/dev/dictus/Dictus/Sounds/` — All ~30 WAV files to bundle in res/raw/

### Color alignment (iOS source of truth)
- `~/dev/dictus/DictusCore/Sources/DictusCore/Design/` — iOS color definitions, gradients, theme tokens

### Settings reference (iOS)
- `~/dev/dictus/DictusApp/Views/SettingsView.swift` — Section grouping, ABC/123 picker, debug logs, sound picker

### Existing Android code to extend
- `core/src/main/java/dev/pivisolutions/dictus/core/ui/WaveformBars.kt` — Current waveform, needs smoothing driver
- `core/src/main/java/dev/pivisolutions/dictus/core/theme/DictusColors.kt` — Color tokens to update for iOS alignment + add light theme
- `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt` — Integration point for suggestion bar + emoji picker
- `ime/src/main/java/dev/pivisolutions/dictus/ime/model/KeyboardLayouts.kt` — KeyType.EMOJI key already defined, handler is stub
- `ime/src/main/java/dev/pivisolutions/dictus/ime/haptics/HapticHelper.kt` — Haptic feedback, verify toggle works
- `app/src/main/java/dev/pivisolutions/dictus/ui/settings/SettingsScreen.kt` — Settings UI to redesign sections + add ABC/123 + debug logs

### Requirements
- `.planning/REQUIREMENTS.md` — DICT-05, DICT-06, KBD-04, KBD-05, DSG-02

### Design mockups
- `design/dictus-android-design.pen` — Waveform spec `VUDbq`, Home `wrqi0`, Models `kSWvw`, Settings `CqKvx`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `WaveformBars.kt` (core): 30-bar Canvas waveform — extend with smoothing driver, don't rewrite
- `HapticHelper.kt` (ime): Haptic feedback on all keys — verify toggle integration
- `RecordingScreen.kt` (ime): Recording overlay — add sound playback on state transitions
- `TranscribingScreen.kt` (ime): Sine wave animation with rememberInfiniteTransition — reference pattern for other animations
- `DictusColors.kt` (core): Color tokens — update values for iOS alignment, add light palette
- `DictusTheme.kt` (core): Theme composable — extend with light/dark/auto switching
- `PreferenceKeys.kt` (core): DataStore keys — add KEYBOARD_MODE (ABC/123), THEME (light/dark/auto)
- `KeyboardLayouts.kt` (ime): KeyType.EMOJI already in all layers — wire handler

### Established Patterns
- Compose-based IME rendering in KeyboardScreen (swap content by state)
- DataStore for preferences, read in SettingsViewModel
- SharingStarted.Eagerly for ViewModel StateFlows (testability)
- Hilt DI via EntryPointAccessors for IME
- Canvas-based rendering for performance-critical UI (waveform)

### Integration Points
- `KeyboardScreen` Column: insert SuggestionBar above KeyboardView
- `KeyboardScreen` state switch: add EmojiPicker state alongside Recording/Transcribing/Idle
- `DictusImeService`: read KEYBOARD_MODE preference to set initial layer on keyboard open
- `DictationService`: play sounds on state transitions (Recording start/stop/cancel)
- `SettingsScreen`: redesign section grouping, add ABC/123 picker, add Debug Logs row
- `MainActivity/AppNavHost`: add DebugLogsScreen route

</code_context>

<specifics>
## Specific Ideas

- Waveform smoothing must feel like iOS: "oscillation + drift" not rigid shift. Study BrandWaveformDriver's tickLevels() carefully
- Colors: iOS blues are softer/deeper — read actual hex values from DictusColors.swift, don't guess
- Settings sections: iOS uses card-style rounded backgrounds per group — replicate that visual pattern
- ABC/123 is a real user-facing feature that affects first impression when opening keyboard
- Debug Logs viewer: same log data as export, just displayed in-app with copy/clear actions
- Bottom nav pill: iOS has a visible filled pill behind the selected tab — Android version is too subtle

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Theme was intentionally pulled from Phase 6.

</deferred>

---

*Phase: 05-polish-differentiators*
*Context gathered: 2026-03-28*
