package com.kriptoncod.app.ui.desktop

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kriptoncod.app.ui.theme.LocalMotionScale
import com.kriptoncod.app.ui.theme.Motion

/** Görünmeyen sekme ekran dışına taşınır (bileşimde kalır: web sayfası, oturum, gezinme durumu kaybolmaz). */
internal const val OFFSCREEN_PX = -30000

/** Açılış animasyonunun başlangıç ölçeği: ikonun boyutuna yakın küçük bir başlangıç. */
private const val REVEAL_FROM_SCALE = 0.12f

/** Sekme geçişinde gelen/giden sekmenin "uzaklaşma" ölçeği (hafif zoom). */
private const val SWITCH_FROM_SCALE = 0.94f

/** Kapanışta pencerenin küçüldüğü ölçek ve aşağı kaydığı mesafe. */
private const val CLOSE_TO_SCALE = 0.82f
private val CLOSE_SLIDE = 64.dp

/**
 * Bir sekmenin içeriğini saran katman; sekmenin görünürlük animasyonunu yönetir. İçerik HER ZAMAN bileşimde kalır
 * (AI web oturumları kaybolmasın); görünmeyen sekme saydam ve ekran dışındadır.
 *
 *  - **Açılış** ([revealOrigin] verilmişse, sekme ilk kez görününce): ikondan merkeze doğru büyür ("expanding reveal");
 *    köşeler yuvarlaktan düze açılır. [revealOrigin] sekme alanına göre kesirli başlangıç noktasıdır (ikonun merkezi).
 *  - **Sekme geçişi**: yatay kayma yok; giden sekme hafif küçülüp solar, gelen hafif büyüyüp belirir (zoom + fade).
 *  - **Kapanış** ([closing]): aşağı doğru küçülerek kaybolur, bitince [onClosed] çağrılır. Görünmeyen (arkadaki) sekme
 *    animasyonsuz hemen kapanır.
 *
 * [background] sekmenin zeminidir (katmanla birlikte hareket eder). Süreler [LocalMotionScale]'den gelir:
 * animasyonlar kapalıysa ya da hız ayarlıysa buna uyar.
 */
@Composable
internal fun TabLayer(
    visible: Boolean,
    closing: Boolean,
    revealOrigin: (() -> TransformOrigin)?,
    onClosed: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotionScale.current
    val originNow by rememberUpdatedState(revealOrigin)
    val onClosedNow by rememberUpdatedState(onClosed)
    // İlk kompozisyonda karar verilir: açılış animasyonu yalnız bu sekme YENİ açıldıysa oynar.
    var revealing by remember { mutableStateOf(revealOrigin != null) }
    val presence = remember { Animatable(if (revealOrigin == null && visible) 1f else 0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(visible, closing) {
        when {
            closing -> {
                // Görünmeyen sekmeyi canlandırmadan kapat.
                if (presence.value > 0.02f) exit.animateTo(1f, motion.spec(Motion.MEDIUM, Motion.EmphasizedAccelerate))
                onClosedNow()
            }

            visible && revealing -> {
                presence.animateTo(1f, motion.spec(Motion.LONG, Motion.EmphasizedDecelerate))
                revealing = false
            }

            visible -> presence.animateTo(1f, motion.spec(Motion.MEDIUM, Motion.Emphasized))

            else -> {
                revealing = false
                presence.animateTo(0f, motion.spec(Motion.SHORT, Motion.EmphasizedAccelerate))
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(if (visible || presence.value > 0.001f) 0 else OFFSCREEN_PX, 0) }
            .zIndex(
                when {
                    closing -> 3f

                    // kapanan sekme küçülürken altındaki komşu görünür
                    visible -> 2f

                    else -> 0f
                },
            )
            .graphicsLayer {
                val p = presence.value
                val g = exit.value
                when {
                    g > 0f -> {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        val s = 1f - (1f - CLOSE_TO_SCALE) * g
                        scaleX = s
                        scaleY = s
                        translationY = CLOSE_SLIDE.toPx() * g
                        alpha = p * (1f - g)
                        clip = true
                        shape = RoundedCornerShape(28.dp.toPx() * g)
                    }

                    revealing -> {
                        transformOrigin = originNow?.invoke() ?: TransformOrigin(0.5f, 1f)
                        val s = REVEAL_FROM_SCALE + (1f - REVEAL_FROM_SCALE) * p
                        scaleX = s
                        scaleY = s
                        // Önce hızla belirir; büyürken tamamen opak olur.
                        alpha = (p * 2.5f).coerceIn(0f, 1f)
                        clip = p < 1f
                        shape = RoundedCornerShape(28.dp.toPx() * (1f - p))
                    }

                    else -> {
                        transformOrigin = TransformOrigin.Center
                        val s = SWITCH_FROM_SCALE + (1f - SWITCH_FROM_SCALE) * p
                        scaleX = s
                        scaleY = s
                        alpha = p
                    }
                }
            }
            // Zemin katmanın İÇİNDE: pencere küçülürken/solarken zemini de onunla birlikte hareket eder.
            .background(background),
    ) { content() }
}

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
