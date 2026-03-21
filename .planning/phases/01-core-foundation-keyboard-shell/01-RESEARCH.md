# Phase 1: Core Foundation + Keyboard Shell - Research

**Researched:** 2026-03-21
**Domain:** Android IME (InputMethodService) with Jetpack Compose, multi-module Gradle project, branded dark theme
**Confidence:** HIGH

## Summary

Phase 1 establishes the foundational Android project: a multi-module Gradle build (`app`, `ime`, `core`), a custom keyboard rendered via Jetpack Compose inside an `InputMethodService`, full AZERTY/QWERTY layouts with accented character long-press popups, text insertion via `InputConnection`, a branded Dictus dark theme using Material 3, and structured logging via Timber. No audio, no dictation, no whisper -- just a fully functional typing keyboard.

The primary technical risk is Compose-in-IME lifecycle wiring. `InputMethodService` is not a standard `ComponentActivity`, so Compose requires manual `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, and `ViewTreeSavedStateRegistryOwner` setup. FlorisBoard's `LifecycleInputMethodService` pattern and the community `ComposedInputMethodService` gist provide proven solutions. The keyboard layout data can be ported directly from the iOS `KeyboardLayout.swift` model (4 layers: letters-AZERTY, letters-QWERTY, numbers, symbols).

**Primary recommendation:** Build a custom `LifecycleInputMethodService` base class that wires Compose lifecycle owners, then define keyboard layouts as pure Kotlin data (ported from iOS Swift models). Use Hilt with `@EntryPoint` / `EntryPointAccessors` for DI in the IME service. Use AGP 8.x (not 9.x) for first-time Android dev stability.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Use an existing open-source Android keyboard project as a foundation/reference (e.g., FlorisBoard, Simple Keyboard, AOSP LatinIME) -- avoid reinventing layout logic, long-press handling, key sizing
- Accented characters via popup bubble on long-press (standard Android convention, like Gboard)
- Mic button in a dedicated row below the keyboard (like iOS Dictus) -- mic is non-functional in Phase 1, just a placeholder
- Key sizes match Gboard proportions for familiarity
- Minimum layers: Letters (AZERTY/QWERTY with shift/caps) + Numbers/Symbols (?123). Take whatever layers the open-source base provides
- Keyboard switcher via standard Android method (long-press spacebar or dedicated button triggers InputMethodManager.showInputMethodPicker())
- Dictus branded dark theme with exact iOS color tokens (#0A1628 bg, #3D7EFF accent, #161C2C surface, etc.) applied via Material 3 surfaces and elevation
- System default font (Roboto) for keyboard keys -- no custom font
- Gboard-style rounded rectangle keys with subtle elevation/shadow on dark background
- 3 modules from the start: `app`, `ime`, `core` (whisper module added in Phase 3)
- Package name: `dev.pivisolutions.dictus`
- Dependency injection: Hilt (note: IME requires EntryPointAccessors, not @AndroidEntryPoint)
- Kotlin + Jetpack Compose
- Gradle Kotlin DSL
- Min SDK 29 (Android 10)
- Keyboard height matches Gboard proportions (~40% of screen) + mic button row below
- Standard Android keyboard switcher behavior
- Plan includes a setup task: install Android Studio, configure SDK 29+, create Pixel 4 emulator
- Testing strategy: emulator for fast iteration + physical Pixel 4 for real IME/input testing
- Physical device: Pixel 4 running LineageOS -- fully compatible, needs USB debugging enabled
- First time Android native dev -- plans should include clear setup instructions

### Claude's Discretion
- Number row (top persistent number row) -- Claude decides based on open-source keyboard base default
- Choice of which open-source keyboard to use as reference/base -- Claude researches and picks best fit
- Exact keyboard height in dp
- Key spacing and padding values
- Timber logging configuration details
- Hilt module organization

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| KBD-01 | User can type on a full AZERTY keyboard layout with shift, caps lock, numbers, and symbols | iOS KeyboardLayout.swift provides exact layout data to port; Compose keyboard rendering pattern from FlorisBoard/community examples |
| KBD-02 | User can switch between AZERTY and QWERTY layouts | Layout selection stored in DataStore preferences, dynamic row switching (same pattern as iOS `currentLettersRows()`) |
| KBD-03 | User can access accented characters via long-press (e, e, e, a, u, c, etc.) | iOS accent map provides character data; Android long-press gesture + popup Composable pattern |
| KBD-06 | User can insert text into any app via InputConnection | `InputConnection.commitText()` is the standard IME text insertion API; lifecycle-aware access via companion object pattern (FlorisBoard) |
| DSG-01 | App uses a branded dark theme matching Dictus iOS colors by default | Material 3 custom `ColorScheme` with exact iOS hex tokens (#0A1628, #3D7EFF, #161C2C, etc.) |
| APP-06 | Structured logging via Timber throughout the app | Timber DebugTree in Application.onCreate(); Timber.d/i/w/e calls throughout; tag auto-detection |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.x (stable) | Language | Current stable; avoid 2.3.x bleeding edge for first project |
| Jetpack Compose BOM | 2025.12.00 or 2026.03.00 | UI framework | Official BOM manages all Compose library versions |
| Android Gradle Plugin | 8.7.x or 8.8.x | Build system | AGP 9.x has breaking changes; 8.x is stable and well-documented for beginners |
| Hilt | 2.57.1 | Dependency injection | Google-recommended DI for Android; KSP support |
| KSP | matching Kotlin version | Annotation processing | Replaces KAPT, 2x faster |
| Timber | 5.0.1 | Logging | Jake Wharton's standard Android logger; auto-tag, tree-based |
| Material 3 | via Compose BOM | Design system | Material Design 3 theming, surfaces, color schemes |
| DataStore Preferences | 1.1.x | Key-value storage | Type-safe replacement for SharedPreferences |
| AndroidX Lifecycle | via Compose BOM | Lifecycle management | Required for Compose-in-IME lifecycle wiring |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AndroidX Core KTX | latest stable | Kotlin extensions | Always -- idiomatic Kotlin for Android APIs |
| Activity Compose | via BOM | Compose integration | Main Activity hosting Compose content |
| Navigation Compose | via BOM | Navigation | If app module needs multi-screen navigation (minimal in Phase 1) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hilt | Koin | Koin is simpler but lacks compile-time safety; Hilt is project decision |
| AGP 9.x | AGP 8.x | AGP 9 has built-in Kotlin support but breaking changes; 8.x safer for first project |
| DataStore | SharedPreferences | SharedPreferences works but DataStore is type-safe and coroutine-friendly |

### Open-Source Keyboard Reference: Recommendation

**Use FlorisBoard as primary architectural reference, not as a code fork.**

| Project | Pros | Cons | Verdict |
|---------|------|------|---------|
| FlorisBoard | Kotlin, Compose-based, full AZERTY support, active (v0.5.2 Nov 2025), MIT-adjacent license, `LifecycleInputMethodService` pattern | Complex codebase (~100+ files), custom extension system, heavy abstractions | **Reference for architecture patterns** |
| Simple Keyboard (rkkr) | Minimal, AOSP LatinIME-based, easy to understand | Java, XML views (not Compose), no long-press accents out of box | Reference for AOSP IME basics only |
| AOSP LatinIME | Canonical Android keyboard | Massive codebase, C++ components, not suitable as reference | Too complex |

**Decision: Don't fork any project.** Instead, build from scratch using:
1. FlorisBoard's `LifecycleInputMethodService` pattern for Compose lifecycle wiring
2. The community `ComposedInputMethodService` gist for the minimal base class
3. iOS Dictus `KeyboardLayout.swift` data ported to Kotlin for exact layout parity

**No number row** -- neither Gboard nor the iOS Dictus keyboard has a persistent top number row. Numbers are accessed via the `?123` layer switch, which is the standard convention. This keeps the keyboard compact.

**Installation:**
```bash
# No CLI install -- Android Studio project creation
# Key dependencies in libs.versions.toml (version catalog)
```

## Architecture Patterns

### Recommended Project Structure
```
dictus-android/
├── app/                              # Main app module
│   ├── src/main/
│   │   ├── java/dev/pivisolutions/dictus/
│   │   │   ├── DictusApplication.kt  # @HiltAndroidApp, Timber init
│   │   │   ├── MainActivity.kt       # Minimal: "Enable IME" instructions
│   │   │   └── di/                   # Hilt modules for app scope
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── ime/                              # IME module (keyboard)
│   ├── src/main/
│   │   ├── java/dev/pivisolutions/dictus/ime/
│   │   │   ├── DictusImeService.kt   # InputMethodService + Compose lifecycle
│   │   │   ├── di/                   # @EntryPoint interface for Hilt
│   │   │   ├── ui/
│   │   │   │   ├── KeyboardScreen.kt # Root Composable for keyboard
│   │   │   │   ├── KeyboardView.kt   # Full keyboard with layer switching
│   │   │   │   ├── KeyRow.kt         # Row of keys
│   │   │   │   ├── KeyButton.kt      # Individual key Composable
│   │   │   │   ├── AccentPopup.kt    # Long-press accent popup
│   │   │   │   └── MicButtonRow.kt   # Placeholder mic row below keyboard
│   │   │   └── model/
│   │   │       ├── KeyDefinition.kt  # Key data model (ported from iOS)
│   │   │       ├── KeyType.kt        # Enum: character, shift, delete, etc.
│   │   │       ├── KeyboardLayout.kt # AZERTY/QWERTY/Numbers/Symbols data
│   │   │       ├── KeyboardLayer.kt  # Layer type enum
│   │   │       └── AccentMap.kt      # Long-press accent variants
│   │   ├── res/xml/
│   │   │   └── method.xml            # IME declaration (subtypes, etc.)
│   │   └── AndroidManifest.xml       # Service declaration + BIND_INPUT_METHOD
│   └── build.gradle.kts
│
├── core/                             # Shared module
│   ├── src/main/
│   │   ├── java/dev/pivisolutions/dictus/core/
│   │   │   ├── theme/
│   │   │   │   ├── DictusTheme.kt    # Material 3 theme wrapper
│   │   │   │   ├── DictusColors.kt   # Brand color tokens
│   │   │   │   └── DictusTypography.kt
│   │   │   ├── logging/
│   │   │   │   └── TimberSetup.kt    # Timber tree planting
│   │   │   └── preferences/
│   │   │       └── PreferenceKeys.kt # DataStore key constants
│   │   └── res/values/
│   │       ├── colors.xml            # XML color resources (for manifest, etc.)
│   │       └── strings.xml           # Shared strings
│   └── build.gradle.kts
│
├── gradle/
│   └── libs.versions.toml            # Version catalog
├── build.gradle.kts                  # Root build file
├── settings.gradle.kts               # Module includes
└── gradle.properties
```

### Pattern 1: Compose-in-InputMethodService (LifecycleInputMethodService)

**What:** A base class that makes `InputMethodService` lifecycle-aware so Compose can run inside it.
**When to use:** Always -- this is the foundation for the entire keyboard UI.
**Example:**
```kotlin
// Source: Community gist + FlorisBoard pattern
// https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff

