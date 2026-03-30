# Phase 4: Model Management + Onboarding - Research

**Researched:** 2026-03-26
**Domain:** Android Jetpack Compose — navigation, DataStore, coroutines Flow, Timber file logging, DM Sans font, BottomNavigation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Onboarding flow:**
- 6 sequential steps: Welcome → Mic permission → Keyboard layout → IME activation → Model download → Success
- User cannot skip steps — each must be validated before advancing
- Model download is MANDATORY during onboarding
- Default model: tiny (~77 MB)
- Success screen ("C'est prêt !") as step 6 → button to Home
- Onboarding is full-screen, replaces the app; after completion switches to Home with 3 tabs
- Persist onboarding completion in DataStore: `HAS_COMPLETED_ONBOARDING`

**Model management:**
- 4 models for MVP: tiny (~77 MB), base (~142 MB), small (~466 MB), small-q5_1 (~190 MB)
- ModelManager MUST support multiple AI providers (Whisper today, Parakeet soon) — provider abstraction from day 1
- Cannot delete last remaining model — show "Au moins un modèle requis"
- Download UI: progress bar + percentage text
- Storage indicator per model + total storage consumed
- ModelDownloader needs `Flow<Int>` progress callback (currently only logs to Timber)

**Settings screen:**
- 3 sections: Transcription (langue, modèle actif), Clavier (layout, haptic, son), À propos (version, debug export, liens)
- Phase 4 includes: language picker (auto/FR/EN), active model, keyboard layout, haptics toggle, sound toggle, debug log export
- Phase 4 does NOT include: Follow System theme toggle, UI language switch (→ Phase 6)
- Debug log export: generate ZIP → open Android share sheet
- DictationService reads language + active model from DataStore (currently hard-coded)

**App navigation:**
- 3-tab bottom nav bar (pill style, frame `d7cJl`): Home / Models / Settings
- Jetpack Compose Navigation (NavController) for tab routing
- Onboarding is a separate full-screen route, shown when `HAS_COMPLETED_ONBOARDING = false`

### Claude's Discretion
- Loading/skeleton states during model operations
- Exact onboarding page animations/transitions
- Error state UI (download failure, permission denial recovery)
- Home tab layout details beyond active model + last transcription
- TimberSetup file appender implementation for log export

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| APP-01 | User completes onboarding: welcome slides, mic permission, layout choice, IME activation, model download, test dictation | Jetpack Navigation Compose for step routing; `ActivityResultContracts.RequestPermission` for mic; `Settings.ACTION_INPUT_METHOD_SETTINGS` for IME; `ModelDownloader` with Flow progress; DataStore `HAS_COMPLETED_ONBOARDING` |
| APP-02 | User can download/delete Whisper models (tiny, base, small, small-quantized) from the model manager | Extend `ModelManager` with 4-model catalog + provider enum; extend `ModelDownloader` with `Flow<DownloadProgress>`; delete via `File.delete()` with last-model guard |
| APP-03 | User sees storage indicator per model and total used | `File.length()` / `File.exists()` on model files; sum for total; rendered in `ModelsScreen` footer |
| APP-04 | User can configure: active model, transcription language, keyboard layout, haptic, sound, theme | Extend `PreferenceKeys` with new keys; `SettingsScreen` reads/writes via `DataStore`; `DictationService.confirmAndTranscribe()` reads from DataStore instead of hard-coded constants |
| APP-05 | User can export debug logs from settings | Timber file-appender `FileLoggingTree`; ZIP creation with `ZipOutputStream`; share via `Intent.ACTION_SEND` + `FileProvider` |
</phase_requirements>

---

## Summary

Phase 4 builds the full user-facing shell of Dictus Android: a guided 6-step onboarding (covering mic permission, IME setup, mode selection, model download, and success), a 3-tab main navigation (Home / Models / Settings), complete model management with download progress and deletion, a full settings screen, and debug log export.

All core building blocks already exist in the codebase — `ModelManager`, `ModelDownloader`, `PermissionHelper`, `ImeStatusCard`, `DataStore`, `WaveformBars`, `DictusTheme`. This phase is primarily about (1) wiring them into a coherent navigation graph, (2) extending `ModelDownloader` with a `Flow<Int>` progress channel, (3) refactoring `ModelManager` with a provider abstraction, (4) extending `PreferenceKeys` and wiring DataStore reads into `DictationService`, and (5) building the UI screens against the finalized UI-SPEC.

