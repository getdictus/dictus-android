---
phase: quick
plan: 260401-jdw
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
  - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
  - app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt
  - app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-fr/strings.xml
  - app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt
autonomous: true
requirements: [dynamic-recommended-model]

must_haves:
  truths:
    - "Device with >= 8 GB RAM sees Parakeet 0.6B as the recommended model in onboarding"
    - "Device with < 8 GB RAM sees Small Q5 as the recommended model in onboarding"
    - "Model card shows the recommended model's actual name, size, and quality label (not hardcoded Tiny)"
    - "Onboarding downloads the recommended model (not always tiny)"
    - "completeOnboarding persists the recommended model key (not DEFAULT_KEY)"
    - "Parakeet download shows extraction phase UI for >= 8 GB devices"
  artifacts:
    - path: "app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt"
      provides: "recommendedModelKey(context) function in ModelCatalog"
      contains: "fun recommendedModelKey"
    - path: "app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt"
      provides: "Dynamic model key from recommendedModelKey(), extraction UI state"
      contains: "recommendedModelKey"
    - path: "app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt"
      provides: "Dynamic model card reading from ModelInfo"
      contains: "ModelInfo"
  key_links:
    - from: "OnboardingViewModel"
      to: "ModelCatalog.recommendedModelKey()"
      via: "constructor injection of Application context"
      pattern: "recommendedModelKey"
    - from: "OnboardingModelDownloadScreen"
      to: "ModelInfo fields"
      via: "composable parameters (modelName, modelSize, qualityLabel)"
      pattern: "modelName|modelSize|qualityLabel"
---

<objective>
Make onboarding recommend a model based on device RAM instead of always downloading Tiny.
>= 8 GB RAM gets parakeet-tdt-0.6b-v3 (640 MB, 25 languages, Premium).
< 8 GB RAM gets small-q5_1 (190 MB, best accuracy/size trade-off).
The model card, download, extraction UI, and DataStore persistence all use the recommended model dynamically.

Purpose: Better out-of-box experience — users with capable devices get the best model automatically.
Output: Updated ModelCatalog, OnboardingViewModel, OnboardingModelDownloadScreen, and string resources.
</objective>

<execution_context>
@/Users/pierreviviere/.claude/get-shit-done/workflows/execute-plan.md
@/Users/pierreviviere/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
@app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
@app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt
@app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
@app/src/main/res/values/strings.xml
@app/src/main/res/values-fr/strings.xml
@app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt

<interfaces>
<!-- ModelCatalog (source of truth for model metadata) -->
From app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt:
```kotlin
data class ModelInfo(
    val key: String,
    val fileName: String,
    val displayName: String,
    val expectedSizeBytes: Long,
    val qualityLabel: String,
    val description: String = "",
    val precision: Float = 0f,
    val speed: Float = 0f,
    val provider: AiProvider = AiProvider.WHISPER,
    val isDeprecated: Boolean = false,
    val isEnglishOnly: Boolean = false,
    val isTransducer: Boolean = false,
    val minRamGb: Int = 0,
)

object ModelCatalog {
    const val DEFAULT_KEY = "tiny"
    val ALL: List<ModelInfo>
    fun findByKey(key: String): ModelInfo?
}
```

<!-- DownloadProgress sealed class -->
```kotlin
sealed class DownloadProgress {
    data class Progress(val percent: Int) : DownloadProgress()
    data object Extracting : DownloadProgress()
    data class Complete(val path: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
```

<!-- OnboardingViewModel constructor (current) -->
```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val modelDownloader: ModelDownloader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel()
```

<!-- OnboardingModelDownloadScreen signature (current) -->
```kotlin
@Composable
fun OnboardingModelDownloadScreen(
    downloadProgress: Int,
    downloadComplete: Boolean,
    downloadError: String?,
    onStartDownload: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
)
```

<!-- AppNavHost call site (step 5) -->
```kotlin
5 -> OnboardingModelDownloadScreen(
    downloadProgress = downloadProgress,
    downloadComplete = downloadComplete,
    downloadError = downloadError,
    onStartDownload = { viewModel.startModelDownload() },
    onRetry = { viewModel.retryDownload() },
    onNext = { viewModel.advanceStep() },
)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add recommendedModelKey() to ModelCatalog and wire OnboardingViewModel</name>
  <files>
    app/src/main/java/dev/pivisolutions/dictus/model/ModelManager.kt
    app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModel.kt
    app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt
  </files>
  <action>
**ModelCatalog — add `recommendedModelKey(context: Context)`:**

Add a function to the `ModelCatalog` object:

```kotlin
/**
 * Return the recommended model key for onboarding based on device RAM.
 *
 * >= 8 GB total RAM -> parakeet-tdt-0.6b-v3 (640 MB, 25 languages, best quality)
 * < 8 GB total RAM  -> small-q5_1 (190 MB, best accuracy/size trade-off)
 *
 * WHY ActivityManager.MemoryInfo.totalMem: This is the physical RAM reported by
 * the kernel. It is stable across reboots and does not change with app state.
 * Available since API 16 (our min is 26).
 */