abstract class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)
            )
            setContent { KeyboardContent() }
        }

        // Wire view tree owners to the IME window
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        return view
    }

    @Composable
    abstract fun KeyboardContent()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
```

### Pattern 2: Hilt DI in InputMethodService via @EntryPoint

**What:** Since `InputMethodService` cannot use `@AndroidEntryPoint`, use `@EntryPoint` + `EntryPointAccessors` to get dependencies.
**When to use:** Whenever the IME service needs injected dependencies (preferences, loggers, etc.).
**Example:**
```kotlin
// Source: https://dagger.dev/hilt/entry-points.html

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DictusImeEntryPoint {
    fun preferencesRepository(): PreferencesRepository
    // Add more dependencies as needed
}

// In DictusImeService:
private val entryPoint: DictusImeEntryPoint by lazy {
    EntryPointAccessors.fromApplication(
        applicationContext,
        DictusImeEntryPoint::class.java
    )
}
```

### Pattern 3: Keyboard Layout as Pure Data

**What:** Define keyboard layouts as static Kotlin data objects (ported from iOS), not XML.
**When to use:** For all keyboard layer definitions.
**Example:**
```kotlin
// Ported from iOS DictusKeyboard/Models/KeyboardLayout.swift

object KeyboardLayouts {
    val azertyLetters: List<List<KeyDefinition>> = listOf(
        // Row 1
        listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        // Row 2
        listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        // Row 3 with shift, adaptive accent, delete
        listOf(
            KeyDefinition("shift", type = KeyType.SHIFT, widthMultiplier = 1.5f),
            KeyDefinition("W", output = "w"),
            KeyDefinition("X", output = "x"),
            KeyDefinition("C", output = "c"),
            KeyDefinition("V", output = "v"),
            KeyDefinition("B", output = "b"),
            KeyDefinition("N", output = "n"),
            KeyDefinition("'", type = KeyType.ACCENT_ADAPTIVE),
            KeyDefinition("delete", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        // Row 4
        listOf(
            KeyDefinition("123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.2f),
            KeyDefinition("emoji", type = KeyType.EMOJI, widthMultiplier = 1.2f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.5f),
            KeyDefinition("return", type = KeyType.RETURN, widthMultiplier = 1.8f),
        ),
    )
    // ... qwertyLetters, numbersRows, symbolsRows follow same pattern
}
```

### Pattern 4: Text Insertion via InputConnection

**What:** Use `currentInputConnection` to insert, delete, and manipulate text in the active app's text field.
**When to use:** Every time a key is pressed.
**Example:**
```kotlin
// In DictusImeService or a helper class:
fun commitText(text: String) {
    currentInputConnection?.commitText(text, 1) // 1 = cursor moves after text
}

fun deleteBackward() {
    currentInputConnection?.deleteSurroundingText(1, 0)
}

fun sendKeyEvent(keyCode: Int) {
    currentInputConnection?.sendKeyEvent(
        KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    )
    currentInputConnection?.sendKeyEvent(
        KeyEvent(KeyEvent.ACTION_UP, keyCode)
    )
}
```

### Pattern 5: IME Manifest Declaration

**What:** The IME service must be declared in AndroidManifest.xml with specific permissions and metadata.
**When to use:** Required for Android to recognize the keyboard.
**Example:**
```xml
<!-- ime/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <service
            android:name=".ime.DictusImeService"
            android:label="@string/ime_name"
            android:permission="android.permission.BIND_INPUT_METHOD"
            android:exported="true">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
    </application>
</manifest>

<!-- ime/src/main/res/xml/method.xml -->
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="dev.pivisolutions.dictus.MainActivity">
    <subtype
        android:label="@string/subtype_azerty"
        android:imeSubtypeLocale="fr_FR"
        android:imeSubtypeMode="keyboard" />
    <subtype
        android:label="@string/subtype_qwerty"
        android:imeSubtypeLocale="en_US"
        android:imeSubtypeMode="keyboard" />
</input-method>
```

### Anti-Patterns to Avoid
- **XML-based keyboard views:** Don't use traditional `KeyboardView` or XML-defined keyboard layouts. The project uses Compose exclusively.
- **@AndroidEntryPoint on InputMethodService:** This will crash. Use `@EntryPoint` + `EntryPointAccessors` instead.
- **Forking an open-source keyboard:** Brings massive complexity and maintenance burden. Use as reference only.
- **Heavy computation in IME thread:** Never block `InputMethodService` callbacks. All key processing must be fast.
- **Using KAPT instead of KSP:** KAPT is deprecated and 2x slower. Use KSP for Hilt annotation processing.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Compose lifecycle in IME | Custom lifecycle hacks | `LifecycleInputMethodService` base class pattern | Requires correct `ViewTreeOwner` wiring; community-proven pattern |
| Logging | Custom Log wrapper | Timber | Auto-tag, tree-based, release-safe, 1 line setup |
| DI | Manual singletons | Hilt + @EntryPoint | Compile-time checked, scope-aware |
| Key-value storage | SharedPreferences | DataStore Preferences | Type-safe, coroutine-friendly, no ANR risk |
| Color theming | Hardcoded colors | Material 3 ColorScheme | System integration, elevation tints, dark/light support |
| Keyboard layout data | Runtime-generated layouts | Static Kotlin data objects | Layouts are constant; runtime generation wastes cycles |

**Key insight:** The hardest custom code in this phase is the `LifecycleInputMethodService` base class. Everything else either comes from libraries or is straightforward Compose UI work.

## Common Pitfalls

### Pitfall 1: Compose Crashes in InputMethodService
**What goes wrong:** `IllegalStateException: Composed into a View which doesn't propagate ViewTreeLifecycleOwner`
**Why it happens:** `InputMethodService` doesn't implement `LifecycleOwner` by default. `ComposeView` requires lifecycle owners set on the view tree.
**How to avoid:** Use the `LifecycleInputMethodService` base class that sets `ViewTreeLifecycleOwner`, `ViewTreeViewModelStoreOwner`, and `ViewTreeSavedStateRegistryOwner` on the window's `decorView`.
**Warning signs:** App crashes immediately when switching to Dictus keyboard.

### Pitfall 2: Keyboard Height Not Set Correctly
**What goes wrong:** Keyboard renders too small, too tall, or overlaps content.
**Why it happens:** Android IMEs don't auto-size. The height must be explicitly set via `InputMethodService.setInputView()` or by controlling the Compose layout constraints.
**How to avoid:** Calculate height as a percentage of screen height (Gboard uses ~40%). Set explicit height on the root Composable. Account for the mic button row below.
**Warning signs:** Keyboard looks wrong on different screen sizes or orientations.

### Pitfall 3: InputConnection is Null
**What goes wrong:** `currentInputConnection` returns null, text doesn't appear.
**Why it happens:** The connection is only valid between `onStartInputView()` and `onFinishInputView()`. Accessing it outside this window or after the editor closes returns null.
**How to avoid:** Always null-check `currentInputConnection`. Don't cache it -- always access via `currentInputConnection` property.
**Warning signs:** Text insertion works sometimes but not always.

### Pitfall 4: Hilt @AndroidEntryPoint on Service
**What goes wrong:** Build error or runtime crash when using `@AndroidEntryPoint` on `InputMethodService`.
**Why it happens:** Hilt's `@AndroidEntryPoint` only supports `Activity`, `Fragment`, `View`, `Service` (standard), and `BroadcastReceiver`. `InputMethodService` has a different initialization path.
**How to avoid:** Use `@EntryPoint` interface + `EntryPointAccessors.fromApplication()`.
**Warning signs:** Hilt compilation errors mentioning unsupported base class.

### Pitfall 5: IME Not Appearing in Settings
**What goes wrong:** After installation, the keyboard doesn't appear in Settings > Languages & Input.
**Why it happens:** Missing or incorrect `AndroidManifest.xml` declaration: wrong permission, missing `intent-filter`, or missing `meta-data` pointing to `method.xml`.
**How to avoid:** Follow the exact manifest pattern (Pattern 5 above). Verify `android.permission.BIND_INPUT_METHOD` permission and `android.view.InputMethod` action.
**Warning signs:** App installs but keyboard isn't listed in system settings.

### Pitfall 6: Multi-Module Hilt Configuration
**What goes wrong:** Hilt can't find modules or entry points across Gradle modules.
**Why it happens:** The `app` module must transitively depend on all modules that use Hilt. Hilt code generation needs access to all annotated classes.
**How to avoid:** Ensure `app` depends on `ime` and `core`. Apply the Hilt plugin in all modules that use `@Inject` or `@EntryPoint`. Use `SingletonComponent` for cross-module dependencies.
**Warning signs:** `ClassNotFoundException` for Hilt-generated classes at runtime.

### Pitfall 7: Long-Press Gesture Conflicts
**What goes wrong:** Long-press for accents interferes with key repeat (holding backspace) or regular taps.
**Why it happens:** Compose gesture detection for `pointerInput` can conflict between tap, long-press, and drag.
**How to avoid:** Use `detectTapGestures(onLongPress = ...)` for accent-bearing keys. For delete key, use separate repeat logic with `repeatWhile` coroutine. Don't mix `clickable` and `pointerInput` on the same element.
**Warning signs:** Accents appear when tapping quickly, or backspace doesn't repeat.

## Code Examples

### Dictus Dark Theme (Material 3)
```kotlin
// Source: PRD.md color palette mapping
// core/src/main/java/.../theme/DictusColors.kt

object DictusColors {
    val Background = Color(0xFF0A1628)
    val Accent = Color(0xFF3D7EFF)
    val AccentHighlight = Color(0xFF6BA3FF)
    val Surface = Color(0xFF161C2C)
    val Recording = Color(0xFFEF4444)
    val SmartMode = Color(0xFF8B5CF6)
    val Success = Color(0xFF22C55E)
    val OnBackground = Color.White
    val OnSurface = Color(0xFFE0E0E0)
    val KeyBackground = Color(0xFF1E2538)  // Slightly lighter than surface
    val KeyText = Color.White
}

// core/src/main/java/.../theme/DictusTheme.kt
private val DictusDarkColorScheme = darkColorScheme(
    primary = DictusColors.Accent,
    onPrimary = Color.White,
    primaryContainer = DictusColors.AccentHighlight,
    background = DictusColors.Background,
    surface = DictusColors.Surface,
    onBackground = DictusColors.OnBackground,
    onSurface = DictusColors.OnSurface,
    error = DictusColors.Recording,
    tertiary = DictusColors.SmartMode,
)

@Composable
fun DictusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DictusDarkColorScheme,
        typography = DictusTypography,
        content = content,
    )
}
```

### Accent Map (Ported from iOS)
```kotlin
// Source: iOS KEYBOARD_BRIEFING.md accent map
// ime/src/main/java/.../model/AccentMap.kt

object AccentMap {
    private val map: Map<Char, List<String>> = mapOf(
        'e' to listOf("e", "e", "e", "e", "e"),
        'a' to listOf("a", "a", "a", "ae"),
        'u' to listOf("u", "u", "u"),
        'i' to listOf("i", "i"),
        'o' to listOf("o", "o", "oe"),
        'c' to listOf("c"),
        // Uppercase
        'E' to listOf("E", "E", "E", "E"),
        'A' to listOf("A", "A", "A", "AE"),
        'U' to listOf("U", "U", "U"),
        'I' to listOf("I", "I"),
        'O' to listOf("O", "O", "OE"),
        'C' to listOf("C"),
    )

    fun accentsFor(char: Char): List<String>? = map[char]
    fun hasAccents(char: Char): Boolean = map.containsKey(char)
}
```

**Note:** The accent characters above are placeholders -- the actual accented Unicode characters (e, e, e, a, c, etc.) must be used in implementation. The iOS source provides the exact characters.

### Timber Setup
```kotlin
// Source: Timber GitHub README
// core/src/main/java/.../logging/TimberSetup.kt

object TimberSetup {
    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        // Release tree can be added later for crash reporting
    }
}

// In DictusApplication.kt:
@HiltAndroidApp
class DictusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TimberSetup.init(BuildConfig.DEBUG)
        Timber.d("Dictus application started")
    }
}
```

### Version Catalog (libs.versions.toml)
```toml
[versions]
kotlin = "2.1.10"
agp = "8.7.3"
compose-bom = "2026.03.00"
hilt = "2.57.1"
ksp = "2.1.10-1.0.31"
timber = "5.0.1"
datastore = "1.1.3"
lifecycle = "2.8.7"
core-ktx = "1.15.0"
activity-compose = "1.9.3"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
savedstate = { group = "androidx.savedstate", name = "savedstate", version = "1.2.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| KAPT for Hilt | KSP for Hilt | Hilt 2.48+ (2023) | 2x faster builds, better Kotlin support |
| SharedPreferences | DataStore Preferences | 2021+ | Type-safe, coroutine-friendly, no ANR on main thread |
| XML keyboard layouts | Compose-based keyboard rendering | 2023+ (FlorisBoard migration) | Declarative UI, easier theming, state management |
| `ComposeView` + manual lifecycle | `LifecycleInputMethodService` pattern | 2022+ (community) | Reliable Compose lifecycle in IME context |
| AGP 8.x | AGP 9.x available (March 2026) | January 2026 | Breaking changes; 8.x recommended for new projects |

**Deprecated/outdated:**
- `KeyboardView` (android.inputmethodservice.KeyboardView): Deprecated, XML-based, don't use
- KAPT: Deprecated in favor of KSP for annotation processing
- `buildSrc` for version management: Use Gradle version catalogs instead

## Open Questions

1. **Exact keyboard height in dp**
   - What we know: Gboard uses ~40% of screen height. Pixel 4 screen is 2280x1080 at ~3.0 density = ~760dp height. 40% = ~304dp for keyboard + mic row.
   - What's unclear: Exact breakdown between letter area and mic button row. Landscape mode dimensions.
   - Recommendation: Start with 280dp for keyboard + 56dp for mic row = 336dp total. Test on emulator and adjust.

2. **Key spacing and padding**
   - What we know: Gboard uses approximately 4dp horizontal gap and 8dp vertical gap between keys.
   - What's unclear: Exact values vary by device density and keyboard width.
   - Recommendation: Start with 4dp horizontal, 6dp vertical spacing. Key corner radius 8dp. Adjust during visual testing.

3. **AGP version selection**
   - What we know: AGP 9.1 is latest (March 2026) with built-in Kotlin support. AGP 8.7.x is stable.
   - What's unclear: Whether AGP 9's breaking changes are significant for this project.
   - Recommendation: Use AGP 8.7.x for stability. Pierre is a first-time Android dev; AGP 9 migration guides assume familiarity. Can upgrade later.

4. **Compose BOM version**
   - What we know: 2026.03.00 is latest. 2025.12.00 maps to Compose 1.10.x (stable).
   - What's unclear: Whether 2026.03.00 is fully stable or has regressions.
   - Recommendation: Use 2026.03.00 (latest stable BOM). If issues arise, fall back to 2025.12.00.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Compose UI Testing (via BOM) |
| Config file | None -- Wave 0 setup needed |
| Quick run command | `./gradlew :ime:testDebugUnitTest` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| KBD-01 | AZERTY layout has correct rows/keys | unit | `./gradlew :ime:testDebugUnitTest --tests "*KeyboardLayoutTest*" -x` | Wave 0 |
| KBD-02 | Layout switching AZERTY/QWERTY | unit | `./gradlew :ime:testDebugUnitTest --tests "*LayoutSwitchTest*" -x` | Wave 0 |
| KBD-03 | Accent map returns correct variants | unit | `./gradlew :ime:testDebugUnitTest --tests "*AccentMapTest*" -x` | Wave 0 |
| KBD-06 | Text insertion via InputConnection | manual-only | Manual: type in emulator, verify text appears | Justification: requires running IME in Android system |
| DSG-01 | Dark theme colors match spec | unit | `./gradlew :core:testDebugUnitTest --tests "*DictusColorsTest*" -x` | Wave 0 |
| APP-06 | Timber is initialized | unit | `./gradlew :app:testDebugUnitTest --tests "*TimberSetupTest*" -x` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :ime:testDebugUnitTest :core:testDebugUnitTest`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full suite green + manual IME test on emulator

### Wave 0 Gaps
- [ ] `ime/src/test/java/.../model/KeyboardLayoutTest.kt` -- covers KBD-01, KBD-02
- [ ] `ime/src/test/java/.../model/AccentMapTest.kt` -- covers KBD-03
- [ ] `core/src/test/java/.../theme/DictusColorsTest.kt` -- covers DSG-01
- [ ] `app/src/test/java/.../TimberSetupTest.kt` -- covers APP-06
- [ ] JUnit 4 dependency in version catalog
- [ ] Compose UI test dependency in version catalog (for future phases)

## Sources

### Primary (HIGH confidence)
- FlorisBoard `FlorisImeService.kt` source code -- Compose-in-IME architecture pattern (https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt)
- Community `ComposedInputMethodService` gist -- minimal lifecycle wiring (https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff)
- Hilt Entry Points official docs (https://dagger.dev/hilt/entry-points.html)
- Android official IME documentation (https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- iOS Dictus source code -- keyboard layout data, accent map, color tokens (~/dev/dictus/)
- Jetpack Compose BOM page (https://developer.android.com/develop/ui/compose/bom)

### Secondary (MEDIUM confidence)
- Timber GitHub repository -- setup patterns (https://github.com/JakeWharton/timber)
- AGP 9.1 release notes (https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- Kotlin 2.3.20 release blog (https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/)
- Hilt multi-module docs (https://developer.android.com/training/dependency-injection/hilt-multi-module)

### Tertiary (LOW confidence)
- Keyboard height proportions (~40% of screen) -- based on general community observation, not official Gboard documentation
- Key spacing values (4dp/6dp) -- estimated from screenshots, not official specification

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all versions verified via official sources and release pages
- Architecture: HIGH -- Compose-in-IME pattern verified via FlorisBoard source code and community gist; iOS layout data available for porting
- Pitfalls: HIGH -- well-documented issues with Compose lifecycle in IME; verified via multiple community sources
- Keyboard dimensions: LOW -- Gboard proportions are estimates, will need empirical testing

**Research date:** 2026-03-21
**Valid until:** 2026-04-21 (30 days -- stable domain, no fast-moving dependencies)