Navigation Compose (already declared in `libs.versions.toml` via `lifecycle-viewmodel-compose`) needs `androidx.navigation:navigation-compose` added explicitly — it is not currently in `build.gradle.kts`. DM Sans font bundling requires the Google Fonts Compose library. Both are the only new `build.gradle.kts` changes needed in `app/`.

**Primary recommendation:** Deliver in 4 waves — (1) navigation skeleton + DataStore wiring, (2) ModelManager refactor + ModelDownloader Flow extension, (3) all screens (onboarding, Home, Models, Settings), (4) debug log export.

---

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose BOM | 2025.03.00 | UI toolkit | Already in use throughout |
| Material 3 | via BOM | Component library + theming | Established in `DictusTheme` |
| DataStore Preferences | 1.1.3 | Persistent settings | Already in `core/` module, `PreferenceKeys.kt` exists |
| Hilt | 2.51.1 | DI | Already wired in all modules |
| Timber | 5.0.1 | Logging | Established in `TimberSetup.kt` |
| OkHttp | 4.12.0 | HTTP downloads | Already in `ModelDownloader` |
| Coroutines | 1.8.1 | Async / Flow | Already used throughout service layer |
| Lifecycle ViewModel Compose | 2.8.7 | `viewModel()` in Composables | Already in `libs.versions.toml` |

### New additions needed
| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| Navigation Compose | 2.8.x (aligned to Compose BOM 2025.03.00) | NavController, NavHost, composable destinations | Needed for 3-tab routing + onboarding separate route |
| Compose UI Google Fonts | via BOM | DM Sans font loading from Google Fonts | UI-SPEC mandates DM Sans; bundling via `GoogleFont` API is standard |
| FileProvider (AndroidX Core) | already via `core-ktx 1.15.0` | Share exported ZIP file | `FileProvider` is part of `androidx.core` — no new dep needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Navigation Compose | Manual screen state + `when` | Navigation Compose handles back stack, deep links, type-safe args. Manual approach sufficient for simple linear flow but fragile for 3-tab + onboarding gate |
| Google Fonts API for DM Sans | Bundle .ttf in `res/font/` | Bundle approach works offline guaranteed; Google Fonts API requires network on first run. For an offline app — **bundle the font files** |
| ZipOutputStream for log export | Third-party ZIP library | ZipOutputStream is in JDK stdlib, zero dependency overhead |

**Font bundling decision:** Since Dictus is a 100% offline app, DM Sans MUST be bundled as resource fonts (`res/font/`) rather than loaded via Google Fonts API, which requires network access. Download DM Sans from Google Fonts and place all required weight files in `core/src/main/res/font/`.

**Installation (new additions to `app/build.gradle.kts`):**
```kotlin
implementation(libs.navigation.compose)
// No Google Fonts dep needed — bundle .ttf in res/font/
```

Add to `libs.versions.toml`:
```toml
[versions]
navigation = "2.8.9"  # align with Compose BOM 2025.03.00

[libraries]
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
```

---

## Architecture Patterns

### Recommended Project Structure

```
app/src/main/java/dev/pivisolutions/dictus/
├── navigation/
│   ├── AppDestination.kt           # sealed class/enum for routes
│   └── AppNavHost.kt               # NavHost wiring onboarding + main tabs
├── onboarding/
│   ├── OnboardingViewModel.kt      # step state, model download progress
│   ├── OnboardingWelcomeScreen.kt
│   ├── OnboardingMicPermissionScreen.kt
│   ├── OnboardingKeyboardSetupScreen.kt
│   ├── OnboardingModeSelectionScreen.kt
│   ├── OnboardingModelDownloadScreen.kt
│   └── OnboardingSuccessScreen.kt
├── home/
│   └── HomeScreen.kt
├── models/
│   ├── ModelsViewModel.kt          # download state, deletion, storage
│   └── ModelsScreen.kt
├── settings/
│   ├── SettingsViewModel.kt        # DataStore reads/writes
│   └── SettingsScreen.kt
├── ui/
│   └── navigation/
│       └── DictusBottomNavBar.kt
└── model/
    └── ModelManager.kt             # extend with provider abstraction + 4 models

core/src/main/java/dev/pivisolutions/dictus/core/
├── ui/
│   ├── GlassCard.kt                # new shared composable
│   └── SkeletonCard.kt             # shimmer loading placeholder
├── theme/
│   └── DictusTypography.kt         # update to DM Sans
└── preferences/
    └── PreferenceKeys.kt           # extend with new keys
```

