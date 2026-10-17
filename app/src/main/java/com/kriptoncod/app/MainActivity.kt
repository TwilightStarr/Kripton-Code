package com.kriptoncod.app

import android.Manifest
import android.content.pm.PackageManager
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kriptoncod.app.ui.navigation.AppRoot
import com.kriptoncod.app.ui.theme.KriptonCodTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            val preference by store.preference.collectAsStateWithLifecycle(initialValue = initial)
            val animationsPref by store.animationsFlow.collectAsStateWithLifecycle(initialValue = initialAnimations)
            // Sistemde "animasyonları kaldır" açıksa uygulama da animasyon oynatmaz.
            val animationsOn = animationsPref && ValueAnimator.areAnimatorsEnabled()
            val scope = rememberCoroutineScope()
            KriptonCodTheme(preference = preference) {
                CompositionLocalProvider(LocalAnimationsEnabled provides animationsOn) {
                    AppRoot(
                        themePreference = preference,
                        onThemeChange = { scope.launch { store.set(it) } },
                    )
                }
            }
        }
    }
}
