---
phase: 07-foundation
verified: 2026-03-30T13:30:00Z
status: passed
score: 9/9 must-haves verified
re_verification: false
human_verification:
  - test: "Toggle AZERTY/QWERTY in Settings on a real device"
    expected: "The IME keyboard layout changes immediately without restarting the keyboard app"
    why_human: "DataStore collectAsState wiring verified in code but reactive recomposition behavior in a running IME process cannot be confirmed programmatically"
---

# Phase 7: Foundation Verification Report

**Phase Goal:** The codebase is ready for multi-provider STT and a public beta — v1.0 bugs are fixed, and the SttProvider abstraction exists before any new engine code is written.
**Verified:** 2026-03-30T13:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                     | Status     | Evidence                                                                      |
|----|-------------------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------|
| 1  | POST_NOTIFICATIONS permission is declared in AndroidManifest.xml                         | VERIFIED   | Line 7: `android.permission.POST_NOTIFICATIONS` present                       |
| 2  | ModelCatalogTest uses corrected byte sizes matching ModelManager.kt production values      | VERIFIED   | Lines 52/59/66: 147_951_465L, 487_601_967L, 190_085_487L all present          |
| 3  | OnboardingViewModelTest calls advanceStep() twice after step 6 to trigger completion      | VERIFIED   | Lines 190-192: two sequential advanceStep() calls before assertion            |
| 4  | SttProvider interface exists in core/stt/ with providerId, displayName, supportedLanguages | VERIFIED   | `core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt` exists with all three fields |
| 5  | WhisperProvider implements SttProvider and wraps whisper.cpp engine                       | VERIFIED   | `class WhisperProvider : SttProvider` with providerId="whisper", real impl    |
| 6  | DictationService uses SttProvider type (not TranscriptionEngine) for its engine field     | VERIFIED   | Line 97: `private val sttProvider: SttProvider = WhisperProvider()`           |
| 7  | Old TranscriptionEngine.kt and WhisperTranscriptionEngine.kt are deleted                  | VERIFIED   | Both files absent from disk; no stale references anywhere in codebase         |
| 8  | KEYBOARD_LAYOUT preference flows from DataStore through DictusImeService to KeyboardScreen | VERIFIED   | DictusImeService lines 274-276 observe; line 321 passes to KeyboardScreen     |
| 9  | KeyboardScreen accepts keyboardLayout parameter, no more hardcoded "azerty" initial state | VERIFIED   | Line 52: parameter; line 62: `remember(keyboardLayout) { mutableStateOf(keyboardLayout) }` |

**Score:** 9/9 truths verified

### Required Artifacts

