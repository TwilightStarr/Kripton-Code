package com.kriptoncod.app.ui.desktop

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/** Görünmeyen (küçültülmüş) pencere ekran dışına taşınır (bileşimde kalır: web sayfası, oturum, gezinme durumu kaybolmaz). */
internal const val OFFSCREEN_PX = -30000

/**
 * Sistemin Animatör süre ölçeği (Geliştirici seçenekleri > "Animatör süre ölçeği"): 1 normal, 0,5 iki kat hızlı,
 * 2 iki kat yavaş, 0 kapalı. [follow] false ise 1 döner ve değişiklik izlenmez. Ayar değişince canlı güncellenir.
 */
@Composable
internal fun rememberSystemAnimatorScale(follow: Boolean): Float {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(readAnimatorScale(context)) }
    DisposableEffect(follow, context) {
        if (!follow) return@DisposableEffect onDispose { }
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        scale = readAnimatorScale(context)
        runCatching { resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer) }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return if (follow) scale else 1f
}

private fun readAnimatorScale(context: Context): Float = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)

/**
 * Fare/dokunmatik yüzeyin SAĞ tuşuna basılınca [onClick] çağrılır (uzun basışın yanında, bilgisayar fareli kullanıcılar için
 * "sağ tık menüsü"). Sağ tuş olayı tüketilir; normal dokunma/sol tık etkilenmez.
 */
internal fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier = pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onClick()
            }
        }
    }
}
