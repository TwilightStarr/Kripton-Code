package com.kriptoncod.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.components.LogoMood
import kotlinx.coroutines.delay
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.kriptoncod.app.ui.screens.UsageScreen
import com.kriptoncod.app.ui.screens.HistoryScreen
import com.kriptoncod.app.ui.screens.HomeScreen
import com.kriptoncod.app.ui.screens.PermissionScreen
import com.kriptoncod.app.ui.screens.RunScreen
import com.kriptoncod.app.ui.screens.PromptEditorScreen
import com.kriptoncod.app.ui.screens.PromptSetScreen
import com.kriptoncod.app.ui.screens.PromptsScreen
import com.kriptoncod.app.ui.screens.SettingsScreen
import com.kriptoncod.app.ui.screens.WebAccountEditScreen
import com.kriptoncod.app.ui.screens.WebAccountScreen
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.ui.adaptive.AdaptiveFrame
import com.kriptoncod.app.ui.adaptive.Adaptive
import com.kriptoncod.app.ui.adaptive.LocalWindowClass
import com.kriptoncod.app.ui.adaptive.WindowClass
import com.kriptoncod.app.ui.adaptive.rememberWindowClass
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import com.kriptoncod.app.ui.theme.LocalAppBusy
import com.kriptoncod.app.ui.theme.isInProgress
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val showNav = currentRoute == null || Route.entries.any { it.path == currentRoute }
    val windowClass = rememberWindowClass()
    val showBottomBar = showNav && windowClass == WindowClass.Compact
    val showSideNav = showNav && windowClass != WindowClass.Compact

    // Bir çalışma sürerken logo (yan menü ve ana ekran) "çalışıyor" animasyonunu oynatır.
    val runs by container.runRepository.observeRuns().collectAsStateWithLifecycle(initialValue = emptyList())
    val busy = runs.any { it.status.isInProgress() }
    val animationsOn = LocalAnimationsEnabled.current

    // Açılış: logo bir kez çizilir, sonra uygulamaya kaybolarak geçilir (animasyonlar kapalıysa atlanır).
    var showIntro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (showIntro) {
            if (animationsOn) delay(1700)
            showIntro = false
        }
    }

    CompositionLocalProvider(LocalWindowClass provides windowClass, LocalAppBusy provides busy) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = scheme.background,
                bottomBar = {
                    if (showBottomBar) {
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
                    }
                },
            ) { inner ->
                Row(Modifier.padding(inner).fillMaxSize()) {
                    if (showSideNav) {
                        SideNavigation(
                            expanded = windowClass == WindowClass.Expanded,
                            isSelected = { route -> backStack?.destination?.hierarchy?.any { it.route == route.path } == true },
                            onNavigate = { route -> navController.navigateTo(route) },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = Route.Home.path,
                            modifier = Modifier.fillMaxSize(),
                            // Animasyonlar kapalıyken ekran geçişleri anında olur.
                            enterTransition = { if (animationsOn) forwardIn() else EnterTransition.None },
                            exitTransition = { if (animationsOn) forwardOut() else ExitTransition.None },
                            popEnterTransition = { if (animationsOn) backIn() else EnterTransition.None },
                            popExitTransition = { if (animationsOn) backOut() else ExitTransition.None },
                        ) {
                            composable(Route.Home.path) {
                                HomeScreen(
                                    onAddAi = { navController.navigate(AisRoutes.ADD) },
                                    onOpenRoute = { route -> navController.navigateTo(route) },
                                    onOpenRun = { id -> navController.navigate(ExtraRoutes.run(id)) },
                                    onOpenSerial = { id -> navController.navigate(ExtraRoutes.serial(id)) },
                                    onOpenUsage = { navController.navigate(ExtraRoutes.USAGE) },
                                )
                            }
                            composable(ExtraRoutes.USAGE) {
                                AdaptiveFrame(Adaptive.FormMaxWidth) { UsageScreen(onBack = { navController.popBackStack() }) }
                            }
                            composable(Route.Ais.path) {
                                AisScreen(
                                    onAdd = { navController.navigate(AisRoutes.ADD) },
                                    onAddWeb = { navController.navigate(AisRoutes.WEB_ADD) },
                                    onQuickAdd = { providerId -> navController.navigate(AisRoutes.webQuick(providerId)) },
                                    onEdit = { id ->
                                        // Web oturumu hesabında "düzenle" = ad düzenleme (yeniden giriş oradan ayrıca açılır); diğerleri anahtar düzenleyicisini açar.
                                        scope.launch {
                                            val web = container.aiAccountRepository.get(id)?.authType == AuthType.WEB_SESSION
                                            navController.navigate(if (web) AisRoutes.webEdit(id) else AisRoutes.edit(id))
                                        }
                                    },
                                )
                            }
                            composable(AisRoutes.WEB_ADD) {
                                AdaptiveFrame(Adaptive.WideFormMaxWidth) {
                                    WebAccountScreen(
                                        accountId = null,
                                        onClose = { navController.popBackStack() },
                                        onUseApiKey = {
                                            navController.popBackStack()
                                            navController.navigate(AisRoutes.ADD)
                                        },
                                    )
                                }
                            }
                            composable(
                                AisRoutes.WEB_QUICK,
                                arguments = listOf(navArgument(AisRoutes.ARG_PROVIDER) { type = NavType.StringType }),
                            ) { entry ->
                                val providerId = entry.arguments?.getString(AisRoutes.ARG_PROVIDER)
                                AdaptiveFrame(Adaptive.WideFormMaxWidth) {
                                    WebAccountScreen(
                                        accountId = null,
                                        initialProvider = providerId,
                                        onClose = { navController.popBackStack() },
                                        onUseApiKey = {
                                            navController.popBackStack()
                                            navController.navigate(AisRoutes.ADD)
                                        },
                                    )
                                }
                            }
                            composable(
                                AisRoutes.WEB_RELOGIN,
                                arguments = listOf(navArgument(AisRoutes.ARG_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val id = entry.arguments?.getLong(AisRoutes.ARG_ID) ?: return@composable
                                AdaptiveFrame(Adaptive.WideFormMaxWidth) { WebAccountScreen(accountId = id, onClose = { navController.popBackStack() }) }
                            }
                            composable(
                                AisRoutes.WEB_EDIT,
                                arguments = listOf(navArgument(AisRoutes.ARG_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val id = entry.arguments?.getLong(AisRoutes.ARG_ID) ?: return@composable
                                AdaptiveFrame(Adaptive.FormMaxWidth) {
                                    WebAccountEditScreen(
                                        accountId = id,
                                        onClose = { navController.popBackStack() },
                                        onRelogin = {
                                            navController.popBackStack()
                                            navController.navigate(AisRoutes.webRelogin(id))
                                        },
                                    )
                                }
                            }
                            composable(AisRoutes.ADD) {
                                AdaptiveFrame(Adaptive.FormMaxWidth) { AccountEditorScreen(accountId = null, onClose = { navController.popBackStack() }) }
                            }
                            composable(
                                AisRoutes.EDIT,
                                arguments = listOf(navArgument(AisRoutes.ARG_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val id = entry.arguments?.getLong(AisRoutes.ARG_ID) ?: return@composable
                                AdaptiveFrame(Adaptive.FormMaxWidth) { AccountEditorScreen(accountId = id, onClose = { navController.popBackStack() }) }
                            }
                            composable(Route.Prompts.path) {
                                PromptsScreen(
                                    onOpenSet = { id -> navController.navigate(PromptRoutes.set(id)) },
                                    onRunStarted = { id -> navController.navigate(ExtraRoutes.run(id)) },
                                    onSerialStarted = { id -> navController.navigate(ExtraRoutes.serial(id)) },
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
                                    onSerialStarted = { id -> navController.navigate(ExtraRoutes.serial(id)) },
                                )
                            }
                            composable(
                                ExtraRoutes.SERIAL,
                                arguments = listOf(navArgument(ExtraRoutes.ARG_SERIAL_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val serialId = entry.arguments?.getLong(ExtraRoutes.ARG_SERIAL_ID) ?: return@composable
                                com.kriptoncod.app.ui.screens.SerialRunScreen(id = serialId, container = container, onBack = { navController.popBackStack() })
                            }
                            composable(
                                PromptRoutes.ADD_PROMPT,
                                arguments = listOf(navArgument(PromptRoutes.ARG_SET_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val setId = entry.arguments?.getLong(PromptRoutes.ARG_SET_ID) ?: return@composable
                                AdaptiveFrame(Adaptive.WideFormMaxWidth) { PromptEditorScreen(setId = setId, promptId = null, onClose = { navController.popBackStack() }) }
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
                                AdaptiveFrame(Adaptive.WideFormMaxWidth) { PromptEditorScreen(setId = setId, promptId = promptId, onClose = { navController.popBackStack() }) }
                            }
                            composable(
                                ExtraRoutes.RUN,
                                arguments = listOf(navArgument(ExtraRoutes.ARG_RUN_ID) { type = NavType.LongType }),
                            ) { entry ->
                                val runId = entry.arguments?.getLong(ExtraRoutes.ARG_RUN_ID) ?: return@composable
                                RunScreen(runId = runId, container = container, onBack = { navController.popBackStack() })
                            }
                            composable(ExtraRoutes.GITHUB) {
                                AdaptiveFrame(Adaptive.FormMaxWidth) { GitHubSettingsScreen(container = container, onBack = { navController.popBackStack() }) }
                            }
                            composable(ExtraRoutes.PERMISSIONS) {
                                AdaptiveFrame(Adaptive.FormMaxWidth) { PermissionScreen(onDone = { navController.popBackStack() }) }
                            }
                            composable(Route.History.path) {
                                HistoryScreen(onOpenRun = { runId -> navController.navigate(ExtraRoutes.run(runId)) })
                            }
                            composable(Route.Settings.path) {
                                AdaptiveFrame(Adaptive.FormMaxWidth) {
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
                }
            }
            AnimatedVisibility(visible = showIntro && animationsOn, enter = EnterTransition.None, exit = fadeOut(tween(350))) {
                Box(Modifier.fillMaxSize().background(scheme.background), contentAlignment = Alignment.Center) {
                    KriptonCodLogo(logoSize = 96.dp, mood = LogoMood.Intro)
                }
            }
        }
    }
}

// Ekran geçişleri: ileri giderken içerik hafifçe sağdan kayarak belirir, geri dönerken tersi; eski ekran hızla solar.
private const val SLIDE_FRACTION = 14

private fun forwardIn(): EnterTransition = fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / SLIDE_FRACTION }

private fun forwardOut(): ExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing))

private fun backIn(): EnterTransition = fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / SLIDE_FRACTION }

private fun backOut(): ExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / SLIDE_FRACTION }

private fun NavHostController.navigateTo(route: Route) {
    navigate(route.path) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