| Artifact                                                                             | Expected                                         | Status     | Details                                                   |
|--------------------------------------------------------------------------------------|--------------------------------------------------|------------|-----------------------------------------------------------|
| `app/src/main/AndroidManifest.xml`                                                   | POST_NOTIFICATIONS permission                    | VERIFIED   | Contains `android.permission.POST_NOTIFICATIONS` at line 7 |
| `app/src/test/.../model/ModelCatalogTest.kt`                                         | Corrected byte size assertions                   | VERIFIED   | 147_951_465L, 487_601_967L, 190_085_487L all present       |
| `app/src/test/.../onboarding/OnboardingViewModelTest.kt`                             | Two-step advanceStep pattern                     | VERIFIED   | Second advanceStep() call added at line 192               |
| `core/src/main/java/dev/pivisolutions/dictus/core/stt/SttProvider.kt`               | Engine-neutral STT contract                      | VERIFIED   | Full interface: 7 members incl. providerId/displayName/supportedLanguages |
| `app/src/main/java/dev/pivisolutions/dictus/service/WhisperProvider.kt`             | Whisper implementation of SttProvider            | VERIFIED   | Substantive impl: WhisperContext lifecycle, real transcription |
| `app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt`            | Uses SttProvider type                            | VERIFIED   | sttProvider field, SttProvider import, WhisperProvider() instantiation |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/DictusImeService.kt`                | DataStore observation of KEYBOARD_LAYOUT         | VERIFIED   | collectAsState for KEYBOARD_LAYOUT at lines 274-276       |
| `ime/src/main/java/dev/pivisolutions/dictus/ime/ui/KeyboardScreen.kt`               | Accepts keyboardLayout from caller               | VERIFIED   | Parameter at line 52; remember(keyboardLayout) at line 62 |
| `core/src/main/java/dev/pivisolutions/dictus/core/whisper/TranscriptionEngine.kt`   | Should NOT exist                                 | VERIFIED   | File deleted from disk                                    |
| `app/src/main/java/dev/pivisolutions/dictus/service/WhisperTranscriptionEngine.kt`  | Should NOT exist                                 | VERIFIED   | File deleted from disk                                    |

### Key Link Verification

| From                         | To                          | Via                                  | Status    | Details                                                          |
|------------------------------|-----------------------------|--------------------------------------|-----------|------------------------------------------------------------------|
| `SttProvider.kt`             | `WhisperProvider.kt`        | interface implementation             | WIRED     | `class WhisperProvider : SttProvider` at line 14                 |
| `WhisperProvider.kt`         | `DictationService.kt`       | instantiation                        | WIRED     | `WhisperProvider()` at line 97 of DictationService               |
| `DictationService.kt`        | `core/stt/SttProvider.kt`   | import and field type                | WIRED     | `import dev.pivisolutions.dictus.core.stt.SttProvider` at line 40 |
| `DictusImeService.kt`        | `PreferenceKeys.KEYBOARD_LAYOUT` | DataStore.data.map.collectAsState | WIRED     | Lines 274-276 observe KEYBOARD_LAYOUT with collectAsState        |
| `DictusImeService.kt`        | `KeyboardScreen`            | keyboardLayout parameter             | WIRED     | Line 321: `keyboardLayout = keyboardLayout` passed to KeyboardScreen call |
| `SettingsViewModel.kt`       | `DictusImeService.kt`       | DataStore write/read of KEYBOARD_LAYOUT | WIRED  | SettingsViewModel line 161 writes; DictusImeService line 275 reads same key |
| `ModelCatalogTest.kt`        | `ModelManager.kt`           | expectedSizeBytes assertions         | WIRED     | assertEquals calls at lines 52, 59, 66 reference production values |
| `OnboardingViewModelTest.kt` | `OnboardingViewModel.kt`    | step state machine assertions        | WIRED     | Two advanceStep() calls aligned with step==7 trigger in ViewModel |

### Requirements Coverage

| Requirement | Source Plan | Description                                                            | Status    | Evidence                                                 |
|-------------|-------------|------------------------------------------------------------------------|-----------|----------------------------------------------------------|
| DEBT-01     | 07-03       | AZERTY/QWERTY toggle propagates to IME                                 | SATISFIED | DictusImeService observes KEYBOARD_LAYOUT; KeyboardScreen uses it |
| DEBT-02     | 07-01       | POST_NOTIFICATIONS declared in AndroidManifest for API 33+             | SATISFIED | `android.permission.POST_NOTIFICATIONS` at manifest line 7 |
| DEBT-03     | 07-01       | Stale tests corrected (ModelCatalogTest, OnboardingViewModelTest, AudioCaptureManagerTest) | SATISFIED | All three test files contain corrected assertions |
| STT-01      | 07-02       | Architecture supports N STT providers via SttProvider extensible interface | SATISFIED | SttProvider in core/stt/ with full metadata contract; WhisperProvider implements it |

No orphaned requirements. All four Phase 7 requirements are covered by a plan and verified in the codebase. REQUIREMENTS.md traceability table marks all four as Complete.

### Anti-Patterns Found

| File                                      | Line | Pattern                                         | Severity | Impact                                                |
|-------------------------------------------|------|-------------------------------------------------|----------|-------------------------------------------------------|
| `OnboardingViewModel.kt`                  | 113  | Comment says "Step 6: triggers DataStore persistence" but code triggers at step==7 | Info | Misleading doc comment; code behavior is correct — stale comment was not updated as specified in 07-01 plan |

No blocker or warning anti-patterns found. One informational stale comment remains.

### Human Verification Required

#### 1. AZERTY/QWERTY live toggle

**Test:** Install the debug APK on a device running Dictus. Open a text field to activate the Dictus keyboard. Navigate to Settings > Clavier and toggle the keyboard layout between AZERTY and QWERTY.
**Expected:** The keyboard layout changes immediately in the already-open keyboard without requiring a restart of the IME process or device.
**Why human:** The DataStore collectAsState wiring is confirmed in code, but the actual recomposition behavior in a running IME process (a Service, not an Activity) and the cross-process DataStore propagation timing cannot be verified through static analysis alone.

### Gaps Summary

No gaps. All nine observable truths are verified. All required artifacts exist, are substantive, and are correctly wired. All four requirement IDs (DEBT-01, DEBT-02, DEBT-03, STT-01) are satisfied with implementation evidence.

The only finding is an informational stale comment in `OnboardingViewModel.kt` line 113 (says "Step 6" but trigger is at step==7). This does not affect runtime behavior.

One human verification item remains: confirming the live reactive AZERTY/QWERTY toggle on a real device.

---

_Verified: 2026-03-30T13:30:00Z_
_Verifier: Claude (gsd-verifier)_
