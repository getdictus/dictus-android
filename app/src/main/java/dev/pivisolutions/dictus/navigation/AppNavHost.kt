package dev.pivisolutions.dictus.navigation

import androidx.compose.foundation.background
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import androidx.compose.material3.MaterialTheme
import dev.pivisolutions.dictus.home.HomeScreen
import dev.pivisolutions.dictus.core.premium.PremiumFlags
import dev.pivisolutions.dictus.history.HistoryRoute
import dev.pivisolutions.dictus.models.ModelsScreen
import dev.pivisolutions.dictus.onboarding.OnboardingKeyboardSetupScreen
import dev.pivisolutions.dictus.recording.RecordingScreen
import dev.pivisolutions.dictus.onboarding.OnboardingMicPermissionScreen
import dev.pivisolutions.dictus.onboarding.OnboardingModeSelectionScreen
import dev.pivisolutions.dictus.onboarding.OnboardingModelDownloadScreen
import dev.pivisolutions.dictus.onboarding.OnboardingSuccessScreen
import dev.pivisolutions.dictus.onboarding.OnboardingTestRecordingScreen
import dev.pivisolutions.dictus.onboarding.OnboardingViewModel
import dev.pivisolutions.dictus.onboarding.OnboardingWelcomeScreen
import dev.pivisolutions.dictus.ui.navigation.DictusBottomNavBar
import dev.pivisolutions.dictus.ui.settings.DebugLogsScreen
import dev.pivisolutions.dictus.ui.settings.LicencesScreen
import dev.pivisolutions.dictus.ui.settings.SettingsScreen
import dev.pivisolutions.dictus.ui.settings.SoundPickerScreen
import dev.pivisolutions.dictus.ui.settings.SoundSettingsScreen
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
    onShowKeyboardPicker: () -> Unit,
    onOpenAppSettings: () -> Unit,
    openSettingsRequested: Boolean = false,
) {
    val hasCompletedOnboarding by dataStore.data
        .map { it[PreferenceKeys.HAS_COMPLETED_ONBOARDING] ?: false }
        .collectAsState(initial = null)

    if (openSettingsRequested) {
        // The IME entry point intentionally has no bottom navigation. This allows Settings and
        // its child screens without exposing Home/Models or mutating onboarding completion.
        MainTabsScreen(
            dataStore = dataStore,
            dictationController = dictationController,
            imeEnabled = imeEnabled,
            imeSelected = imeSelected,
            onOpenKeyboardSettings = onOpenKeyboardSettings,
            onOpenAppSettings = onOpenAppSettings,
            startDestination = AppDestination.Settings.route,
            showMainNavigation = false,
        )
        return
    }

    when (hasCompletedOnboarding) {
        null -> {
            // DataStore not yet read — show blank screen to avoid flash
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        false -> {
            // Onboarding not complete — show the full 7-step onboarding flow
            OnboardingScreen(
                dictationController = dictationController,
                imeEnabled = imeEnabled,
                imeSelected = imeSelected,
                onOpenKeyboardSettings = onOpenKeyboardSettings,
                onShowKeyboardPicker = onShowKeyboardPicker,
            )
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
 * Full 7-step onboarding flow.
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
 * IME state comes from MainActivity, which re-reads Android's system state after settings
 * and picker round trips. The system remains the source of truth across process recreation.
 */
@Composable
private fun OnboardingScreen(
    dictationController: DictationController?,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onShowKeyboardPicker: () -> Unit,
) {
    val viewModel: OnboardingViewModel = hiltViewModel()

    val currentStep by viewModel.currentStep.collectAsState()
    val micGranted by viewModel.micPermissionGranted.collectAsState()
    val selectedLayout by viewModel.selectedLayout.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadComplete by viewModel.modelDownloadComplete.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val recommendedModel = viewModel.recommendedModelInfo
    val modelSizeMb = recommendedModel.expectedSizeBytes / (1024 * 1024)


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
            imeEnabled = imeEnabled,
            imeSelected = imeSelected,
            onOpenSettings = onOpenKeyboardSettings,
            onOpenPicker = onShowKeyboardPicker,
            onNext = { viewModel.advanceStep() },
        )
        4 -> OnboardingModeSelectionScreen(
            selectedLayout = selectedLayout,
            onSelectLayout = { viewModel.setLayout(it) },
            onNext = { viewModel.advanceStep() },
        )
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
        6 -> OnboardingTestRecordingScreen(
            dictationController = dictationController,
            onNext = { viewModel.advanceStep() },
        )
        7 -> OnboardingSuccessScreen(
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
    startDestination: String = AppDestination.Home.route,
    showMainNavigation: Boolean = true,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.Home.route

    // Hide the bottom nav bar when on the Recording screen so it gets full-screen immersion.
    // WHY conditional (not AnimatedVisibility): simple show/hide is sufficient here; there
    // is no animation spec for the nav bar in the design.
    val showBottomBar = showMainNavigation &&
        currentRoute != AppDestination.Recording.route &&
        currentRoute != AppDestination.History.route &&
        currentRoute != AppDestination.Licences.route &&
        currentRoute != AppDestination.DebugLogs.route &&
        currentRoute != AppDestination.SoundSettings.route &&
        !currentRoute.startsWith("sound_picker")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    dataStore = dataStore,
                    onNewDictation = {
                        navController.navigate(AppDestination.Recording.route)
                    },
                    onOpenHistory = {
                        navController.navigate(AppDestination.History.route) { launchSingleTop = true }
                    },
                )
            }
            // History is a Pro feature that has not shipped yet: while the flag is off the
            // route is never registered, so no navigation can reach the screen even by
            // mistake. The implementation stays compiled and unit-tested behind it.
            if (PremiumFlags.HISTORY_VISIBLE) {
                composable(
                    route = AppDestination.History.route,
                    enterTransition = { slideInVertically(initialOffsetY = { it }) },
                    exitTransition = { slideOutVertically(targetOffsetY = { it }) },
                    popEnterTransition = { slideInVertically(initialOffsetY = { -it }) },
                    popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                ) {
                    HistoryRoute(onBack = { navController.popBackStack() })
                }
            }
            composable(AppDestination.Models.route) {
                ModelsScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    onNavigateToLicences = {
                        navController.navigate(AppDestination.Licences.route)
                    },
                    onNavigateToDebugLogs = {
                        navController.navigate(AppDestination.DebugLogs.route)
                    },
                    onNavigateToSoundSettings = {
                        navController.navigate(AppDestination.SoundSettings.route)
                    },
                )
            }
            composable(AppDestination.Licences.route) {
                LicencesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppDestination.DebugLogs.route) {
                DebugLogsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppDestination.SoundSettings.route) {
                SoundSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSoundPicker = { soundType ->
                        navController.navigate(AppDestination.SoundPicker.createRoute(soundType))
                    },
                )
            }
            composable(AppDestination.SoundPicker.route) { backStackEntry ->
                val soundType = backStackEntry.arguments?.getString("soundType") ?: "start"
                SoundPickerScreen(
                    soundType = soundType,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(AppDestination.Recording.route) {
                RecordingScreen(
                    dictationController = dictationController,
                    dataStore = dataStore,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