### Pattern 1: ViewModel + StateFlow for Download Progress

The `ModelDownloader` currently logs progress but doesn't emit it. Add a `Flow<DownloadProgress>` via `callbackFlow` or a simple `MutableStateFlow<Map<String, Int>>` managed by a `ModelRepository` / `ModelsViewModel`.

**What:** `ModelDownloader.downloadWithProgress()` returns `Flow<Int>` (0–100). `ModelsViewModel` collects this Flow and exposes `downloadProgress: StateFlow<Map<String, Int>>` to the UI.

**When to use:** Whenever long-running async operations need to stream intermediate results to Compose UI.

```kotlin
// Source: Kotlin Coroutines docs — callbackFlow pattern
fun downloadWithProgress(modelKey: String): Flow<Int> = callbackFlow {
    // ... OkHttp download loop
    val buffer = ByteArray(8192)
    var bytesRead = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        bytesRead += read
        if (totalBytes > 0) {
            trySend(((bytesRead * 100) / totalBytes).toInt())
        }
    }
    close()  // completes the flow
} .flowOn(Dispatchers.IO)
```

### Pattern 2: Onboarding Gate in NavHost

**What:** `MainActivity` no longer shows `TestSurfaceScreen`. Instead it hosts an `AppNavHost` that reads `HAS_COMPLETED_ONBOARDING` from DataStore and navigates to either the onboarding graph or the main tabs.

**When to use:** Standard pattern for first-run experience gating navigation.

```kotlin
// Pattern: collect DataStore value as state in the composable, navigate once
val hasCompletedOnboarding by preferencesDataStore
    .data.map { it[PreferenceKeys.HAS_COMPLETED_ONBOARDING] ?: false }
    .collectAsState(initial = null)

// null = loading (show splash/nothing), false = onboarding, true = main tabs
when (hasCompletedOnboarding) {
    null -> SplashScreen()
    false -> NavHost(startDestination = "onboarding") { ... }
    true -> MainTabsScreen()
}
```

**Critical:** Read the DataStore value BEFORE displaying any screen to avoid flashing the onboarding to a returning user. Use `initial = null` as the loading state.

### Pattern 3: ModelManager Provider Abstraction

Mirror the iOS `ModelInfo.engine` enum. Keep all Whisper-specific URL/file logic behind a provider interface so that adding Parakeet in Phase 6 is additive, not a rewrite.

```kotlin
enum class AiProvider { WHISPER, PARAKEET }

data class ModelInfo(
    val key: String,
    val fileName: String,
    val displayName: String,
    val expectedSizeBytes: Long,
    val qualityLabel: String,    // "Rapide", "Équilibré", "Précis"
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
)

object ModelCatalog {
    val ALL = listOf(
        ModelInfo("tiny",       "ggml-tiny.bin",         "Tiny",  77_691_713L,  "Rapide"),
        ModelInfo("base",       "ggml-base.bin",        "Base",  142_000_000L, "Équilibré"),
        ModelInfo("small",      "ggml-small.bin",       "Small", 466_000_000L, "Précis"),
        ModelInfo("small-q5_1", "ggml-small-q5_1.bin",  "Small Q5", 190_031_232L, "Précis"),
    )
    // Provider routing: only WHISPER today
    fun downloadUrl(info: ModelInfo): String = when(info.provider) {
        AiProvider.WHISPER -> "$WHISPER_BASE_URL/${info.fileName}"
        AiProvider.PARAKEET -> TODO("Phase 6")
    }
}
```

### Pattern 4: DataStore Extension for Single-Value Read

```kotlin
// Standard pattern — suspend fun for one-shot reads, Flow for reactive
suspend fun DataStore<Preferences>.getActiveModel(): String {
    return data.first()[PreferenceKeys.ACTIVE_MODEL] ?: ModelCatalog.DEFAULT_KEY
}
```

In `DictationService.confirmAndTranscribe()`, replace the hard-coded `"tiny"` and `"fr"` with single DataStore reads at call time. The DataStore instance should be injected via Hilt application context (standard pattern with `@Singleton` DataStore provider in a Hilt module).

### Pattern 5: Timber FileLoggingTree for Log Export

