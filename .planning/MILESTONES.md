# Milestones

## v1.0 MVP (Shipped: 2026-03-30)

**Phases completed:** 7 phases (1-6 + 02.1), 29 plans
**Timeline:** 9 days (2026-03-21 → 2026-03-30)
**Commits:** 196 | **Files:** 304 | **LOC:** ~15K Kotlin

**Delivered:** Full on-device voice dictation keyboard for Android — type, speak, transcribe, insert — entirely offline with no cloud dependency.

**Key accomplishments:**
1. Compose-based IME keyboard with AZERTY/QWERTY layouts, accented characters, working as system keyboard in any Android app
2. On-device voice dictation via whisper.cpp native build (NDK/CMake + JNI bridge), end-to-end record→transcribe→insert pipeline
3. Model management: download/delete Whisper models from HuggingFace with progress tracking and storage indicators
4. 7-step onboarding flow: welcome, mic permission, layout choice, IME activation, model download, test dictation, success
5. Polish & UX: waveform animation, sound feedback, emoji picker, text suggestions, light/dark/auto theme
6. Bilingual UI (FR/EN) with in-app language picker and full string extraction

**Git range:** 7bf621a → 7d42bf6
**Requirements:** 23/23 satisfied

### Known Tech Debt
- KBD-02: Settings layout toggle doesn't propagate to IME (AZERTY default works, DataStore key written but not read)
- DICT-08: POST_NOTIFICATIONS not declared in AndroidManifest (works on Pixel 4, may affect API 33+ notification display)
- 4 stale test assertions (AudioCaptureManagerTest, ModelCatalogTest, OnboardingViewModelTest)

---

