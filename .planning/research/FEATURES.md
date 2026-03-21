# Feature Landscape

**Domain:** Android voice dictation custom keyboard (on-device, privacy-first)
**Researched:** 2026-03-21
**Confidence:** MEDIUM-HIGH (based on competitive analysis + PRD + iOS parity requirements)

## Competitive Context

The Android voice dictation keyboard space has distinct tiers:

1. **Mainstream keyboards with voice** -- Gboard (dominant), SwiftKey, Samsung Keyboard. Voice is a feature, not the product.
2. **Overlay dictation apps** -- Wispr Flow (cloud-based, overlay bubble, works with any keyboard). Not a keyboard itself.
3. **Privacy-first keyboards** -- FUTO Keyboard (full keyboard + offline Whisper voice input, open source, closest competitor).
4. **Whisper-only keyboards** -- Transcribro, WhisperInput, Kaiboard. Voice-only input, no full keyboard layout.

Dictus sits at the intersection of tiers 3 and 4: a full custom keyboard with integrated on-device Whisper dictation, privacy-first, open source. FUTO Keyboard is the direct competitor.

---

## Table Stakes

Features users expect from any custom keyboard with voice dictation. Missing any of these and users will return to Gboard.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Full letter layout (AZERTY/QWERTY) | Every keyboard has it. Users will not switch without a complete typing experience. | Medium | Must include shift, caps lock, long-press accented characters. AZERTY is a differentiator for French users but table stakes for Dictus's target audience. |
| Number and symbol layers | Standard keyboard feature. Users type numbers, punctuation, special characters constantly. | Low | Two layers: numbers/symbols (?123) and extended symbols (=\<). |
| Backspace, enter, space bar | Fundamental text editing. | Low | Space bar must be large and centered. Enter key must respect `imeOptions` (search, done, next, etc.). |
| Microphone button (dictation trigger) | Core product purpose. Must be prominent and easy to reach. | Low | Large, visually distinct, always visible. This is THE feature that defines Dictus. |
| On-device speech-to-text | Core value proposition. Users chose Dictus for privacy. | High | whisper.cpp via JNI. This is the most complex feature. Must work offline. |
| Recording feedback (visual) | User must know the app is listening. Without visual feedback, users will wonder if dictation started. | Medium | Waveform animation, recording indicator, elapsed time. Gboard shows a pulsing mic icon at minimum. |
| Text insertion into any app | The keyboard must work everywhere -- messaging apps, browsers, email, notes. | Low | `InputConnection.commitText()`. Must handle all text field types. |
| Settings screen | Users expect to configure their keyboard. | Low | Model selection, language, layout, haptics, sound, theme. |
| Onboarding flow | IME activation on Android is confusing (Settings > System > Keyboard). Users need hand-holding. | Medium | Must guide through: enable IME, set as default, grant mic permission, download model. Without this, most users will never activate the keyboard. |
| Model download/management | Users need to download the STT model before first use. Must be clear about size, speed, and storage. | Medium | Download from HuggingFace, show progress, storage indicator, delete unused models. |
| Haptic feedback on key press | Every major keyboard provides key press haptics. Typing without haptics feels broken. | Low | Use `HapticFeedbackConstants` in Compose. Must be toggleable. |
| Sound feedback on key press | Optional but expected. Gboard, SwiftKey all offer it. | Low | Key click sounds, toggleable. Separate from dictation start/stop sounds. |
| Dark theme | Modern keyboards default to dark. Users expect it. | Low | Material You dark theme. Dictus brand colors as default. |
| Permission handling | RECORD_AUDIO is a runtime permission. Must handle denial gracefully. | Low | Explain why mic is needed. Provide path to settings if denied. |

---

## Differentiators