```kotlin
// TimberSetup.kt — extend with file appender
class FileLoggingTree(private val logFile: File) : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logFile.appendText("${timestamp()} [$priority/$tag] $message\n")
        t?.let { logFile.appendText(it.stackTraceToString() + "\n") }
    }
}
// Plant in TimberSetup.init() alongside DebugTree
Timber.plant(FileLoggingTree(logFile))
```

Log export ZIP + share sheet:
```kotlin
// In SettingsViewModel or SettingsScreen action handler
val zipFile = File(context.cacheDir, "dictus-logs.zip")
ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
    zip.putNextEntry(ZipEntry("dictus.log"))
    logFile.inputStream().copyTo(zip)
    zip.closeEntry()
}
// Share via FileProvider
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/zip"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, "Exporter les logs"))
```

**FileProvider requires** a `file_paths.xml` in `res/xml/` and a `<provider>` declaration in `AndroidManifest.xml`. Both are straightforward but MUST be done.

### Pattern 6: Compose Navigation with Bottom Tabs (No Back Stack Between Tabs)

```kotlin
// Standard pattern — launchSingleTop + restoreState for tab stability
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

### Anti-Patterns to Avoid

- **Storing download state in ModelManager directly:** ModelManager is a repository/catalog. Download state (progress, error, cancellation) belongs in a ViewModel. Keep them separate.
- **Calling DataStore.data.first() on the main thread:** Always collect DataStore inside a coroutine (`viewModelScope.launch`, `LaunchedEffect`, or `lifecycleScope`).
- **Binding DictationService inside a Composable:** Already established — binding belongs in `MainActivity` lifecycle. Keep this pattern.
- **Sharing the logFile File reference across threads without synchronization:** Use `synchronized` or `Dispatchers.IO` for file writes.
- **Using Google Fonts API for DM Sans:** Network dependency in an offline app. Bundle the TTF files instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Navigation + back stack | Custom screen stack with `remember` | Navigation Compose `NavHost` | Back stack, predictive back gesture, type-safe args, deep link handling are all handled |
| HTTP download with progress | Custom `HttpURLConnection` loop | Existing `ModelDownloader` + `callbackFlow` | OkHttp redirect handling already proven; only needs Flow wrapping |
| File share (log export) | Custom file server or base64 encoding | `FileProvider` + `Intent.ACTION_SEND` | Android's share sheet system; FileProvider is mandatory for content URIs on Android 7+ |
| DataStore access boilerplate | Multiple `context.dataStore.data.map {}` chains | Single DataStore module + extension functions | Consistent defaults, single source of truth |
| Permission rationale flow | Custom dialog | `ActivityResultContracts.RequestPermission` + check `shouldShowRequestPermissionRationale` | Standard Android permission API handles system dialog and rationale |
| Shimmer loading animation | Custom `Canvas` animation | Compose `InfiniteTransition` + `Brush.linearGradient` | 15 lines of code, no extra lib needed |

**Key insight:** This phase is primarily wiring + extension, not greenfield. The risk is over-engineering — ModelManager provider abstraction, DataStore plumbing, and Navigation setup are the only architectural novelties. Everything else is straightforward screen composition.

---

## Common Pitfalls

### Pitfall 1: DataStore Cold-Start Flash (Onboarding shown to returning user)
**What goes wrong:** `HAS_COMPLETED_ONBOARDING` DataStore read takes ~50ms. If NavHost starts at onboarding by default, returning users briefly see the welcome screen before navigation corrects.
**Why it happens:** `collectAsState(initial = false)` defaults to false (onboarding). Should be `null` to distinguish "loading" from "not completed".
**How to avoid:** Use `initial = null` as a tri-state (null = loading, false = show onboarding, true = show main). Show a blank/splash screen while null.
**Warning signs:** Unit test passes, but physical device shows a flash on app restart.

### Pitfall 2: ModelDownloader Progress Flow Not Collecting on Cancellation
**What goes wrong:** If user leaves the Models screen while downloading, the coroutine collecting the Flow gets cancelled. The download itself (OkHttp) may continue or leak.
**Why it happens:** OkHttp `Call` is not cancelled when the coroutine is cancelled unless explicitly wired.
**How to avoid:** In the `callbackFlow` or the ViewModel collection, call `response.body?.close()` and `call.cancel()` in `awaitClose {}` / `onCompletion {}`.
**Warning signs:** Download continues in background after user navigates away; memory not freed.

### Pitfall 3: FileProvider Not Declared → FileUriExposedException
**What goes wrong:** Sharing the log ZIP via `Intent.ACTION_SEND` crashes with `FileUriExposedException` on Android 7+ if a `file://` URI is used directly.
**Why it happens:** Android 7 (API 24) banned sharing raw file paths between apps.
**How to avoid:** Always use `FileProvider.getUriForFile()` to get a `content://` URI. Declare `<provider>` in `AndroidManifest.xml` and create `res/xml/file_paths.xml` pointing to `cacheDir`.
**Warning signs:** Works on emulator but crashes on device; crash log shows `FileUriExposedException`.

