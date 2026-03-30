---
status: complete
phase: 03-whisper-integration
source: [03-01-SUMMARY.md, 03-02-SUMMARY.md, 03-03-SUMMARY.md]
started: 2026-03-26T10:00:00Z
updated: 2026-03-26T15:50:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Transcribing UI feedback
expected: After tapping the confirm/check button while recording, the keyboard UI transitions from the recording waveform to a "Transcribing" screen showing animated 30-bar waveform and a "Transcription..." label.
result: issue
reported: "sinusoïde ligne pas du tout cohérente avec le mockup, pas les barres du design system"
severity: major

### 2. Model auto-download on first use
expected: On first dictation attempt (no model file on device yet), the app automatically downloads the whisper model from HuggingFace without user intervention. The download requires INTERNET permission and completes before transcription begins. Subsequent uses skip the download.
result: issue
reported: "UnknownHostException - download fails, no transcription possible. Workaround: pushed model via adb"
severity: blocker

### 3. End-to-end dictation pipeline
expected: Record audio via the keyboard mic button, then tap confirm. The app stops recording, transcribes the audio on-device using whisper.cpp, and inserts the resulting French text at the cursor position in the active text field. The full flow is: Recording → Transcribing → text appears → keyboard returns to idle.
result: issue
reported: "transcription timed out at 30s, result was ready at 31s. After timeout fix to 120s, text inserted correctly"
severity: blocker

### 4. Text post-processing quality
expected: Transcribed text has proper French capitalization (first letter capitalized) and punctuation applied. Whitespace is trimmed. Output matches iOS Dictus behavior for basic sentences.
result: pass

## Summary

total: 4
passed: 1
issues: 3
pending: 0
skipped: 0

## Gaps

- truth: "Transcribing UI uses same 30-bar waveform as recording screen"
  status: fixed
  reason: "User reported: sinusoïde ligne, not matching mockup design system bars"
  severity: major
  test: 1
  fix: "Replaced Canvas sine Path with WaveformBars fed by sine-generated energy values"
  artifacts:
    - path: "ime/src/main/java/dev/pivisolutions/dictus/ime/ui/TranscribingScreen.kt"
      issue: "Was drawing Path line instead of reusing WaveformBars"

- truth: "Model auto-downloads from HuggingFace on first use"
  status: failed
  reason: "User reported: UnknownHostException - Unable to resolve host huggingface.co from app process"
  severity: blocker
  test: 2
  root_cause: ""
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/service/ModelDownloader.kt"
      issue: "DNS resolution fails from app process despite device having internet"
  missing:
    - "Investigate Android network security config or app-level DNS restrictions"

- truth: "Transcription completes within timeout and text is inserted"
  status: fixed
  reason: "User reported: timeout at 30s, whisper.cpp took 31s on Pixel 4"
  severity: blocker
  test: 3
  fix: "Increased TRANSCRIPTION_TIMEOUT_MS from 30_000 to 120_000"
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt"
      issue: "Timeout too short for older devices"