fun recommendedModelKey(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalGb = memInfo.totalMem / (1024L * 1024L * 1024L)
    return if (totalGb >= 8) "parakeet-tdt-0.6b-v3" else "small-q5_1"
}
```

Add `import android.app.ActivityManager` and `import android.content.Context` at the top of the file (Context import may already exist — check first).

**OnboardingViewModel — use recommended model key:**

1. Add `@dagger.hilt.android.qualifiers.ApplicationContext context: Context` as a constructor parameter (Hilt provides this automatically for `@HiltViewModel`). Import `android.content.Context` and `dagger.hilt.android.qualifiers.ApplicationContext`.

2. Add a `val recommendedKey: String` property initialized in the constructor body (or init block):
   ```kotlin
   val recommendedKey: String = ModelCatalog.recommendedModelKey(context)
   ```

3. Expose a `val recommendedModelInfo: ModelInfo` property:
   ```kotlin
   val recommendedModelInfo: ModelInfo = ModelCatalog.findByKey(recommendedKey)!!
   ```

4. In `startModelDownload()`: replace `ModelCatalog.DEFAULT_KEY` with `recommendedKey`.

5. In `completeOnboarding()`: replace `prefs[PreferenceKeys.ACTIVE_MODEL] = ModelCatalog.DEFAULT_KEY` with `prefs[PreferenceKeys.ACTIVE_MODEL] = recommendedKey`.

6. Handle the `DownloadProgress.Extracting` state properly: instead of just setting progress to 100, add a new `_isExtracting` MutableStateFlow(false) and expose it as `val isExtracting: StateFlow<Boolean>`. In the Extracting branch, set `_isExtracting.value = true`. In the Complete branch, set `_isExtracting.value = false`. Keep `_downloadProgress.value = 99` during extraction (not 100, since it's not done yet).

**OnboardingViewModelTest — update constructor call:**

The test creates `OnboardingViewModel(fakeDataStore, fakeDownloader, SavedStateHandle())`. Add `RuntimeEnvironment.getApplication()` as the first argument (after dataStore): `OnboardingViewModel(fakeDataStore, fakeDownloader, SavedStateHandle(), RuntimeEnvironment.getApplication())`. Wait — check the parameter order. The new constructor will be `(context, dataStore, modelDownloader, savedStateHandle)` with context first (Hilt convention for @ApplicationContext). So:
   ```kotlin
   viewModel = OnboardingViewModel(
       RuntimeEnvironment.getApplication(),
       fakeDataStore,
       fakeDownloader,
       SavedStateHandle(),
   )
   ```
   Adjust the constructor parameter order in OnboardingViewModel to put context first, matching Hilt convention.
  </action>
  <verify>
    <automated>cd /Users/pierreviviere/dev/dictus-android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>
    - `ModelCatalog.recommendedModelKey(context)` returns "parakeet-tdt-0.6b-v3" on >= 8GB, "small-q5_1" on < 8GB
    - OnboardingViewModel uses recommendedKey for download and DataStore persistence
    - OnboardingViewModel exposes recommendedModelInfo and isExtracting
    - Tests compile with updated constructor
  </done>
</task>

<task type="auto">
  <name>Task 2: Make OnboardingModelDownloadScreen dynamic and handle extraction UI</name>
  <files>
    app/src/main/java/dev/pivisolutions/dictus/onboarding/OnboardingModelDownloadScreen.kt
    app/src/main/java/dev/pivisolutions/dictus/navigation/AppNavHost.kt
    app/src/main/res/values/strings.xml
    app/src/main/res/values-fr/strings.xml
  </files>
  <action>
**OnboardingModelDownloadScreen — add model info parameters:**

1. Add parameters to the composable signature:
   ```kotlin
   fun OnboardingModelDownloadScreen(
       modelName: String,
       modelSize: String,
       modelQualityLabel: String,
       isExtracting: Boolean,
       downloadProgress: Int,
       downloadComplete: Boolean,
       downloadError: String?,
       onStartDownload: () -> Unit,
       onRetry: () -> Unit,
       onNext: () -> Unit,
   )
   ```

2. Pass `modelName`, `modelSize`, and `modelQualityLabel` down to `ModelInfoCard`. Update `ModelInfoCard` to accept these parameters instead of being hardcoded.

3. In `ModelInfoCard`:
   - Replace hardcoded `"Tiny"` text with the `modelName` parameter.
   - Replace `stringResource(R.string.onboarding_model_download_size)` with the `modelSize` parameter.
   - Replace `stringResource(R.string.onboarding_model_download_fast)` with the `modelQualityLabel` parameter.

4. Add extraction state UI: when `isExtracting` is true and not yet complete, show a different progress text. Add a new string resource for extraction. Below the progress bar, when `isExtracting`:
   ```kotlin
   Text(
       text = stringResource(R.string.onboarding_model_download_extracting),
       ...
   )
   ```
   And show an indeterminate-style progress (set the bar to full width with a pulsing animation, or just show it at 99% with the extracting text). Simplest approach: when `isExtracting`, show the progress bar at full width and change the text below to the extraction string.

5. Update the `isDownloading` logic to also account for extraction:
   ```kotlin
   val isDownloading = (downloadProgress in 0..99 || isExtracting) && !downloadComplete && downloadError == null
   ```

**AppNavHost — pass model info to OnboardingModelDownloadScreen:**

In the `OnboardingScreen` composable, collect `isExtracting`:
```kotlin
val isExtracting by viewModel.isExtracting.collectAsState()
```

Compute display values from `viewModel.recommendedModelInfo`:
```kotlin
val recommendedModel = viewModel.recommendedModelInfo
val modelSizeMb = recommendedModel.expectedSizeBytes / (1024 * 1024)
```

Update the step 5 call:
```kotlin
5 -> OnboardingModelDownloadScreen(
    modelName = recommendedModel.displayName,
    modelSize = "~$modelSizeMb MB",
    modelQualityLabel = recommendedModel.qualityLabel,
    isExtracting = isExtracting,
    downloadProgress = downloadProgress,
    downloadComplete = downloadComplete,
    downloadError = downloadError,
    onStartDownload = { viewModel.startModelDownload() },
    onRetry = { viewModel.retryDownload() },
    onNext = { viewModel.advanceStep() },
)
```

**String resources:**

In `values/strings.xml`, add:
```xml
<string name="onboarding_model_download_extracting">Extracting model files\u2026</string>
```

In `values-fr/strings.xml`, add:
```xml
<string name="onboarding_model_download_extracting">Extraction des fichiers\u2026</string>
```

Remove the now-unused `onboarding_model_download_size` and `onboarding_model_download_fast` strings from both files (the values are now dynamic from ModelInfo). Actually, keep them — they might be useful as fallback or for other purposes. No, they are only used in the hardcoded card. Remove them to avoid dead strings.
  </action>
  <verify>
    <automated>cd /Users/pierreviviere/dev/dictus-android && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20</automated>
  </verify>
  <done>
    - ModelInfoCard displays dynamic model name, size, and quality label from parameters
    - Extraction state shows "Extracting model files..." text with full progress bar
    - AppNavHost passes recommendedModelInfo fields and isExtracting to the screen
    - No hardcoded "Tiny", "77 MB", or "Rapide" in the onboarding download screen
    - String resources updated (extraction added, dead hardcoded strings removed)
  </done>
</task>

<task type="auto">
  <name>Task 3: Run tests and verify build</name>
  <files>
    app/src/test/java/dev/pivisolutions/dictus/onboarding/OnboardingViewModelTest.kt
  </files>
  <action>
Run the existing OnboardingViewModelTest suite to confirm nothing is broken by the constructor change. If any test fails due to the recommended model key (e.g., tests that assert on DEFAULT_KEY being persisted), update them to assert on the Robolectric device's recommended key instead.

Robolectric emulates a device with ~2 GB RAM by default, so `recommendedModelKey()` will return `"small-q5_1"`. Update the `completeOnboarding` test assertion from `ModelCatalog.DEFAULT_KEY` (or `"tiny"`) to `"small-q5_1"` if it checks the persisted ACTIVE_MODEL value.

Also verify the full debug build compiles cleanly (not just Kotlin — also resource processing).
  </action>
  <verify>
    <automated>cd /Users/pierreviviere/dev/dictus-android && ./gradlew :app:testDebugUnitTest --tests "dev.pivisolutions.dictus.onboarding.OnboardingViewModelTest" 2>&1 | tail -30 && ./gradlew :app:assembleDebug 2>&1 | tail -10</automated>
  </verify>
  <done>
    - All OnboardingViewModelTest tests pass
    - Debug APK builds successfully
    - No lint errors related to removed string resources
  </done>
</task>

</tasks>

<verification>
1. `./gradlew :app:assembleDebug` completes with no errors
2. `./gradlew :app:testDebugUnitTest --tests "dev.pivisolutions.dictus.onboarding.OnboardingViewModelTest"` — all tests pass
3. On a device/emulator: launch app, go through onboarding to step 5, verify the model card shows the correct recommended model for the device's RAM
</verification>

<success_criteria>
- Onboarding step 5 dynamically shows the recommended model based on device RAM
- Device with >= 8 GB RAM sees "Parakeet 0.6B" with "~640 MB" and "Premium" badge
- Device with < 8 GB RAM sees "Small Q5" with "~190 MB" and "Precis" badge
- Download starts the correct model (not always tiny)
- ACTIVE_MODEL preference is set to the recommended model key after onboarding
- Parakeet download shows extraction UI during the archive extraction phase
- All existing tests pass with updated constructor
</success_criteria>

<output>
After completion, create `.planning/quick/260401-jdw-dynamic-recommended-model-in-onboarding-/260401-jdw-SUMMARY.md`
</output>
