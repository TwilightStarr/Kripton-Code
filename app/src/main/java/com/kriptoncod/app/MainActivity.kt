package com.kriptoncod.app

import android.Manifest
import android.content.pm.PackageManager
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.ui.desktop.DesktopModeHost
import com.kriptoncod.app.ui.theme.KriptonCodTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    /** Ana ekran (HOME) girişiyle açıldı mı: masaüstü modu zorla açılır, geri tuşu uygulamadan çıkarmaz. */
    private var launchedAsHome by mutableStateOf(false)

    private fun isHomeIntent(i: Intent?): Boolean = i?.hasCategory(Intent.CATEGORY_HOME) == true

    /** Başka uygulamadan "Paylaş" ile gelen düz metin: launcher'ın "AI'a sor" sayfasında hazır açılır. */
    private var shareTransient by mutableStateOf(false)

    private fun readShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                com.kriptoncod.app.launcher.LauncherBus.share(text)
                shareTransient = true
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHomeIntent(intent)) launchedAsHome = true
        readShare(intent)
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kriptoncod.app.data.settings.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchedAsHome = isHomeIntent(intent)
        readShare(intent)
        enableEdgeToEdge()
        // Android 13+: bildirim izni ilk açılışta istenir (reddedilirse çalışma yine sürer, yalnız bildirim görünmez).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val store = (application as KriptonCodApp).container.themeStore
        // İlk karede tema yanıp sönmesin diye tek seferlik, küçük bir okuma.
        val initial = runBlocking { store.preference.first() }
        val initialAnimations = runBlocking { store.animationsFlow.first() }
        val initialDesktop = runBlocking { store.desktopModeFlow.first() }
        setContent {
            val preference by store.preference.collectAsStateWithLifecycle(initialValue = initial)
            val animationsPref by store.animationsFlow.collectAsStateWithLifecycle(initialValue = initialAnimations)
            // Sistemde "animasyonları kaldır" açıksa uygulama da animasyon oynatmaz.
            val animationsOn = animationsPref && ValueAnimator.areAnimatorsEnabled()
            val scope = rememberCoroutineScope()
            KriptonCodTheme(preference = preference, animate = animationsOn) {
                CompositionLocalProvider(LocalAnimationsEnabled provides animationsOn) {
                    DesktopModeHost(
                        container = (application as KriptonCodApp).container,
                        initialEnabled = initialDesktop,
                        asHome = launchedAsHome,
                        transientDesktop = shareTransient,
                        themePreference = preference,
                        onThemeChange = { scope.launch { store.set(it) } },
                    )
                }
            }
        }
    }
}
