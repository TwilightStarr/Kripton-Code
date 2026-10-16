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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.ui.screens.AccountEditorScreen
import com.kriptoncod.app.ui.screens.AisScreen
import com.kriptoncod.app.ui.screens.GitHubSettingsScreen
import com.kriptoncod.app.ui.screens.HistoryScreen
import com.kriptoncod.app.ui.screens.HomeScreen
import com.kriptoncod.app.ui.screens.PermissionScreen
import com.kriptoncod.app.ui.screens.RunScreen
import com.kriptoncod.app.ui.screens.PromptEditorScreen
import com.kriptoncod.app.ui.screens.PromptSetScreen
import com.kriptoncod.app.ui.screens.PromptsScreen
import com.kriptoncod.app.ui.screens.SettingsScreen
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container: AppContainer = (context.applicationContext as KriptonCodApp).container
    val googleSettings =
        (context.applicationContext as KriptonCodApp).container.googleAuthSettings
    var googleClientId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(googleSettings) {
        googleClientId = googleSettings.clientIdFlow.first().orEmpty()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val scheme = MaterialTheme.colorScheme
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute == null || Route.entries.any { it.path == currentRoute }

    Scaffold(
        containerColor = scheme.background,
        bottomBar = {
            if (showBottomBar) Column {
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
            composable(Route.Home.path) { HomeScreen(onAddAi = { navController.navigate(AisRoutes.ADD) }) }
            composable(Route.Ais.path) {
                AisScreen(
                    onAdd = { navController.navigate(AisRoutes.ADD) },
                    onEdit = { id -> navController.navigate(AisRoutes.edit(id)) },
                )
            }
            composable(AisRoutes.ADD) {
                AccountEditorScreen(accountId = null, onClose = { navController.popBackStack() })
            }
            composable(
                AisRoutes.EDIT,
                arguments = listOf(navArgument(AisRoutes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(AisRoutes.ARG_ID) ?: return@composable
                AccountEditorScreen(accountId = id, onClose = { navController.popBackStack() })
            }
            composable(Route.Prompts.path) {
                PromptsScreen(
                    onOpenSet = { id -> navController.navigate(PromptRoutes.set(id)) },
                    onRunStarted = { id -> navController.navigate(ExtraRoutes.run(id)) },
                )
            }
            composable(
                PromptRoutes.SET,
                arguments = listOf(navArgument(PromptRoutes.ARG_SET_ID) { type = NavType.LongType }),
            ) { entry ->
                val setId = entry.arguments?.getLong(PromptRoutes.ARG_SET_ID) ?: return@composable
                PromptSetScreen(
                    setId = setId,
                    onBack = { navController.popBackStack() },
                    onAddPrompt = { navController.navigate(PromptRoutes.addPrompt(setId)) },
                    onEditPrompt = { promptId -> navController.navigate(PromptRoutes.editPrompt(setId, promptId)) },
                    onRunStarted = { runId -> navController.navigate(ExtraRoutes.run(runId)) },
                )
            }
            composable(
                PromptRoutes.ADD_PROMPT,
                arguments = listOf(navArgument(PromptRoutes.ARG_SET_ID) { type = NavType.LongType }),
            ) { entry ->
                val setId = entry.arguments?.getLong(PromptRoutes.ARG_SET_ID) ?: return@composable
                PromptEditorScreen(setId = setId, promptId = null, onClose = { navController.popBackStack() })
            }
            composable(
                PromptRoutes.EDIT_PROMPT,
                arguments = listOf(
                    navArgument(PromptRoutes.ARG_SET_ID) { type = NavType.LongType },
                    navArgument(PromptRoutes.ARG_PROMPT_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                val setId = entry.arguments?.getLong(PromptRoutes.ARG_SET_ID) ?: return@composable
                val promptId = entry.arguments?.getLong(PromptRoutes.ARG_PROMPT_ID) ?: return@composable
                PromptEditorScreen(setId = setId, promptId = promptId, onClose = { navController.popBackStack() })
            }
            composable(
                ExtraRoutes.RUN,
                arguments = listOf(navArgument(ExtraRoutes.ARG_RUN_ID) { type = NavType.LongType }),
            ) { entry ->
                val runId = entry.arguments?.getLong(ExtraRoutes.ARG_RUN_ID) ?: return@composable
                RunScreen(runId = runId, container = container, onBack = { navController.popBackStack() })
            }
            composable(ExtraRoutes.GITHUB) {
                GitHubSettingsScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(ExtraRoutes.PERMISSIONS) {
                PermissionScreen(onDone = { navController.popBackStack() })
            }
            composable(Route.History.path) {
                HistoryScreen(onOpenRun = { runId -> navController.navigate(ExtraRoutes.run(runId)) })
            }
            composable(Route.Settings.path) {
                SettingsScreen(
                    themePreference = themePreference,
                    onThemeChange = onThemeChange,
                    googleClientId = googleClientId,
                    onGoogleClientIdChange = { value ->
                        googleClientId = value
                        scope.launch { googleSettings.setClientId(value) }
                    },
                    onOpenGitHub = { navController.navigate(ExtraRoutes.GITHUB) },
                    onOpenPermissions = { navController.navigate(ExtraRoutes.PERMISSIONS) },
                )
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