### Pitfall 4: Multiple DataStore Instances Created
**What goes wrong:** `Context.dataStore` extension property creates a new instance if called from multiple places. Two DataStore instances on the same file cause `IllegalStateException`.
**Why it happens:** DataStore is designed as a singleton. Creating via extension on `Context` in multiple call sites bypasses singleton enforcement.
**How to avoid:** Provide DataStore as a `@Singleton` via a Hilt module. All ViewModels receive the same instance via injection.
**Warning signs:** `java.lang.IllegalStateException: There are multiple DataStores active` in logcat.

### Pitfall 5: Navigation Compose Version / BOM Mismatch
**What goes wrong:** `navigation-compose` is NOT included in the Compose BOM — it has its own release cadence. Using a mismatched version causes runtime crashes or annotation processor failures.
**Why it happens:** Common assumption that Compose BOM covers navigation-compose. It does not.
**How to avoid:** Check https://developer.android.com/jetpack/androidx/releases/navigation for the version compatible with the project's Compose BOM (2025.03.00 → Navigation 2.8.x). Pin explicitly in `libs.versions.toml`.
**Warning signs:** Build fails with `Duplicate class` or runtime `NoClassDefFoundError`.

### Pitfall 6: DM Sans Font Missing Weights → Wrong Rendering
**What goes wrong:** UI-SPEC requires 5 weights (ExtraLight/200, Normal/400, Medium/500, SemiBold/600, Bold/700). If only 400 is bundled, all other weights render as 400 (Android synthesizes bold poorly).
**Why it happens:** Developers download only `DM_Sans-Regular.ttf` and forget the weight variants.
**How to avoid:** Download and bundle all 5 weight variants from Google Fonts. Reference each via a `FontFamily` with explicit `FontWeight` entries.
**Warning signs:** "Dictus" wordmark appears too heavy or not thin enough; all text looks the same weight visually.

### Pitfall 7: DictationService Hard-Coded Constants Not Updated
**What goes wrong:** After DataStore wiring, `DictationService` still uses `private const val LANGUAGE = "fr"` and `ModelManager.DEFAULT_MODEL_KEY` for the model. Changing settings has no effect on transcription.
**Why it happens:** Phase 3 intentionally hard-coded these with a comment `// Phase 4 adds settings`.
**How to avoid:** In `confirmAndTranscribe()`, read `ACTIVE_MODEL` and `TRANSCRIPTION_LANGUAGE` from DataStore (one-shot `first()` call) before calling `ensureModelAvailable()` and `transcribe()`.

---

## Code Examples

### Navigation Compose Setup

```kotlin
// Source: developer.android.com/jetpack/compose/navigation
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "main") {
        composable("onboarding") { OnboardingGraph(navController) }
        composable("main") { MainTabsScreen(navController) }
    }
}
```

### DataStore Singleton Hilt Module

```kotlin
// Source: developer.android.com/topic/libraries/architecture/datastore#preferences-create
val Context.dictusDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "dictus_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dictusDataStore
}
```

### ModelDownloader with Flow Progress

```kotlin
// Pattern: callbackFlow for bridging callback-to-Flow
fun downloadWithProgress(modelKey: String): Flow<DownloadProgress> = callbackFlow {
    val url = modelManager.downloadUrl(modelKey) ?: run { close(); return@callbackFlow }
    val targetPath = modelManager.getTargetPath(modelKey) ?: run { close(); return@callbackFlow }
    val tempFile = File("$targetPath.tmp")

    val request = Request.Builder().url(url).build()
    val call = client.newCall(request)

    awaitClose { call.cancel(); tempFile.delete() }  // cancellation cleanup

    withContext(Dispatchers.IO) {
        val response = call.execute()
        val body = response.body ?: run { close(Exception("empty body")); return@withContext }
        val totalBytes = body.contentLength()
        var bytesRead = 0L
        body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (totalBytes > 0) {
                        trySend(DownloadProgress.Progress(((bytesRead * 100) / totalBytes).toInt()))
                    }
                }
            }
        }
        if (tempFile.renameTo(File(targetPath))) {
            trySend(DownloadProgress.Complete(targetPath))
        }
        close()
    }
}.flowOn(Dispatchers.IO)

sealed class DownloadProgress {
    data class Progress(val percent: Int) : DownloadProgress()
    data class Complete(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
```

