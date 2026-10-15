package com.kriptoncod.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.ui.screens.AisScreen
import com.kriptoncod.app.ui.screens.HistoryScreen
import com.kriptoncod.app.ui.screens.HomeScreen
import com.kriptoncod.app.ui.screens.PromptsScreen
import com.kriptoncod.app.ui.screens.SettingsScreen
import com.kriptoncod.app.ui.theme.KriptonTheme

@Composable
fun AppRoot(
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.5.dp, color = KriptonTheme.colors.nodeBorder)
                NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
                    Route.entries.forEach { route ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTo(route) },
                            icon = { Icon(route.icon, contentDescription = null) },
                            label = {
                                Text(stringResource(route.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = KriptonTheme.colors.nodeFill,
                                selectedIconColor = scheme.primary,
                                selectedTextColor = scheme.primary,
                                unselectedIconColor = KriptonTheme.colors.muted,
                                unselectedTextColor = KriptonTheme.colors.muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(inner),
        ) {
            composable(Route.Home.path) { HomeScreen(onAddAi = { navController.navigateTo(Route.Ais) }) }
            composable(Route.Ais.path) { AisScreen() }
            composable(Route.Prompts.path) { PromptsScreen() }
            composable(Route.History.path) { HistoryScreen() }
            composable(Route.Settings.path) {
                SettingsScreen(themePreference = themePreference, onThemeChange = onThemeChange)
            }
        }
    }
}

private fun NavHostController.navigateTo(route: Route) {
    navigate(route.path) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
