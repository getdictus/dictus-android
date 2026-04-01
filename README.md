# Dictus

Dictus is a free, privacy-first Android keyboard for offline voice dictation. All speech recognition runs on-device — no cloud, no data collection.

## Features

- **Offline voice dictation** — Powered by Whisper and NVIDIA Parakeet, running entirely on-device
- **Multi-engine STT** — Choose between Whisper (multilingual) and Parakeet (English, fast)
- **Smart suggestions** — Word predictions from FR+EN dictionaries while typing
- **Personal dictionary** — Learns your frequently typed words
- **System keyboard** — Works in any app as your default keyboard
- **AZERTY & QWERTY** — Switchable keyboard layouts

## Beta Installation

Dictus is currently in public beta. You can install it by sideloading the APK from GitHub Releases.

### Steps

1. On your Android device, go to **Settings > Apps > Special app access > Install unknown apps** and allow your browser
2. Download the latest APK from [GitHub Releases](https://github.com/getdictus/dictus-android/releases/latest)
3. Open the downloaded `.apk` file and tap **Install**
4. Go to **Settings > System > Languages & input > On-screen keyboard > Manage on-screen keyboards**
5. Enable **Dictus**
6. Open any text field, tap the keyboard icon in the navigation bar, and select **Dictus**

### Requirements

- Android 10 (API 29) or higher
- ~150 MB free storage for the smallest Whisper model

## Feedback

Found a bug or have a feature request? [Open an issue](https://github.com/getdictus/dictus-android/issues).

## Tech Stack

- Kotlin + Jetpack Compose
- whisper.cpp (MIT) for Whisper STT
- sherpa-onnx (Apache 2.0) for Parakeet STT
- Material Design 3

## License

MIT License. See [LICENSE](LICENSE) for details.
