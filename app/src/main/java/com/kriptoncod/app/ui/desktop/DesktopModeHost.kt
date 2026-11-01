package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.ThemePreference
import com.kriptoncod.app.ui.navigation.AppRoot
import kotlinx.coroutines.launch

private enum class HostDialog { Enable, Exit }

/**
 * Uygulamanın kökü: masaüstü modu açıksa (ve cihaz tabletse) [DesktopShell], değilse normal [AppRoot].
 * Mod ayarı kalıcıdır; bir kez açılınca uygulama hep masaüstüyle başlar, kapatılana dek.
 */
@Composable
fun DesktopModeHost(
    container: AppContainer,
    initialEnabled: Boolean,
    /** Ana ekran (launcher) olarak açıldı: masaüstü modu zorunlu. */
    asHome: Boolean = false,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
) {
    val store = container.themeStore
    val scope = rememberCoroutineScope()
    val enabled by store.desktopModeFlow.collectAsStateWithLifecycle(initialValue = initialEnabled)
    // Mod her cihazda açılabilir (tablette en rahatı); ana ekran olarak kullanılırken zaten zorunludur.
    val available = true
    var dialog by remember { mutableStateOf<HostDialog?>(null) }
    // Çalışırken mod değişirse açılış animasyonu yeniden oynatılmaz.
    var switched by remember { mutableStateOf(false) }

    LaunchedEffect(asHome, enabled) { if (asHome && !enabled) store.setDesktopMode(true) }
    val controller = remember(available, enabled) {
        DesktopModeController(
            available = available,
            enabled = enabled,
            requestEnable = { dialog = HostDialog.Enable },
            requestExit = { dialog = HostDialog.Exit },
        )
    }

    CompositionLocalProvider(LocalDesktopMode provides controller) {
        Box(Modifier.fillMaxSize()) {
            if ((enabled || asHome) && available) {
                DesktopShell(container = container, themePreference = themePreference, onThemeChange = onThemeChange, isHome = asHome)
            } else {
                AppRoot(themePreference = themePreference, onThemeChange = onThemeChange, playIntro = !switched)
            }
            when (dialog) {
                HostDialog.Enable -> DesktopConfirm(
                    title = stringResource(R.string.desktop_enable_title),
                    body = stringResource(R.string.desktop_enable_body),
                    confirmText = stringResource(R.string.desktop_enable_confirm),
                    dismissText = stringResource(R.string.action_cancel),
                    onConfirm = {
                        dialog = null
                        switched = true
                        scope.launch { store.setDesktopMode(true) }
                    },
                    onDismiss = { dialog = null },
                )

                HostDialog.Exit -> DesktopConfirm(
                    title = stringResource(R.string.desktop_exit_title),
                    body = stringResource(R.string.desktop_exit_body),
                    confirmText = stringResource(R.string.desktop_exit_confirm),
                    dismissText = stringResource(R.string.action_cancel),
                    onConfirm = {
                        dialog = null
                        switched = true
                        scope.launch { store.setDesktopMode(false) }
                    },
                    onDismiss = { dialog = null },
                )

                null -> Unit
            }
        }
    }
}
