# Milestones

## v1.1 Public Beta (Shipped: 2026-04-01)

**Phases completed:** 5 phases (7-11), 15 plans
**Timeline:** 3 days (2026-03-30 → 2026-04-01)
**Commits:** 100 | **Files:** 135 | **LOC:** ~18K Kotlin

**Delivered:** Production text suggestions, multi-provider STT (Whisper + Parakeet), CI/CD pipeline, license audit, and OSS repository scaffolding — transforming the v1.0 MVP into a public beta ready for open contribution.

**Key accomplishments:**
1. Production text prediction engine with AOSP FR+EN dictionaries (50k words) and personal word learning via DataStore
2. Parakeet/Nvidia STT via sherpa-onnx as second engine alongside Whisper — safe engine switching, no OOM
3. GitHub Actions CI (lint + tests + build on PR) + signed APK release workflow on v* tags
4. License audit via cashapp/licensee with auto-generated LicencesScreen from artifacts.json
5. OSS repository: MIT LICENSE, CONTRIBUTING.md, issue/PR templates, enhanced README with badges

**Git range:** 87d8cc1 → 725872c
**Requirements:** 19/20 satisfied (1 known deviation: OSS-03 CODE_OF_CONDUCT.md skipped)

### Known Gaps
- **OSS-03**: CODE_OF_CONDUCT.md — skipped due to content filtering; user-accepted deviation

---

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

