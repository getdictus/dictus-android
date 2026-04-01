---
status: complete
phase: 09-parakeet-integration
source: [09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-03-SUMMARY.md]
started: 2026-04-01T11:36:00Z
updated: 2026-04-01T11:42:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Parakeet models visible in catalog with NV badge
expected: Open the Models screen. You should see Parakeet models listed alongside Whisper models. Each Parakeet card shows an amber "NV" badge, while Whisper cards show a blue "WK" badge. There should be 2 Parakeet entries (110M INT8 and 0.6B TDT v3) and no FP16 Parakeet model.
result: pass

### 2. Amber language warning on Parakeet cards
expected: On the Models screen, each Parakeet model card displays an amber warning banner indicating limited language support (English-only for 110M, or multilingual info for v3).
result: pass

### 3. Download a Parakeet model with extraction UX
expected: Tap download on Parakeet 110M INT8. A progress bar appears during download. Once download reaches 100%, the UI transitions to an "Extracting" state (not stuck at 100%) while the tar.bz2 archive is decompressed. After extraction completes, the model shows as available. No ANR or freeze during the process.
result: pass

### 4. Activate Parakeet with non-English language
expected: Set transcription language to French (or any non-English language). Then tap to activate a downloaded Parakeet model. A confirmation dialog appears warning that Parakeet may not support this language well. You can confirm or dismiss. If dismissed, the active model does not change.
result: pass

### 5. RAM guard on 0.6B TDT model
expected: On your Pixel 4 (6 GB RAM, < 8 GB threshold), tapping the 0.6B TDT model shows a blocking dialog explaining the device doesn't have enough RAM. The model cannot be activated.
result: pass

### 6. Engine swap snackbar
expected: With a Whisper model active, activate a downloaded Parakeet model (or vice versa). A snackbar notification appears briefly indicating the engine switch (e.g., "Switched to Parakeet" or similar).
result: pass

### 7. Transcribe with Parakeet engine
expected: With a Parakeet model active, start a new dictation. Speak a few words in English. The transcription completes and shows recognized text. No crash or OOM.
result: pass

### 8. Switch back to Whisper and transcribe
expected: After using Parakeet, switch active model back to a Whisper model. Start a new dictation. Transcription works correctly with Whisper. The previous Parakeet engine is released (no OOM from both engines loaded simultaneously).
result: pass

### 9. Provider footer in Models screen
expected: Scroll to the bottom of the Models screen. A footer section shows provider information (Whisper/Parakeet attribution or licence info).
result: pass

### 10. Medium Whisper model in catalog
expected: The Models screen includes a "Medium" Whisper model (ggml-medium-q5_0, ~539 MB) as a mid-tier option between Small and Large.
result: pass

## Summary

total: 10
passed: 10
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
