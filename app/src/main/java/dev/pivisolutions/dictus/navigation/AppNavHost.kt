package dev.pivisolutions.dictus.navigation

import androidx.compose.foundation.background
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pivisolutions.dictus.core.preferences.PreferenceKeys
import dev.pivisolutions.dictus.core.service.DictationController
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.home.HomeScreen
import dev.pivisolutions.dictus.models.ModelsScreen
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
            // Onboarding not complete — placeholder until Plan 03 implements it
            OnboardingPlaceholder(
                dataStore = dataStore,
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
 * Temporary onboarding placeholder shown to first-time users.
 *
 * Plan 03 will replace this with the full 6-step onboarding flow.
 * For now, a "Skip onboarding" button writes HAS_COMPLETED_ONBOARDING = true
 * so developers can reach the main tabs during Phase 4 development.
 */
@Composable
private fun OnboardingPlaceholder(
    dataStore: DataStore<Preferences>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DictusColors.Background)
    )
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
                SettingsScreen(
                    dataStore = dataStore,
                    imeEnabled = imeEnabled,
                    imeSelected = imeSelected,
                    onOpenKeyboardSettings = onOpenKeyboardSettings,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
        }
    }
}