### DM Sans Font Family (bundled TTF approach)

```kotlin
// Source: developer.android.com/develop/ui/compose/text/fonts#font-family
// Requires: res/font/dm_sans_extralight.ttf, dm_sans_regular.ttf, dm_sans_medium.ttf,
//           dm_sans_semibold.ttf, dm_sans_bold.ttf
val DmSans = FontFamily(
    Font(R.font.dm_sans_extralight, FontWeight.ExtraLight),
    Font(R.font.dm_sans_regular,    FontWeight.Normal),
    Font(R.font.dm_sans_medium,     FontWeight.Medium),
    Font(R.font.dm_sans_semibold,   FontWeight.SemiBold),
    Font(R.font.dm_sans_bold,       FontWeight.Bold),
)
```

Update `DictusTypography.kt` to replace all `FontFamily.Default` with `DmSans`.

### GlassCard Composable (new, in core)

```kotlin
// Pattern: surface with custom colors, no Material surface elevation
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DictusColors.Surface)          // #161C2C
            .border(1.dp, DictusColors.GlassBorder, RoundedCornerShape(16.dp))
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}
```

### FileProvider Manifest Declaration

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<paths>
    <cache-path name="logs" path="." />
</paths>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `SharedPreferences` for settings | `DataStore Preferences` | Android Jetpack 2020+ | Type-safe, coroutines-native, already in use |
| Navigation via `FragmentManager` | Navigation Compose `NavHost` | Compose stable 2021 | No fragments needed; composable destinations |
| `HttpURLConnection` for downloads | `OkHttp` | OkHttp stable, long-established | Already in use; handles redirects (HuggingFace uses 302) |
| System fonts only | Custom fonts via `res/font/` or Google Fonts API | Compose 1.0+ | Supported; bundle for offline apps |

**Deprecated/outdated:**
- `TestSurfaceScreen` + direct `setContent` in `MainActivity`: replaced by `AppNavHost` + `NavController`
- Hard-coded `LANGUAGE = "fr"` and `DEFAULT_MODEL_KEY` in `DictationService`: replaced by DataStore reads

---

## Open Questions

1. **Navigation Compose exact version for BOM 2025.03.00**
   - What we know: Navigation Compose is not in the BOM. BOM 2025.03.00 is based on Compose 1.7.x. Navigation 2.8.x targets Compose 1.7+.
   - What's unclear: Whether 2.8.9 or 2.7.x is the correct compatible version for the project's exact AGP/Kotlin combo.
   - Recommendation: Use `2.8.9` as the starting point (latest 2.8.x as of early 2026). If KSP annotation processor errors appear, try `2.7.7`.

2. **DataStore injection: provide from `core` module or `app` module?**
   - What we know: `datastore-preferences` is already in `core/build.gradle.kts`. `PreferenceKeys.kt` lives in `core`. `DictationService` (in `app`) needs DataStore access.
   - What's unclear: Whether the Hilt module providing `DataStore` should live in `core` (no Hilt dep currently) or `app`.
   - Recommendation: Keep the Hilt `@Module` in `app/` since `core` has no Hilt dependency. `DictationService` is in `app/` and is where the DataStore is consumed.

3. **Log file size management**
   - What we know: `FileLoggingTree` appends to a file. No size cap yet.
   - What's unclear: Should the file be capped (e.g., 5 MB rolling)? iOS uses a similar unbounded approach.
   - Recommendation: For Phase 4 MVP, no cap. Add rotation in Phase 6 if needed.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Robolectric 4.14.1 + Coroutines Test 1.8.1 |
