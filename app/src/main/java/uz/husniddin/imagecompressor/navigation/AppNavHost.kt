package uz.husniddin.imagecompressor.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.reflect.KClass
import uz.husniddin.imagecompressor.ui.comparison.ComparisonRouteScreen
import uz.husniddin.imagecompressor.ui.configure.ConfigureRoute as ConfigureDestination
import uz.husniddin.imagecompressor.ui.history.HistoryRoute as HistoryDestination
import uz.husniddin.imagecompressor.ui.home.HomeRoute as HomeDestination
import uz.husniddin.imagecompressor.ui.processing.ProcessingRoute as ProcessingDestination
import uz.husniddin.imagecompressor.ui.results.ResultsRoute as ResultsDestination
import uz.husniddin.imagecompressor.ui.selection.SelectionRoute as SelectionDestination
import uz.husniddin.imagecompressor.ui.settings.SettingsRoute as SettingsDestination

private enum class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    COMPRESS(HomeRoute, HomeRoute::class, "Compress", Icons.Outlined.Compress, Icons.Filled.Compress),
    HISTORY(HistoryRoute, HistoryRoute::class, "History", Icons.Outlined.History, Icons.Filled.History),
    SETTINGS(SettingsRoute, SettingsRoute::class, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Switches tabs like a bottom bar should: one copy per tab, state saved and restored. */
private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isTab(tab: Tab): Boolean = this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true

/** "uz.….navigation.ComparisonRoute/{itemId}" → "Comparison" (no arguments, no user data). */
private fun NavDestination.screenName(): String? =
    route?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('.')?.removeSuffix("Route")

@Composable
fun AppNavHost(
    onScreenView: (String) -> Unit = {},
    startDestination: Any = HomeRoute,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val screenName = destination?.screenName()
    LaunchedEffect(screenName) { screenName?.let(onScreenView) }
    val showTabs = Tab.entries.any { destination.isTab(it) }

    val goHome: () -> Unit = {
        navController.navigate(HomeRoute) { popUpTo(HomeRoute) { inclusive = true } }
    }

    Scaffold(
        // Screens handle system insets themselves; this Scaffold only reserves room for the tab bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = destination.isTab(tab)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeDestination(
                    onImagesPicked = { navController.navigate(SelectionRoute) { launchSingleTop = true } },
                    onOpenHistory = { navController.navigateToTab(Tab.HISTORY) },
                )
            }
            composable<HistoryRoute> { HistoryDestination() }
            composable<SettingsRoute> { SettingsDestination() }
            composable<SelectionRoute> {
                SelectionDestination(
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate(ConfigureRoute) { launchSingleTop = true } },
                )
            }
            composable<ConfigureRoute> {
                ConfigureDestination(
                    onBack = { navController.popBackStack() },
                    onStarted = { navController.navigate(ProcessingRoute) { launchSingleTop = true } },
                )
            }
            composable<ProcessingRoute> {
                ProcessingDestination(
                    onFinished = {
                        navController.navigate(ResultsRoute) {
                            popUpTo(ProcessingRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onHome = goHome,
                )
            }
            composable<ResultsRoute> {
                ResultsDestination(
                    // Back returns to the settings so the user can tweak and re-run.
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id -> navController.navigate(ComparisonRoute(id)) },
                    onDone = goHome,
                )
            }
            composable<ComparisonRoute> {
                ComparisonRouteScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
