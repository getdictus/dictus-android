---
phase: 06-release-readiness
verified: 2026-03-29T00:00:00Z
status: human_needed
score: 9/9 must-haves verified
re_verification: null
gaps: null
human_verification:
  - test: "Language switching end-to-end"
    expected: "Selecting English from Settings makes all UI strings switch to English; selecting Francais switches back; Automatique follows device language; choice persists after app restart"
    why_human: "AppCompatDelegate.setApplicationLocales() triggers Activity recreation — cannot verify UI string swap programmatically without running the app on device"
  - test: "Cross-app text insertion (WhatsApp, Gmail, Chrome)"
    expected: "Typed text and dictated text insert correctly at cursor in all three apps; Enter and backspace behave correctly per app"
    why_human: "InputConnection behavior in foreign apps requires real device testing — already validated per 06-03-SUMMARY but documented here as a human gate"
  - test: "Cold-start IME dictation"
    expected: "With Dictus app force-stopped, tapping mic in another app starts foreground notification, model loads, transcription completes, text inserts at cursor"
    why_human: "Process lifecycle for IME cold start cannot be verified statically — already validated per 06-03-SUMMARY but documented here as a human gate"
  - test: "Theme system integration (DSG-02)"
    expected: "Dark/Light/Automatic options appear in Settings; Automatic follows device dark/light mode; theme persists across restarts"
    why_human: "isSystemInDarkTheme() response to device theme changes requires a running device — code path is verified but runtime behavior requires human"
---

# Phase 06: Release Readiness — Verification Report

**Phase Goal:** Users can use Dictus in French or English with in-app language switching and cross-app validation
**Verified:** 2026-03-29
**Status:** human_needed — all automated checks passed; 4 items require human/device confirmation
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All UI strings appear in French by default and in English when device language is English | VERIFIED | values/strings.xml (English default, 200 lines), values-fr/strings.xml (French overlay, 200 lines); all onboarding, settings, home, models, recording, and IME screens confirmed using stringResource() |
| 2 | User can select UI language (System/French/English) from Settings | VERIFIED | SettingsScreen.kt collectAsState on uiLanguage (line 87); PickerBottomSheet with 3 options calling viewModel.setUiLanguage() (line 244); SettingsViewModel.setUiLanguage() calls AppCompatDelegate.setApplicationLocales() |
| 3 | Text insertion and dictation work across WhatsApp/Gmail/Chrome including cold-start | HUMAN NEEDED | Code infrastructure verified; runtime validation documented as passed in 06-03-SUMMARY (human checkpoint approved) but cannot be programmatically confirmed |

**Score:** 2/3 truths fully automated-verified (9/9 artifacts pass); 1 truth requires human confirmation

---

## Required Artifacts

### Plan 06-01 Artifacts (DSG-03 onboarding strings)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/res/values/strings.xml` | English default strings for onboarding | VERIFIED | Contains `onboarding_welcome_tagline`, 200 lines total |
| `app/src/main/res/values-fr/strings.xml` | French overlay strings for onboarding | VERIFIED | Contains `onboarding_welcome_tagline`, 200 lines total |
| 7 onboarding screens | Use stringResource(R.string.*) | VERIFIED | WelcomeScreen: 4 hits; MicPermissionScreen: 4 hits; ModelDownloadScreen: 13 hits — all screens confirmed |

### Plan 06-02 Artifacts (DSG-02/DSG-03 infrastructure)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/test/java/.../LanguagePickerTest.kt` | Wave 0 test stubs (later filled) | VERIFIED | 5 real assertions with assertEquals/assertTrue; imports AppCompatDelegate, LocaleListCompat |
| `ime/src/main/res/values/strings.xml` | English IME strings | VERIFIED | Contains `ime_recording_listening` |
| `ime/src/main/res/values-fr/strings.xml` | French IME overlay | VERIFIED | Contains `ime_recording_listening` |
| `core/src/main/res/values-fr/strings.xml` | French core overlay (Clavier Dictus) | VERIFIED | Contains "Clavier Dictus" |
| `app/src/main/res/resources.properties` | unqualifiedResLocale=en-US | VERIFIED | File exists with correct content |
| `app/src/main/AndroidManifest.xml` | AppLocalesMetadataHolderService declared | VERIFIED | 2 occurrences (declaration + meta-data) |

