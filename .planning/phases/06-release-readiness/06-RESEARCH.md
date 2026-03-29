# Phase 6: Release Readiness - Research

**Researched:** 2026-03-29
**Domain:** Android string resources / localization, in-app language switching, cross-app InputConnection validation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Bilingual string strategy**
- Default language: English in `values/strings.xml`, French in `values-fr/strings.xml`
- Per-module strings: app/ has onboarding+settings strings, ime/ has keyboard strings, core/ has shared strings
- String IDs: English descriptive convention (e.g. `settings_section_transcription`, `onboarding_welcome_subtitle`, `key_label_return`)
- 50+ hardcoded French strings to extract from Compose files into string resources
- Prep for future languages: add an in-app language override picker in Settings (FR/EN for v1, extensible later)
- Device locale detection drives UI language automatically

**Material You dynamic colors**
- Abandoned for v1. Dictus has a strong brand identity (accent blue #3D7EFF, waveform colors)
- The existing theme system (Sombre/Clair/Automatique with Dictus custom colors) is the final deliverable
- DSG-02 requirement updated: light theme + auto mode already shipped in Phase 5. Material You deferred to v2 only
- No code changes needed for theme system

**Cross-app validation**
- Validate in 2-3 apps (WhatsApp + Gmail + Chrome)
- Cold start transcription: Critical test — keyboard mic tap -> record -> transcribe WITHOUT main app open
- Basic text insertion: commitText works across messaging, email, browser search bar
- Backspace + Enter behavior: deleteSurroundingText and KEYCODE_ENTER work correctly
- Special characters/accents not explicitly tested (already validated in Phase 1)

**Performance profiling**
- Minimal sanity checks only: no memory leak in long keyboard sessions, service cleanup after transcription
- Document Phase 3 benchmarks (<8s for 10s audio) as v1 baseline
- No new profiling effort — Pixel 4 too old for representative benchmarks

### Claude's Discretion
- Exact string extraction order (which files first)
- In-app language picker UI placement and design within Settings
- Memory leak detection approach (Android Studio profiler vs log-based)
- Cross-app test protocol documentation format

### Deferred Ideas (OUT OF SCOPE)
- Material You dynamic colors — abandoned for v1
- Full performance profiling — deferred to v1.1 with a more representative test device
- Multi-device testing (Samsung, Xiaomi) — out of scope per PROJECT.md, planned for v1.1
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DSG-02 | User can toggle "Follow System" to use Material You dynamic colors | **Abandoned for v1.** Existing dark/light/auto theme shipped in Phase 5. Requirement is considered met by the current theme system; no new code needed. |
| DSG-03 | UI is bilingual French (default) / English | String resources in `values/` (EN) + `values-fr/` (FR), per-module pattern, in-app language picker via `AppCompatDelegate.setApplicationLocales()`. ~60 strings to extract across app/ + ime/ modules. |
</phase_requirements>

---

## Summary

Phase 6 has three workstreams: (1) extract ~60 hardcoded French strings into Android string resources with English as default and French as `values-fr/` overlay, (2) add an in-app language override picker in Settings using the `AppCompatDelegate.setApplicationLocales()` API, and (3) manually validate cross-app text insertion focusing on the cold-start IME-only scenario.

The string extraction is the bulk of the work. The codebase has hardcoded French strings scattered across 8 onboarding screens, the settings screen, sound settings, debug logs screen, model cards, and the IME's recording/transcribing screens. All strings live directly in Compose `Text()` calls as Unicode-escaped literals. The per-module resource strategy (core/ for shared strings, app/ for onboarding+settings, ime/ for keyboard screens) is sensible and avoids cross-module resource leakage.

The in-app language picker is a UI addition to SettingsScreen reusing the existing `SettingPickerRow` + `PickerBottomSheet` pattern. The Android-recommended approach is `AppCompatDelegate.setApplicationLocales()` from `androidx.appcompat:appcompat:1.6+`, which handles persistence automatically on API 33+ and via an opt-in service declaration for API 24–32. This is the only approach that does not require Activity restart tricks or manual DataStore persistence.

**Primary recommendation:** Extract strings module by module (core → ime → app), then add the language picker row. Cold-start IME validation is done manually on a physical Pixel 4 — no automation possible here.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.appcompat:appcompat` | 1.6.x (already in project) | `AppCompatDelegate.setApplicationLocales()` for in-app language switching | Official Android API, backward-compatible to API 24, persists locale without DataStore |
| Android `res/values/strings.xml` | N/A | Externalized string resources | Platform standard; `stringResource()` in Compose resolves locale automatically |
| `res/values-fr/strings.xml` | N/A | French locale overlay | Standard Android resource qualifier pattern |
| `res/xml/locales_config.xml` | N/A | Declare supported locales for system language picker (API 33+) | Required for Android 13 system language picker integration |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AGP `generateLocaleConfig = true` | 8.x (already in project) | Auto-generates LocaleConfig from `values-*` dirs | Simplest approach; eliminates manual locales_config.xml maintenance |
| `AppLocalesMetadataHolderService` manifest entry | N/A | Enable AndroidX locale auto-storage on API 24–32 | Required for `setApplicationLocales()` to persist without custom DataStore logic |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AppCompatDelegate.setApplicationLocales()` | Manual DataStore + `attachBaseContext` override | Custom approach requires `ContextWrapper` wrapping, does not integrate with system Settings, Activity restart on every locale change is harder to control |
| `generateLocaleConfig = true` (AGP) | Manual `locales_config.xml` | Manual XML requires maintenance when adding languages; AGP auto-generation is simpler for this project size |

**Installation:** appcompat is already a dependency in the project. No new packages needed.

---

## Architecture Patterns

### Recommended Project Structure
```
core/src/main/res/
├── values/strings.xml          # EN default (shared: app_name, ime_name, subtypes)
└── values-fr/strings.xml       # FR overlay for shared strings

app/src/main/res/
├── values/strings.xml          # EN default (onboarding + settings strings)
└── values-fr/strings.xml       # FR overlay

ime/src/main/res/
├── values/strings.xml          # EN default (keyboard UI strings)
└── values-fr/strings.xml       # FR overlay

app/src/main/res/xml/
└── locales_config.xml          # Declare supported locales (en, fr)
```

### Pattern 1: String Resource in Compose
**What:** Replace hardcoded `text = "Ma Chaine"` with `text = stringResource(R.string.my_string_id)`
**When to use:** Every user-visible string in a Composable

```kotlin
// Source: Android developer docs (official)
import androidx.compose.ui.res.stringResource

Text(
    text = stringResource(R.string.onboarding_welcome_tagline),
    color = LocalDictusColors.current.textSecondary,
    fontSize = 17.sp,
)
```

For strings with formatting (e.g. download percentage), use:
```kotlin
// Source: Android developer docs
text = stringResource(R.string.model_download_progress, downloadProgress)
// strings.xml: <string name="model_download_progress">Téléchargement — %1$d%%</string>
```

### Pattern 2: In-App Language Picker via AppCompatDelegate
**What:** Allow user to override device locale for Dictus only, persisted automatically by AndroidX
**When to use:** In-app language picker row in SettingsScreen

```kotlin
// Source: https://developer.android.com/guide/topics/resources/app-languages
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// Set language (triggers Activity recreate on API 32 and below only if needed)
fun setUiLanguage(languageTag: String) {
    val locales = if (languageTag == "system") {
        LocaleListCompat.getEmptyLocaleList()  // Follow device locale
    } else {
        LocaleListCompat.forLanguageTags(languageTag)  // "fr" or "en"
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

// Read current selection (returns empty list = "system")
fun getCurrentUiLanguage(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return locales.toLanguageTags().ifEmpty { "system" }
}
```

### Pattern 3: Manifest Service for API 24–32 Auto-Storage
**What:** Let AndroidX persist locale preferences on older API levels automatically
**When to use:** Required once in AndroidManifest.xml

```xml
<!-- Source: https://developer.android.com/guide/topics/resources/app-languages -->
<!-- In app/src/main/AndroidManifest.xml, inside <application> -->
<service
    android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
    android:enabled="false"
    android:exported="false">
    <meta-data
        android:name="autoStoreLocales"
        android:value="true" />
</service>
```

### Pattern 4: String Naming Convention
**What:** English-descriptive IDs with screen-prefix scoping
**When to use:** All new string resources in Phase 6

```xml
<!-- English default: app/src/main/res/values/strings.xml -->
<string name="onboarding_welcome_tagline">Voice dictation, 100\u00a0% offline</string>
<string name="onboarding_welcome_cta">Start</string>
<string name="onboarding_mic_permission_title">Microphone</string>
<string name="onboarding_mic_permission_body">Dictus needs the microphone to transcribe your voice. Your recordings stay on your device.</string>
<string name="onboarding_mic_permission_cta_grant">Allow Microphone</string>
<string name="onboarding_mic_permission_cta_continue">Continue</string>

<!-- French overlay: app/src/main/res/values-fr/strings.xml -->
<string name="onboarding_welcome_tagline">Dictation vocale, 100\u00a0% offline</string>
<string name="onboarding_welcome_cta">Commencer</string>
<string name="onboarding_mic_permission_title">Microphone</string>
<string name="onboarding_mic_permission_body">Dictus a besoin du microphone pour transcrire votre voix. Vos enregistrements restent sur votre appareil.</string>
<string name="onboarding_mic_permission_cta_grant">Autoriser le micro</string>
<string name="onboarding_mic_permission_cta_continue">Continuer</string>
```

### Pattern 5: In-App Language Picker Row (reusing SettingPickerRow)
**What:** Add a "Language UI" picker row in the A PROPOS or new LANGUE section of SettingsScreen
**When to use:** Extending SettingsScreen with the language picker

```kotlin
// In SettingsScreen.kt — add to SettingsViewModel:
val uiLanguage: StateFlow<String> = // read from AppCompatDelegate or DataStore mirror
// Display: "system" -> "Automatique / System", "fr" -> "Français", "en" -> "English"

// New row in SettingsCard:
SettingPickerRow(
    label = stringResource(R.string.settings_ui_language_label),
    value = when (uiLanguage) {
        "fr" -> "Français"
        "en" -> "English"
        else -> stringResource(R.string.settings_ui_language_system)
    },
    onClick = { showUiLanguagePicker = true },
)
```

### Anti-Patterns to Avoid
- **Storing locale in DataStore manually:** `AppCompatDelegate.setApplicationLocales()` already persists locale via AndroidX; adding a DataStore key duplicates this and creates a source-of-truth conflict.
- **Calling `setApplicationLocales()` from a ViewModel coroutine:** It must be called on the main thread. Call it directly from the UI layer (onClick handler) or ensure it's dispatched to `Dispatchers.Main`.
- **Using `values-en/strings.xml`:** The English fallback is `values/strings.xml` (no qualifier). Creating `values-en/` causes unexpected behavior when the device language is not French and not explicitly English.
- **Forgetting `%` escaping in format strings:** In Android strings.xml, literal `%` must be `%%`. The existing `"Téléchargement — $downloadProgress%"` uses Kotlin string interpolation — when extracted, it becomes a format string argument.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Locale persistence across app restarts | DataStore key + ContextWrapper in `attachBaseContext` | `AppCompatDelegate.setApplicationLocales()` + `AppLocalesMetadataHolderService` | AndroidX handles persistence, API 24–33 compat, system settings integration, Activity recreation |
| Locale config declaration | Manual `locales_config.xml` maintenance | AGP `generateLocaleConfig = true` in `build.gradle.kts` | Auto-generated from `values-*` directories; no sync needed |
| String format with dynamic values | String concatenation or Kotlin template in Text() | `stringResource(R.string.foo, arg1)` with `%1$d` / `%1$s` placeholders | Translators can reorder arguments for other languages |

**Key insight:** `AppCompatDelegate.setApplicationLocales()` is the only API that integrates with the Android 13 system language picker AND provides backward-compat persistence. Custom solutions reimplement what the platform provides and break system-level language preferences.

---

## Common Pitfalls

### Pitfall 1: Missing `AppLocalesMetadataHolderService` manifest entry
**What goes wrong:** On devices running API 32 and below, `setApplicationLocales()` does NOT persist the locale across app restarts. The language reverts to the device default on every cold start.
**Why it happens:** Automatic storage requires a manifest opt-in; AndroidX won't write to shared preferences without it.
**How to avoid:** Add the service entry to `app/src/main/AndroidManifest.xml` with `autoStoreLocales="true"` and `android:enabled="false"`.
**Warning signs:** Language picker works on a Pixel 7 (API 33+) but reverts on older devices.

### Pitfall 2: Activity needs to extend AppCompatActivity
**What goes wrong:** `setApplicationLocales()` silently does nothing if `MainActivity` extends `ComponentActivity` or `FragmentActivity` directly instead of `AppCompatActivity`.
**Why it happens:** The locale injection is implemented in `AppCompatActivity.attachBaseContext()`.
**How to avoid:** Verify `MainActivity` extends `AppCompatActivity`. This is already the case in most Hilt + Compose projects, but worth verifying.
**Warning signs:** Language picker saves the selection (no crash) but UI never changes.

### Pitfall 3: Compose `stringResource()` only resolves at composition time
**What goes wrong:** After calling `setApplicationLocales()`, old Composables that are already in the composition tree may show stale strings until they recompose.
**Why it happens:** `setApplicationLocales()` triggers an Activity recreation on API 32 and below. On API 33+, it updates the `Configuration` which triggers a global recomposition — but only if the app properly propagates the new configuration.
**How to avoid:** The standard Activity recreation path handles this correctly. Do not try to update strings manually via state variables.
**Warning signs:** Some UI elements change language, others don't after switching.

### Pitfall 4: `values/strings.xml` vs `values-en/strings.xml` confusion
**What goes wrong:** If `values-en/strings.xml` exists alongside `values/strings.xml`, Android may not fall back correctly on non-French, non-English devices — it picks `values-en/` for en-US devices but ignores `values/` fallback for other cases.
**Why it happens:** Android resource qualifier resolution uses the most specific match.
**How to avoid:** English strings go ONLY in `values/strings.xml` (no qualifier). Only `values-fr/strings.xml` is needed.

### Pitfall 5: String extraction breaking format strings
**What goes wrong:** `"Téléchargement — $downloadProgress%"` uses Kotlin string interpolation. When extracted to strings.xml as `"Téléchargement — %1$d%%"`, the `%%` for literal `%` and `%1$d` for the integer argument must be used, and the Compose call must change to `stringResource(R.string.model_download_progress, downloadProgress)`.
**Why it happens:** strings.xml uses Java-style format specifiers, not Kotlin templates.
**How to avoid:** Test format strings at extraction time. Pattern: `$variable` → `%1$s` (string) or `%1$d` (integer).

### Pitfall 6: Cold start IME-only scenario — model loading race
**What goes wrong:** When the keyboard triggers dictation WITHOUT the main app running, `DictusImeService` must load the Whisper model from scratch. If the foreground service starts but the model isn't loaded yet, transcription fails silently or crashes.
**Why it happens:** The main app normally pre-loads the model. The IME's standalone service path may not guard against the loading race condition.
**How to avoid:** In the cold start test, wait for the transcription result before declaring success. Verify the foreground service notification appears and the model loads within a reasonable timeout.

---

## Code Examples

### Generating locales_config.xml automatically (build.gradle.kts)
```kotlin
// Source: https://developer.android.com/guide/topics/resources/app-languages
// In app/build.gradle.kts:
android {
    androidResources {
        generateLocaleConfig = true
    }
}
```

```properties
# app/src/main/resources.properties (new file)
unqualifiedResLocale=en-US
```

### Accessing stringResource in a non-Composable context (ViewModel/Service)
```kotlin
// Source: Android developer docs
// If strings are needed outside Compose (e.g., notification text in DictusImeService):
// Use context.getString(R.string.notification_dictation_active)
// Inject Context via Hilt @ApplicationContext where needed
```

### Adding LANGUAGE_UI preference key
```kotlin
// In PreferenceKeys.kt — only needed if mirroring the locale to DataStore
// (NOT needed if relying solely on AppCompatDelegate for persistence)
// val UI_LANGUAGE = stringPreferencesKey("ui_language") // SKIP: AppCompatDelegate persists this
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ContextWrapper` + `updateConfiguration()` | `AppCompatDelegate.setApplicationLocales()` | Android 13 / AppCompat 1.6.0 (2022) | Declarative, persists automatically, integrates with system language picker |
| Manual `locales_config.xml` | AGP `generateLocaleConfig = true` | AGP 8.1+ (2023) | Auto-generated from resource dirs, zero maintenance |
| `Configuration.locale` (deprecated) | `LocaleListCompat` / `LocaleList` | API 24+ | Multi-locale aware |

**Deprecated/outdated:**
- `Configuration.locale` (singular): Deprecated since API 24. Use `LocaleList`.
- `updateConfiguration()` on `Resources`: Deprecated since API 25. Use `AppCompatDelegate`.

---

## Open Questions

1. **Does MainActivity already extend AppCompatActivity?**
   - What we know: Hilt + Compose scaffold typically generates AppCompatActivity
   - What's unclear: Not directly verified — check `app/src/main/java/.../MainActivity.kt`
   - Recommendation: Verify before implementing; if it extends ComponentActivity, change to AppCompatActivity

2. **IME cold-start: does DictusImeService load the model independently?**
   - What we know: The foreground service + whisper.cpp integration is complete from Phase 3; the CONTEXT.md flags this as a critical test scenario
   - What's unclear: Whether the IME's `DictationServiceEntryPoint` DataStore path works correctly when the main app process has never been started
   - Recommendation: Test manually on Pixel 4 — uninstall, reinstall, open WhatsApp, trigger keyboard, tap mic, dictate; verify transcription inserts

3. **Should the language picker row appear in APPARENCE or a new LANGUE section?**
   - What we know: CONTEXT.md leaves placement to Claude's discretion
   - Recommendation: Add it to the TRANSCRIPTION section as "Langue de l'interface" immediately below the existing transcription language row — both are language-related settings

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.x (existing) |
| Config file | None — configured via `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [33])` |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "dev.pivisolutions.dictus.ui.settings.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DSG-03 | PreferenceKeys.UI_LANGUAGE key reads/writes correctly via SettingsViewModel | unit | `./gradlew :app:testDebugUnitTest --tests "*.SettingsViewModelTest"` | ✅ extend existing |
| DSG-03 | `setUiLanguage("fr")` calls AppCompatDelegate correctly | unit (mockable) | `./gradlew :app:testDebugUnitTest --tests "*.LanguagePickerTest"` | ❌ Wave 0 |
| DSG-03 | French strings.xml covers all keys defined in English values/strings.xml | build-time | `./gradlew :app:lintDebug` (lint reports missing translations) | ✅ automatic |
| DSG-02 | Theme system unchanged — dark/light/auto still works | unit | `./gradlew :core:testDebugUnitTest --tests "*.DictusThemeTest"` | ✅ existing |
| Cross-app | commitText inserts in WhatsApp/Gmail/Chrome | manual | N/A — requires physical device + app install | manual-only |
| Cross-app | Cold start IME dictation without main app running | manual | N/A — requires physical device scenario | manual-only |
| Perf | No memory leak after long keyboard session | manual | Android Studio Memory Profiler or Logcat GC observation | manual-only |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/dev/pivisolutions/dictus/ui/settings/LanguagePickerTest.kt` — covers DSG-03 in-app language picker logic
- [ ] `app/src/main/resources.properties` — needed for `generateLocaleConfig = true`

---

## String Inventory (Extraction Scope)

### app/ module — onboarding screens (~35 strings)
| Screen | Strings to Extract |
|--------|--------------------|
| OnboardingWelcomeScreen | tagline "Dictation vocale, 100 % offline", CTA "Commencer" |
| OnboardingMicPermissionScreen | title "Microphone", body text, CTA "Autoriser le micro"/"Continuer" |
| OnboardingModeSelectionScreen | title "Choisissez votre clavier" |
| OnboardingKeyboardSetupScreen | title "Ajouter le clavier" |
| OnboardingModelDownloadScreen | title "Modèle vocal", body, "Recommandé pour votre appareil", "Rapide", "Réessayer", "Téléchargement — %d%%" |
| OnboardingTestRecordingScreen | "Résultat", "Testez la dictée", "Transcription en cours…", "Continuer" |
| OnboardingSuccessScreen | "C'est prêt !", body confirmation, CTA "Commencer" |

### app/ module — settings screens (~20 strings)
| Screen | Strings to Extract |
|--------|--------------------|
| SettingsScreen | All SectionHeader texts (TRANSCRIPTION, CLAVIER, APPARENCE, A PROPOS), picker labels, picker option labels, bottom sheet titles, Toast messages |
| SoundSettingsScreen | "Sons", "GENERAL", "SONS" section headers |
| DebugLogsScreen | "Debug Logs" title, "Aucun log disponible" |
| ModelCard | "Actif", "Télécharger", "Réessayer", "Téléchargement — %d%%", "WK" badge |

### ime/ module — keyboard screens (~3 strings)
| Screen | Strings to Extract |
|--------|--------------------|
| RecordingScreen | "En ecoute..." |
| TranscribingScreen | "Transcription..." |
| EmojiPickerScreen | "ABC" (return-to-keyboard label) |

### core/ module — existing strings (minimal additions)
- `app_name`, `ime_name`, subtype labels already exist in English; French overlay needed for `ime_name` ("Clavier Dictus") and subtype labels

---

## Sources

### Primary (HIGH confidence)
- [Android Developer Docs — Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages) — `AppCompatDelegate.setApplicationLocales()` API, manifest service entry, AGP `generateLocaleConfig`, LocaleConfig XML format
- Android string resource qualifier documentation — `values-fr/` overlay pattern, format strings with `%1$d`, `%%` escaping

### Secondary (MEDIUM confidence)
- WebSearch cross-reference: AppCompat 1.6.0+ requirement, `AppCompatActivity` requirement for Compose verified against official docs

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Official Android docs verified via WebFetch
- Architecture: HIGH — String resource pattern is Android standard; existing project code confirms per-module structure
- Pitfalls: HIGH — Verified against official docs + known Android localization edge cases
- Cold-start IME validation: MEDIUM — Behavior described in CONTEXT.md; actual outcome requires physical device test

**Research date:** 2026-03-29
**Valid until:** 2026-09-29 (stable Android APIs, 6-month horizon)
