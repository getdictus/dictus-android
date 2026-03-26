package dev.pivisolutions.dictus.navigation

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.home.HomeScreen
import dev.pivisolutions.dictus.models.ModelsScreen
import dev.pivisolutions.dictus.onboarding.OnboardingKeyboardSetupScreen
import dev.pivisolutions.dictus.onboarding.OnboardingMicPermissionScreen
import dev.pivisolutions.dictus.onboarding.OnboardingModeSelectionScreen
import dev.pivisolutions.dictus.onboarding.OnboardingModelDownloadScreen
import dev.pivisolutions.dictus.onboarding.OnboardingSuccessScreen
import dev.pivisolutions.dictus.onboarding.OnboardingViewModel
import dev.pivisolutions.dictus.onboarding.OnboardingWelcomeScreen
import dev.pivisolutions.dictus.ui.navigation.DictusBottomNavBar
import dev.pivisolutions.dictus.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map

/**
 * Root navigation host for the Dictus app.
 *
 * Acts as an onboarding gate: reads `HAS_COMPLETED_ONBOARDING` from DataStore
 * and shows either the onboarding flow (Plan 03) or the main tab layout.
 *
 * WHY tri-state loading (null/false/true): DataStore reads are asynchronous. Using
 * `collectAsState(initial = null)` gives us three states:
 * - null:  DataStore not yet read — show blank/splash to avoid flicker
 * - false: Onboarding not complete — show OnboardingPlaceholder (Plan 03 will replace)
 * - true:  Onboarding complete — show main tabs
 *
 * WHY DataStore here (not ViewModel): AppNavHost is the structural root; the
 * onboarding gate decision must be made before any tab ViewModel is created.
 * Reading DataStore directly in the root composable is the cleanest pattern
 * for a single boolean gate.
 *
 * @param dataStore Application-scoped DataStore for persistent preferences.
 * @param dictationController Service controller for dictation (may be null if not yet bound).
 * @param imeEnabled Whether the Dictus keyboard is enabled in system settings.
 * @param imeSelected Whether the Dictus keyboard is selected as the default input method.
 * @param onOpenKeyboardSettings Callback to open system keyboard settings.
 * @param onOpenAppSettings Callback to open app details settings.
 */
@Composable
fun AppNavHost(
    dataStore: DataStore<Preferences>,
    dictationController: DictationController?,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val hasCompletedOnboarding by dataStore.data
        .map { it[PreferenceKeys.HAS_COMPLETED_ONBOARDING] ?: false }
        .collectAsState(initial = null)

    when (hasCompletedOnboarding) {
        null -> {
            // DataStore not yet read — show blank screen to avoid flash
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DictusColors.Background)
            )
        }
        false -> {
            // Onboarding not complete — show the full 6-step onboarding flow
            OnboardingScreen()
        }
        true -> {
            MainTabsScreen(
                dataStore = dataStore,
                dictationController = dictationController,
                imeEnabled = imeEnabled,
                imeSelected = imeSelected,
                onOpenKeyboardSettings = onOpenKeyboardSettings,
                onOpenAppSettings = onOpenAppSettings,
            )
        }
    }
}

/**
 * Full 6-step onboarding flow.
 *
 * Hosts an [OnboardingViewModel] via hiltViewModel() and switches between step screens
 * using a simple when(currentStep) expression. Each screen receives the ViewModel's
 * current state and delegates actions back via callbacks.
 *
 * Step 6's "Commencer" tap calls viewModel.advanceStep(), which writes
 * HAS_COMPLETED_ONBOARDING=true to DataStore. AppNavHost's collectAsState() on that
 * key triggers recomposition and switches to MainTabsScreen automatically.
 *
 * WHY when(step) (not AnimatedContent or NavHost): The step count is small (6) and
 * the transitions are purely sequential. A simple when keeps the code easy to read and
 * avoids navigation graph complexity for a one-time, non-navigable flow.
 *
 * WHY LaunchedEffect for IME check: IME status must be re-checked when the user returns
 * from system settings. The context and imeEnabled/imeSelected values from MainActivity
 * are not directly available here, so we check via the Settings.Secure API on step 3.
 */
@Composable
private fun OnboardingScreen() {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val context = LocalContext.current

    val currentStep by viewModel.currentStep.collectAsState()
    val micGranted by viewModel.micPermissionGranted.collectAsState()
    val imeActivated by viewModel.imeActivated.collectAsState()
    val selectedLayout by viewModel.selectedLayout.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadComplete by viewModel.modelDownloadComplete.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()

    // Re-check IME status whenever we are on step 3 so returning from Settings
    // updates the imeActivated flag without needing a button tap.
    LaunchedEffect(currentStep) {
        if (currentStep == 3) {
            val enabledInputMethods = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
            ) ?: ""
            val dictusPackage = context.packageName
            val isEnabled = enabledInputMethods.contains(dictusPackage)
            viewModel.setImeActivated(isEnabled)
        }
    }

    when (currentStep) {
        1 -> OnboardingWelcomeScreen(
            onNext = { viewModel.advanceStep() },
        )
        2 -> OnboardingMicPermissionScreen(
            micGranted = micGranted,
            onPermissionResult = { viewModel.setMicPermissionGranted(it) },
            onNext = { viewModel.advanceStep() },
        )
        3 -> OnboardingKeyboardSetupScreen(
            imeActivated = imeActivated,
            onOpenSettings = {
                val intent = android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            onNext = { viewModel.advanceStep() },
        )
        4 -> OnboardingModeSelectionScreen(
            selectedLayout = selectedLayout,
            onSelectLayout = { viewModel.setLayout(it) },
            onNext = { viewModel.advanceStep() },
        )
        5 -> OnboardingModelDownloadScreen(
            downloadProgress = downloadProgress,
            downloadComplete = downloadComplete,
            downloadError = downloadError,
            onStartDownload = { viewModel.startModelDownload() },
            onRetry = { viewModel.retryDownload() },
            onNext = { viewModel.advanceStep() },
        )
        6 -> OnboardingSuccessScreen(
            onComplete = { viewModel.advanceStep() },
        )
    }
}

/**
 * Main 3-tab layout with bottom navigation.
 *
 * Uses Scaffold to place DictusBottomNavBar as the persistent bottom bar.
 * Each tab preserves its scroll state via saveState = true on navigation.
 *
 * WHY popUpTo + saveState: Without this, tapping a tab creates a new back-stack
 * entry every time. popUpTo clears intermediate destinations and saveState restores
 * the tab's previous scroll position when the user returns to it.
 */
@Composable
private fun MainTabsScreen(
    dataStore: DataStore<Preferences>,
    dictationController: DictationController?,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.Home.route

    Scaffold(
        containerColor = DictusColors.Background,
        bottomBar = {
            DictusBottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(AppDestination.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    dataStore = dataStore,
                    onNewDictation = { /* Wiring for future recording screen */ },
                )
            }
            composable(AppDestination.Models.route) {
                ModelsScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