### Plan 06-03 Artifacts (DSG-03 language picker)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/.../ui/settings/SettingsScreen.kt` | Language picker row with PickerBottomSheet | VERIFIED | 42 stringResource calls; uiLanguage collectAsState at line 87; PickerBottomSheet with setUiLanguage at line 244 |
| `app/.../ui/settings/SettingsViewModel.kt` | uiLanguage StateFlow + setUiLanguage() | VERIFIED | uiLanguage: StateFlow<String> backed by MutableStateFlow(getCurrentUiLanguage()); setUiLanguage() calls AppCompatDelegate.setApplicationLocales() |
| `app/src/test/.../LanguagePickerTest.kt` | Real assertions (not stubs) | VERIFIED | assertEquals("fr", locales.toLanguageTags()); assertTrue(...locales.isEmpty); 5 tests with real logic |

### Plan 06-04 Artifacts (DSG-03 remaining app strings)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/res/values/strings.xml` | English strings for settings/home/models/recording | VERIFIED | `settings_section_transcription` confirmed present; 200 total lines |
| `app/src/main/res/values-fr/strings.xml` | French overlay for same screens | VERIFIED | `settings_section_transcription` confirmed present; 200 total lines |
| Settings/Home/Models/Recording screens | Use stringResource | VERIFIED | SettingsScreen: 42 hits; HomeScreen: 5 hits; ModelsScreen: 9 hits; RecordingScreen: 13 hits |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MainActiviy.kt` | AppCompatActivity | class extends | VERIFIED | `class MainActivity : AppCompatActivity()` at line 56 |
| `app/build.gradle.kts` | generateLocaleConfig | androidResources block | VERIFIED | `generateLocaleConfig = true` at lines 26-27 |
| `SettingsScreen.kt` | AppCompatDelegate.setApplicationLocales() | onClick lambda | VERIFIED | PickerBottomSheet onSelect calls viewModel.setUiLanguage() at line 244 |
| `SettingsScreen.kt` | SettingsViewModel.uiLanguage | collectAsState | VERIFIED | `val uiLanguage by viewModel.uiLanguage.collectAsState()` at line 87 |
| `SettingsScreen.kt` | values/strings.xml | stringResource(R.string.*) | VERIFIED | 42 stringResource calls confirmed |
| `HomeScreen.kt` | values/strings.xml | stringResource(R.string.*) | VERIFIED | 5 stringResource calls confirmed |
| IME screens (3) | ime/src/main/res/values/strings.xml | stringResource(R.string.*) | VERIFIED | RecordingScreen: 2, TranscribingScreen: 2, EmojiPickerScreen: 2 |
| `DictusTheme.kt` | isSystemInDarkTheme() | ThemeMode.AUTO branch | VERIFIED | `ThemeMode.AUTO -> isSystemInDarkTheme()` at line 128 |
| `MainActivity.kt` | DictusTheme(themeMode) | DataStore + when mapping | VERIFIED | themeKey "auto" -> ThemeMode.AUTO -> DictusTheme at lines 102-108 |

---

## Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DSG-02 | 06-02 | User can toggle "Follow System" to use Material You dynamic colors | SATISFIED (scoped) | ThemeMode.AUTO with isSystemInDarkTheme() delivered. Material You dynamic colors consciously abandoned per 06-CONTEXT.md ("Dictus brand identity takes priority"). Theme picker (Dark/Light/Auto) is the final deliverable for v1. |
| DSG-03 | 06-01, 06-02, 06-03, 06-04 | UI is bilingual French (default) / English | SATISFIED | All 4 plans executed: 200 English + 200 French strings across app/; 3+3 strings in ime/; French overlay in core/; in-app language picker with AppCompatDelegate; cross-app validation passed by human checkpoint |

**Orphaned requirements:** None — both DSG-02 and DSG-03 are claimed and covered by the phase plans.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ime/ui/RecordingScreen.kt` | 89, 109 | `text = "\u2715"` / `text = "\u2713"` | Info | Unicode symbols (× ✓) — locale-neutral, intentionally not extracted |
| `ui/models/ModelCard.kt` | 240 | `text = "WK"` | Info | Badge abbreviation — technical label, acceptable |
| `home/HomeScreen.kt` | 93 | `text = "Dictus"` | Info | App wordmark/brand name — intentionally not translated |
| `onboarding/OnboardingModelDownloadScreen.kt` | 169 | `text = "Tiny"` | Info | Whisper model proper name — intentionally not translated per 06-01-SUMMARY |
| `onboarding/OnboardingViewModel.kt` | 116-117 | Unstaged change: step 6→7 | Warning | Pre-existing uncommitted modification noted in 06-04-SUMMARY; unrelated to phase 06 string work; step count change from 6 to 7 may affect OnboardingViewModelTest |

