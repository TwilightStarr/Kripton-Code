package com.kriptoncod.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.delay

/**
 * Animasyonlar açık mı? Ayarlar > Görünüm > "Animasyonlar" anahtarı ile sistemin "animasyonları kaldır"
 * seçeneğinin birleşimidir. Tüm hareketli bileşenler (logo, yükleme göstergesi, ekran geçişleri, kart girişleri,
 * basma efekti) bunu okur; kapalıyken hiçbir şey hareket etmez.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/** Arka planda bir çalışma sürüyor mu? Logo bunu görünce "çalışıyor" animasyonunu oynatır. */
val LocalAppBusy = compositionLocalOf { false }

/** Kullanıcı açısından "iş yürüyor" sayılan çalışma durumları (duraklatılmış/bitmiş değil). */
fun RunStatus.isInProgress(): Boolean = when (this) {
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING -> true
    RunStatus.PAUSED, RunStatus.DONE, RunStatus.FAILED, RunStatus.CANCELLED -> false
}

/** Aynı anda en fazla bu kadar öğe kademeli girer; sonrakiler gecikmesiz (uzun listeler yavaşlamasın). */
private const val MAX_STAGGER = 8
private const val STAGGER_STEP_MS = 55L

/** Ekrana ilk girişte aşağıdan yukarı kayarak belirir; [index] ile öğeler sırayla (kademeli) girer. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    if (!LocalAnimationsEnabled.current) {
        this
    } else {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(index.coerceIn(0, MAX_STAGGER) * STAGGER_STEP_MS)
            progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        this.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 20.dp.toPx()
        }
    }
}

/** Basılıyken hafifçe küçülür, bırakınca yaylanarak döner. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val on = LocalAnimationsEnabled.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (on && pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Run durumuna göre logonun ruh hâli: iş sürerken çalışır, duraklatılınca bekler, bitince sevinir, hatada sarsılır. */
fun RunStatus.logoMood(): com.kriptoncod.app.ui.components.LogoMood = when (this) {
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING ->
        com.kriptoncod.app.ui.components.LogoMood.Working

    RunStatus.PAUSED -> com.kriptoncod.app.ui.components.LogoMood.Waiting

    RunStatus.DONE -> com.kriptoncod.app.ui.components.LogoMood.Success

    RunStatus.FAILED -> com.kriptoncod.app.ui.components.LogoMood.Error

    RunStatus.CANCELLED -> com.kriptoncod.app.ui.components.LogoMood.Idle
}