Features that set Dictus apart from Gboard and other keyboards. Not expected, but create competitive advantage.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| 100% on-device, zero cloud | Strongest privacy guarantee. Gboard's on-device mode is Pixel 6+ only. Wispr Flow requires internet. FUTO is the only real competitor here. | Already architected | This is the core brand promise. No network requests except model downloads. |
| AZERTY keyboard (French-first) | No Whisper keyboard on Android offers proper AZERTY with accented characters. FUTO has French autocorrect but standard QWERTY only. Gboard has AZERTY but its offline voice is Pixel 6+ only. | Medium | Long-press accented characters (e, a, u, o, i, c). French is a first-class citizen, not an afterthought. |
| Bilingual FR/EN interface | App and keyboard UI in both French and English. Most Whisper keyboards are English-only (Transcribro explicitly only supports English). | Low | Android string resources with `values-fr/`. French as default. |
| Open source (MIT) | Full transparency. Users can verify the privacy claims. FUTO is also open source, but Kaiboard and Wispr Flow are not. | N/A | Community contributions, trust, auditability. |
| Free with no ads or tracking | No subscription, no freemium, no data collection. Wispr Flow has word limits on free tier. | N/A | Sustainable via open source community model. |
| Waveform animation during recording | Visual feedback that the mic is actively hearing you. More engaging than Gboard's static pulsing icon. Creates a premium feel. | Medium | Real-time audio energy visualization in Compose Canvas. Differentiates from bare-bones Whisper keyboards. |
| Dictation start/stop audio cues | Audible confirmation that recording started and stopped. Reduces uncertainty. | Low | Configurable. Short, distinctive sounds. |
| Multiple Whisper model sizes | User chooses accuracy vs speed tradeoff. Most Whisper keyboards ship with one model (usually tiny). FUTO offers model selection too. | Low (UI) | tiny (~1-2s), base (~2-4s), small quantized (~4-8s). Let users decide based on their device and patience. |
| Suggestion bar with transcription preview | After dictation, show the transcribed text in the suggestion bar for quick review before committing. | Medium | Allows user to see and potentially correct before text is inserted. |
| Emoji picker with categories | Full emoji input within the keyboard. Most Whisper-only keyboards lack this entirely. | Medium | Lazy grid with categories, recents, search. Table stakes for a full keyboard, but a differentiator vs Whisper-only apps. |

---

## Anti-Features

Features to explicitly NOT build. Each has a reason that prevents scope creep.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Swipe/glide typing | Massive implementation effort (requires ML model for gesture recognition). FUTO's swipe is still in alpha after years. Dictus's value is voice, not gesture typing. | Focus engineering time on STT quality. Users who want swipe typing already use Gboard. |
| AI text reformatting / LLM integration | Requires cloud API or large on-device LLM. Breaks the "no cloud" promise if using API. Not yet on iOS either. Wispr Flow does this but it's cloud-dependent. | Defer to post-MVP. If implemented later, use user-provided API key (as planned on iOS). |
| Autocorrect with ML model | Requires training/shipping language models for text prediction. FUTO and Gboard have years of investment here. Dictus cannot compete on typing intelligence. | Provide basic suggestion bar. Users who need strong autocorrect keep Gboard as secondary keyboard. |
| Clipboard manager | Not related to voice dictation. Feature bloat. SwiftKey has it, but it's a separate product concern. | Out of scope. Android has system clipboard. |
| Inline translation | Wispr Flow offers this, but it requires cloud. Whisper's translate mode exists but quality is inconsistent for non-English targets. | Defer. Could be a v2 feature using Whisper's translate task, but only EN target is reliable. |
| GIF/sticker search | Requires network. Not related to voice dictation. Feature bloat typical of mainstream keyboards. | Out of scope. |
| Multi-device sync | No cloud = no sync. Settings are device-local. | Out of scope. Consistent with privacy-first positioning. |
| Streaming/real-time transcription | whisper.cpp does batch inference (record then transcribe). Streaming requires different architecture (Sherpa-ONNX supports it). Significantly more complex. | Use batch mode: record -> stop -> transcribe -> insert. Adequate for dictation use case. Consider for v2 with Sherpa-ONNX. |
| Voice Activity Detection (auto-stop) | Silero VAD or WebRTC VAD could auto-stop recording on silence. Adds dependency and complexity. Can cause premature stops during natural pauses. | Manual stop (tap mic to stop). Simpler, more predictable. Consider VAD as v1.1 feature. |
| GPU/NNAPI acceleration | whisper.cpp NNAPI backend is experimental. Adds complexity for marginal gains on Pixel 4. | CPU multi-thread inference (4 threads on big cores). Defer GPU to v1.1. |
| One-handed mode | Keyboard layout resize for single-hand use. Nice to have but not related to core value prop. | Defer to v1.1+. |
| Custom themes / theme editor | Users love customizing keyboard appearance, but it's engineering time away from STT quality. | Ship Dictus Dark + Follow System. Two options, done. |
| Floating/split keyboard | Tablet optimization. Pixel 4 is the target device. | Out of scope for MVP. |