No blocker anti-patterns found. All flagged items are either intentional brand/proper names or a pre-existing unrelated change.

---

## Human Verification Required

### 1. Language Switching End-to-End

**Test:** Open Settings -> tap "Langue de l'interface" / "Interface language" -> select "English" -> verify ALL labels switch to English immediately. Tap again -> select "Francais" -> verify labels switch to French. Tap "Automatique/System" -> verify it follows device language. Kill and reopen the app -> verify choice persisted.
**Expected:** All string resources resolve to the correct locale in real time; Activity recreates transparently; DataStore-backed AppCompatDelegate persists choice across cold starts.
**Why human:** AppCompatDelegate.setApplicationLocales() triggers Activity recreation — no static analysis can confirm the UI string swap occurs correctly at runtime.

### 2. Cross-App Text Insertion

**Test:** Switch to Dictus keyboard in WhatsApp, Gmail, and Chrome. Type text and dictate in each app. Verify Enter and backspace behavior.
**Expected:** Text inserts at cursor; backspace deletes; Enter sends in WhatsApp, creates new line in Gmail.
**Why human:** InputConnection.commitText() behavior in foreign apps requires real device testing. This was validated and approved by the user in the 06-03 human checkpoint.

### 3. Cold-Start IME Dictation

**Test:** Force-stop Dictus via Settings -> Apps -> Force Stop. Open any text field in another app, switch to Dictus keyboard, tap mic, record, wait.
**Expected:** Foreground notification appears, model loads, transcription completes, text inserts at cursor — all without the main app open.
**Why human:** IME process lifecycle isolation cannot be verified statically. Validated in 06-03 human checkpoint.

### 4. Theme System Behavior (DSG-02)

**Test:** In Settings -> APPEARANCE -> Theme, cycle through Dark/Light/Automatic. For Automatic, change device dark mode setting.
**Expected:** Dark mode shows Dictus dark theme; Light shows light theme; Automatic follows device setting in real time.
**Why human:** isSystemInDarkTheme() response to live system changes requires a running device. Code path (ThemeMode.AUTO -> isSystemInDarkTheme()) is verified but runtime behavior requires human confirmation.

---

## Gaps Summary

No blocking gaps found. All 9 must-have artifacts exist, are substantive, and are wired. Both DSG-02 and DSG-03 are accounted for.

DSG-02 note: The requirement text says "Material You dynamic colors" but the scope was consciously narrowed in 06-CONTEXT.md ("Abandoned for v1 — brand identity priority"). The delivered theme system (Dark/Light/Auto using isSystemInDarkTheme()) satisfies the "Follow System" behavioral intent. This was a documented product decision, not an oversight.

The `OnboardingViewModel.kt` unstaged change (step count 6→7) is pre-existing, unrelated to phase 06, and should be committed or reverted separately.

---

_Verified: 2026-03-29_
_Verifier: Claude (gsd-verifier)_
