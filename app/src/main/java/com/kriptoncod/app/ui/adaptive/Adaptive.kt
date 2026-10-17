package com.kriptoncod.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pencere genişliği sınıfı (Material boyut sınıfları): telefon < 600dp ≤ küçük tablet/katlanabilir < 840dp ≤ tablet.
 * Tüm ekranlar yerleşimini buna göre seçer; tek doğruluk kaynağı [windowClassFor]'dur.
 */
enum class WindowClass { Compact, Medium, Expanded }

fun windowClassFor(widthDp: Int): WindowClass = when {
    widthDp < 600 -> WindowClass.Compact
    widthDp < 840 -> WindowClass.Medium
    else -> WindowClass.Expanded
}

@Composable
fun rememberWindowClass(): WindowClass = windowClassFor(LocalConfiguration.current.screenWidthDp)

/** [AppRoot] sağlar; ekranlar `LocalWindowClass.current` ile okur. */
val LocalWindowClass = compositionLocalOf { WindowClass.Compact }

object Adaptive {
    /** Kart ızgarasında bir sütunun asgari genişliği: telefonda 1, tablette 2-3 sütun verir. */
    val CardMinWidth: Dp = 340.dp

    /** Form/ayar ekranlarının tablette azami okunabilir genişliği. */
    val FormMaxWidth: Dp = 720.dp
    val WideFormMaxWidth: Dp = 960.dp

    fun pagePadding(c: WindowClass): Dp = when (c) {
        WindowClass.Compact -> 16.dp
        WindowClass.Medium -> 24.dp
        WindowClass.Expanded -> 32.dp
    }
}

/**
 * Telefonda içeriği olduğu gibi koyar; tablette ortalanmış, azami genişlikli bir sütuna sarar
 * (uzun form satırları ekranı boydan boya kaplamasın).
 */
@Composable
fun AdaptiveFrame(maxWidth: Dp = Adaptive.FormMaxWidth, content: @Composable () -> Unit) {
    if (LocalWindowClass.current == WindowClass.Compact) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
