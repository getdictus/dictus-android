# Contributing to Dictus

Thanks for your interest in contributing! This guide covers everything you need to build and contribute to the project.

## Prerequisites

- JDK 17
- Android SDK (API 34 target, API 29 minimum)
- Android NDK 27.2 (required for whisper.cpp and sherpa-onnx native builds)

## Build

```bash
git clone https://github.com/getdictus/dictus-android.git
cd dictus-android
./gradlew assembleDebug
```

## Module Map

| Module | Purpose | Key entry point |
|--------|---------|-----------------|
| `app` | Main app UI, onboarding, settings, model manager | `MainActivity.kt` |
| `ime` | System keyboard, suggestion bar, recording UI | `DictusImeService.kt` |
| `core` | Shared models, design system, STT interface | `SttProvider.kt` |
| `whisper` | Whisper.cpp JNI bridge and native libs | `WhisperProvider.kt` |
| `asr` | Sherpa-onnx Kotlin API and Parakeet provider | `ParakeetProvider.kt` |

## Testing

- Run unit tests: `./gradlew testDebugUnitTest`
- To test the keyboard on a device: enable Dictus in **Settings > System > Languages & input > On-screen keyboard > Manage on-screen keyboards**, then switch to it in any text field

## Contributing Guidelines

- Branch naming: `type/description` (e.g., `fix/keyboard-crash`, `feat/emoji-search`)
- Commit messages: Conventional Commits suggested (`feat:`, `fix:`, `docs:`) but not enforced
- Code language: English for comments and variable names; UI strings bilingual FR+EN via `strings.xml`
- All PRs require maintainer review before merge