---

## Feature Dependencies

```
Permission (RECORD_AUDIO) --> Audio Recording --> Transcription --> Text Insertion
                                    |
                                    v
                            Waveform Animation
                            Recording Feedback (sound/haptic)

Model Download --> Model Loading --> Transcription
                       |
                       v
               Model Manager UI

IME Activation (onboarding) --> Keyboard Layout --> All keyboard features
                                     |
                                     v
                              Number/Symbol layers
                              Accented characters (long-press)
                              Emoji picker
                              Suggestion bar

Settings Screen --> Layout selection (AZERTY/QWERTY)
                --> Model selection
                --> Language selection
                --> Haptic/Sound toggles
                --> Theme toggle

Onboarding Flow --> Enable IME --> Set Default --> Mic Permission --> Model Download --> Test Dictation
(each step depends on the previous)
```

**Critical path:** IME setup + whisper.cpp JNI integration + audio recording pipeline. Everything else layers on top.

---

## MVP Recommendation

### Must ship (P0 -- without these, product is non-functional):

1. **Custom keyboard IME** (AZERTY + QWERTY, shift, caps, numbers, symbols, accented chars)
2. **On-device STT** via whisper.cpp (JNI bridge, GGML model loading, batch transcription)
3. **Audio recording** via AudioRecord in foreground Service
4. **Text insertion** via InputConnection
5. **Onboarding flow** (IME activation is confusing on Android -- this is critical for retention)
6. **Model download** from HuggingFace (at least small quantized)
7. **Basic settings** (model, language, layout)

### Should ship (P1 -- significantly improves experience):

8. **Waveform animation** during recording
9. **Haptic feedback** on key press and dictation start/stop
10. **Sound feedback** for dictation start/stop
11. **Debug logging** (Timber + file export -- essential for beta testing)
12. **Dark theme** (Dictus brand colors)

### Can defer (P2 -- nice to have, not launch-blocking):

13. **Emoji picker** with categories
14. **Suggestion bar** with transcription preview
15. **Follow System theme** option
16. **Bilingual UI** (FR/EN string resources)

### Defer to v1.1+:

- Voice Activity Detection (auto-stop on silence)
- Streaming transcription (Sherpa-ONNX)
- Multiple STT engines (Parakeet)
- GPU/NNAPI acceleration
- One-handed mode
- Smart modes / LLM integration

---

## Sources

- [FUTO Keyboard](https://keyboard.futo.org/) -- closest open-source competitor with offline Whisper
- [Transcribro (GitHub)](https://github.com/soupslurpr/Transcribro) -- on-device Whisper keyboard, English only
- [Kaiboard (GitHub)](https://github.com/kaisoapbox/kaiboard) -- open-source Whisper keyboard
- [WhisperInput (GitHub)](https://github.com/alex-vt/WhisperInput) -- offline Whisper voice input for Android
- [Wispr Flow Android](https://wisprflow.ai/android) -- cloud-based overlay dictation, launched Feb 2026
- [Gboard advanced voice typing](https://support.google.com/gboard/answer/11197787) -- on-device voice, Pixel 6+ only
- [Android VAD library](https://github.com/gkonovalov/android-vad) -- WebRTC/Silero/Yamnet VAD for Android
- [Best keyboard apps 2026](https://www.clevertype.co/post/best-keyboard-apps-for-android-2025-ai-powered-typing-showdown)
- [Wispr Flow TechCrunch launch](https://techcrunch.com/2026/02/23/wispr-flow-launches-an-android-app-for-ai-powered-dictation/)
- [FUTO Keyboard review](https://www.androidpolice.com/tried-futo-keyboard-gboard-swiftkey-for-a-month/)