| Config file | none — configured via `testOptions` in `build.gradle.kts` |
| Quick run command | `./gradlew :app:test :core:test --tests "*.ModelCatalogTest,*.PreferenceKeysTest,*.OnboardingViewModelTest" -x lint` |
| Full suite command | `./gradlew :app:test :core:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| APP-01 | `HAS_COMPLETED_ONBOARDING` persists to DataStore after step 6 | unit | `./gradlew :app:test --tests "*.OnboardingViewModelTest"` | ❌ Wave 0 |
| APP-01 | `OnboardingViewModel` step advancement only allowed when condition met | unit | `./gradlew :app:test --tests "*.OnboardingViewModelTest"` | ❌ Wave 0 |
| APP-02 | `ModelCatalog` contains exactly 4 models with correct sizes | unit | `./gradlew :app:test --tests "*.ModelCatalogTest"` | ❌ Wave 0 |
| APP-02 | `ModelManager.canDelete()` returns false when only 1 model downloaded | unit | `./gradlew :app:test --tests "*.ModelManagerTest"` | ❌ Wave 0 |
| APP-02 | `ModelDownloader.downloadWithProgress()` emits progress 0→100 then Complete | unit (Robolectric + MockWebServer) | `./gradlew :app:test --tests "*.ModelDownloaderTest"` | ❌ Wave 0 |
| APP-03 | `ModelManager.storageUsedBytes()` returns correct sum for downloaded models | unit | `./gradlew :app:test --tests "*.ModelManagerTest"` | ❌ Wave 0 |
| APP-04 | `PreferenceKeys` has all required new keys (HAPTICS, SOUND, HAS_COMPLETED_ONBOARDING) | unit | `./gradlew :core:test --tests "*.PreferenceKeysTest"` | ❌ Wave 0 |
| APP-04 | `SettingsViewModel` reads/writes active model and language via DataStore | unit | `./gradlew :app:test --tests "*.SettingsViewModelTest"` | ❌ Wave 0 |
| APP-05 | `TimberSetup` with file tree writes to file | unit | `./gradlew :core:test --tests "*.TimberSetupTest"` | ✅ (extend) |
| APP-05 | Log export creates valid ZIP with log file inside | unit | `./gradlew :app:test --tests "*.LogExportTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:test :core:test -x lint`
- **Per wave merge:** `./gradlew :app:test :core:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/.../onboarding/OnboardingViewModelTest.kt` — covers APP-01 step logic + DataStore persistence
- [ ] `app/src/test/.../model/ModelCatalogTest.kt` — covers APP-02 catalog completeness
- [ ] `app/src/test/.../model/ModelManagerTest.kt` — covers APP-02 delete guard, APP-03 storage calculation
- [ ] `app/src/test/.../service/ModelDownloaderTest.kt` — covers APP-02 progress flow (needs OkHttp MockWebServer)
- [ ] `app/src/test/.../settings/SettingsViewModelTest.kt` — covers APP-04 DataStore read/write
- [ ] `app/src/test/.../settings/LogExportTest.kt` — covers APP-05 ZIP creation
- [ ] `app/src/test/.../preferences/PreferenceKeysTest.kt` (in `core`) — covers APP-04 key names

Add to `app/build.gradle.kts` test dependencies:
```kotlin
testImplementation(libs.okhttp.mockwebserver)
```

Add to `libs.versions.toml`:
```toml
[libraries]
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

---

## Sources

### Primary (HIGH confidence)
- Existing codebase (direct file reads) — `ModelManager.kt`, `ModelDownloader.kt`, `DictationService.kt`, `PreferenceKeys.kt`, `TimberSetup.kt`, `DictusTypography.kt`, `build.gradle.kts`, `libs.versions.toml`
- `04-CONTEXT.md` — locked architecture decisions
- `04-UI-SPEC.md` — pixel-level screen contracts
- iOS `ModelManager.swift` — provider abstraction pattern reference

### Secondary (MEDIUM confidence)
- Android DataStore documentation pattern (well-established, production-stable since 2021)
- FileProvider + `Intent.ACTION_SEND` pattern (stable Android API, unchanged since API 24)
- Navigation Compose documentation (version recommendation for 2.8.x requires verification on first build)

### Tertiary (LOW confidence)
- Navigation Compose exact version `2.8.9` for BOM 2025.03.00 — should be verified at build time; alternative is `2.7.7`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all major libraries already in project; only Navigation Compose is new and well-documented
- Architecture: HIGH — patterns directly mirror established iOS implementation and Android best practices
- Pitfalls: HIGH — all identified from direct code inspection (hard-coded constants, missing FileProvider, font weight gaps)
- Test map: HIGH — requirements are concrete and map cleanly to unit-testable classes

**Research date:** 2026-03-26
**Valid until:** 2026-04-25 (Navigation Compose version should be re-verified if build date exceeds this)
